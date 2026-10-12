package me.veselin.probity.portfolio.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PortfolioJpaEntity.
 * This is the low-level JPA interface that the PortfolioRepository adapter wraps.
 */
public interface PortfolioJpaRepository extends JpaRepository<PortfolioJpaEntity, UUID> {

    @Query("SELECT p FROM PortfolioJpaEntity p LEFT JOIN FETCH p.positions WHERE p.id = :id AND p.deleted = false")
    Optional<PortfolioJpaEntity> findByIdWithPositions(@Param("id") UUID id);

    /**
     * As {@link #findByIdWithPositions}, but also fetches each position's asset.
     *
     * <p>{@link #findByIdWithPositions} leaves the assets lazy, which is what makes it
     * unusable outside a session: the caller gets proxies that throw
     * {@code LazyInitializationException} on the first {@code getTicker()}. That is fine for
     * a request-scoped caller under {@code @Transactional} and useless for a detached one.
     *
     * <p>Kept separate rather than widened in place because {@link #findByIdWithPositions}
     * also serves the write paths ({@code saveWithPositionsAndFlush}, the position-diff in
     * {@code PortfolioCommandService}), which never read asset state and should not pay for
     * the join.
     */
    @Query("SELECT p FROM PortfolioJpaEntity p LEFT JOIN FETCH p.positions pos LEFT JOIN FETCH pos.asset "
            + "WHERE p.id = :id AND p.deleted = false")
    Optional<PortfolioJpaEntity> findByIdWithPositionsAndAssets(@Param("id") UUID id);

    @Query("SELECT p FROM PortfolioJpaEntity p LEFT JOIN FETCH p.positions WHERE p.userId = :userId AND p.deleted = false")
    List<PortfolioJpaEntity> findByUserIdWithPositions(@Param("userId") UUID userId);

    boolean existsByNameAndUserId(String name, UUID userId);
}
