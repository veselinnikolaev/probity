package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.domain.PortfolioPosition;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Coordinates historical and latest price fetching for portfolio positions.
 * Handles deduplication and error handling for market data retrieval.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PortfolioPriceFetcher {

    private final MarketDataPort marketDataPort;

    private final Executor ioExecutor;

    /**
     * Fetches the latest prices for all distinct tickers in the portfolio.
     *
     * @param positions the portfolio positions
     * @return map of ticker to latest price (as double), with 0.0 for failed fetches
     */
    public Map<String, Double> fetchLatestPrices(List<PortfolioPosition> positions) {
        return positions.stream()
                .map(pos -> pos.getAsset().getTicker())
                .distinct()
                .collect(Collectors.toMap(
                        ticker -> ticker,
                        ticker -> {
                            try {
                                return marketDataPort.getLatestPrice(ticker).doubleValue();
                            } catch (Exception e) {
                                log.error("Failed to fetch latest price ticker={}", ticker, e);
                                return 0.0;
                            }
                        }
                ));
    }

    /**
     * Fetches historical price bars for all distinct tickers in the portfolio.
     * Uses concurrent fetching with virtual threads to eliminate N+1 query problem.
     *
     * @param positions the portfolio positions
     * @param from start date
     * @param to end date
     * @return map of ticker to list of price bars
     */
    public Map<String, List<PriceBar>> fetchBars(
            List<PortfolioPosition> positions, LocalDate from, LocalDate to) {

        log.debug("fetchBars tickers={} from={} to={}",
                positions.stream().map(p -> p.getAsset().getTicker()).toList(), from, to);

        // Get distinct tickers
        List<String> distinctTickers = positions.stream()
                .map(pos -> pos.getAsset().getTicker())
                .distinct()
                .toList();

        // Fetch all tickers concurrently using virtual threads
        List<CompletableFuture<Map.Entry<String, List<PriceBar>>>> futures = distinctTickers.stream()
                .map(ticker -> CompletableFuture.supplyAsync(() -> {
                    try {
                        List<PriceBar> bars = marketDataPort.getHistoricalBars(ticker, from, to);
                        log.info("fetchBars ticker={} bars.size()={}", ticker, bars == null ? "null" : bars.size());
                        if (bars != null && !bars.isEmpty()) {
                            return Map.entry(ticker, bars);
                        } else {
                            log.warn("No bars returned for ticker={} range={} - {}", ticker, from, to);
                            return null;
                        }
                    } catch (Exception e) {
                        log.error("Failed to fetch bars ticker={} range={} - {}", ticker, from, to, e);
                        return null;
                    }
                }, ioExecutor))
                .toList();

        // Collect results maintaining order
        Map<String, List<PriceBar>> result = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, List<PriceBar>>> future : futures) {
            Map.Entry<String, List<PriceBar>> entry = future.join();
            if (entry != null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    /**
     * Computes the total portfolio value given positions and their latest prices.
     *
     * @param positions the portfolio positions
     * @param priceByTicker map of ticker to latest price
     * @return total portfolio value
     */
    public double computeTotalValue(
            List<PortfolioPosition> positions, Map<String, Double> priceByTicker) {

        return positions.stream().mapToDouble(pos -> {
            double price = priceByTicker.getOrDefault(pos.getAsset().getTicker(), 0.0);
            return price * pos.getQuantity().doubleValue();
        }).sum();
    }

    /**
     * Computes the total portfolio value at a specific date using historical price bars.
     *
     * @param positions the portfolio positions
     * @param barsByTicker map of ticker to list of price bars
     * @param date the date to calculate value for
     * @return total portfolio value at the specified date
     */
    public double computeTotalValueAtDate(
            List<PortfolioPosition> positions,
            Map<String, List<PriceBar>> barsByTicker,
            LocalDate date) {

        return positions.stream().mapToDouble(pos -> {
            List<PriceBar> bars = barsByTicker.get(pos.getAsset().getTicker());
            if (bars == null || bars.isEmpty()) return 0.0;

            // Find the bar closest to the target date (or the last available date)
            PriceBar bar = bars.stream()
                    .filter(b -> !b.getBarDate().isAfter(date))
                    .max(java.util.Comparator.comparing(PriceBar::getBarDate))
                    .orElse(bars.getLast());

            return bar.getAdjClose().doubleValue() * pos.getQuantity().doubleValue();
        }).sum();
    }
}
