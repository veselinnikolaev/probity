package me.veselin.probity.portfolio.service.asset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.marketdata.finance.FinanceAdapter;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.domain.Asset;
import me.veselin.probity.portfolio.dto.AssetSearchResultDto;
import me.veselin.probity.portfolio.port.asset.AssetQueryPort;
import me.veselin.probity.portfolio.repository.AssetRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
/**
 * Query-side asset lookup service combining local search with external metadata fallback.
 */
public class AssetQueryService implements AssetQueryPort {

    private static final int MAX_DB_RESULTS    = 8;
    private static final int MIN_TICKER_LENGTH = 1;

    private final AssetRepository assetRepository;
    private final MarketDataPort  marketDataPort;
    private final FinanceAdapter financeAdapter;

    /**
     * Two-phase search:
     *   1. Query local Asset table (fast, no network)
     *   2. If query looks like an exact ticker and DB returned nothing,
     *      probe Yahoo Finance for that ticker directly
     *
     * Results are deduplicated by ticker and enriched with the latest cached price.
     */
    public List<AssetSearchResultDto> search(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return List.of();

        String q = rawQuery.trim().toUpperCase();
        log.debug("Asset search q='{}'", q);

        // Phase 1: local DB search
        List<Asset> dbResults = assetRepository.searchByQuery(
                q, PageRequest.of(0, MAX_DB_RESULTS));

        // Use LinkedHashMap to preserve rank order and deduplicate by ticker
        Map<String, AssetSearchResultDto> resultMap = new LinkedHashMap<>();
        for (Asset a : dbResults) {
            resultMap.put(a.getTicker(), toDto(a));
        }

        // Phase 2: if the query is short (likely a ticker) and we got nothing,
        // try Yahoo directly — this handles tickers not yet in your DB
        boolean looksLikeTicker = q.length() <= 5 && q.matches("[A-Z0-9.\\-]+");
        if (resultMap.isEmpty() && looksLikeTicker) {
            log.debug("DB miss for q='{}' — probing Yahoo Finance", q);
            financeAdapter.fetchMetadata(q).ifPresent(meta -> {
                BigDecimal price = fetchPriceSafe(meta.ticker());
                resultMap.put(meta.ticker(), new AssetSearchResultDto(
                        meta.ticker(),
                        meta.name(),
                        meta.quoteType(),  // raw Yahoo type — frontend maps display label
                        meta.sector() != null ? meta.sector() : "Unknown",
                        price
                ));
            });
        }

        return new ArrayList<>(resultMap.values());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AssetSearchResultDto toDto(Asset asset) {
        BigDecimal price = fetchPriceSafe(asset.getTicker());
        return new AssetSearchResultDto(
                asset.getTicker(),
                asset.getName(),
                asset.getType().name(),
                asset.getSector().getLabel(),
                price
        );
    }

    /** Never throws — price is best-effort. Returns null if unavailable. */
    private BigDecimal fetchPriceSafe(String ticker) {
        try {
            BigDecimal p = marketDataPort.getLatestPrice(ticker);
            return p.compareTo(BigDecimal.ZERO) > 0 ? p : null;
        } catch (Exception e) {
            log.warn("Price fetch failed for ticker={} during search", ticker);
            return null;
        }
    }
}
