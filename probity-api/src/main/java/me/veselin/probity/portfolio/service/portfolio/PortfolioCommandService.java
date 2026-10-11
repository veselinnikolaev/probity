package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.portfolio.dto.AddPositionCommand;
import me.veselin.probity.portfolio.dto.UpdatePositionCommand;
import me.veselin.probity.common.domain.event.DomainEventPublisher;
import me.veselin.probity.common.domain.event.PortfolioUpdatedEvent;
import me.veselin.probity.common.exception.ConflictException;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.domain.Asset;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.domain.PortfolioPosition;
import me.veselin.probity.portfolio.dto.PortfolioData;
import me.veselin.probity.portfolio.dto.PositionCreatedDto;
import me.veselin.probity.portfolio.exception.PortfolioNotFoundException;
import me.veselin.probity.portfolio.exception.PositionNotFoundException;
import me.veselin.probity.portfolio.port.portfolio.PortfolioCommandPort;
import me.veselin.probity.portfolio.persistence.PortfolioRepository;
import me.veselin.probity.portfolio.service.asset.AssetResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
/**
 * Command-side application service for portfolio mutations.
 */
public class PortfolioCommandService implements PortfolioCommandPort {

    private final PortfolioRepository portfolioRepository;
    private final AssetResolver assetResolver;
    private final MarketDataPort marketDataPort;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Creates a portfolio for a user while enforcing unique portfolio names per owner.
     */
    @Override
    public PortfolioData create(String name, UUID userId) {
        if (portfolioRepository.existsByNameAndUserId(name, userId)) {
            throw new ConflictException("Portfolio '" + name + "' already exists");
        }
        Portfolio saved = portfolioRepository.save(Portfolio.create(name, userId));
        domainEventPublisher.publish(new PortfolioUpdatedEvent(saved.getId(), userId, Instant.now()));
        return new PortfolioData(saved.getId().toString(), saved.getName(), saved.getDescription());
    }

    /**
     * Adds a position to a portfolio and returns the created position identifier.
     */
    @Override
    public PositionCreatedDto addPosition(UUID portfolioId,
                                          AddPositionCommand request,
                                          UUID userId) {

        Portfolio portfolio = portfolioRepository.findByIdWithPositions(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(
                        "Portfolio not found: " + portfolioId));

        if (!portfolio.getUserId().equals(userId))
            throw new PortfolioNotFoundException("Portfolio not found: " + portfolioId);

        Asset asset = assetResolver.resolve(request.ticker());
        BigDecimal currentPrice = marketDataPort.getLatestPrice(asset.getTicker());

        portfolio.addPosition(asset, request.quantity(), currentPrice);
        portfolioRepository.saveWithPositionsAndFlush(portfolio);

        // Position ID is now populated in the domain object after save
        PortfolioPosition position = portfolio.getPositions().stream()
                .filter(p -> p.getAsset().getId().equals(asset.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Position not found after save for asset: " + asset.getTicker()));

        domainEventPublisher.publish(new PortfolioUpdatedEvent(portfolioId, userId, Instant.now()));

        return new PositionCreatedDto(
                position.getId().toString(),
                asset.getTicker(),
                position.getQuantity()
        );
    }

    /**
     * Updates the quantity of an existing position using the latest market price.
     */
    @Override
    public void updatePosition(UUID portfolioId, UUID positionId,
                               UpdatePositionCommand request, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdWithPositions(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found: " + portfolioId + " for user: " + userId));

        if (!portfolio.getUserId().equals(userId))
            throw new PortfolioNotFoundException("Portfolio not found: " + portfolioId);

        // Find the position to get its ticker, then fetch current price
        PortfolioPosition pos = portfolio.getPositions().stream()
                .filter(p -> p.getId().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new PositionNotFoundException("Position not found: " + positionId + " in portfolio: " + portfolioId));

        BigDecimal currentPrice = marketDataPort.getLatestPrice(pos.getAsset().getTicker());

        portfolio.updatePositionQuantity(positionId, request.quantity(), currentPrice);
        portfolioRepository.saveWithPositionsAndFlush(portfolio);
        domainEventPublisher.publish(new PortfolioUpdatedEvent(portfolioId, userId, Instant.now()));
    }

    /**
     * Removes a position from the portfolio owned by the requesting user.
     */
    @Override
    public void deletePosition(UUID portfolioId, UUID positionId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdWithPositions(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found: " + portfolioId));

        if (!portfolio.getUserId().equals(userId)) {
            throw new PortfolioNotFoundException("Portfolio not found: " + portfolioId);
        }

        portfolio.removePosition(positionId);
        portfolioRepository.saveWithPositionsAndFlush(portfolio);
        domainEventPublisher.publish(new PortfolioUpdatedEvent(portfolioId, userId, Instant.now()));
    }

    @Override
    public PortfolioData update(UUID portfolioId, UUID userId, String name, String description) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found: " + portfolioId));

        if (!portfolio.getUserId().equals(userId)) {
            throw new PortfolioNotFoundException("Portfolio not found: " + portfolioId);
        }

        boolean nameChanged = !portfolio.getName().equalsIgnoreCase(name.trim());
        if (nameChanged && portfolioRepository.existsByNameAndUserId(name.trim(), userId)) {
            throw new ConflictException("Portfolio '" + name.trim() + "' already exists");
        }

        portfolio.update(name, description);
        Portfolio saved = portfolioRepository.save(portfolio);
        domainEventPublisher.publish(new PortfolioUpdatedEvent(saved.getId(), userId, Instant.now()));
        return new PortfolioData(saved.getId().toString(), saved.getName(), saved.getDescription());
    }

    @Override
    public void delete(UUID portfolioId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found: " + portfolioId));

        if (!portfolio.getUserId().equals(userId)) {
            throw new PortfolioNotFoundException("Portfolio not found: " + portfolioId);
        }

        portfolio.softDelete();
        portfolioRepository.save(portfolio);
        domainEventPublisher.publish(new PortfolioUpdatedEvent(portfolioId, userId, Instant.now()));
    }
}
