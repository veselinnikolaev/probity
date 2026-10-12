package me.veselin.probity.portfolio.port.portfolio;

import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.dto.PortfolioDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inbound query port for portfolio aggregate read use cases.
 */
public interface PortfolioQueryPort {

    /**
     * Returns all portfolios owned by a user enriched with valuation and risk metrics.
     */
    List<PortfolioDto> getPortfolios(UUID userId);

    /**
     * Returns a single portfolio overview enriched with valuation and trend metrics.
     */
    PortfolioDto getPortfolio(UUID id, UUID userId);

    /**
     * Loads a portfolio aggregate with positions while enforcing owner-level access.
     */
    Portfolio loadPortfolioWithPositions(UUID uuid, UUID userId);

    /**
     * Loads a portfolio aggregate with positions and their assets while enforcing
     * owner-level access.
     *
     * <p>Same access rules and same result as {@link #loadPortfolioWithPositions}, but the
     * assets arrive initialized. That difference matters for any caller that is not inside a
     * transaction: {@link #loadPortfolioWithPositions} returns detached proxies that throw
     * {@code LazyInitializationException} on first use.
     *
     * <p>Use this from detached callers — the async simulation worker is the one that
     * exists today. Request-scoped callers under {@code @Transactional} do not need it.
     */
    Portfolio loadPortfolioWithPositionsAndAssets(UUID uuid, UUID userId);

    /**
     * Returns the last modified timestamp for a portfolio.
     */
    Instant getLastModified(UUID uuid, UUID userId);
}
