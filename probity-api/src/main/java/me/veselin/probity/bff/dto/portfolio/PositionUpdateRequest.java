package me.veselin.probity.bff.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * BFF request DTO for changing the quantity of an existing position.
 */
public record PositionUpdateRequest(
        @Schema(description = "New position quantity (positive for long, negative for short)", example = "150.0")
        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.0001", message = "Quantity must be greater than 0")
        BigDecimal quantity
) {}
