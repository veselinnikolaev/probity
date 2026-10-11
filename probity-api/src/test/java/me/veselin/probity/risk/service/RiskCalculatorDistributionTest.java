package me.veselin.probity.risk.service;

import me.veselin.probity.common.config.TradingConfiguration;
import me.veselin.probity.risk.dto.DistributionStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the three distribution/simulation math methods added to RiskCalculator:
 *   - distributionStatistics(double[])
 *   - simulationVaR(double[], double)
 *   - conditionalValueAtRisk(double[], double)
 *
 * All inputs are pre-sorted ascending, matching the contract the service guarantees
 * before calling these methods (Arrays.sort(finalValues) in MonteCarloSimulationService).
 */
class RiskCalculatorDistributionTest {

    private RiskCalculator calculator;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    // 10 evenly spaced values [10, 20, 30, ..., 100]
    // Mean = 55, Median = (50+60)/2 = 55, Min = 10, Max = 100
    private static final double[] SORTED_10 = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    // Odd-length array [10, 20, 30] — median is middle element = 20
    private static final double[] SORTED_3_ODD = {10, 20, 30};

    // All same value — stdDev should be 0
    private static final double[] SORTED_FLAT = {50, 50, 50, 50, 50};

    // Single element edge case
    private static final double[] SORTED_SINGLE = {42.0};

    // Empty
    private static final double[] EMPTY = {};

    @BeforeEach
    void setUp() {
        calculator = new RiskCalculator(new TradingConfiguration());
    }

    // ── distributionStatistics ───────────────────────────────────────────────

    @Test
    void distributionStatistics_returnsAllZeros_forEmptyArray() {
        DistributionStatistics result = calculator.distributionStatistics(EMPTY);

        assertThat(result.mean()).isZero();
        assertThat(result.median()).isZero();
        assertThat(result.stdDeviation()).isZero();
        assertThat(result.min()).isZero();
        assertThat(result.max()).isZero();
    }

    @Test
    void distributionStatistics_singleElement_allFieldsEqualThatElement() {
        DistributionStatistics result = calculator.distributionStatistics(SORTED_SINGLE);

        assertThat(result.mean()).isCloseTo(42.0, within(1e-9));
        assertThat(result.median()).isCloseTo(42.0, within(1e-9));
        assertThat(result.stdDeviation()).isZero();
        assertThat(result.min()).isCloseTo(42.0, within(1e-9));
        assertThat(result.max()).isCloseTo(42.0, within(1e-9));
    }

