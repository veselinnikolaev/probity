package me.veselin.probity.portfolio.dto;

import java.util.List;

/**
 * Portfolio query DTO representing a user-facing risk alert card.
 */
public record RiskAlertDto(
        String id,
        String message,
        String severity,
        String timestamp,
        List<String> affectedAssets
) {}
