package me.veselin.probity.bff.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password change requires the current password for re-authentication.
 */
public record ChangePasswordRequest(
        @Schema(description = "Current password for verification", example = "OldP@ss123")
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(description = "New password", example = "NewSecureP@ss456", minLength = 8)
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters")
        String newPassword,

        @Schema(description = "New password confirmation", example = "NewSecureP@ss456")
        @NotBlank(message = "Password confirmation is required")
        String confirmPassword
) {}
