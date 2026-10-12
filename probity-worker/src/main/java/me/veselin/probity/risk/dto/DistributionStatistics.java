package me.veselin.probity.risk.dto;

/**
 * Immutable statistics DTO returned by distribution analysis in the risk layer.
 */
public record DistributionStatistics(
        double mean,
        double median,
        double stdDeviation,
        double min,
        double max
) {}

