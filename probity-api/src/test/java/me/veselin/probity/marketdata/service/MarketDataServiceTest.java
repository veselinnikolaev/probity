package me.veselin.probity.marketdata.service;

import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.marketdata.dto.PriceBarDto;
import me.veselin.probity.marketdata.finance.FinanceAdapter;
import me.veselin.probity.marketdata.repository.PriceBarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock PriceBarRepository repository;
    @Mock FinanceAdapter financeAdapter;

    @InjectMocks MarketDataSyncService syncService;

    private static final String TICKER = "AAPL";
    private static final LocalDate FROM  = LocalDate.of(2024, 1, 2);
    private static final LocalDate TO    = LocalDate.of(2024, 1, 5);

    // ── fetchAndPersist: adapter happy path ─────────────────────────────────

    @Test
    void fetchAndPersist_savesNewBars_whenAdapterReturnsData() {
        List<PriceBarDto> dtos = List.of(
                dto(TICKER, LocalDate.of(2024, 1, 2)),
                dto(TICKER, LocalDate.of(2024, 1, 3))
        );
        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO)).thenReturn(dtos);
        when(repository.findBarDatesByTickerAndBarDateBetween(TICKER, FROM, TO))
                .thenReturn(Collections.emptyList());

        syncService.fetchAndPersist(TICKER, FROM, TO);

        ArgumentCaptor<List<PriceBar>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void fetchAndPersist_doesNotSave_whenAllBarsAlreadyExist() {
        LocalDate date = LocalDate.of(2024, 1, 2);
        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO))
                .thenReturn(List.of(dto(TICKER, date)));
        when(repository.findBarDatesByTickerAndBarDateBetween(TICKER, FROM, TO))
                .thenReturn(List.of(date)); // already in DB

        syncService.fetchAndPersist(TICKER, FROM, TO);

        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void fetchAndPersist_savesOnlyNewBars_whenSomeAlreadyExist() {
        LocalDate existing = LocalDate.of(2024, 1, 2);
        LocalDate newDate  = LocalDate.of(2024, 1, 3);

        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO))
                .thenReturn(List.of(dto(TICKER, existing), dto(TICKER, newDate)));
        when(repository.findBarDatesByTickerAndBarDateBetween(TICKER, FROM, TO))
                .thenReturn(List.of(existing));

        syncService.fetchAndPersist(TICKER, FROM, TO);

        ArgumentCaptor<List<PriceBar>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getBarDate()).isEqualTo(newDate);
    }

    // ── fetchAndPersist: empty / null adapter responses ──────────────────────

    @Test
    void fetchAndPersist_doesNothing_whenAdapterReturnsEmptyList() {
        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO)).thenReturn(Collections.emptyList());

        syncService.fetchAndPersist(TICKER, FROM, TO);

        verify(repository, never()).findBarDatesByTickerAndBarDateBetween(any(), any(), any());
        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void fetchAndPersist_doesNothing_whenAdapterReturnsNull() {
        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO)).thenReturn(null);

        syncService.fetchAndPersist(TICKER, FROM, TO);

        verify(repository, never()).saveAllAndFlush(any());
    }

    // ── fetchAndPersist: adapter failure ─────────────────────────────────────

    @Test
    void fetchAndPersist_doesNotThrow_whenAdapterThrows() {
        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO))
                .thenThrow(new RuntimeException("Yahoo down"));

        // must not propagate — silent recovery
        syncService.fetchAndPersist(TICKER, FROM, TO);

        verify(repository, never()).saveAllAndFlush(any());
    }

    // ── fetchAndPersist: repository failure ─────────────────────────────────

    @Test
    void fetchAndPersist_doesNotThrow_whenFindExistingDatesThrows() {
        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO))
                .thenReturn(List.of(dto(TICKER, LocalDate.of(2024, 1, 2))));
        when(repository.findBarDatesByTickerAndBarDateBetween(any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        syncService.fetchAndPersist(TICKER, FROM, TO);

        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void fetchAndPersist_doesNotThrow_whenSaveAllAndFlushThrows() {
        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO))
                .thenReturn(List.of(dto(TICKER, LocalDate.of(2024, 1, 2))));
        when(repository.findBarDatesByTickerAndBarDateBetween(TICKER, FROM, TO))
                .thenReturn(Collections.emptyList());
        when(repository.saveAllAndFlush(any())).thenThrow(new RuntimeException("flush failed"));

        syncService.fetchAndPersist(TICKER, FROM, TO);
        // no exception leaks out
    }

    // ── fetchAndPersist: date boundary filtering ─────────────────────────────

    @Test
    void fetchAndPersist_filtersOutBarsOutsideRequestedRange() {
        // Adapter returns a bar dated one day before FROM — should be dropped
        LocalDate outOfRange = FROM.minusDays(1);
        LocalDate inRange    = FROM;

        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO))
                .thenReturn(List.of(dto(TICKER, outOfRange), dto(TICKER, inRange)));
        when(repository.findBarDatesByTickerAndBarDateBetween(TICKER, FROM, TO))
                .thenReturn(Collections.emptyList());

        syncService.fetchAndPersist(TICKER, FROM, TO);

        ArgumentCaptor<List<PriceBar>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getBarDate()).isEqualTo(inRange);
    }

    @Test
    void fetchAndPersist_filtersOutBarsWithNullDate() {
        PriceBarDto nullDateDto = new PriceBarDto(
                TICKER, null,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                1_000L
        );
        PriceBarDto validDto = dto(TICKER, FROM);

        when(financeAdapter.fetchDailyBars(TICKER, FROM, TO))
                .thenReturn(List.of(nullDateDto, validDto));
        when(repository.findBarDatesByTickerAndBarDateBetween(TICKER, FROM, TO))
                .thenReturn(Collections.emptyList());

        syncService.fetchAndPersist(TICKER, FROM, TO);

        ArgumentCaptor<List<PriceBar>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    private PriceBarDto dto(String ticker, LocalDate date) {
        return new PriceBarDto(
                ticker, date,
                new BigDecimal("100.00"),
                new BigDecimal("105.00"),
                new BigDecimal("98.00"),
                new BigDecimal("102.00"),
                new BigDecimal("102.00"),
                500_000L
        );
    }
}