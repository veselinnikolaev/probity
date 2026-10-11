package me.veselin.probity.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.config.TradingConfiguration;
import me.veselin.probity.common.util.DataUtil;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.risk.dto.AssetRiskProfile;
import me.veselin.probity.risk.dto.DistributionStatistics;
import me.veselin.probity.risk.dto.PositionWeight;
import me.veselin.probity.risk.enumeration.RiskLevel;
import me.veselin.probity.risk.port.RiskPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared kernel component providing financial risk calculations.
 *
 * This service implements the RiskPort interface and is used across multiple
 * bounded contexts (portfolio, simulation). It provides pure mathematical
 * risk calculations that are domain-agnostic, making it suitable as a shared
 * kernel component.
 *
 * <p><strong>Architecture Note:</strong> This is intentionally placed in the
 * risk package as a shared kernel because:
 * <ul>
 *   <li>Risk calculations are mathematical operations with no domain-specific business rules</li>
 *   <li>Both portfolio and simulation contexts require identical risk metric calculations</li>
 *   <li>Duplicating these calculations would violate DRY and introduce inconsistency risks</li>
 * </ul>
 *
 * <p><strong>Coupling Considerations:</strong> Some methods (e.g., computeHHI, riskScore) have
 * dependencies on portfolio domain types (PortfolioPosition, AssetType). These represent
 * acceptable coupling since the calculations are inherently portfolio-specific. Pure
 * mathematical methods (volatility, Sharpe, VaR, correlation) are fully domain-agnostic.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskCalculator implements RiskPort {

    private final TradingConfiguration tradingConfig;

    @Override
    public double annualisedVolatility(List<Double> dailyReturns) {
        if (dailyReturns.size() < 2) return 0.0;
        double mean = DataUtil.mean(dailyReturns);
        double variance = DataUtil.variance(dailyReturns, mean);
        return Math.sqrt(variance) * Math.sqrt(tradingConfig.getTradingDaysPerYear()) * 100;
    }

    @Override
    public double sharpeRatio(List<Double> dailyReturns) {
        if (dailyReturns.size() < 2) return 0.0;
        double mean = DataUtil.mean(dailyReturns);
        double stdDev = Math.sqrt(DataUtil.variance(dailyReturns, mean));
        if (stdDev == 0) {
            log.warn("Zero standard deviation detected in Sharpe ratio calculation - returns may be constant");
            return 0.0;
        }
        double tradingDays = tradingConfig.getTradingDaysPerYear();
        double riskFreeDaily = tradingConfig.getRiskFreeRate() / tradingDays;
        return ((mean - riskFreeDaily) / stdDev) * Math.sqrt(tradingDays);
    }

    @Override
    public double var95(double portfolioValue, List<Double> dailyReturns) {
        if (dailyReturns.size() < 2) return 0.0;
        double mean = DataUtil.mean(dailyReturns);
        double stdDev = Math.sqrt(DataUtil.variance(dailyReturns, mean));
        return portfolioValue * tradingConfig.getZScore95() * stdDev;
    }

    @Override
    public List<Double> rollingVolatility(List<Double> priceSeries, int windowDays) {
        List<Double> returns = toDailyReturns(priceSeries);
        return calculateRollingVolatility(returns, windowDays);
    }

    @Override
    public List<Double> rollingVolatilityFromReturns(List<Double> dailyReturns, int windowDays) {
        return calculateRollingVolatility(dailyReturns, windowDays);
    }

    @Override
    public List<Double> toDailyReturns(List<Double> priceSeries) {
        if (priceSeries.size() < 2) return List.of();
        var result = new ArrayList<Double>(priceSeries.size() - 1);
        for (int i = 1; i < priceSeries.size(); i++) {
            double prev = priceSeries.get(i - 1);
            if (prev == 0) continue;
            result.add((priceSeries.get(i) - prev) / prev);
        }
        return result;
    }

    @Override
    public int riskScore(AssetRiskProfile profile, double annualisedVol) {
        int base = profile.type().getBaseRiskScore();
        int volAddon = annualisedVol <= tradingConfig.getVolatilityThreshold()
                ? 0
                : (int) ((annualisedVol - tradingConfig.getVolatilityThreshold()) / tradingConfig.getVolatilityAddonFactor() * tradingConfig.getRiskScoreIncrement());
        return Math.min(100, base + volAddon);
    }

    @Override
    public String riskLevel(int score) {
        return RiskLevel.fromScore(score).name();
    }

    @Override
    public double maxDrawdown(List<Double> values) {
        if (values.size() < 2) return 0.0;
        double peak = values.getFirst();
        double maxDd = 0.0;
        for (double v : values) {
            if (v > peak) peak = v;
            double dd = (peak - v) / peak;
            if (dd > maxDd) maxDd = dd;
        }
        return maxDd * 100;
    }

    @Override
    public double computeHHI(List<PositionWeight> positions,
                             Map<String, List<PriceBar>> barsByTicker) {
        Map<String, Double> latestPrices = barsByTicker.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getLast().getAdjClose().doubleValue()
                ));

        double total = positions.stream()
                .mapToDouble(p -> latestPrices.getOrDefault(p.ticker(), 0.0)
                        * p.quantity().doubleValue())
                .sum();

        if (total == 0.0) return 0.0;

        return positions.stream()
                .mapToDouble(p -> {
                    double val = latestPrices.getOrDefault(p.ticker(), 0.0)
                            * p.quantity().doubleValue();
                    double w = val / total;
                    return w * w;
                })
                .sum();
    }

    /**
     * Calculates the Pearson correlation coefficient between two return series.
     *
     * <h2>Mathematical Formulation</h2>
     * The Pearson correlation coefficient (ρ) measures the linear relationship between two variables:
     * <pre>
     * ρ(X,Y) = Cov(X,Y) / (σ_X * σ_Y)
     * </pre>
     * Where:
     * <ul>
     *   <li>Cov(X,Y) = Σ((x_i - μ_X)(y_i - μ_Y)) / (n-1) is the covariance</li>
     *   <li>σ_X, σ_Y are the standard deviations of X and Y</li>
     *   <li>μ_X, μ_Y are the means of X and Y</li>
     *   <li>n is the number of paired observations</li>
     * </ul>
     *
     * <h2>Financial Implications for Portfolio Diversification</h2>
     * <ul>
     *   <li>ρ = 1.0: Perfect positive correlation (assets move together) - no diversification benefit</li>
     *   <li>ρ = 0.0: No linear correlation (assets move independently) - partial diversification benefit</li>
     *   <li>ρ = -1.0: Perfect negative correlation (assets move oppositely) - maximum diversification benefit</li>
     *   <li>Typical equity correlations range from 0.3 to 0.9 in normal market conditions</li>
     *   <li>Correlations tend to increase toward 1.0 during market stress (correlation breakdown)</li>
     * </ul>
     *
     * <h2>Edge Case Handling</h2>
     * <ul>
     *   <li>Insufficient data (n < 2): Returns 0.0 (no correlation can be computed)</li>
     *   <li>Zero variance (constant returns): Returns 0.0 (undefined mathematically, treated as no correlation)</li>
     *   <li>Mismatched list lengths: Uses the shorter length (truncates excess data)</li>
     * </ul>
     *
     * @param x First return series (e.g., daily returns of asset A)
     * @param y Second return series (e.g., daily returns of asset B)
     * @return Pearson correlation coefficient in range [-1.0, 1.0], or 0.0 if undefined
     */
    @Override
    public double pearsonCorrelation(List<Double> x, List<Double> y) {
        int n = Math.min(x.size(), y.size());
        if (n < 2) return 0.0;
        double meanX = x.subList(0, n).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanY = y.subList(0, n).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double cov = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            double dx = x.get(i) - meanX;
            double dy = y.get(i) - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }
        double denom = Math.sqrt(varX * varY);
        return denom == 0.0 ? 0.0 : cov / denom;
    }

    /**
     * Descriptive statistics over a pre-sorted ascending array of simulated values.
     * Single-pass mean + variance to avoid iterating the array twice.
     */
    @Override
    public DistributionStatistics distributionStatistics(double[] sortedValues) {
        int n = sortedValues.length;
        if (n == 0) return new DistributionStatistics(0, 0, 0, 0, 0);

        // Single-pass Welford online mean + M2 accumulator (numerically stable)
        double mean = 0.0;
        double m2   = 0.0;
        for (int i = 0; i < n; i++) {
            double delta = sortedValues[i] - mean;
            mean += delta / (i + 1);
            m2   += delta * (sortedValues[i] - mean);
        }
        double stdDev = n > 1 ? Math.sqrt(m2 / n) : 0.0;

        // Median: already sorted — midpoint for odd N, average of two midpoints for even
        double median = (n % 2 == 1)
                ? sortedValues[n / 2]
                : (sortedValues[n / 2 - 1] + sortedValues[n / 2]) / 2.0;

        return new DistributionStatistics(mean, median, stdDev, sortedValues[0], sortedValues[n - 1]);
    }

    /**
     * Empirical VaR from a sorted simulation distribution.
     * Reads the value at the (1 - confidenceLevel) quantile of the sorted array.
     */
    @Override
    public double simulationVaR(double[] sortedValues, double confidenceLevel) {
        if (sortedValues.length == 0) return 0.0;
        int n = sortedValues.length;
        int index = (int) Math.floor((1.0 - confidenceLevel) * n + 1e-9);
        index = Math.max(0, Math.min(index, n - 1));
        return sortedValues[index];
    }

    /**
     * Conditional VaR (Expected Shortfall) — average of all values at or below the VaR threshold.
     * Requires sortedValues to be ascending (same precondition as simulationVaR).
     */
    @Override
    public double conditionalValueAtRisk(double[] sortedValues, double confidenceLevel) {
        if (sortedValues.length == 0) return 0.0;
        int n = sortedValues.length;
        int cutoff = Math.max(1, (int) Math.floor((1.0 - confidenceLevel) * n + 1e-9));
        double sum = 0.0;
        for (int i = 0; i < cutoff; i++) sum += sortedValues[i];
        return sum / cutoff;
    }

    protected List<Double> calculateRollingVolatility(List<Double> dailyReturns, int windowDays) {
        var result = new ArrayList<Double>(dailyReturns.size());
        for (int i = 0; i < dailyReturns.size(); i++) {
            int from = Math.max(0, i - windowDays + 1);
            List<Double> window = dailyReturns.subList(from, i + 1);
            result.add(window.size() < 2 ? null : annualisedVolatility(window));
        }
        return result;
    }
}
