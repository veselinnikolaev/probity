package me.veselin.probity.bff.dto.simulation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * BFF request DTO for starting a Monte Carlo simulation run.
 */
public record RunSimulationRequest(
        @Schema(description = "Portfolio ID to simulate", example = "123e4567-e89b-12d3-a456-426614174000")
        @NotNull(message = "portfolioId is required")
        UUID portfolioId,

        @Schema(description = "Number of Monte Carlo simulation paths", example = "1000", minimum = "100", maximum = "10000")
        @Min(value = 100, message = "numberOfSimulations must be at least 100")
        @Max(value = 10_000, message = "numberOfSimulations must not exceed 10 000")
        int numberOfSimulations,

        @Schema(description = "Time horizon in days", example = "90", minimum = "1", maximum = "1260")
        @Min(value = 1,   message = "timeHorizonDays must be at least 1")
        @Max(value = 1260, message = "timeHorizonDays must not exceed 1260 (5 years)")
        int timeHorizonDays,

        @Schema(description = "Confidence level for VaR calculations", example = "0.95", minimum = "0.80", maximum = "0.99")
        @DecimalMin(value = "0.80", message = "confidenceLevel must be at least 0.80")
        @DecimalMax(value = "0.99", message = "confidenceLevel must not exceed 0.99")
        double confidenceLevel,

        // Optional overrides — when null the service derives them from market data
        @Schema(description = "Optional assumed annual return percent override", example = "8.5", minimum = "-50.0", maximum = "100.0")
        @DecimalMin(value = "-50.0", message = "assumedReturnPercent must be ≥ -50")
        @DecimalMax(value = "100.0",  message = "assumedReturnPercent must be ≤ 100")
        Double assumedReturnPercent,

        @Schema(description = "Optional assumed annual volatility percent override", example = "15.0", minimum = "1.0", maximum = "200.0")
        @DecimalMin(value = "1.0",  message = "assumedVolatilityPercent must be ≥ 1")
        @DecimalMax(value = "200.0", message = "assumedVolatilityPercent must be ≤ 200")
        Double assumedVolatilityPercent
) {}