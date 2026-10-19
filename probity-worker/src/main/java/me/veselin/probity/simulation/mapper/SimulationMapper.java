package me.veselin.probity.simulation.mapper;

import lombok.RequiredArgsConstructor;
import me.veselin.probity.simulation.config.SimulationProperties;
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

    private final SimulationProperties simulationProperties;

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
                    List.of(),  // empty allPaths
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
                resolveAllPaths(simulation.getId(), p.allPaths()),
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
     * The dual-source read of the Q9a storage split (docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md
     * §9 row 5): a row that still carries its paths inline — every row written before the
     * split, plus the sync {@code POST /run} write, which Q9a deliberately leaves whole —
     * serves them straight from the payload; a row written by the split completion has an
     * empty inline array and gets its paths from {@code simulation_paths}. A row with
     * neither (an empty array and no paths row) reads as an empty list, exactly as an
     * empty inline array does today, so no endpoint can tell the two storage shapes apart
     * from the response alone — that is the point of the split.
     *
     * <p>Sampling, if configured, happens here — after reassembly, on the response side
     * only. Statistics/percentiles/distribution were computed from every path back in
     * {@code MonteCarloSimulationService.run} and are never re-derived from a sample; with
     * the default (0, disabled) this returns the stored list untouched, which is what keeps
     * every response byte-identical to the recorded pre-Q9a contract.
     */
    private List<SimulationPayload.SimulatedPath> resolveAllPaths(
            UUID simulationId, List<SimulationPayload.SimulatedPath> inlinePaths) {
        List<SimulationPayload.SimulatedPath> stored =
                (inlinePaths != null && !inlinePaths.isEmpty())
                        ? inlinePaths
                        : simulationRepository.findPaths(simulationId).orElse(List.of());
        return ResponsePathSampler.sample(stored, simulationProperties.getResponsePathsSampleSize());
    }
}
