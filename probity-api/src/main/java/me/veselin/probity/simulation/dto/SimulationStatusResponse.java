package me.veselin.probity.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for simulation status endpoint.
 * Returns the current execution status and result if completed.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationStatusResponse {

    private UUID id;
    private UUID portfolioId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    // Only populated when status == COMPLETED
    private SimulationResponseResult result;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResponseResult {
        private BigDecimal currentPortfolioValue;
        private SimulationResponseParameters parameters;
        private SimulationResponseStatistics statistics;
        private SimulationResponseOutcomes outcomes;
        private java.util.List<SimulationResponseAllPath> allPaths;
        private java.util.List<SimulationResponsePercentileSeries> percentileSeries;
        private java.util.List<SimulationResponseDistributionBucket> distribution;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResponseParameters {
        private int numberOfSimulations;
        private int timeHorizonDays;
        private double confidenceLevel;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResponseStatistics {
        private double expectedFinalValue;
        private double medianFinalValue;
        private double stdDeviation;
        private double minValue;
        private double maxValue;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResponseOutcomes {
        private double probabilityOf10PercentLoss;
        private double probabilityOf20PercentLoss;
        private double valueAtRisk95;
        private double conditionalValueAtRisk95;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResponseAllPath {
        private int pathId;
        private java.util.List<SimulationResponsePathPoint> values;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResponsePathPoint {
        private int dayIndex;
        private double value;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResponsePercentileSeries {
        private int percentile;
        private double targetFinalValue;
        private java.util.List<SimulationResponsePathPoint> values;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResponseDistributionBucket {
        private double lowerBound;
        private double upperBound;
        private int count;
        private double percentage;
    }
}