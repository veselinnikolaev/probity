package me.veselin.probity.portfolio.dto;

import java.math.BigDecimal;

/**
 * Command DTO for adding a position to a portfolio.
 * Application-specific DTO in the portfolio bounded context.
 */
public record AddPositionCommand(
        String ticker,
        BigDecimal quantity
) {}
