package me.veselin.probity.simulation.mapper;

import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationPayload;
import me.veselin.probity.simulation.dto.SimulationData;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SimulationMapper {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

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
                p.allPaths(),
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
}
