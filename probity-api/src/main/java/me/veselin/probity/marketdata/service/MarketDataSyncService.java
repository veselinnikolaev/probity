package me.veselin.probity.marketdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.marketdata.dto.PriceBarDto;
import me.veselin.probity.marketdata.finance.FinanceAdapter;
import me.veselin.probity.marketdata.repository.PriceBarRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataSyncService {
    private final PriceBarRepository repository;
    private final FinanceAdapter financeAdapter;

    private final Executor ioExecutor;

    /**
     * Fetches bars from the remote adapter and persists any that are not already
     * in the database. Each invocation runs in its own transaction so that a
     * failure in one gap does not roll back a previously committed gap.
     *
     * <p>An empty result from the adapter is logged as a warning but is not
     * treated as an error — it is a normal outcome for date ranges that consist
     * entirely of market holidays (e.g. a range of [Jan 1, Jan 1]).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fetchAndPersist(String ticker, LocalDate from, LocalDate to) {
        fetchAndPersistInternal(ticker, from, to);
    }

    /**
     * Async version of fetchAndPersist that runs on the virtual thread executor.
     * Returns a CompletableFuture for non-blocking I/O operations.
     */
    @Async("ioExecutor")
    public CompletableFuture<Void> fetchAndPersistAsync(String ticker, LocalDate from, LocalDate to) {
        try {
            fetchAndPersistInternal(ticker, from, to);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void fetchAndPersistInternal(String ticker, LocalDate from, LocalDate to) {
        log.debug("Fetching bars from adapter ticker={} range={} - {}", ticker, from, to);

        List<PriceBarDto> bars;
        try {
            bars = financeAdapter.fetchDailyBars(ticker, from, to);
        } catch (Exception e) {
            log.error("Adapter call failed for ticker={} range={} - {}", ticker, from, to, e);
            return;
        }

        if (bars == null || bars.isEmpty()) {
            // Normal for holiday-only ranges (e.g. New Year's Day).
            // The gap detection in ensureDataExists will not re-attempt this range
            // because the clamp and boundary logic already exclude future dates,
            // and a holiday in the middle of a larger range is not re-fetched in
            // isolation — only the boundary gaps are.
            log.debug("No bars returned from adapter for ticker={} range={} - {} (holiday range?)", ticker, from, to);
            return;
        }

        Set<LocalDate> existingDates;
        try {
            existingDates = new HashSet<>(
                    repository.findBarDatesByTickerAndBarDateBetween(ticker, from, to)
            );
        } catch (Exception e) {
            log.error("Failed to load existing dates for ticker={}", ticker, e);
            return;
        }

        List<PriceBar> toSave = bars.stream()
                .filter(dto -> dto.barDate() != null)
                .filter(dto -> !dto.barDate().isBefore(from) && !dto.barDate().isAfter(to))
                .filter(dto -> !existingDates.contains(dto.barDate()))
                .map(PriceBar::from)
                .toList();

        if (toSave.isEmpty()) {
            log.debug("All fetched bars already exist in DB for ticker={} range={} - {}", ticker, from, to);
            return;
        }

        try {
            repository.saveAllAndFlush(toSave);
            log.info("Persisted {} bars for ticker={} range={} - {}", toSave.size(), ticker, from, to);
        } catch (Exception e) {
            log.error("Failed to persist bars for ticker={} range={} - {}", ticker, from, to, e);
        }
    }
}