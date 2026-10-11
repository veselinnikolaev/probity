package me.veselin.probity.bff.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for profile field updates. Email is immutable — not accepted here.
 */
public record UpdateProfileRequest(
        @Schema(description = "User first name", example = "John", maxLength = 100)
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Schema(description = "User last name", example = "Doe", maxLength = 100)
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName
) {}