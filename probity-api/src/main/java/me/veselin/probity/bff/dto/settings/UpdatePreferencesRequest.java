package me.veselin.probity.bff.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * UI preference defaults — all fields required to prevent partial updates.
 */
public record UpdatePreferencesRequest(
        @Schema(description = "Default currency code", example = "USD")
        @NotBlank(message = "Currency is required")
        String defaultCurrency,

        @Schema(description = "Default confidence level for risk calculations", example = "95")
        @NotNull(message = "Confidence level is required")
        Integer defaultConfidenceLevel,

        @Schema(description = "Default time horizon", example = "90d")
        @NotBlank(message = "Time horizon is required")
        String defaultTimeHorizon
) {}
