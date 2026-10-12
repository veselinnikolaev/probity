package me.veselin.probity.auth.dto;

/**
 * Domain DTO for user preferences data transferred between auth module and BFF.
 */
public record PreferencesData(
        String defaultCurrency,
        int defaultConfidenceLevel,
        String defaultTimeHorizon
) {}
