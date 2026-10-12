package me.veselin.probity.portfolio.service.asset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.marketdata.finance.FinanceAdapter;
import me.veselin.probity.portfolio.domain.Asset;
import me.veselin.probity.portfolio.dto.AssetMetadataDto;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.portfolio.enumeration.Sector;
import me.veselin.probity.portfolio.exception.AssetNotFoundException;
import me.veselin.probity.portfolio.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves an Asset by ticker — from DB if cached, from Yahoo Finance otherwise.
 * Auto-creates the asset on first encounter so subsequent lookups are instant.
 *
 * Uses REQUIRES_NEW so the saved Asset is committed independently of the
 * calling transaction — preventing duplicate-creation races across concurrent
 * addPosition calls for the same ticker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetResolver {

    private final AssetRepository assetRepository;
    private final FinanceAdapter  financeAdapter;

    /**
     * Returns an existing Asset or creates one from Yahoo Finance metadata.
     * Throws AssetNotFoundException if Yahoo cannot resolve the ticker.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Asset resolve(String ticker) {
        String upper = ticker.toUpperCase();

        return assetRepository.findByTicker(upper).orElseGet(() -> {
            log.info("Asset not found in DB for ticker={} — fetching from Yahoo", upper);

            AssetMetadataDto meta = financeAdapter.fetchMetadata(upper)
                    .orElseThrow(() -> new AssetNotFoundException(
                            "Unknown ticker: " + upper + " — not found in Yahoo Finance"));

            Asset created = Asset.create(
                    upper,
                    meta.name(),
                    mapSector(meta.sector()),
                    mapType(meta.quoteType())
            );

            Asset saved = assetRepository.save(created);
            log.info("Auto-created asset ticker={} name='{}' type={} sector={}",
                    upper, meta.name(), saved.getType(), saved.getSector());
            return saved;
        });
    }

    // ── Enum mapping ──────────────────────────────────────────────────────────

    /**
     * Maps Yahoo quoteType strings to your AssetType enum.
     * Yahoo values: EQUITY, ETF, CRYPTOCURRENCY, MUTUALFUND, FUTURE, INDEX, CURRENCY
     */
    private AssetType mapType(String yahooQuoteType) {
        if (yahooQuoteType == null) return AssetType.STOCK;
        return switch (yahooQuoteType.toUpperCase()) {
            case "ETF", "MUTUALFUND" -> AssetType.ETF;
            case "CRYPTOCURRENCY"    -> AssetType.CRYPTO;
            default                  -> AssetType.STOCK; // EQUITY, INDEX, etc.
        };
    }

    /**
     * Maps Yahoo sector strings to your Sector enum.
     * Returns UNKNOWN for ETF/CRYPTO where Yahoo returns no sector.
     */
    private Sector mapSector(String yahooSector) {
        if (yahooSector == null || yahooSector.isBlank()) return Sector.UNKNOWN;
        return switch (yahooSector) {
            case "Technology"               -> Sector.TECHNOLOGY;
            case "Financial Services",
                 "Financial"               -> Sector.FINANCIAL;
            case "Healthcare"               -> Sector.HEALTHCARE;
            case "Energy"                   -> Sector.ENERGY;
            case "Consumer Cyclical",
                 "Consumer Defensive",
                 "Consumer"                -> Sector.CONSUMER;
            case "Basic Materials",
                 "Industrials",
                 "Utilities",
                 "Real Estate",
                 "Communication Services" -> Sector.UNKNOWN;
            default -> {
                log.warn("Unmapped Yahoo sector='{}' — defaulting to UNKNOWN", yahooSector);
                yield Sector.UNKNOWN;
            }
        };
    }
}
