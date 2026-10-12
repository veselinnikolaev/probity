package me.veselin.probity.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.assistant.port.AssistantPort;
import me.veselin.probity.bff.dto.assistant.ChatRequest;
import me.veselin.probity.bff.dto.assistant.ChatResponse;
import me.veselin.probity.bff.security.filter.jwt.UserPrincipal;
import me.veselin.probity.bff.security.rate_limit.RateLimit;
import me.veselin.probity.common.util.ApiRoutes;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Assistant", description = "AI-powered portfolio risk analysis")
@RestController
@RequiredArgsConstructor
@Slf4j
/**
 * Controller for AI-powered portfolio risk analysis assistant.
 */
public class AssistantController {

    private final AssistantPort assistantPort;

    /**
     * POST /assistant/chat
     * Processes a user message through the AI assistant with portfolio tool access.
     * Rate limited to 10 requests per 60 seconds per IP due to AI API cost.
     *
     * @param principal authenticated user principal
     * @param request chat request with user message
     * @return 200 OK with AI response
     */
    @Operation(summary = "Chat with AI assistant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "AI response returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @RateLimit(requests = 10, seconds = 60)
    @PostMapping(ApiRoutes.Assistant.CHAT)
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatRequest request) {

        log.info("AI chat request from user: {}, message length: {}", principal.id(), request.message().length());
        String response = assistantPort.chat(principal.id(), request.message());
        log.info("AI chat response generated for user: {}, response length: {}", principal.id(), response.length());
        return ResponseEntity.ok(new ChatResponse(response));
    }
}