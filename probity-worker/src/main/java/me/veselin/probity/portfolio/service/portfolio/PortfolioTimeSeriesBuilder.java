package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.util.DataUtil;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.portfolio.domain.PortfolioPosition;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Handles time-series array assembly for portfolio analysis.
 * Builds portfolio value series, dates, and daily returns from price bars.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PortfolioTimeSeriesBuilder {

    private final PortfolioPriceFetcher priceFetcher;

    /**
     * Bundles the bars, portfolio value series, dates, and daily returns for a
     * given position list and date window into a single value object.
     * Zero-value dates (holidays / corrupt bars) are stripped before returning.
     */
    record PortfolioTimeSeries(
            Map<String, List<PriceBar>> barsByTicker,
            List<Double> values,
            List<LocalDate> dates,
            List<Double> returns
    ) {
        boolean isEmpty() {
            return values.isEmpty() || returns.isEmpty();
        }
    }

    /**
     * Builds a complete time series for the portfolio including bars, values, dates, and returns.
     *
     * @param positions the portfolio positions
     * @param from start date
     * @param to end date
     * @return PortfolioTimeSeries containing all time series data
     */
    public PortfolioTimeSeries buildTimeSeries(
            List<PortfolioPosition> positions, LocalDate from, LocalDate to) {

        Map<String, List<PriceBar>> barsByTicker = priceFetcher.fetchBars(positions, from, to);

        Map<LocalDate, Double> valueByDate = DataUtil.buildValueSeries(positions, barsByTicker);
        // Strip zero-value dates — these are holidays or corrupt bars where
        // no position had valid price data, and would distort return calculations.
        valueByDate.entrySet().removeIf(e -> e.getValue() == 0.0);

        List<Double> values = new ArrayList<>(valueByDate.values());
        List<LocalDate> dates = new ArrayList<>(valueByDate.keySet());
        List<Double> returns = buildDailyReturns(positions, barsByTicker);

        return new PortfolioTimeSeries(barsByTicker, values, dates, returns);
    }

    /**
     * Builds daily portfolio returns from historical price bars.
     * Uses weighted returns based on position weights at each time step.
     *
     * @param positions the portfolio positions
     * @param barsByTicker map of ticker to price bars
     * @return list of daily portfolio returns
     */
    private List<Double> buildDailyReturns(
            List<PortfolioPosition> positions,
            Map<String, List<PriceBar>> barsByTicker) {

        log.debug("buildDailyReturns tickers={} barCounts={}",
                barsByTicker.keySet(),
                barsByTicker.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));

        if (barsByTicker.isEmpty()) {
            log.warn("No bar data available for return calculation");
            return List.of();
        }

        // Build per-ticker date→bar lookup for O(1) date access.
        Map<String, Map<LocalDate, PriceBar>> barMapByTicker = new LinkedHashMap<>();

        for (Map.Entry<String, List<PriceBar>> entry : barsByTicker.entrySet()) {
            if (entry.getValue().size() < 2) {
                log.warn("Insufficient bars for ticker={} ({}), excluding from returns",
                        entry.getKey(), entry.getValue().size());
                continue;
            }
            Map<LocalDate, PriceBar> dateMap = new LinkedHashMap<>();
            for (PriceBar bar : entry.getValue()) {
                dateMap.put(bar.getBarDate(), bar);
            }
            barMapByTicker.put(entry.getKey(), dateMap);
        }

        if (barMapByTicker.isEmpty()) return List.of();

        // Intersection of all dates so every return is computed on the same day
        // across all tickers — eliminates cross-ticker date misalignment.
        Set<LocalDate> commonDates = null;
        for (Map<LocalDate, PriceBar> dateMap : barMapByTicker.values()) {
            if (commonDates == null) {
                commonDates = new TreeSet<>(dateMap.keySet());
            } else {
                commonDates.retainAll(dateMap.keySet());
            }
        }

        if (commonDates.size() < 2) {
            log.warn("Fewer than 2 common trading days across tickers — cannot compute returns");
            return List.of();
        }

        List<LocalDate> sortedDates = new ArrayList<>(commonDates);
        List<Double> portfolioReturns = new ArrayList<>(sortedDates.size() - 1);

        for (int i = 1; i < sortedDates.size(); i++) {
            LocalDate prevDate = sortedDates.get(i - 1);
            LocalDate currDate = sortedDates.get(i);

            // Use centralized portfolio value calculation for weight computation
            double totalPrevValue = priceFetcher.computeTotalValueAtDate(positions, barsByTicker, prevDate);

            if (totalPrevValue == 0.0) {
                log.warn("Zero portfolio value on date={}, skipping return calculation", prevDate);
                portfolioReturns.add(0.0);
                continue;
            }

            // Second pass: weighted return for this day.
            double weightedReturn = 0.0;

            for (PortfolioPosition pos : positions) {
                Map<LocalDate, PriceBar> dateMap = barMapByTicker.get(pos.getAsset().getTicker());
                if (dateMap == null) continue;

                PriceBar prev = dateMap.get(prevDate);
                PriceBar curr = dateMap.get(currDate);
                if (prev == null || curr == null) {
                    log.debug("Missing price data for ticker={} on date range={} - {}, skipping position",
                            pos.getAsset().getTicker(), prevDate, currDate);
                    continue;
                }

                double posWeight = (prev.getAdjClose().doubleValue()
                        * pos.getQuantity().doubleValue()) / totalPrevValue;

                weightedReturn += posWeight * curr.dailyReturn(prev);
            }

            portfolioReturns.add(weightedReturn);
        }

        return portfolioReturns;
    }
}
