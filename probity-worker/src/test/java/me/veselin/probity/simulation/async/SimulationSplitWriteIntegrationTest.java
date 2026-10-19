package me.veselin.probity.simulation.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.veselin.probity.simulation.config.SimulationProperties;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.dto.SimulationData;
import me.veselin.probity.simulation.mapper.SimulationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static me.veselin.probity.simulation.async.AsyncKafkaTestSupport.registerDormantListener;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Q9a storage split (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §9/§10): the fenced
 * completion write is now one transaction of the trimmed {@code result_payload} UPDATE
 * plus the {@code simulation_paths} INSERT, and the dual-source read in
 * {@link SimulationMapper} puts the halves back together.
 *
 * <p>No Kafka and no listener — the repository's write-back path in isolation, mirroring
 * {@link SimulationFencedCompleteIntegrationTest}. Read assertions run through the real
 * mapper bean with the shipped default (sampling off), which is the "byte-identical at
 * default config" half of §10's gate.
 */
class SimulationSplitWriteIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    /** Distinctive sizes so an assertion can prove which half of the payload moved. */
    private static final int SPLIT_PATHS = 50;
    private static final int OLD_STYLE_PATHS = 2;

    private static final double MIN_VALUE = 1570.0; // path 7, day 5
    private static final double MAX_VALUE = 2930.0; // path 3, day 5

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registerDormantListener(registry);
    }

    @Autowired
    private SimulationMapper simulationMapper;

    @Autowired
    private SimulationProperties simulationProperties;

    private final ObjectMapper treeMapper = new ObjectMapper();

    // ── the split write ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Q9a: completion stores a paths-free payload inline plus one simulation_paths row in the same transaction")
    void complete_splitsTheWriteAndReadsBackTheSameContract() throws Exception {
        UUID id = seedPendingSimulation();
        Instant fence = simulationRepository.claimForProcessing(id, Duration.ofMinutes(5)).orElseThrow();
        SimulationPayload payload = payloadWithPaths(SPLIT_PATHS);

        Simulation returned = simulationRepository
                .complete(id, new BigDecimal("2160.00"), payload, fence)
                .orElseThrow();

        assertEquals(0, intValue(
                        "SELECT jsonb_array_length(result_payload->'allPaths') FROM simulations WHERE id = ?", id),
                "the inline payload must carry no paths after the split");
        assertEquals(1, intValue("SELECT count(*) FROM simulation_paths WHERE simulation_id = ?", id),
                "exactly one paths row must exist for the split-written simulation");
        assertEquals(SPLIT_PATHS, intValue(
                        "SELECT jsonb_array_length(paths) FROM simulation_paths WHERE simulation_id = ?", id),
                "the paths row must hold every path that left the payload");

        int inlineBytes = intValue("SELECT length(result_payload::text) FROM simulations WHERE id = ?", id);
        int pathsBytes = intValue(
                "SELECT length((SELECT paths::text FROM simulation_paths WHERE simulation_id = ?))", id);
        assertTrue(inlineBytes < 10_000, () -> "inline result_payload should be the "
                + inlineBytes + " B summary/series half, not the path array");
        assertTrue(pathsBytes > inlineBytes,
                "the byte-heavy half must live in simulation_paths, not in the inline payload");

        // The stored paths must be exactly what left the payload — a typed round-trip, so
        // a reassembled response is indistinguishable from a pre-Q9a response.
        String stored = jdbcTemplate.queryForObject(
                "SELECT paths::text FROM simulation_paths WHERE simulation_id = ?", String.class, id);
        assertNotNull(stored);
        List<SimulationPayload.SimulatedPath> storedPaths = List.of(
                treeMapper.readValue(stored, SimulationPayload.SimulatedPath[].class));
        assertEquals(payload.allPaths(), storedPaths,
                "simulation_paths must store the payload's allPaths verbatim");

        // Re-read through the real mapper: the two halves reassemble into the original
        // contract — every non-path field identical, all paths back, default sampling off.
        SimulationData data = simulationMapper.toDto(simulationRepository.findById(id).orElseThrow());
        assertEquals(payload.allPaths(), data.allPaths());
        assertEquals(payload.percentileSeries(), data.percentileSeries());
        assertEquals(payload.distribution(), data.distribution());
        assertEquals(treeMapper.valueToTree(payload.statistics()), treeMapper.valueToTree(data.statistics()),
                "statistics must survive the split untouched");
        assertEquals(treeMapper.valueToTree(payload.outcomes()), treeMapper.valueToTree(data.outcomes()),
                "outcomes must survive the split untouched");

        assertEquals(SimulationStatus.COMPLETED, returned.getStatus(),
                "the read-back row must be the completed one");
    }

    // ── old rows ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Q9a: a pre-split row keeps its inline payload, gains no paths row, and is served unchanged")
    void oldStyleRow_staysInlineAndIsServedWithoutThePathsTable() {
        SimulationPayload payload = payloadWithPaths(OLD_STYLE_PATHS);
        Simulation old = simulationRepository.save(Simulation.builder()
                .portfolioId(UUID.fromString(portfolioId))
                .userId(seededUserId)
                .numberOfSimulations(100)
                .timeHorizonDays(10)
                .confidenceLevel(0.95)
                .currentPortfolioValue(new BigDecimal("2160.00"))
                .resultPayload(payload)          // full and inline, as written before Q9a
                .status(SimulationStatus.COMPLETED)
                .build());

        assertEquals(0, intValue("SELECT count(*) FROM simulation_paths WHERE simulation_id = ?", old.getId()),
                "an old-style row must not be backfilled by reads or by anything else");

        SimulationData data = simulationMapper.toDto(simulationRepository.findById(old.getId()).orElseThrow());
        assertEquals(payload.allPaths(), data.allPaths(),
                "a non-empty inline array is served as-is — the storage half never wins");
        assertEquals(payload.percentileSeries(), data.percentileSeries());
    }

    // ── the fence ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Q9a: a lost fence writes neither payload nor paths row")
    void fenceLost_writesNoPathsRowAndLeavesTheRowUntouched() {
        UUID id = seedPendingSimulation();
        simulationRepository.claimForProcessing(id, Duration.ofMinutes(5)).orElseThrow();

        var result = simulationRepository.complete(id, new BigDecimal("2160.00"),
                payloadWithPaths(OLD_STYLE_PATHS), Instant.now().plusSeconds(60)); // wrong token

        assertTrue(result.isEmpty(), "a stale fence must be rejected");
        assertEquals(0, intValue("SELECT count(*) FROM simulation_paths WHERE simulation_id = ?", id),
                "the paths INSERT is gated on the fence having held — no orphan beside a lost claim");
        assertEquals(SimulationStatus.PROCESSING, simulationRepository.findById(id).orElseThrow().getStatus(),
                "a lost fence changes nothing: the row keeps its claimed PROCESSING state, "
                        + "neither completed nor reverted");
        assertNull(jdbcTemplate.queryForObject(
                        "SELECT result_payload FROM simulations WHERE id = ?", Object.class, id),
                "the payload half must not be written either");
    }

    // ── sampling (dormant by default, exercised here with N > 0) ─────────────

    @Test
    @DisplayName("FanChart: sampling bounds the response but keeps the global extremes and the full percentile series")
    void samplingOn_keepsGlobalExtremesAndTheFullPercentileSeries() {
        UUID id = seedPendingSimulation();
        Instant fence = simulationRepository.claimForProcessing(id, Duration.ofMinutes(5)).orElseThrow();
        SimulationPayload payload = payloadWithPaths(10);
        simulationRepository.complete(id, new BigDecimal("2160.00"), payload, fence).orElseThrow();

        double fullMin = global(payload.allPaths(), true);
        double fullMax = global(payload.allPaths(), false);
        assertEquals(MIN_VALUE, fullMin, 1e-9, "fixture sanity: global min lives in path 7");
        assertEquals(MAX_VALUE, fullMax, 1e-9, "fixture sanity: global max lives in path 3");

        simulationProperties.setResponsePathsSampleSize(4);
        try {
            SimulationData data = simulationMapper.toDto(simulationRepository.findById(id).orElseThrow());

            assertEquals(4, data.allPaths().size(), "the configured sample size must bound the response");
            assertEquals(fullMin, global(data.allPaths(), true), 1e-9,
                    "y-range floor computed from the sampled paths must equal the full-data floor");
            assertEquals(fullMax, global(data.allPaths(), false), 1e-9,
                    "y-range ceiling computed from the sampled paths must equal the full-data ceiling");
            assertEquals(payload.percentileSeries(), data.percentileSeries(),
                    "the percentile bands are part of the contract and are never sampled");
            assertEquals(payload.distribution(), data.distribution(),
                    "the distribution is part of the contract and is never sampled");
            assertEquals(treeMapper.valueToTree(payload.statistics()), treeMapper.valueToTree(data.statistics()),
                    "statistics are computed from all paths at run time and never re-derived from a sample");
        } finally {
            simulationProperties.setResponsePathsSampleSize(0); // restore the shipped default
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /**
     * 11-day payload with all five payload families populated. Path values use
     * {@link #pathValue(int, int)}: global min 1570.0 in path 7, global max 2930.0 in
     * path 3 — both mid-list, so an evenly-spaced-only sample would miss them.
     */
    private static SimulationPayload payloadWithPaths(int pathCount) {
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

    private static double pathValue(int path, int day) {
        double base = 2000.0 + path * 10.0;
        if (day == 5 && path == 7) return base - 500.0; // 1570.0 — global min
        if (day == 5 && path == 3) return base + 900.0; // 2930.0 — global max
        return base;
    }

    private static double global(List<SimulationPayload.SimulatedPath> paths, boolean min) {
        double best = min ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (SimulationPayload.SimulatedPath path : paths) {
            for (SimulationPayload.PortfolioPath point : path.values()) {
                if (min ? point.value() < best : point.value() > best) best = point.value();
            }
        }
        return best;
    }

    private int intValue(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        assertNotNull(value, "query returned no value: " + sql);
        return value;
    }
}
