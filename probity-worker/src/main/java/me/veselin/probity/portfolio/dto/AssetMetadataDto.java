package me.veselin.probity.portfolio.dto;

/**
 * Portfolio-layer DTO carrying normalized metadata for a resolved ticker.
 */
public record AssetMetadataDto(
        String ticker,
        String name,
        String quoteType,
        String sector
) {}

