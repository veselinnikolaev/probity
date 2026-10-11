package me.veselin.probity.portfolio.persistence;

import lombok.RequiredArgsConstructor;
import me.veselin.probity.portfolio.domain.Portfolio;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter wrapping PortfolioJpaRepository and PortfolioMapper.
 * Provides domain-oriented interface for services to use.
 */
@Repository
@RequiredArgsConstructor
public class PortfolioRepository {

    private final PortfolioJpaRepository jpaRepository;

    /**
     * Saves a portfolio without updating positions.
     * Used for create/update operations where positions are not loaded.
     */
    public Portfolio save(Portfolio domain) {
        PortfolioJpaEntity jpaEntity;
        if (domain.getId() == null) {
            // Create new
            jpaEntity = PortfolioMapper.toJpaEntity(domain);
        } else {
            // Update existing - don't touch positions
            jpaEntity = jpaRepository.findById(domain.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Portfolio not found with id: " + domain.getId()));
            PortfolioMapper.updateJpaEntity(jpaEntity, domain, false);
        }
        PortfolioJpaEntity saved = jpaRepository.save(jpaEntity);
        return PortfolioMapper.toDomain(saved);
    }

    /**
     * Saves a portfolio with position diff logic and flushes immediately.
     * Used for position mutations where positions are loaded via findByIdWithPositions.
     * Guard: throws if positions collection is null (not loaded).
     */
    public Portfolio saveWithPositionsAndFlush(Portfolio domain) {
        PortfolioJpaEntity jpaEntity;
        if (domain.getId() == null) {
            // Create new
            jpaEntity = PortfolioMapper.toJpaEntity(domain);
        } else {
            // Update existing - diff positions
            jpaEntity = jpaRepository.findByIdWithPositions(domain.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Portfolio not found with id: " + domain.getId()));
            PortfolioMapper.updateJpaEntity(jpaEntity, domain, true);
        }
        PortfolioJpaEntity saved = jpaRepository.saveAndFlush(jpaEntity);
        // Update the domain object with generated IDs instead of creating a new one
        PortfolioMapper.updateDomainFromJpa(domain, saved);
        return domain;
    }

    public Optional<Portfolio> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(PortfolioMapper::toDomain);
    }

    public Optional<Portfolio> findByIdWithPositions(UUID id) {
        return jpaRepository.findByIdWithPositions(id)
                .map(PortfolioMapper::toDomain);
    }

    /**
     * Portfolio with positions <em>and</em> their assets, safe to use after the session
     * that loaded it has closed.
     *
     * <p>See {@link PortfolioJpaRepository#findByIdWithPositionsAndAssets} for why this is
     * not just {@link #findByIdWithPositions}.
     */
    public Optional<Portfolio> findByIdWithPositionsAndAssets(UUID id) {
        return jpaRepository.findByIdWithPositionsAndAssets(id)
                .map(PortfolioMapper::toDomain);
    }

    public List<Portfolio> findByUserIdWithPositions(UUID userId) {
        return jpaRepository.findByUserIdWithPositions(userId).stream()
                .map(PortfolioMapper::toDomain)
                .toList();
    }

    public boolean existsByNameAndUserId(String name, UUID userId) {
        return jpaRepository.existsByNameAndUserId(name, userId);
    }

    /**
     * Deletes all portfolios (for test cleanup only).
     */
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
