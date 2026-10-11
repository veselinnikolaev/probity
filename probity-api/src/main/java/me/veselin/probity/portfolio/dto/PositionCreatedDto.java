package me.veselin.probity.portfolio.dto;

import java.math.BigDecimal;

/**
 * BFF response DTO returned after adding a portfolio position.
 */
public record PositionCreatedDto(
        String id,
        String ticker,
        BigDecimal quantity
) {}
