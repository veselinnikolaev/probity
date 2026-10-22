package me.veselin.probity.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Q9b sampled-paths endpoint (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10):
 * {@code GET /api/v1/simulations/{id}/paths?n=N}.
 *
 * <p>Covers the endpoint contract the Q9a machinery was built for: bounds validation,
 * deterministic (reproducible) sampling, global min/max always present so a client's
 * y-range is never clipped, the default N when {@code n} is omitted, that the endpoint reads
 * old inline-payload rows as well as split rows, and that auth/ownership are exactly those
 * of {@code GET /{id}}.
 */
class SimulationPathsEndpointIntegrationTest extends BaseSimulationIntegrationTest {

    /** Big enough that an evenly-spaced-only sample would miss the extremes. */
    private static final int PATHS = 40;
    private static final double MIN_VALUE = 1570.0; // path 7, day 5
    private static final double MAX_VALUE = 2930.0; // path 3, day 5

    // ── the sample ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Q9b: n bounds the sample, keeps the global min/max paths, and is reproducible")
    void paths_nBoundsAndKeepsExtremesReproducibly() throws Exception {
        AuthResult auth = login();
        UUID id = seedInlineCompletedRow(seededAdminId(), PATHS);
        Cookie cookie = new Cookie(Token.ACCESS.getCookieName(), auth.accessToken());

        String first = mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id).param("n", "5").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.totalPaths").value(PATHS))
                .andExpect(jsonPath("$.sampleSize").value(5))
                .andExpect(jsonPath("$.paths", hasSize(5)))
                .andExpect(jsonPath("$.paths[0].pathId").exists())
                .andExpect(jsonPath("$.paths[0].values", hasSize(11)))
                .andReturn().getResponse().getContentAsString();

        // Extremes are present even though 5 evenly-spaced indices would not include them.
        JsonNode paths = objectMapper.readTree(first).get("paths");
        assertEquals(MIN_VALUE, minOverPaths(paths), 1e-9,
                "the y-range floor from the sample must equal the full-data floor");
        assertEquals(MAX_VALUE, maxOverPaths(paths), 1e-9,
                "the y-range ceiling from the sample must equal the full-data ceiling");

