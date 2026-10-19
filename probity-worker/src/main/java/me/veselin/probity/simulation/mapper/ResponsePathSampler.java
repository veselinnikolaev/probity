package me.veselin.probity.simulation.mapper;

import me.veselin.probity.simulation.domain.SimulationPayload;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic response-side path sampling for the Q9a storage split
 * (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §9/§10).
 *
 * <p>This runs on the assembled response only — after the dual-source read and after
 * {@code MonteCarloSimulationService} has computed statistics, percentileSeries and
 * distribution from <em>every</em> path — so a sample can never change a number, only how
 * many paths the body carries. {@code sampleSize <= 0} (the shipped default) returns the
 * input untouched, which is what keeps every response byte-identical to the pre-Q9a
 * contract until PR-Q9b picks a real N (§9 parks that choice there).
 *
 * <p>Two guarantees make a sampled body safe for a client that draws the paths and
 * derives its y-scale from them (the FanChart y-range note recorded with this PR):
 * <ul>
 *   <li><strong>Extremes:</strong> the paths holding the global minimum and maximum values
 *       across all days are always in the sample (they claim their slots before any
 *       other), so {@code min/max(sampled values) == min/max(all values)} — a y-range a
 *       client computes from the returned paths equals the full-data range.</li>
 *   <li><strong>Reproducibility:</strong> no randomness anywhere — the rest of the sample
 *       is evenly spaced indices over the original order, emitted back in that order.
 *       Same paths + same N always produce the same sample, so responses are stable to
 *       diff and cache.</li>
 * </ul>
 *
 * <p>Edge cases: {@code sampleSize >= size} (and negative, meaning off) returns
 * everything; {@code sampleSize == 1} cannot hold both extremes and returns the
 * global-maximum path — the extremes guarantee needs {@code N >= 2}, and a chart clipped
 * on one side loses less from the top than the bottom of its range.
 */
public final class ResponsePathSampler {

    private ResponsePathSampler() {
    }

    /**
     * @param paths      the full stored path list (never null; possibly empty)
     * @param sampleSize maximum paths to return; {@code <= 0} disables sampling
     * @return the same list when sampling is off or pointless, otherwise a deterministic
     *         subset of at most {@code sampleSize} paths in original order
     */
    public static List<SimulationPayload.SimulatedPath> sample(
            List<SimulationPayload.SimulatedPath> paths, int sampleSize) {

        int size = paths.size();
        if (sampleSize <= 0 || sampleSize >= size) {
            return paths; // disabled (the default 0) or asking for at least everything
        }

        int minIndex = indexOfGlobalExtreme(paths, true);
        int maxIndex = indexOfGlobalExtreme(paths, false);

        if (sampleSize == 1) {
            // Documented degenerate case: one slot cannot hold both extremes.
            return List.of(paths.get(maxIndex));
        }

        Set<Integer> chosen = new LinkedHashSet<>();
        chosen.add(minIndex);
        chosen.add(maxIndex);
        // Evenly spaced indices over the original order; each distinct because
        // (size - 1) / (sampleSize - 1) > 1 whenever sampleSize < size. The size bound is
        // belt-and-braces: it can only trip if that ever stopped holding, in which case a
        // slightly short sample beats a loop that never ends.
        for (int k = 0; k <= size && chosen.size() < sampleSize; k++) {
            chosen.add((int) Math.round((double) k * (size - 1) / (sampleSize - 1)));
        }

        return chosen.stream().sorted().map(paths::get).toList();
    }

    /**
     * Index of the path containing the global minimum (or maximum) value across every day
     * of every path; ties resolve to the first path in original order.
     */
    private static int indexOfGlobalExtreme(List<SimulationPayload.SimulatedPath> paths, boolean min) {
        int best = 0;
        double bestValue = min ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (int i = 0; i < paths.size(); i++) {
            for (SimulationPayload.PortfolioPath point : paths.get(i).values()) {
                double value = point.value();
                if ((min && value < bestValue) || (!min && value > bestValue)) {
                    bestValue = value;
                    best = i;
                }
            }
        }
        return best;
    }
}
