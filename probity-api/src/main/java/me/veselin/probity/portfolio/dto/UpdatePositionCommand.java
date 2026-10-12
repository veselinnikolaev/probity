package me.veselin.probity.portfolio.dto;

import java.math.BigDecimal;

/**
 * Command DTO for updating a position quantity in a portfolio.
 * Application-specific DTO in the portfolio bounded context.
 */
public record UpdatePositionCommand(
        BigDecimal quantity
) {}
