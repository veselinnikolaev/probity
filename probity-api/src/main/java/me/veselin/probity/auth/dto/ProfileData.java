package me.veselin.probity.auth.dto;

/**
 * Domain DTO for user profile data transferred between auth module and BFF.
 */
public record ProfileData(
        String username,
        String email,
        String firstName,
        String lastName,
        String memberSince
) {}