        // No seed, no randomness: the same request returns byte-identical paths.
        String again = mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id).param("n", "5").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(first, again, "sampling must be deterministic — no seed, no randomness");
    }

    @Test
    @DisplayName("Q9b: n above the stored path count is rejected with 400, not clamped")
    void paths_nAboveStoredCount_returns400() throws Exception {
        AuthResult auth = login();
        UUID id = seedInlineCompletedRow(seededAdminId(), 3);

        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id).param("n", "5")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Q9b: an omitted n below the default is served capped to the stored count, not 400")
    void paths_nOmitted_belowDefault_capsToStoredCount() throws Exception {
        AuthResult auth = login();
        // Fewer paths than the configured default (100): the caller did not ask for more than
        // exists, so the default is capped rather than rejected.
        UUID id = seedInlineCompletedRow(seededAdminId(), 2);

        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaths").value(2))
                .andExpect(jsonPath("$.sampleSize").value(2))
                .andExpect(jsonPath("$.paths", hasSize(2)));
    }

    @Test
    @DisplayName("Q9b: n outside [1, max] is rejected with 400")
    void paths_nOutOfBounds_returns400() throws Exception {
        AuthResult auth = login();
        UUID id = seedInlineCompletedRow(seededAdminId(), PATHS);
        Cookie cookie = new Cookie(Token.ACCESS.getCookieName(), auth.accessToken());

        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id).param("n", "0").cookie(cookie))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id).param("n", "-1").cookie(cookie))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id).param("n", "1001").cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Q9b: n omitted serves the configured default sample size")
    void paths_nOmitted_usesConfiguredDefault() throws Exception {
        AuthResult auth = login();
        UUID id = seedInlineCompletedRow(seededAdminId(), 500);

        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaths").value(500))
                .andExpect(jsonPath("$.sampleSize").value(100))
                .andExpect(jsonPath("$.paths", hasSize(100)));
    }

    // ── storage shapes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Q9b: the endpoint reads split rows and old inline-payload rows alike")
    void paths_servesBothStorageShapes() throws Exception {
        AuthResult auth = login();
        UUID userId = seededAdminId();
        Cookie cookie = new Cookie(Token.ACCESS.getCookieName(), auth.accessToken());

        UUID inlineId = seedInlineCompletedRow(userId, PATHS);
        UUID splitId = seedSplitRow(userId, PATHS);

        for (UUID id : List.of(inlineId, splitId)) {
            mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id).param("n", "5").cookie(cookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalPaths").value(PATHS))
                    .andExpect(jsonPath("$.paths", hasSize(5)));
        }
    }

    @Test
    @DisplayName("Q9b: a row with no paths serves an empty sample, not 404")
    void paths_rowWithoutPaths_servesEmptySample() throws Exception {
        AuthResult auth = login();
        UUID userId = seededAdminId();

        // A completed row whose payload carries no paths and has no simulation_paths row.
        UUID empty = simulationRepository.save(Simulation.builder()
                .portfolioId(UUID.fromString(portfolioId))
                .userId(userId)
                .numberOfSimulations(100)
                .timeHorizonDays(10)
                .confidenceLevel(0.95)
                .currentPortfolioValue(new BigDecimal("2160.00"))
                .resultPayload(payloadWithPathCount(0))
                .status(SimulationStatus.COMPLETED)
                .build()).getId();

        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, empty)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaths").value(0))
                .andExpect(jsonPath("$.sampleSize").value(0))
                .andExpect(jsonPath("$.paths", hasSize(0)));
    }

    // ── auth and ownership (same guard as GET /{id}) ──────────────────────────

    @Test
    @DisplayName("Q9b: paths honours the same ownership guard as the detail endpoint — 404 for another user")
    void paths_anotherUsersSimulation_returns404() throws Exception {
        AuthResult admin = login();
        UUID id = seedInlineCompletedRow(seededAdminId(), PATHS);

        AuthResult spy = registerAndLogin("paths_spy", "paths_spy@probity.test", "Password789!");
        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, id)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), spy.accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Q9b: paths requires auth — 401 without a session")
    void paths_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Q9b: paths on an unknown simulation is 404")
    void paths_unknownSimulation_returns404() throws Exception {
        AuthResult auth = login();
        mockMvc.perform(get(ApiRoutes.Simulations.PATHS, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isNotFound());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /** A COMPLETED row whose paths live inline in the payload — the pre-Q9a / sync-run shape. */
    private UUID seedInlineCompletedRow(UUID userId, int pathCount) {
        return simulationRepository.save(Simulation.builder()
                .portfolioId(UUID.fromString(portfolioId))
                .userId(userId)
                .numberOfSimulations(100)
                .timeHorizonDays(10)
                .confidenceLevel(0.95)
                .currentPortfolioValue(new BigDecimal("2160.00"))
                .resultPayload(payloadWithPathCount(pathCount))
                .status(SimulationStatus.COMPLETED)
                .build()).getId();
    }

    /** A row written the way the split completion writes it — paths in simulation_paths. */
    private UUID seedSplitRow(UUID userId, int pathCount) {
        Simulation pending = Simulation.builder()
                .portfolioId(UUID.fromString(portfolioId))
                .userId(userId)
                .numberOfSimulations(100)
                .timeHorizonDays(10)
                .confidenceLevel(0.95)
                .currentPortfolioValue(BigDecimal.ZERO)
                .resultPayload(null)
                .status(SimulationStatus.PENDING)
                .build();
        UUID id = simulationRepository.save(pending).getId();

        Instant fence = simulationRepository.claimForProcessing(id, Duration.ofMinutes(5)).orElseThrow();
        simulationRepository.complete(id, new BigDecimal("2160.00"), payloadWithPathCount(pathCount), fence).orElseThrow();
        return id;
    }

    private UUID seededAdminId() {
        return userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("seeded admin user not found"));
    }

    private static SimulationPayload payloadWithPathCount(int pathCount) {
        List<SimulationPayload.PortfolioPath> seriesValues = IntStream.range(0, 11)
                .mapToObj(d -> new SimulationPayload.PortfolioPath(d, 2160.0 + d * 3.5))
                .toList();
        List<SimulationPayload.PercentileSeries> percentileSeries = IntStream.of(5, 10, 25, 50, 75, 90, 95)
                .mapToObj(p -> new SimulationPayload.PercentileSeries(p, 2250.0 + p, seriesValues))
                .toList();
        List<SimulationPayload.SimulatedPath> paths = IntStream.range(0, pathCount)
                .mapToObj(p -> new SimulationPayload.SimulatedPath(p, IntStream.range(0, 11)
                        .mapToObj(d -> new SimulationPayload.PortfolioPath(d, pathValue(p, d)))
                        .toList()))
                .toList();
        List<SimulationPayload.DistributionBucket> distribution = IntStream.range(0, 5)
                .mapToObj(b -> new SimulationPayload.DistributionBucket(
                        1900.0 + b * 200, 2100.0 + b * 200, 10 + b, 20.0))
                .toList();
        return new SimulationPayload(
                new SimulationPayload.Statistics(2250.0, 2240.0, 150.0, MIN_VALUE, MAX_VALUE),
                new SimulationPayload.Outcomes(0.05, 0.01, 1700.0, 1550.0),
                percentileSeries,
                paths,
                distribution);
    }

    /** Global min 1570.0 in path 7 and max 2930.0 in path 3 — both mid-list. */
    private static double pathValue(int path, int day) {
        double base = 2000.0 + path * 10.0;
        if (day == 5 && path == 7) return base - 500.0;
        if (day == 5 && path == 3) return base + 900.0;
        return base;
    }

    private static double minOverPaths(JsonNode paths) {
        double best = Double.POSITIVE_INFINITY;
        for (JsonNode path : paths) {
            for (JsonNode point : path.get("values")) {
                best = Math.min(best, point.get("value").asDouble());
            }
        }
        return best;
    }

    private static double maxOverPaths(JsonNode paths) {
        double best = Double.NEGATIVE_INFINITY;
        for (JsonNode path : paths) {
            for (JsonNode point : path.get("values")) {
                best = Math.max(best, point.get("value").asDouble());
            }
        }
        return best;
    }
}
