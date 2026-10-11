package me.veselin.probity.bff.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * BFF request DTO for self-service account registration.
 */
public record RegisterRequest(
        @Schema(description = "User first name", example = "John", minLength = 2, maxLength = 50)
        @NotBlank @Size(min = 2, max = 50)
        String firstName,

        @Schema(description = "User last name", example = "Doe", minLength = 2, maxLength = 50)
        @NotBlank @Size(min = 2, max = 50)
        String lastName,

        @Schema(description = "Unique username", example = "john_doe", minLength = 3, maxLength = 20)
        @NotBlank @Size(min = 3, max = 20)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$",
                message = "Username can contain only letters, numbers and underscore")
        String username,

        @Schema(description = "User email address", example = "john@example.com", maxLength = 100)
        @NotBlank @Email @Size(max = 100)
        String email,

        @Schema(description = "User password", example = "SecureP@ss123", minLength = 8, maxLength = 64)
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,64}$",
                message = "Password must contain uppercase, lowercase, number and special character"
        )
        String password
) {
}
