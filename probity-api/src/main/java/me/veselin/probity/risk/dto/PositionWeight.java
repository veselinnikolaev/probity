package me.veselin.probity.risk.dto;

import java.math.BigDecimal;

/**
 * Value object for position data needed for HHI calculation.
 * Minimal data required: ticker, quantity, and current price.
 */
public record PositionWeight(
        String ticker,
        BigDecimal quantity,
        BigDecimal price
) {}
