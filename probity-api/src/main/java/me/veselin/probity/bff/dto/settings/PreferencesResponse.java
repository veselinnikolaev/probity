package me.veselin.probity.bff.dto.settings;

/**
 * Read model returned after preferences fetch or update.
 */
public record PreferencesResponse(
        String defaultCurrency,
        int defaultConfidenceLevel,
        String defaultTimeHorizon
) {}
