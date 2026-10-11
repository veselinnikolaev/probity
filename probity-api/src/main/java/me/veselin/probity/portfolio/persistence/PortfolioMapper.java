package me.veselin.probity.portfolio.persistence;

import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.domain.PortfolioPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper between domain Portfolio and JPA PortfolioJpaEntity.
 * Handles bidirectional conversion with position collection diff logic.
 */
public class PortfolioMapper {

    /**
     * Converts a domain Portfolio to a JPA entity for creation.
     * Creates a fresh entity graph - used only for new aggregates.
     */
    public static PortfolioJpaEntity toJpaEntity(Portfolio domain) {
        PortfolioJpaEntity entity = new PortfolioJpaEntity(
                domain.getName(),
                domain.getUserId(),
                domain.getDescription()
        );
        entity.setId(domain.getId());

        // Convert positions
        for (PortfolioPosition domainPosition : domain.getPositions()) {
            PortfolioPositionJpaEntity positionEntity = toJpaEntity(domainPosition, entity);
            entity.addPosition(positionEntity);
        }

        return entity;
    }

    /**
     * Updates an existing domain Portfolio from a saved JPA entity.
     * Syncs IDs and timestamps back to the domain object without creating a new instance.
     * This ensures that newly-generated IDs from flush are reflected in the in-memory domain object.
     *
     * @param domain The domain object to update
     * @param savedEntity The JPA entity after save/flush with generated IDs
     */
    public static void updateDomainFromJpa(Portfolio domain, PortfolioJpaEntity savedEntity) {
        domain.setId(savedEntity.getId());
        domain.setCreatedAt(savedEntity.getCreatedAt());
        domain.setUpdatedAt(savedEntity.getUpdatedAt());
        domain.setVersion(savedEntity.getVersion());

        // Sync positions by matching asset IDs (since new positions have null ID before save)
        Map<UUID, PortfolioPosition> domainPositionsByAssetId = domain.getPositions().stream()
                .collect(Collectors.toMap(
                        p -> p.getAsset().getId(),
                        p -> p,
                        (a, b) -> a
                ));

        for (PortfolioPositionJpaEntity jpaPos : savedEntity.getPositions()) {
            UUID assetId = jpaPos.getAsset().getId();
            PortfolioPosition domainPos = domainPositionsByAssetId.get(assetId);
            if (domainPos != null) {
                // Update the domain position with generated ID and timestamps
                domainPos.setId(jpaPos.getId());
                domainPos.setCreatedAt(jpaPos.getCreatedAt());
                domainPos.setUpdatedAt(jpaPos.getUpdatedAt());
                domainPos.setVersion(jpaPos.getVersion());
            }
        }
    }

    /**
     * Converts a JPA entity to a domain Portfolio.
     * Copies IDs and timestamps from JPA entity to domain.
     */
    public static Portfolio toDomain(PortfolioJpaEntity entity) {
        Portfolio domain = Portfolio.create(
                entity.getName(),
                entity.getUserId(),
                entity.getDescription()
        );
        domain.setId(entity.getId());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setVersion(entity.getVersion());

        // Convert positions - pass the domain portfolio to avoid circular reference
        for (PortfolioPositionJpaEntity positionEntity : entity.getPositions()) {
            PortfolioPosition domainPosition = toDomain(positionEntity, domain);
            // Manually add position to domain (bypassing domain.addPosition to avoid validation)
            domain.getPositionsInternal().add(domainPosition);
        }

        return domain;
    }

    /**
     * Updates an existing managed JPA entity from a domain Portfolio.
     * Mutates the entity in place rather than replacing it.
     *
     * @param managedEntity The JPA entity currently managed by Hibernate
     * @param domain The domain object with updated state
     * @param updatePositions If true, diff and update the position collection.
     *                        If false, leave positions untouched (for partial loads).
     */
    public static void updateJpaEntity(PortfolioJpaEntity managedEntity, Portfolio domain, boolean updatePositions) {
        // Update scalar fields
        managedEntity.setName(domain.getName());
        managedEntity.setDescription(domain.getDescription());

        // Position collection diff by ID
        if (updatePositions) {
            if (domain.getPositions() == null) {
                throw new IllegalStateException(
                        "Cannot update positions: domain position collection is null (not loaded). " +
                        "Use saveWithPositions() only after findByIdWithPositions()."
                );
            }

            diffAndUpdatePositions(managedEntity, domain.getPositions());
        }
    }

    /**
     * Diffs the position collection by ID and updates the JPA entity accordingly.
     * - Match by ID: update quantity and avgBuyPrice
     * - New positions (null ID in domain): add to JPA collection
     * - Missing positions (in JPA but not in domain): remove (orphanRemoval handles deletion)
     */
    private static void diffAndUpdatePositions(PortfolioJpaEntity jpaEntity, List<PortfolioPosition> domainPositions) {
        // Build map of JPA positions by ID for O(1) lookup
        Map<UUID, PortfolioPositionJpaEntity> jpaPositionsById = jpaEntity.getPositions().stream()
                .collect(Collectors.toMap(
                        PortfolioPositionJpaEntity::getId,
                        p -> p,
                        (a, b) -> a // handle duplicates (shouldn't happen)
                ));

        // Build map of domain positions by ID
        Map<UUID, PortfolioPosition> domainPositionsById = domainPositions.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(
                        PortfolioPosition::getId,
                        p -> p,
                        (a, b) -> a
                ));

        // Update existing positions (match by ID)
        // Iterate over a snapshot to avoid ConcurrentModificationException when removing
        for (PortfolioPositionJpaEntity jpaPos : new ArrayList<>(jpaEntity.getPositions())) {
            PortfolioPosition domainPos = domainPositionsById.get(jpaPos.getId());
            if (domainPos != null) {
                // Position exists in both - update fields
                jpaPos.setQuantity(domainPos.getQuantity());
                jpaPos.setAvgBuyPrice(domainPos.getAvgBuyPrice());
            } else {
                // Position in JPA but not in domain - remove (orphanRemoval will delete)
                jpaEntity.removePosition(jpaPos);
            }
        }

        // Add new positions (null ID in domain)
        for (PortfolioPosition domainPos : domainPositions) {
            if (domainPos.getId() == null) {
                PortfolioPositionJpaEntity newPos = toJpaEntity(domainPos, jpaEntity);
                jpaEntity.addPosition(newPos);
            }
        }
    }

    /**
     * Converts a domain PortfolioPosition to a JPA entity.
     */
    private static PortfolioPositionJpaEntity toJpaEntity(PortfolioPosition domain, PortfolioJpaEntity portfolioEntity) {
        PortfolioPositionJpaEntity entity = new PortfolioPositionJpaEntity(
                portfolioEntity,
                domain.getAsset(),
                domain.getQuantity(),
                domain.getAvgBuyPrice()
        );
        entity.setId(domain.getId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setVersion(domain.getVersion());
        return entity;
    }

    /**
     * Converts a JPA PortfolioPosition entity to a domain PortfolioPosition.
     * Uses the public constructor for mapper reconstruction.
     * @param entity The JPA entity to convert
     * @param portfolio The domain portfolio (already converted) to avoid circular reference
     */
    private static PortfolioPosition toDomain(PortfolioPositionJpaEntity entity, Portfolio portfolio) {
        return new PortfolioPosition(
                portfolio,
                entity.getAsset(),
                entity.getQuantity(),
                entity.getAvgBuyPrice(),
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
