package me.veselin.probity.portfolio.dto;

/**
 * Portfolio query DTO for a single allocation slice in composition charts.
 */
public record CompositionEntryDto(
        String label,
        double value,
        String color
) {}