    @Test
    void distributionStatistics_flatArray_hasZeroStdDev() {
        DistributionStatistics result = calculator.distributionStatistics(SORTED_FLAT);

        assertThat(result.stdDeviation()).isZero();
        assertThat(result.mean()).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void distributionStatistics_mean_isArithmeticAverage() {
        // [10..100] → sum = 550, mean = 55
        DistributionStatistics result = calculator.distributionStatistics(SORTED_10);
        assertThat(result.mean()).isCloseTo(55.0, within(1e-9));
    }

    @Test
    void distributionStatistics_median_isAverageOfTwoMiddleElements_forEvenLength() {
        // n=10, even: median = (sorted[4] + sorted[5]) / 2 = (50 + 60) / 2 = 55
        DistributionStatistics result = calculator.distributionStatistics(SORTED_10);
        assertThat(result.median()).isCloseTo(55.0, within(1e-9));
    }

    @Test
    void distributionStatistics_median_isMiddleElement_forOddLength() {
        // n=3, odd: median = sorted[1] = 20
        DistributionStatistics result = calculator.distributionStatistics(SORTED_3_ODD);
        assertThat(result.median()).isCloseTo(20.0, within(1e-9));
    }

    @Test
    void distributionStatistics_minMax_areFirstAndLastElements() {
        DistributionStatistics result = calculator.distributionStatistics(SORTED_10);
        assertThat(result.min()).isCloseTo(10.0, within(1e-9));
        assertThat(result.max()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void distributionStatistics_stdDev_isPositive_forNonFlatArray() {
        DistributionStatistics result = calculator.distributionStatistics(SORTED_10);
        assertThat(result.stdDeviation()).isGreaterThan(0.0);
    }

    @Test
    void distributionStatistics_stdDev_matchesManualPopulationFormula() {
        // For [10..100] step 10:
        // mean = 55
        // variance = avg of (xi - 55)^2:
        //   (-45)^2=2025, (-35)^2=1225, (-25)^2=625, (-15)^2=225, (-5)^2=25,
        //   (5)^2=25, (15)^2=225, (25)^2=625, (35)^2=1225, (45)^2=2025
        // sum = 8250, variance = 825, stdDev = sqrt(825) ≈ 28.7228
        double expected = Math.sqrt(825.0);
        DistributionStatistics result = calculator.distributionStatistics(SORTED_10);
        assertThat(result.stdDeviation()).isCloseTo(expected, within(1e-6));
    }

    @Test
    void distributionStatistics_welford_isNumericallyStableForLargeValues() {
        // Values near 1_000_000 — naive two-pass sum-of-squares can lose precision
        double base = 1_000_000.0;
        double[] large = {base, base + 1, base + 2, base + 3, base + 4};
        DistributionStatistics result = calculator.distributionStatistics(large);

        // Mean = base + 2
        assertThat(result.mean()).isCloseTo(base + 2, within(1e-6));
        // StdDev of [0,1,2,3,4] centred = sqrt(2)
        assertThat(result.stdDeviation()).isCloseTo(Math.sqrt(2.0), within(1e-6));
    }

    // ── simulationVaR ────────────────────────────────────────────────────────

    @Test
    void simulationVaR_returnsZero_forEmptyArray() {
        assertThat(calculator.simulationVaR(EMPTY, 0.95)).isZero();
    }

    @Test
    void simulationVaR_returnsOnlyElement_forSingleElementArray() {
        assertThat(calculator.simulationVaR(SORTED_SINGLE, 0.95))
                .isCloseTo(42.0, within(1e-9));
    }

    @Test
    void simulationVaR_at95_picksValueAtFivePercentile() {
        // n=10, 95% CL → index = floor(0.05 * 10) = 0 → sorted[0] = 10
        assertThat(calculator.simulationVaR(SORTED_10, 0.95))
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    void simulationVaR_at90_picksValueAtTenPercentile() {
        // n=10, 90% CL → index = floor(0.10 * 10) = 1 → sorted[1] = 20
        assertThat(calculator.simulationVaR(SORTED_10, 0.90))
                .isCloseTo(20.0, within(1e-9));
    }

    @Test
    void simulationVaR_at99_picksValueAtOnePercentile() {
        // n=10, 99% CL → index = floor(0.01 * 10) = 0 → sorted[0] = 10
        assertThat(calculator.simulationVaR(SORTED_10, 0.99))
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    void simulationVaR_isLowerForHigherConfidenceLevel() {
        // Higher CL → smaller tail → value further into the loss tail → lower absolute value
        double var90 = calculator.simulationVaR(SORTED_10, 0.90);
        double var95 = calculator.simulationVaR(SORTED_10, 0.95);
        // At 90% we get sorted[1]=20; at 95% we get sorted[0]=10 — VaR is more conservative
        assertThat(var95).isLessThanOrEqualTo(var90);
    }

    @Test
    void simulationVaR_doesNotThrow_forExtremeConfidenceLevels() {
        // Edge: index clamped to [0, n-1] — no ArrayIndexOutOfBoundsException
        assertThatCode(() -> calculator.simulationVaR(SORTED_10, 0.9999)).doesNotThrowAnyException();
        assertThatCode(() -> calculator.simulationVaR(SORTED_10, 0.0001)).doesNotThrowAnyException();
    }

    // ── conditionalValueAtRisk ────────────────────────────────────────────────

    @Test
    void cvar_returnsZero_forEmptyArray() {
        assertThat(calculator.conditionalValueAtRisk(EMPTY, 0.95)).isZero();
    }

    @Test
    void cvar_returnsOnlyElement_forSingleElementArray() {
        assertThat(calculator.conditionalValueAtRisk(SORTED_SINGLE, 0.95))
                .isCloseTo(42.0, within(1e-9));
    }

    @Test
    void cvar_at95_isAverageOfTailValues() {
        // n=10, 95% CL → cutoff = max(1, floor(0.05 * 10)) = max(1, 0) = 1
        // CVaR = sorted[0] / 1 = 10
        assertThat(calculator.conditionalValueAtRisk(SORTED_10, 0.95))
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    void cvar_at90_isAverageOfTailValues() {
        // n=10, 90% CL → cutoff = floor(0.10 * 10) = 1 → average of sorted[0..0] = 10
        assertThat(calculator.conditionalValueAtRisk(SORTED_10, 0.90))
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    void cvar_usesLargerTailFor80PercentConfidence() {
        // n=10, 80% CL → cutoff = floor(0.20 * 10) = 2 → average of [10, 20] = 15
        assertThat(calculator.conditionalValueAtRisk(SORTED_10, 0.80))
                .isCloseTo(15.0, within(1e-9));
    }

    @Test
    void cvar_usesLargerTailFor50PercentConfidence() {
        // n=10, 50% CL → cutoff = floor(0.50 * 10) = 5 → average of [10,20,30,40,50] = 30
        assertThat(calculator.conditionalValueAtRisk(SORTED_10, 0.50))
                .isCloseTo(30.0, within(1e-9));
    }

    @Test
    void cvar_isAlwaysLessThanOrEqualTo_varAtSameConfidenceLevel() {
        // CVaR is the average of the losses beyond VaR — it must be ≤ VaR
        // (for loss-valued distributions where smaller = worse)
        for (double cl : new double[]{0.80, 0.90, 0.95, 0.99}) {
            double var  = calculator.simulationVaR(SORTED_10, cl);
            double cvar = calculator.conditionalValueAtRisk(SORTED_10, cl);
            assertThat(cvar)
                    .as("CVaR should be ≤ VaR at confidence level %s", cl)
                    .isLessThanOrEqualTo(var + 1e-9); // tolerance for floating point
        }
    }

    @Test
    void cvar_decreasesOrStays_asConfidenceLevelIncreases() {
        // Higher CL → smaller tail → averages over fewer (more extreme) losses
        double cvar80 = calculator.conditionalValueAtRisk(SORTED_10, 0.80);
        double cvar90 = calculator.conditionalValueAtRisk(SORTED_10, 0.90);
        double cvar95 = calculator.conditionalValueAtRisk(SORTED_10, 0.95);
        assertThat(cvar95).isLessThanOrEqualTo(cvar90 + 1e-9);
        assertThat(cvar90).isLessThanOrEqualTo(cvar80 + 1e-9);
    }

    @Test
    void cvar_forFlatDistribution_equalsEveryValue() {
        // All values identical → CVaR = that value regardless of confidence level
        assertThat(calculator.conditionalValueAtRisk(SORTED_FLAT, 0.95))
                .isCloseTo(50.0, within(1e-9));
    }

    @Test
    void cvar_doesNotThrow_forExtremeConfidenceLevels() {
        assertThatCode(() -> calculator.conditionalValueAtRisk(SORTED_10, 0.9999)).doesNotThrowAnyException();
        assertThatCode(() -> calculator.conditionalValueAtRisk(SORTED_10, 0.0001)).doesNotThrowAnyException();
    }
}
