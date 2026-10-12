package me.veselin.probity.bff.security.filter.jwt;

import java.util.UUID;

/**
 * Authenticated principal DTO attached to secured controller methods.
 */
public record UserPrincipal(
        UUID id,
        String username,
        String role
) {}
