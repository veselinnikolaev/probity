package me.veselin.probity.auth.dto;

/**
 * Application-layer command carrying registration data into the auth domain.
 */
public record RegisterCommand(String firstName, String lastName, String username, String email, String password) {}
