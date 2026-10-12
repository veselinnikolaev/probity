package me.veselin.probity.marketdata.finance;

import me.veselin.probity.marketdata.dto.PriceBarDto;
import me.veselin.probity.portfolio.dto.AssetMetadataDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinanceAdapter {
    List<PriceBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to);
    Optional<AssetMetadataDto> fetchMetadata(String ticker);
}
