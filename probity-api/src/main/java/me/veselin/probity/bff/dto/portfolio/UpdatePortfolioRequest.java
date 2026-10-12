package me.veselin.probity.bff.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BFF request DTO for updating portfolio name and description.
 */
public record UpdatePortfolioRequest(
        @Schema(description = "Portfolio display name", example = "Retirement Fund", minLength = 1, maxLength = 100)
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 100)
        String name,

        @Schema(description = "Optional portfolio description", example = "Long-term index fund strategy", maxLength = 500)
        @Size(max = 500)
        String description
) {}
