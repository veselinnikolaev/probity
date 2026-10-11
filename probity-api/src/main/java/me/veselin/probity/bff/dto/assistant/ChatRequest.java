package me.veselin.probity.bff.dto.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BFF request DTO for AI assistant chat messages.
 */
public record ChatRequest(
        @Schema(description = "User message to the AI assistant", example = "What is my portfolio risk?", maxLength = 1000)
        @NotBlank(message = "Message cannot be blank")
        @Size(max = 1000, message = "Message too long")
        String message
) {}