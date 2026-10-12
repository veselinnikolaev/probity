package me.veselin.probity.bff.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * BFF request DTO for credential-based login submission.
 */
public record LoginRequest(
        @Schema(description = "Username or email", example = "john_doe")
        @NotBlank(message = "Identifier is required")
        String identifier,

        @Schema(description = "User password", example = "SecureP@ss123", minLength = 8, maxLength = 64)
        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,64}$",
                message = "Password must contain uppercase, lowercase, number and special character"
        )
        String password
) {}
