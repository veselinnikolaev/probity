package me.veselin.probity.risk.port;

import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.risk.dto.AssetRiskProfile;
import me.veselin.probity.risk.dto.DistributionStatistics;
import me.veselin.probity.risk.dto.PositionWeight;

import java.util.List;
import java.util.Map;

/**
 * Outbound risk-calculation port exposing reusable quantitative metrics.
 */
public interface RiskPort {

    /**
     * Annualised volatility as a percentage.
     * e.g. 14.2 means 14.2% annualised vol.
     */
    double annualisedVolatility(List<Double> dailyReturns);

    /**
     * Sharpe ratio annualised, using configurable risk-free rate.
     */
    double sharpeRatio(List<Double> dailyReturns);

    /**
     * Parametric VaR at 95% confidence, one-day horizon.
     * Returns absolute dollar value.
     */
    double var95(double portfolioValue, List<Double> dailyReturns);

    /**
     * Rolling annualised volatility over a sliding window.
     * Input is a price series (not returns) — conversion handled internally.
     * Returns one vol value per price point.
     */
    List<Double> rollingVolatility(List<Double> priceSeries, int windowDays);

    List<Double> rollingVolatilityFromReturns(List<Double> dailyReturns, int windowDays);

    /**
     * Converts a price series to daily log returns.
     * Exposed here so callers don't reimplement it.
     */
    List<Double> toDailyReturns(List<Double> priceSeries);

    /**
     * Risk score 0–100 combining asset type and volatility.
     */
    int riskScore(AssetRiskProfile profile, double annualisedVol);

    /**
     * Human-readable risk level from a score.
     */
    String riskLevel(int score);

    /**
     * Max drawdown as a percentage of portfolio value.
     * Input raw values, not returns.
     * Returns percentage of portfolio value, or 0 if no drawdown.
     */
    double maxDrawdown(List<Double> portfolioValues);

    /**
     * Herfindahl–Hirschman Index — sum of squared weights.
     * Ranges 0 (perfectly diversified) to 1 (single position).
     */
    double computeHHI(List<PositionWeight> positions,
                      Map<String, List<PriceBar>> barsByTicker);

    /**
     * Pearson correlation coefficient between two return series.
     * Returns value in [-1, 1].
     */
    double pearsonCorrelation(List<Double> x, List<Double> y);

    // ── Monte Carlo / Distribution math ──────────────────────────────────

    /**
     * Descriptive statistics over a pre-sorted array of final simulation values.
     * Used by MonteCarloSimulationService to avoid duplicating mean/stdDev/median logic.
     *
     * @param sortedValues ascending-sorted array of simulated final portfolio values
     * @return DistributionStatistics record (mean, median, stdDev, min, max)
     */
    DistributionStatistics distributionStatistics(double[] sortedValues);

    /**
     * Conditional Value at Risk (Expected Shortfall) at an arbitrary confidence level.
     * Returns the average of simulated values that fall below the VaR threshold —
     * i.e. the expected loss given that we are in the tail.
     *
     * @param sortedValues    ascending-sorted array of simulated final portfolio values
     * @param confidenceLevel e.g. 0.95 for 95%
     * @return CVaR as an absolute portfolio value (not a loss delta)
     */
    double conditionalValueAtRisk(double[] sortedValues, double confidenceLevel);

    /**
     * Value at Risk from a sorted simulation distribution at an arbitrary confidence level.
     * Distinct from {@link #var95} which uses a parametric (normal) formula —
     * this one reads directly from the empirical sorted distribution.
     *
     * @param sortedValues    ascending-sorted array of simulated final portfolio values
     * @param confidenceLevel e.g. 0.95 for 95%
     * @return the portfolio value at the VaR threshold
     */
    double simulationVaR(double[] sortedValues, double confidenceLevel);
}