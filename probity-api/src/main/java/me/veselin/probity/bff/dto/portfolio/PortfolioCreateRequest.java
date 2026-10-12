package me.veselin.probity.bff.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * BFF request DTO for creating a new user portfolio.
 */
public record PortfolioCreateRequest(
        @Schema(description = "Portfolio display name", example = "Retirement Fund")
        @NotBlank(message = "Name is required") String name,

        @Schema(description = "Optional portfolio description", example = "Long-term index fund strategy")
        String description
) {}
