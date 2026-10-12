package me.veselin.probity.marketdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.util.TradingUtil;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.marketdata.repository.PriceBarRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
/**
 * Application service that serves market prices from local storage and
 * backfills missing ranges from the external data adapter on demand.
 */
public class MarketDataService implements MarketDataPort {

    @Value("${probity.marketdata.latest-price-lookback-days:14}")
    private int latestPriceLookbackDays;

    private final PriceBarRepository priceBarRepository;
    private final MarketDataSyncService marketDataSyncService;

    // Track in-progress syncs per ticker to prevent redundant concurrent fetches
    // without blocking unrelated tickers against each other.
    private final ConcurrentHashMap<String, CompletableFuture<Void>> syncInProgress = new ConcurrentHashMap<>();

    @Override
    /**
     * Returns the most recent known adjusted close for a ticker.
     * Uses an on-demand sync window so reads stay resilient when bars are stale.
     */
    public BigDecimal getLatestPrice(String ticker) {
        String upper = ticker.toUpperCase();
        log.trace("Fetching latest price for ticker={}", upper);

        LocalDate from = LocalDate.now().minusDays(latestPriceLookbackDays);
        LocalDate to   = LocalDate.now();

        try {
            ensureDataExists(upper, from, to);
        } catch (Exception e) {
            log.error("Sync failed for ticker={} during latest-price lookup", upper, e);
        }

        return priceBarRepository
                .findTopByTickerOrderByBarDateDesc(upper)
                .map(bar -> {
                    log.trace("Latest price ticker={} date={} price={}", upper, bar.getBarDate(), bar.getAdjClose());
                    return bar.getAdjClose();
                })
                .orElseGet(() -> {
                    log.error("No price data available for ticker={} after sync — returning ZERO sentinel", upper);
                    return BigDecimal.ZERO;
                });
    }

    @Override
    @Cacheable(value = "marketData", key = "#ticker + ':' + #from + ':' + #to")
    /**
     * Returns historical daily bars for a ticker and date range.
     * Missing segments are fetched before read to keep callers stateless.
     * Results are cached using composite key pattern to prevent redundant database hits.
     */
    public List<PriceBar> getHistoricalBars(String ticker, LocalDate from, LocalDate to) {
        String upper = ticker.toUpperCase();
        log.trace("Fetching historical bars ticker={} range={} - {}", upper, from, to);

        try {
            ensureDataExists(upper, from, to);
        } catch (Exception e) {
            log.error("Failed to ensure data for ticker={} range={} - {}", upper, from, to, e);
        }

        List<PriceBar> bars = priceBarRepository
                .findByTickerAndBarDateBetweenOrderByBarDateAsc(upper, from, to);

        if (bars.isEmpty()) {
            log.warn("No historical bars in DB after sync for ticker={} range={} - {}", upper, from, to);
        } else {
            log.trace("Returning {} bars from DB for ticker={}", bars.size(), upper);
        }

        return bars;
    }

    /**
     * Ensures that all trading bars for {@code ticker} in [from, to] exist in the
     * database, fetching any missing gaps from the remote adapter.
     *
     * <p>Does NOT use @Transactional at the method level — each fetchAndPersist
     * call opens its own REQUIRES_NEW transaction so partial successes are committed
     * immediately. Uses atomic pattern with futures to prevent redundant concurrent
     * fetches without blocking threads.
     */
    private void ensureDataExists(String ticker, LocalDate from, LocalDate to) {
        log.info("ensureDataExists ENTER ticker={} from={} to={}", ticker, from, to);

        LocalDate safeTo = TradingUtil.clampToLastCompletedTradingDay(to);
        log.info("ensureDataExists ticker={} safeTo={}", ticker, safeTo);

        if (from.isAfter(safeTo)) {
            log.info("Skipping: from={} is after safeTo={}", from, safeTo);
            return;
        }

        LocalDate safeFrom = TradingUtil.isWeekend(from) ? TradingUtil.nextTradingDay(from) : from;
        log.info("ensureDataExists ticker={} safeFrom={}", ticker, safeFrom);

        if (safeFrom.isAfter(safeTo)) {
            log.info("Skipping: safeFrom={} is after safeTo={}", safeFrom, safeTo);
            return;
        }

        // Atomic pattern: computeIfAbsent ensures only one thread performs the sync
        // for a given ticker, while others wait on the same future
        CompletableFuture<Void> syncFuture = syncInProgress.computeIfAbsent(ticker, k -> CompletableFuture.runAsync(() -> performSync(ticker, safeFrom, safeTo))
                .whenComplete((result, error) -> syncInProgress.remove(ticker)));

        // Block until sync completes (maintains synchronous API contract)
        try {
            syncFuture.join();
        } catch (Exception e) {
            log.error("Sync failed for ticker={}", ticker, e);
            syncInProgress.remove(ticker);
        }
    }

    private void performSync(String ticker, LocalDate safeFrom, LocalDate safeTo) {
        try {
            Optional<LocalDate> minOpt = priceBarRepository.findMinBarDate(ticker, safeFrom, safeTo);
            Optional<LocalDate> maxOpt = priceBarRepository.findMaxBarDate(ticker, safeFrom, safeTo);

            log.info("gap check ticker={} minOpt={} maxOpt={}", ticker, minOpt, maxOpt);

            if (minOpt.isEmpty()) {
                log.info("No data → full fetch ticker={} {} - {}", ticker, safeFrom, safeTo);
                marketDataSyncService.fetchAndPersist(ticker, safeFrom, safeTo);
                return;
            }

            LocalDate min = minOpt.get();
            LocalDate max = maxOpt.get();

            if (min.isAfter(safeFrom)) {
                LocalDate leftTo = TradingUtil.prevTradingDay(min);
                log.info("Left gap ticker={} leftFrom={} leftTo={}", ticker, safeFrom, leftTo);
                if (!leftTo.isBefore(safeFrom)) {
                    marketDataSyncService.fetchAndPersist(ticker, safeFrom, leftTo);
                }
            }

            if (max.isBefore(safeTo)) {
                LocalDate rightFrom = TradingUtil.nextTradingDay(max);
                log.info("Right gap ticker={} rightFrom={} rightTo={}", ticker, rightFrom, safeTo);
                if (!rightFrom.isAfter(safeTo)) {
                    marketDataSyncService.fetchAndPersist(ticker, rightFrom, safeTo);
                }
            }
        } catch (Exception e) {
            log.error("Exception during sync ticker={}", ticker, e);
        }
    }
}