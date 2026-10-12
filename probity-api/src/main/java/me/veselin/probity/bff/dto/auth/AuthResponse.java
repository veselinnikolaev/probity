package me.veselin.probity.bff.dto.auth;

/**
 * BFF response DTO returned after successful auth lifecycle operations.
 */
public record AuthResponse(String username, String role) {}
