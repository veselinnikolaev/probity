package me.veselin.probity.bff.dto.settings;

/**
 * Lightweight session summary for the active sessions list.
 */
public record SessionResponse(
        String sessionId,
        String deviceHint,
        String ipAddress,
        String lastActiveAt
) {}
