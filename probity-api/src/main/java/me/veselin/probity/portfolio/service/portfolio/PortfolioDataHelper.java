package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.portfolio.domain.PortfolioPosition;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Facade for portfolio data operations, delegating to specialized components.
 * Maintains backward compatibility while providing clean separation of concerns.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PortfolioDataHelper {

    private final PortfolioPriceFetcher priceFetcher;
    private final PortfolioTimeSeriesBuilder timeSeriesBuilder;
    private final PortfolioRiskMetricCalculator riskCalculator;

    // Re-export constants for backward compatibility
    static final int PORTFOLIO_LOOKBACK_DAYS = PortfolioRiskMetricCalculator.PORTFOLIO_LOOKBACK_DAYS;
    static final int SPARKLINE_POINTS = PortfolioRiskMetricCalculator.SPARKLINE_POINTS;
    static final double TRADING_DAYS = PortfolioRiskMetricCalculator.TRADING_DAYS;

    // Re-export time series record for backward compatibility
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

    // Delegate methods to specialized components

    PortfolioTimeSeries buildTimeSeries(
            List<PortfolioPosition> positions, LocalDate from, LocalDate to) {
        PortfolioTimeSeriesBuilder.PortfolioTimeSeries ts = timeSeriesBuilder.buildTimeSeries(positions, from, to);
        return new PortfolioTimeSeries(ts.barsByTicker(), ts.values(), ts.dates(), ts.returns());
    }

    double safeVolatility(List<Double> returns) {
        return riskCalculator.safeVolatility(returns);
    }

    double safeSharpe(List<Double> returns) {
        return riskCalculator.safeSharpe(returns);
    }

    List<Double> buildRollingMetric(
            List<Double> values, int points, java.util.function.Function<List<Double>, Double> metric) {
        return riskCalculator.buildRollingMetric(values, points, metric);
    }

    Map<String, Double> fetchLatestPrices(List<PortfolioPosition> positions) {
        return priceFetcher.fetchLatestPrices(positions);
    }

    Map<String, List<PriceBar>> fetchBars(
            List<PortfolioPosition> positions, LocalDate from, LocalDate to) {
        return priceFetcher.fetchBars(positions, from, to);
    }

    double computeTotalValue(
            List<PortfolioPosition> positions, Map<String, Double> priceByTicker) {
        return priceFetcher.computeTotalValue(positions, priceByTicker);
    }

    List<Double> buildDailyReturns(
            List<PortfolioPosition> positions,
            Map<String, List<PriceBar>> barsByTicker) {
        // This is now internal to PortfolioTimeSeriesBuilder
        // For backward compatibility, we delegate through the time series builder
        PortfolioTimeSeries ts = buildTimeSeries(positions,
                barsByTicker.values().stream().findFirst().map(bars -> bars.getFirst().getBarDate()).orElse(LocalDate.now()),
                barsByTicker.values().stream().flatMap(List::stream)
                        .map(PriceBar::getBarDate)
                        .max(LocalDate::compareTo)
                        .orElse(LocalDate.now()));
        return ts.returns();
    }
}