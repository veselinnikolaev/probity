package me.veselin.probity.bff.dto.simulation;

import me.veselin.probity.simulation.domain.SimulationPayload;

import java.util.List;

/**
 * BFF response DTO for the Q9b sampled-paths endpoint
 * ({@code GET /api/v1/simulations/{id}/paths?n=N},
 * docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10).
 *
 * <p>The paths here are the deterministic sample {@code ResponsePathSampler} produces: at
 * most {@code n} evenly-spaced indices over the stored order, always including the paths
 * holding the global minimum and maximum values, so a client that derives its y-range from
 * these paths (FanChart) never clips an extreme. {@code totalPaths}/{@code sampleSize} let
 * a client tell "the sample is everything" from "the sample is bounded" without guessing.
 */
public record SimulationPathsResponse(
        String id,
        int totalPaths,
        int sampleSize,
        List<SimulationPayload.SimulatedPath> paths
) {
}
