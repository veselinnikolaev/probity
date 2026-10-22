package me.veselin.probity.simulation.mapper;

import lombok.RequiredArgsConstructor;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.dto.SimulationData;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SimulationMapper {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final SimulationRepository simulationRepository;

    /**
     * Maps a simulation row to the summary DTO served by the status/list/result bodies.
     *
     * <p>Q9b API split (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10): the paths are no
     * longer part of this transport at all. The reader of a paths-free body cannot tell the
     * two Q9a storage shapes apart, because neither contributes a path here.
     */
    public SimulationData toDto(Simulation simulation) {
        SimulationPayload p = simulation.getResultPayload();

        // BaseEntity.createdAt is Instant — format as ISO-8601 UTC string
        String createdAt = simulation.getCreatedAt() != null
                ? ISO_FORMATTER.format(simulation.getCreatedAt())
                : null;

        // Handle null payload (PENDING/PROCESSING simulations)
        if (p == null) {
            return new SimulationData(
                    simulation.getId().toString(),
                    simulation.getPortfolioId().toString(),
                    createdAt,
                    new SimulationData.Parameters(
                            simulation.getNumberOfSimulations(),
                            simulation.getTimeHorizonDays(),
                            simulation.getConfidenceLevel()
                    ),
                    simulation.getCurrentPortfolioValue(),
                    List.of(),  // empty percentileSeries
                    new SimulationData.Statistics(
                            0.0, 0.0, 0.0, 0.0, 0.0
                    ),
                    new SimulationData.Outcomes(
                            0.0, 0.0, 0.0, 0.0
                    ),
                    List.of()  // empty distribution
            );
        }

        return new SimulationData(
                simulation.getId().toString(),
                simulation.getPortfolioId().toString(),
                createdAt,
                new SimulationData.Parameters(
                        simulation.getNumberOfSimulations(),
                        simulation.getTimeHorizonDays(),
                        simulation.getConfidenceLevel()
                ),
                simulation.getCurrentPortfolioValue(),
                p.percentileSeries(),
                new SimulationData.Statistics(
                        p.statistics().expectedFinalValue(),
                        p.statistics().medianFinalValue(),
                        p.statistics().stdDeviation(),
                        p.statistics().minValue(),
                        p.statistics().maxValue()
                ),
                new SimulationData.Outcomes(
                        p.outcomes().probabilityOf10PercentLoss(),
                        p.outcomes().probabilityOf20PercentLoss(),
                        p.outcomes().valueAtRisk95(),
                        p.outcomes().conditionalValueAtRisk95()
                ),
                p.distribution()
        );
    }

    /**
     * The full stored paths of a simulation, reassembled from whichever half of the Q9a
     * storage split holds them — the inline payload (every row written before the split,
     * plus the whole sync {@code POST /run} write) or the {@code simulation_paths} row (a
     * split-written completion row). Returns the stored list <em>unbounded</em>: the caller
     * decides the sample (see {@link ResponsePathSampler}) and the response size.
     *
     * <p>An empty result means the row genuinely has no paths (a PENDING/FAILED row, or a
     * row written with none) — the paths endpoint turns that into its response, not a 404,
     * so a completed-but-empty row and a never-completed row read the same way they do on
     * the result body.
     */
    public List<SimulationPayload.SimulatedPath> storedPaths(Simulation simulation) {
        SimulationPayload p = simulation.getResultPayload();
        List<SimulationPayload.SimulatedPath> inlinePaths = p == null ? null : p.allPaths();
        return resolveAllPaths(simulation.getId(), inlinePaths);
    }

    /**
     * The dual-source read of the Q9a storage split (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md
     * §9 row 5): a row that still carries its paths inline — every row written before the
     * split, plus the sync {@code POST /run} write, which Q9a deliberately leaves whole —
     * serves them straight from the payload; a row written by the split completion has an
     * empty inline array and gets its paths from {@code simulation_paths}. A row with
     * neither (an empty array and no paths row) reads as an empty list, exactly as an
     * empty inline array does today.
     */
    private List<SimulationPayload.SimulatedPath> resolveAllPaths(
            UUID simulationId, List<SimulationPayload.SimulatedPath> inlinePaths) {
        return (inlinePaths != null && !inlinePaths.isEmpty())
                ? inlinePaths
                : simulationRepository.findPaths(simulationId).orElse(List.of());
    }
}
