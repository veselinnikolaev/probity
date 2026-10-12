package me.veselin.probity.risk.dto;

import me.veselin.probity.portfolio.enumeration.AssetType;

/**
 * Value object for asset risk data needed for risk score calculation.
 * Minimal data required: asset type and base risk score.
 */
public record AssetRiskProfile(
        AssetType type,
        int baseRiskScore
) {}
