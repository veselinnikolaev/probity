package me.veselin.probity.marketdata.port;

import me.veselin.probity.marketdata.domain.PriceBar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outbound port for market data access used by portfolio and simulation contexts.
 */
public interface MarketDataPort {

    /**
     * Returns the latest adjusted close for a symbol.
     */
    BigDecimal getLatestPrice(String ticker);

    /**
     * Daily bars ordered oldest → newest, gap-filled from Yahoo on demand.
     * Returns PriceBar domain objects so callers can use domain methods
     * like dailyReturn(previousBar) directly.
     */
    List<PriceBar> getHistoricalBars(
            String ticker, LocalDate from, LocalDate to);
}