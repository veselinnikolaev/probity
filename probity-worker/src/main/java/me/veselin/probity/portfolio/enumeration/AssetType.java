package me.veselin.probity.portfolio.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AssetType {
    STOCK(35),
    ETF(25),
    CRYPTO(60),
    BOND(10);

    private final int baseRiskScore;
}