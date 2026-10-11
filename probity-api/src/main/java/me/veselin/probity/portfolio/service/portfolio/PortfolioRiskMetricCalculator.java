package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.risk.port.RiskPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Handles mathematical risk metrics calculations for portfolio analysis.
 * Provides safe wrappers around risk port operations with minimum data requirements.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PortfolioRiskMetricCalculator {

    static final int PORTFOLIO_LOOKBACK_DAYS = 30; // Historical price window for standard analysis
    static final int SPARKLINE_POINTS = 7;          // Number of points in sparkline downsampling
    static final double TRADING_DAYS = 252.0;       // Trading days per year for volatility scaling

    private final RiskPort riskPort;

    /**
     * Returns annualised volatility, or 0.0 when there are fewer than 2 return points.
     *
     * @param returns list of daily returns
     * @return annualised volatility percentage
     */
    public double safeVolatility(List<Double> returns) {
        return returns.size() >= 2 ? riskPort.annualisedVolatility(returns) : 0.0;
    }

    /**
     * Returns Sharpe ratio, or 0.0 when there are fewer than 2 return points.
     *
     * @param returns list of daily returns
     * @return Sharpe ratio
     */
    public double safeSharpe(List<Double> returns) {
        return returns.size() >= 2 ? riskPort.sharpeRatio(returns) : 0.0;
    }

    /**
     * Builds a rolling metric over a time series by computing the metric
     * over sliding windows and downsampling to the requested number of points.
     *
     * @param values the time series values
     * @param points number of output points
     * @param metric the metric function to apply to each window
     * @return list of metric values at each point (null if insufficient data)
     */
    public List<Double> buildRollingMetric(
            List<Double> values, int points, Function<List<Double>, Double> metric) {

        if (values.size() < 2) return Collections.nCopies(points, null);

        List<Double> result = new ArrayList<>(points);
        double step = (double) (values.size() - 1) / (points - 1);

        for (int i = 0; i < points; i++) {
            int end = Math.min((int) Math.round(i * step) + 1, values.size());
            int start = Math.max(0, end - 30);
            List<Double> slice = values.subList(start, end);
            result.add(slice.size() < 2 ? null : metric.apply(slice));
        }
        return result;
    }
}
