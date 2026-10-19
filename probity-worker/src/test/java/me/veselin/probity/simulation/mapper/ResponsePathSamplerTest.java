package me.veselin.probity.simulation.mapper;

import me.veselin.probity.simulation.domain.SimulationPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Q9a response-side sampling (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §9/§10).
 *
 * <p>The y-range test is the one the design note records: a chart that derives its scale
 * from the returned paths (FanChart computes min/max over path values) must see the same
 * range from a sampled body as from the full one, which is only true because the sample
 * always contains the paths holding the global extremes.
 */
class ResponsePathSamplerTest {

    // ── disabled / no-op cases (the shipped default is 0) ───────────────────

    @Test
    @DisplayName("sampleSize 0 (the shipped default) and negative return the same list untouched")
    void disabled_returnsEveryPath() {
        List<SimulationPayload.SimulatedPath> paths = pathsWithExtremes(10);

        assertSame(paths, ResponsePathSampler.sample(paths, 0),
                "the default must be a pass-through — §10 measures unchanged status body bytes with it");
        assertSame(paths, ResponsePathSampler.sample(paths, -1),
                "a negative size must behave like 'off', not like 'zero or fewer paths'");
    }

    @Test
    @DisplayName("asking for the whole list (or more) returns the whole list")
    void askingForEverything_returnsEveryPath() {
        List<SimulationPayload.SimulatedPath> paths = pathsWithExtremes(10);

        assertSame(paths, ResponsePathSampler.sample(paths, 10), "N == size is not a sample");
        assertSame(paths, ResponsePathSampler.sample(paths, 50), "N > size must not truncate");

        List<SimulationPayload.SimulatedPath> empty = List.of();
        assertSame(empty, ResponsePathSampler.sample(empty, 5), "an empty source stays empty");
    }

    // ── the guarantees the design leans on ───────────────────────────────────

    @Test
    @DisplayName("FanChart y-range: sampled min/max values equal the full-data min/max")
    void sample_keepsTheGlobalExtremePaths_soAYRangeFromTheSampleMatchesFullData() {
        List<SimulationPayload.SimulatedPath> paths = pathsWithExtremes(10);
        double fullMin = 1570.0;   // path 7, day 5 — a mid-list path, not an evenly spaced slot
        double fullMax = 2930.0;   // path 3, day 5
        assertEquals(fullMin, global(paths, true), 1e-9, "fixture sanity");
        assertEquals(fullMax, global(paths, false), 1e-9, "fixture sanity");

        List<SimulationPayload.SimulatedPath> sample = ResponsePathSampler.sample(paths, 5);

        assertEquals(5, sample.size());
        assertTrue(sample.stream().anyMatch(p -> p.pathId() == 7),
                "the path holding the global minimum must be in the sample");
        assertTrue(sample.stream().anyMatch(p -> p.pathId() == 3),
                "the path holding the global maximum must be in the sample");
        assertEquals(fullMin, global(sample, true), 1e-9,
                "y-range floor computed from the sample must equal the full-data floor");
        assertEquals(fullMax, global(sample, false), 1e-9,
                "y-range ceiling computed from the sample must equal the full-data ceiling");
    }

    @Test
    @DisplayName("same input and N always give the same sample, evenly spaced, in original order")
    void sample_isDeterministicEvenlySpacedAndOrdered() {
        // 20 paths; min at path 0, max at path 2 — the extremes claim their slots first,
        // the rest fills evenly (round(k * 19 / 3)), and the result is emitted in
        // original order: [0, 2, 6, 13].
        List<SimulationPayload.SimulatedPath> paths = IntStream.range(0, 20)
                .mapToObj(i -> path(i, 0, i == 0 ? 100.0 : i == 2 ? 9999.0 : 2160.0))
                .toList();

        List<SimulationPayload.SimulatedPath> first = ResponsePathSampler.sample(paths, 4);
        List<SimulationPayload.SimulatedPath> second = ResponsePathSampler.sample(paths, 4);

        assertEquals(List.of(0, 2, 6, 13), first.stream().map(SimulationPayload.SimulatedPath::pathId).toList(),
                "extremes at 0/2 plus evenly spaced fill, emitted in original order");
        assertEquals(first, second, "the same paths with the same N must produce the identical sample");
    }

    @Test
    @DisplayName("N = 1 (documented degenerate) keeps only the global-maximum path")
    void singleSlot_keepsTheGlobalMaximumPath() {
        List<SimulationPayload.SimulatedPath> sample =
                ResponsePathSampler.sample(pathsWithExtremes(10), 1);

        assertEquals(1, sample.size());
        assertEquals(3, sample.get(0).pathId(),
                "one slot cannot hold both extremes; the documented tie-break is the global-max path");
    }

    @Test
    @DisplayName("identical values still yield exactly the requested count")
    void identicalValues_stillReturnTheRequestedCount() {
        List<SimulationPayload.SimulatedPath> flat = IntStream.range(0, 10)
                .mapToObj(i -> path(i, 0, 4242.0))
                .toList();

        List<SimulationPayload.SimulatedPath> sample = ResponsePathSampler.sample(flat, 3);

        assertEquals(3, sample.size());
        assertTrue(sample.stream().allMatch(p -> p.values().get(0).value() == 4242.0));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /**
     * 10 paths × 11 days. Global minimum 1570.0 lives in path 7, global maximum 2930.0 in
     * path 3 — deliberately mid-list so an evenly-spaced-only sample would miss them.
     */
    private static List<SimulationPayload.SimulatedPath> pathsWithExtremes(int pathCount) {
        return IntStream.range(0, pathCount)
                .mapToObj(p -> new SimulationPayload.SimulatedPath(
                        p, IntStream.range(0, 11)
                        .mapToObj(d -> new SimulationPayload.PortfolioPath(d, pathValue(p, d)))
                        .toList()))
                .toList();
    }

    private static double pathValue(int path, int day) {
        double base = 2000.0 + path * 10.0;
        if (day == 5 && path == 7) return base - 500.0; // 1570.0 — global min
        if (day == 5 && path == 3) return base + 900.0; // 2930.0 — global max
        return base;
    }

    private static SimulationPayload.SimulatedPath path(int pathId, int day, double value) {
        return new SimulationPayload.SimulatedPath(pathId, List.of(new SimulationPayload.PortfolioPath(day, value)));
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
}
