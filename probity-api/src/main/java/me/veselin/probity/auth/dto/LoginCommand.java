package me.veselin.probity.auth.dto;

/**
 * Application-layer command carrying login credentials to the auth domain.
 */
public record LoginCommand(String identifier, String password) {}
