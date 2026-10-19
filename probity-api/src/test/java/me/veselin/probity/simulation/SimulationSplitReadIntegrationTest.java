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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Q9a storage split (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10): the HTTP surface must
 * not know about the split. A row written before it (full payload inline) and a row
 * written by the split completion (paths in {@code simulation_paths}) both come back with
 * every field the recorded pre-Q9a contract has — the reassembled body is served by the
 * same {@code mapToResponse}/{@code mapToResult} code either way.
 *
 * <p>Sampling is off by default, so these assertions double as §10's status-body-bytes
 * gate: at the shipped configuration the bodies are the pre-Q9a bodies.
 */
class SimulationSplitReadIntegrationTest extends BaseSimulationIntegrationTest {

    private static final int OLD_STYLE_PATHS = 2;
    private static final int SPLIT_PATHS = 3;
    private static final int SERIES_BANDS = 7;
    private static final int DISTRIBUTION_BUCKETS = 5;

    // ── GET /simulations (list) ───────────────────────────────────────────────

    @Test
    @DisplayName("Q9a: the list serves split rows reassembled and old full-payload rows inline")
    void list_servesReassembledSplitRowsAndInlineOldRows() throws Exception {
        AuthResult auth = login();
        UUID userId = seededAdminId();

        UUID oldId = simulationRepository.save(completedRow(userId, payloadWithPaths(OLD_STYLE_PATHS))).getId();
        UUID splitId = seedSplitRow(userId, payloadWithPaths(SPLIT_PATHS));

        String body = mockMvc.perform(get(ApiRoutes.Simulations.SIMULATIONS)
                        .param("portfolioId", portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        JsonNode oldItem = itemById(objectMapper.readTree(body), oldId);
        JsonNode splitItem = itemById(objectMapper.readTree(body), splitId);

        assertEquals(OLD_STYLE_PATHS, oldItem.get("allPaths").size(),
                "an old full-payload row must be served unchanged (paths still inline)");
        assertEquals(SPLIT_PATHS, splitItem.get("allPaths").size(),
                "a split row's allPaths must be reassembled from simulation_paths");

        // Everything around allPaths is part of the recorded contract for both shapes.
        for (JsonNode item : List.of(oldItem, splitItem)) {
            assertEquals(SERIES_BANDS, item.get("percentileSeries").size(),
                    "the seven bands are served in full regardless of storage shape");
            assertEquals(DISTRIBUTION_BUCKETS, item.get("distribution").size());
            assertTrue(item.get("statistics").get("expectedFinalValue").isNumber());
            assertTrue(item.get("outcomes").get("probabilityOf10PercentLoss").isNumber());
            assertTrue(item.get("currentPortfolioValue").isNumber());
        }
    }

    // ── GET /simulations/{id}/status ─────────────────────────────────────────

    @Test
    @DisplayName("Q9a: the status contract survives the split — statistics/outcomes non-null, bands and paths present")
    void getStatus_splitRow_keepsTheRecordedContract() throws Exception {
        AuthResult auth = login();
        UUID splitId = seedSplitRow(seededAdminId(), payloadWithPaths(SPLIT_PATHS));

        mockMvc.perform(get(ApiRoutes.Simulations.STATUS, splitId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.allPaths", hasSize(SPLIT_PATHS)))
                .andExpect(jsonPath("$.result.percentileSeries", hasSize(SERIES_BANDS)))
                .andExpect(jsonPath("$.result.distribution", hasSize(DISTRIBUTION_BUCKETS)))
                .andExpect(jsonPath("$.result.statistics.expectedFinalValue").isNumber())
                .andExpect(jsonPath("$.result.outcomes.probabilityOf10PercentLoss").isNumber())
                .andExpect(jsonPath("$.result.currentPortfolioValue").isNumber());
    }

    // ── GET /simulations/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("Q9a: the full-payload endpoint serves a split row with paths reassembled")
    void getSimulation_splitRow_reassemblesPaths() throws Exception {
        AuthResult auth = login();
        UUID splitId = seedSplitRow(seededAdminId(), payloadWithPaths(SPLIT_PATHS));

        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATION, splitId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(splitId.toString()))
                .andExpect(jsonPath("$.allPaths", hasSize(SPLIT_PATHS)))
                .andExpect(jsonPath("$.percentileSeries", hasSize(SERIES_BANDS)))
                .andExpect(jsonPath("$.distribution", hasSize(DISTRIBUTION_BUCKETS)))
                .andExpect(jsonPath("$.statistics.expectedFinalValue").isNumber());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /** A COMPLETED row whose payload still carries its paths inline — pre-Q9a shape. */
    private Simulation completedRow(UUID userId, SimulationPayload payload) {
        return Simulation.builder()
                .portfolioId(UUID.fromString(portfolioId))
                .userId(userId)
                .numberOfSimulations(100)
                .timeHorizonDays(10)
                .confidenceLevel(0.95)
                .currentPortfolioValue(new BigDecimal("2160.00"))
                .resultPayload(payload)
                .status(SimulationStatus.COMPLETED)
                .build();
    }

    /**
     * A row written the way the split completion writes it: PENDING, claimed, fenced
     * complete — inline payload trimmed, paths row inserted in the same transaction.
     */
    private UUID seedSplitRow(UUID userId, SimulationPayload payload) {
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
        simulationRepository.complete(id, new BigDecimal("2160.00"), payload, fence).orElseThrow();
        return id;
    }

    private UUID seededAdminId() {
        return userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("seeded admin user not found"));
    }

    private static JsonNode itemById(JsonNode items, UUID id) {
        for (JsonNode item : items) {
            if (id.toString().equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("no list item for simulation " + id);
    }

    private static SimulationPayload payloadWithPaths(int pathCount) {
        List<SimulationPayload.PortfolioPath> seriesValues = IntStream.range(0, 11)
                .mapToObj(d -> new SimulationPayload.PortfolioPath(d, 2160.0 + d * 3.5))
                .toList();
        List<SimulationPayload.PercentileSeries> percentileSeries = IntStream.of(5, 10, 25, 50, 75, 90, 95)
                .mapToObj(p -> new SimulationPayload.PercentileSeries(p, 2250.0 + p, seriesValues))
                .toList();
        List<SimulationPayload.SimulatedPath> paths = IntStream.range(0, pathCount)
                .mapToObj(p -> new SimulationPayload.SimulatedPath(p, IntStream.range(0, 11)
                        .mapToObj(d -> new SimulationPayload.PortfolioPath(d, 2000.0 + p * 10.0 + d))
                        .toList()))
                .toList();
        List<SimulationPayload.DistributionBucket> distribution = IntStream.range(0, DISTRIBUTION_BUCKETS)
                .mapToObj(b -> new SimulationPayload.DistributionBucket(
                        1900.0 + b * 200, 2100.0 + b * 200, 10 + b, 20.0))
                .toList();
        return new SimulationPayload(
                new SimulationPayload.Statistics(2250.0, 2240.0, 150.0, 1900.0, 2930.0),
                new SimulationPayload.Outcomes(0.05, 0.01, 1700.0, 1550.0),
                percentileSeries,
                paths,
                distribution);
    }
}
