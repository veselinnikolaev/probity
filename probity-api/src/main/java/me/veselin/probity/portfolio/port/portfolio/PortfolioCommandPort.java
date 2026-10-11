package me.veselin.probity.portfolio.port.portfolio;

import me.veselin.probity.portfolio.dto.AddPositionCommand;
import me.veselin.probity.portfolio.dto.PortfolioData;
import me.veselin.probity.portfolio.dto.PositionCreatedDto;
import me.veselin.probity.portfolio.dto.UpdatePositionCommand;

import java.util.UUID;

/**
 * Inbound command port for portfolio mutation use cases.
 */
public interface PortfolioCommandPort {

    /**
     * Creates a new portfolio for a user while enforcing unique portfolio names per owner.
     */
    PortfolioData create(String name, UUID userId);

    /**
     * Adds a position to a portfolio and returns the created position identifier.
     */
    PositionCreatedDto addPosition(UUID portfolioId, AddPositionCommand request, UUID userId);

    /**
     * Updates the quantity of an existing position using the latest market price.
     */
    void updatePosition(UUID portfolioId, UUID positionId, UpdatePositionCommand request, UUID userId);

    /**
     * Removes a position from the portfolio owned by the requesting user.
     */
    void deletePosition(UUID portfolioId, UUID positionId, UUID userId);

    /**
     * Updates portfolio name and description while enforcing unique name constraint.
     */
    PortfolioData update(UUID portfolioId, UUID userId, String name, String description);

    /**
     * Soft-deletes a portfolio owned by the requesting user.
     */
    void delete(UUID portfolioId, UUID userId);
}
