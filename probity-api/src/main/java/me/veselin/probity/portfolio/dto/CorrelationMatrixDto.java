package me.veselin.probity.portfolio.dto;

import java.util.List;

/**
 * Portfolio query DTO for pairwise return correlation output.
 */
public record CorrelationMatrixDto(
        List<String> assets,
        List<List<Double>> matrix
) {}
