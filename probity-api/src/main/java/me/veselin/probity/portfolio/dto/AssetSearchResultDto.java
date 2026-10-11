package me.veselin.probity.portfolio.dto;

import java.math.BigDecimal;

/**
 * Portfolio query DTO representing one asset-search candidate and optional price.
 */
public record AssetSearchResultDto(
        String ticker,
        String name,
        String type,
        String sector,
        BigDecimal currentPrice
) {}
