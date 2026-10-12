package me.veselin.probity.auth.dto;

/**
 * Application-layer auth result containing principal identity and issued tokens.
 */
public record AuthResult(String username, String role, String accessToken, String refreshToken) {
}
