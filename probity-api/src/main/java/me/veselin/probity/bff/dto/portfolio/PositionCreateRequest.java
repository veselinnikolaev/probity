package me.veselin.probity.bff.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * BFF request DTO for adding a position to a portfolio.
 */
public record PositionCreateRequest(
        @Schema(description = "Asset ticker symbol", example = "AAPL")
        @NotBlank(message = "Ticker cannot be blank")
        String ticker,

        @Schema(description = "Position quantity (positive for long, negative for short)", example = "100.5")
        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.0001", message = "Quantity must be greater than 0")
        BigDecimal quantity
) {}

