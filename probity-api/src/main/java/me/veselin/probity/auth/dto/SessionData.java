package me.veselin.probity.auth.dto;

/**
 * Domain DTO for user session data transferred between auth module and BFF.
 */
public record SessionData(
        String sessionId,
        String deviceHint,
        String ipAddress,
        String issuedAt
) {}
