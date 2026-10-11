package me.veselin.probity.risk.service;

import me.veselin.probity.common.config.TradingConfiguration;
import me.veselin.probity.common.util.DataUtil;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.risk.dto.AssetRiskProfile;
import me.veselin.probity.risk.enumeration.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RiskCalculatorTest {

    private RiskCalculator calculator;

    // [+10%, -5%] → mean = 0.025, stdDev > 0; used as the default non-trivial series
    private static final List<Double> RETURNS_SIMPLE = List.of(0.10, -0.05);

    // Zero variance — vol = 0, sharpe division-guard kicks in
    private static final List<Double> RETURNS_FLAT = List.of(0.01, 0.01, 0.01);

    // Longer series for rolling-window tests (5 prices → 4 daily returns)
    private static final List<Double> PRICES_5 = List.of(100.0, 102.0, 101.0, 103.0, 105.0);

    @BeforeEach
    void setUp() {
        calculator = new RiskCalculator(new TradingConfiguration());
    }

    // ── annualisedVolatility ─────────────────────────────────────────────────

    @Test
    void volatility_returnsZero_forEmptySeries() {
        assertThat(calculator.annualisedVolatility(List.of())).isZero();
    }

    @Test
    void volatility_returnsZero_forSingleReturn() {
        assertThat(calculator.annualisedVolatility(List.of(0.05))).isZero();
    }

    @Test
    void volatility_isZero_forFlatReturns() {
        assertThat(calculator.annualisedVolatility(RETURNS_FLAT)).isZero();
    }

    @Test
    void volatility_isPositive_forNonFlatReturns() {
        assertThat(calculator.annualisedVolatility(RETURNS_SIMPLE)).isGreaterThan(0.0);
    }

    @Test
    void volatility_matchesManualFormula() {
        // mean([0.1, -0.05]) = 0.025
        // variance = avg[(0.075)^2, (-0.075)^2] = 0.005625
        // vol = sqrt(0.005625) * sqrt(252) * 100
        double stdDev = Math.sqrt(0.005625);
        double expected = stdDev * Math.sqrt(252) * 100;

        assertThat(calculator.annualisedVolatility(RETURNS_SIMPLE))
                .isCloseTo(expected, within(1e-9));
    }

    @Test
    void volatility_isExpressedAsPercentage_notDecimal() {
        // annualised vol of a realistic return series should be in the single/double digits
        // as a percentage (e.g. 18.0), not as a decimal (e.g. 0.18)
        double vol = calculator.annualisedVolatility(RETURNS_SIMPLE);
        assertThat(vol).isGreaterThan(1.0);
    }

    // ── sharpeRatio ──────────────────────────────────────────────────────────

    @Test
    void sharpe_returnsZero_forEmptySeries() {
        assertThat(calculator.sharpeRatio(List.of())).isZero();
    }

    @Test
    void sharpe_returnsZero_forSingleReturn() {
        assertThat(calculator.sharpeRatio(List.of(0.05))).isZero();
    }

    @Test
    void sharpe_returnsZero_forFlatReturns() {
        // stdDev = 0 → division guard must return 0 rather than throw
        assertThat(calculator.sharpeRatio(RETURNS_FLAT)).isZero();
    }

    @Test
    void sharpe_isPositive_whenMeanExceedsRiskFreeRate() {
        // daily mean 0.025 >> daily RF ≈ 0.000178
        assertThat(calculator.sharpeRatio(RETURNS_SIMPLE)).isGreaterThan(0.0);
    }

    @Test
    void sharpe_isNegative_whenMeanBelowRiskFreeRate() {
        // All large negative returns → mean << risk-free → negative Sharpe
        List<Double> badReturns = List.of(-0.05, -0.06, -0.04);
        assertThat(calculator.sharpeRatio(badReturns)).isLessThan(0.0);
    }

    @Test
    void sharpe_matchesManualFormula() {
        double rfDaily = 0.045 / 252.0;
        double mean = DataUtil.mean(RETURNS_SIMPLE);
        double stdDev = Math.sqrt(DataUtil.variance(RETURNS_SIMPLE, mean));
        double expected = ((mean - rfDaily) / stdDev) * Math.sqrt(252.0);

        assertThat(calculator.sharpeRatio(RETURNS_SIMPLE)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void sharpe_changesWith_differentRiskFreeRate() {
        double sharpeAt045 = calculator.sharpeRatio(RETURNS_SIMPLE);

        injectRiskFreeRate(calculator, 0.0);
        double sharpeAt0 = calculator.sharpeRatio(RETURNS_SIMPLE);

        // Lower RF → higher excess return → higher Sharpe
        assertThat(sharpeAt0).isGreaterThan(sharpeAt045);
    }

    // ── var95 ────────────────────────────────────────────────────────────────

    @Test
    void var95_returnsZero_forEmptySeries() {
        assertThat(calculator.var95(10_000, List.of())).isZero();
    }

    @Test
    void var95_returnsZero_forSingleReturn() {
        assertThat(calculator.var95(10_000, List.of(0.05))).isZero();
    }

    @Test
    void var95_isPositive_forNonFlatReturns() {
        assertThat(calculator.var95(100_000, RETURNS_SIMPLE)).isGreaterThan(0.0);
    }

    @Test
    void var95_isZero_forFlatReturns() {
        // stdDev = 0 → VaR = portfolioValue * Z * 0 = 0
        assertThat(calculator.var95(100_000, RETURNS_FLAT)).isZero();
    }

    @Test
    void var95_scalesLinearlyWithPortfolioValue() {
        double v1 = calculator.var95(100_000, RETURNS_SIMPLE);
        double v2 = calculator.var95(200_000, RETURNS_SIMPLE);
        assertThat(v2).isCloseTo(v1 * 2, within(1e-6));
    }

    @Test
    void var95_matchesManualFormula() {
        double mean = DataUtil.mean(RETURNS_SIMPLE);
        double stdDev = Math.sqrt(DataUtil.variance(RETURNS_SIMPLE, mean));
        double expected = 50_000 * 1.645 * stdDev;

        assertThat(calculator.var95(50_000, RETURNS_SIMPLE)).isCloseTo(expected, within(1e-9));
    }

    // ── toDailyReturns ───────────────────────────────────────────────────────

    @Test
    void toDailyReturns_returnsEmpty_forEmptySeries() {
        assertThat(calculator.toDailyReturns(List.of())).isEmpty();
    }

    @Test
    void toDailyReturns_returnsEmpty_forSinglePrice() {
        assertThat(calculator.toDailyReturns(List.of(100.0))).isEmpty();
    }

    @Test
    void toDailyReturns_computesSimpleReturnsCorrectly() {
        // [100, 110, 99] → [+10%, -10%]
        List<Double> result = calculator.toDailyReturns(List.of(100.0, 110.0, 99.0));
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isCloseTo(0.10, within(1e-9));
        assertThat(result.get(1)).isCloseTo(-0.10, within(1e-9));   // 99/110 - 1
    }

    @Test
    void toDailyReturns_skipsZeroPrevPrice_noDivisionByZero() {
        // [0, 100, 110]: first step (prev=0) is skipped; only 100→110 is computed
        List<Double> result = calculator.toDailyReturns(List.of(0.0, 100.0, 110.0));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isCloseTo(0.10, within(1e-9));
    }

    @Test
    void toDailyReturns_sizeIsInputMinusOne_whenNoPrevZero() {
        assertThat(calculator.toDailyReturns(PRICES_5)).hasSize(PRICES_5.size() - 1);
    }

    // ── rollingVolatility ────────────────────────────────────────────────────

    @Test
    void rollingVolatility_outputSize_equalsNumberOfReturns() {
        // 5 prices → 4 returns → 4 rolling-vol entries
        List<Double> result = calculator.rollingVolatility(PRICES_5, 3);
        assertThat(result).hasSize(PRICES_5.size() - 1);
    }

    @Test
    void rollingVolatility_firstEntry_isNullWhenWindowNotFilled() {
        // window = 3, but first return only has 1 data point → null
        List<Double> result = calculator.rollingVolatility(PRICES_5, 3);
        assertThat(result.getFirst()).isNull();
    }

    @Test
    void rollingVolatility_laterEntries_areNonNullOnceWindowFilled() {
        // After window fills (index ≥ window-1) values should be non-null
        List<Double> result = calculator.rollingVolatility(PRICES_5, 3);
        // index 2 onward (0-based in returns) should have a value
        assertThat(result.get(2)).isNotNull().isGreaterThanOrEqualTo(0.0);
        assertThat(result.get(3)).isNotNull().isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void rollingVolatility_returnsEmptyList_forSinglePrice() {
        assertThat(calculator.rollingVolatility(List.of(100.0), 3)).isEmpty();
    }

    @Test
    void rollingVolatility_windowOne_producesNullsOnly_sinceEveryWindowHasOneReturn() {
        // window=1 means every sub-list has size 1 → annualisedVolatility returns 0 → null by guard
        List<Double> result = calculator.rollingVolatility(PRICES_5, 1);
        assertThat(result).hasSize(PRICES_5.size() - 1);
        // Each window of size 1 → annualisedVolatility(size<2) = 0... but guard is size<2 → null
        result.forEach(v -> assertThat(v).isNull());
    }

    // ── rollingVolatilityFromReturns ─────────────────────────────────────────

    @Test
    void rollingVolatilityFromReturns_outputSize_equalsInputSize() {
        List<Double> returns = calculator.toDailyReturns(PRICES_5); // 4 returns
        List<Double> result = calculator.rollingVolatilityFromReturns(returns, 3);
        assertThat(result).hasSize(returns.size());
    }

    @Test
    void rollingVolatilityFromReturns_matchesRollingVolatility_givenSamePrices() {
        // rollingVolatility(prices) should produce the same result as
        // rollingVolatilityFromReturns(toDailyReturns(prices))
        List<Double> fromPrices = calculator.rollingVolatility(PRICES_5, 3);
        List<Double> fromReturns = calculator.rollingVolatilityFromReturns(
                calculator.toDailyReturns(PRICES_5), 3);

        assertThat(fromReturns).hasSize(fromPrices.size());
        for (int i = 0; i < fromPrices.size(); i++) {
            if (fromPrices.get(i) == null) {
                assertThat(fromReturns.get(i)).isNull();
            } else {
                assertThat(fromReturns.get(i)).isCloseTo(fromPrices.get(i), within(1e-9));
            }
        }
    }

    @Test
    void rollingVolatilityFromReturns_returnsEmptyList_forEmptyInput() {
        assertThat(calculator.rollingVolatilityFromReturns(List.of(), 3)).isEmpty();
    }

    // ── maxDrawdown ──────────────────────────────────────────────────────────

    @Test
    void maxDrawdown_returnsZero_forEmptySeries() {
        assertThat(calculator.maxDrawdown(List.of())).isZero();
    }

    @Test
    void maxDrawdown_returnsZero_forSingleValue() {
        assertThat(calculator.maxDrawdown(List.of(100.0))).isZero();
    }

    @Test
    void maxDrawdown_returnsZero_forMonotonicallyIncreasing() {
        // Never drops below peak → drawdown is always 0
        assertThat(calculator.maxDrawdown(List.of(100.0, 110.0, 120.0, 130.0))).isZero();
    }

    @Test
    void maxDrawdown_isExpressedAsPercentage() {
        // [100, 50]: drawdown = (100-50)/100 = 50% → returned as 50.0, not 0.5
        double dd = calculator.maxDrawdown(List.of(100.0, 50.0));
        assertThat(dd).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void maxDrawdown_picksGlobalPeak_notJustLocalPeak() {
        // Peak at 200, then drops to 100 → 50% drawdown, ignoring earlier 100→50 pattern
        List<Double> values = List.of(100.0, 200.0, 100.0);
        double dd = calculator.maxDrawdown(values);
        assertThat(dd).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void maxDrawdown_matchesManualFormula() {
        // [100, 150, 90]: peak=150, trough=90 → dd = (150-90)/150 = 40%
        double dd = calculator.maxDrawdown(List.of(100.0, 150.0, 90.0));
        assertThat(dd).isCloseTo(40.0, within(1e-9));
    }

    @Test
    void maxDrawdown_isPositive_forDecliningSeries() {
        assertThat(calculator.maxDrawdown(List.of(100.0, 80.0, 60.0))).isGreaterThan(0.0);
    }

    // ── riskScore ────────────────────────────────────────────────────────────

    @Test
    void riskScore_clampedToOneHundred_forExtremeVol() {
        AssetRiskProfile profile = new AssetRiskProfile(AssetType.CRYPTO, AssetType.CRYPTO.getBaseRiskScore());
        assertThat(calculator.riskScore(profile, 999.0)).isEqualTo(100);
    }

    @Test
    void riskScore_equalsBaseScore_whenVolAtOrBelow15Pct() {
        int base = AssetType.STOCK.getBaseRiskScore();
        AssetRiskProfile profile = new AssetRiskProfile(AssetType.STOCK, base);
        // vol = 15 → addon = 0
        assertThat(calculator.riskScore(profile, 15.0)).isEqualTo(base);
        // vol < 15 → also no addon
        assertThat(calculator.riskScore(profile, 10.0)).isEqualTo(base);
    }

    @Test
    void riskScore_increasesAboveBase_whenVolExceeds15Pct() {
        int base = AssetType.STOCK.getBaseRiskScore();
        AssetRiskProfile profile = new AssetRiskProfile(AssetType.STOCK, base);
        int score = calculator.riskScore(profile, 25.0);
        assertThat(score).isGreaterThan(base);
    }

    @Test
    void riskScore_increasesMonotonically_withRisingVol() {
        AssetRiskProfile profile = new AssetRiskProfile(AssetType.STOCK, AssetType.STOCK.getBaseRiskScore());
        int s1 = calculator.riskScore(profile, 20.0);
        int s2 = calculator.riskScore(profile, 35.0);
        int s3 = calculator.riskScore(profile, 50.0);
        assertThat(s1).isLessThanOrEqualTo(s2);
        assertThat(s2).isLessThanOrEqualTo(s3);
    }

    @Test
    void riskScore_cryptoBaseScore_isHigherThanStockBaseScore() {
        // At equal vol the more volatile asset class should start higher
        AssetRiskProfile stockProfile = new AssetRiskProfile(AssetType.STOCK, AssetType.STOCK.getBaseRiskScore());
        AssetRiskProfile cryptoProfile = new AssetRiskProfile(AssetType.CRYPTO, AssetType.CRYPTO.getBaseRiskScore());
        int stockScore = calculator.riskScore(stockProfile, 10.0);
        int cryptoScore = calculator.riskScore(cryptoProfile, 10.0);
        assertThat(cryptoScore).isGreaterThan(stockScore);
    }

    // ── riskLevel ────────────────────────────────────────────────────────────

    @Test
    void riskLevel_returnsLow_forLowScore() {
        assertThat(calculator.riskLevel(5)).isEqualTo(RiskLevel.LOW.name());
    }

    @Test
    void riskLevel_returnsModerate_forMidScore() {
        assertThat(calculator.riskLevel(50)).isEqualTo(RiskLevel.MODERATE.name());
    }

    @Test
    void riskLevel_returnsHigh_forHighScore() {
        assertThat(calculator.riskLevel(95)).isEqualTo(RiskLevel.HIGH.name());
    }

    @Test
    void riskLevel_matchesRiskLevelEnum_fromScore() {
        for (int score : List.of(0, 25, 50, 75, 100)) {
            String expected = RiskLevel.fromScore(score).name();
            assertThat(calculator.riskLevel(score)).isEqualTo(expected);
        }
    }

    // ── mean / variance / stdDev helpers ─────────────────────────────────────

    @Test
    void mean_returnsZero_forEmptyList() {
        assertThat(DataUtil.mean(List.of())).isZero();
    }

    @Test
    void mean_returnsOnlyElement_forSingletonList() {
        assertThat(DataUtil.mean(List.of(7.0))).isCloseTo(7.0, within(1e-9));
    }

    @Test
    void mean_isArithmeticAverage() {
        assertThat(DataUtil.mean(List.of(1.0, 2.0, 3.0))).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void variance_isZero_forFlatValues() {
        double mean = DataUtil.mean(RETURNS_FLAT);
        assertThat(DataUtil.variance(RETURNS_FLAT, mean)).isZero();
    }

    @Test
    void variance_isPositive_forNonFlatValues() {
        double mean = DataUtil.mean(RETURNS_SIMPLE);
        assertThat(DataUtil.variance(RETURNS_SIMPLE, mean)).isGreaterThan(0.0);
    }

    @Test
    void variance_matchesManualFormula() {
        // mean([0.1, -0.05]) = 0.025
        // variance = avg[(0.075)^2, (0.075)^2] = 0.005625
        double mean = DataUtil.mean(RETURNS_SIMPLE);
        assertThat(DataUtil.variance(RETURNS_SIMPLE, mean)).isCloseTo(0.005625, within(1e-9));
    }

    @Test
    void stdDev_isZero_forFlatValues() {
        assertThat(DataUtil.stdDev(RETURNS_FLAT)).isZero();
    }

    @Test
    void stdDev_equalsSqrtOfVariance() {
        double mean = DataUtil.mean(RETURNS_SIMPLE);
        double expected = Math.sqrt(DataUtil.variance(RETURNS_SIMPLE, mean));
        assertThat(DataUtil.stdDev(RETURNS_SIMPLE)).isCloseTo(expected, within(1e-9));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void injectRiskFreeRate(RiskCalculator calc, double rate) {
        try {
            var tradingConfigField = RiskCalculator.class.getDeclaredField("tradingConfig");
            tradingConfigField.setAccessible(true);
            var tradingConfig = tradingConfigField.get(calc);
            var riskFreeRateField = tradingConfig.getClass().getDeclaredField("riskFreeRate");
            riskFreeRateField.setAccessible(true);
            riskFreeRateField.set(tradingConfig, rate);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject riskFreeRate", e);
        }
    }
}