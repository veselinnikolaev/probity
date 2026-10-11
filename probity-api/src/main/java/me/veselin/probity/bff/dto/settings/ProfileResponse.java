package me.veselin.probity.bff.dto.settings;

/**
 * Read model returned after profile fetch or update.
 */
public record ProfileResponse(
        String username,
        String email,
        String firstName,
        String lastName,
        String memberSince
) {}
