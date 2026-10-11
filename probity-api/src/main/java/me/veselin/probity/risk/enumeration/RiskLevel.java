package me.veselin.probity.risk.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RiskLevel {
    // Risk level score bands: LOW (0-34), MODERATE (35-59), HIGH (60-100)
    LOW(0, 34),
    MODERATE(35, 59),
    HIGH(60, 100);

    private final int minScore;
    private final int maxScore;

    public static RiskLevel fromScore(int score) {
        for (RiskLevel level : values()) {
            if (score >= level.minScore && score <= level.maxScore) return level;
        }
        return HIGH;
    }
}