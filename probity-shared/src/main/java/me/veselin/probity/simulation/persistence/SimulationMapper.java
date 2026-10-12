package me.veselin.probity.simulation.persistence;

import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;

/**
 * Mapper between domain Simulation and JPA SimulationJpaEntity.
 * Handles bidirectional conversion for create and update.
 */
public class SimulationMapper {

    /**
     * Converts a domain Simulation to a JPA entity for creation or update.
     * Includes the domain ID so that JPA can merge on existing entities.
     */
    public static SimulationJpaEntity toJpaEntity(Simulation domain) {
        return SimulationJpaEntity.builder()
                .portfolioId(domain.getPortfolioId())
                .userId(domain.getUserId())
                .numberOfSimulations(domain.getNumberOfSimulations())
                .timeHorizonDays(domain.getTimeHorizonDays())
                .confidenceLevel(domain.getConfidenceLevel())
                .assumedReturnPct(domain.getAssumedReturnPct())
                .assumedVolatilityPct(domain.getAssumedVolatilityPct())
                .currentPortfolioValue(domain.getCurrentPortfolioValue())
                .resultPayload(domain.getResultPayload())
                .status(domain.getStatus() != null ? domain.getStatus().name() : SimulationStatus.PENDING.name())
                .build();
    }

    /**
     * Converts a JPA entity to a domain Simulation.
     * Copies IDs and timestamps from JPA entity to domain.
     */
    public static Simulation toDomain(SimulationJpaEntity entity) {
        Simulation domain = Simulation.builder()
                .portfolioId(entity.getPortfolioId())
                .userId(entity.getUserId())
                .numberOfSimulations(entity.getNumberOfSimulations())
                .timeHorizonDays(entity.getTimeHorizonDays())
                .confidenceLevel(entity.getConfidenceLevel())
                .assumedReturnPct(entity.getAssumedReturnPct())
                .assumedVolatilityPct(entity.getAssumedVolatilityPct())
                .currentPortfolioValue(entity.getCurrentPortfolioValue())
                .resultPayload(entity.getResultPayload())
                .status(entity.getStatus() != null ? SimulationStatus.valueOf(entity.getStatus()) : SimulationStatus.PENDING)
                .build();
        domain.setId(entity.getId());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setVersion(entity.getVersion());
        return domain;
    }
}
