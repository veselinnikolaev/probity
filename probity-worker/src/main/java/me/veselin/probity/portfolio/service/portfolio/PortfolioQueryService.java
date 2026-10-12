package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.util.SeriesUtil;
import me.veselin.probity.common.util.TradingUtil;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.domain.PortfolioPosition;
import me.veselin.probity.portfolio.dto.PortfolioDto;
import me.veselin.probity.portfolio.exception.PortfolioNotFoundException;
import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.portfolio.persistence.PortfolioRepository;
import me.veselin.probity.portfolio.service.portfolio.PortfolioDataHelper.PortfolioTimeSeries;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
/**
 * Query-side portfolio read model assembler used by BFF endpoints.
 */
public class PortfolioQueryService implements PortfolioQueryPort {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioDataHelper portfolioDataHelper;

    // ── Access control ────────────────────────────────────────────────────────

    /**
     * Loads a portfolio aggregate with positions while enforcing owner-level access.
     */
    @Override
    public Portfolio loadPortfolioWithPositions(UUID id, UUID principalId) {
        return requireOwner(portfolioRepository.findByIdWithPositions(id).orElse(null), id, principalId);
    }

    @Override
    public Portfolio loadPortfolioWithPositionsAndAssets(UUID id, UUID principalId) {
        return requireOwner(portfolioRepository.findByIdWithPositionsAndAssets(id).orElse(null), id, principalId);
    }

    /**
     * The shared half of every portfolio read: the row must exist, and it must be the
     * caller's. The not-found log stays here so both loaders report it identically.
     */
    private Portfolio requireOwner(Portfolio portfolio, UUID id, UUID principalId) {
        if (portfolio == null) {
            log.error("Portfolio not found id={}", id);
            throw new PortfolioNotFoundException("Portfolio not found: " + id);
        }
        if (!portfolio.getUserId().equals(principalId)) {
            throw new AccessDeniedException("Access denied to portfolio: " + id);
        }
        return portfolio;
    }

    @Override
    public Instant getLastModified(UUID portfolioId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(
                        "Portfolio not found: " + portfolioId));

        if (!portfolio.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to portfolio: " + portfolioId);
        }

        return portfolio.getUpdatedAt();
    }

    // ── Portfolios list ───────────────────────────────────────────────────────

    /**
     * Builds portfolio cards with current valuation and compact risk indicators.
     */
    @Override
    public List<PortfolioDto> getPortfolios(UUID userId) {
        log.debug("Fetching portfolios for userId={}", userId);

        return portfolioRepository.findByUserIdWithPositions(userId).stream()
                .map(this::toPortfolioDto)
                .toList();
    }

    // ── Single portfolio ──────────────────────────────────────────────────────

    /**
     * Returns a single portfolio overview enriched with valuation and trend metrics.
     */
    @Override
    public PortfolioDto getPortfolio(UUID id, UUID principalId) {
        log.debug("Fetching portfolio with id={}", id);

        return portfolioRepository.findByIdWithPositions(id)
                .map(portfolio -> {
                    if (!portfolio.getUserId().equals(principalId)) {
                        throw new AccessDeniedException("Access denied to portfolio: " + id);
                    }
                    return toPortfolioDto(portfolio);
                })
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found: " + id));
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Converts a Portfolio aggregate into a PortfolioDto enriched with
     * current valuation, volatility, Sharpe ratio, and a value sparkline.
     */
    private PortfolioDto toPortfolioDto(Portfolio portfolio) {
        List<PortfolioPosition> positions = portfolio.getPositions();

        Map<String, Double> prices = portfolioDataHelper.fetchLatestPrices(positions);
        double total = portfolioDataHelper.computeTotalValue(positions, prices);

        LocalDate to = TradingUtil.lastCompletedTradingDay();
        LocalDate from = to.minusDays(PortfolioDataHelper.PORTFOLIO_LOOKBACK_DAYS);
        PortfolioTimeSeries ts = portfolioDataHelper.buildTimeSeries(positions, from, to);

        double vol = portfolioDataHelper.safeVolatility(ts.returns());
        double sharpe = portfolioDataHelper.safeSharpe(ts.returns());
        List<Double> spark = SeriesUtil.downsample(ts.values(), PortfolioDataHelper.SPARKLINE_POINTS);

        String createdAt = portfolio.getCreatedAt() != null
                ? portfolio.getCreatedAt().toString() : null;

        return new PortfolioDto(
                portfolio.getId().toString(),
                portfolio.getName(),
                total,
                createdAt,
                positions.size(),
                vol,
                sharpe,
                spark
        );
    }
}