package me.veselin.probity.marketdata.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data-transfer representation of a daily price bar from external market data.
 */
public record PriceBarDto(
        String ticker,
        LocalDate barDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjClose,
        Long volume
) {}