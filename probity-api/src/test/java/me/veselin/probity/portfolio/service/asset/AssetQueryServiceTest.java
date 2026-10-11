package me.veselin.probity.portfolio.service.asset;

import me.veselin.probity.marketdata.finance.FinanceAdapter;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.domain.Asset;
import me.veselin.probity.portfolio.dto.AssetMetadataDto;
import me.veselin.probity.portfolio.dto.AssetSearchResultDto;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.portfolio.enumeration.Sector;
import me.veselin.probity.portfolio.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetQueryServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock MarketDataPort marketDataPort;
    @Mock FinanceAdapter financeAdapter;

    @InjectMocks AssetQueryService service;

    // ── search() ─────────────────────────────────────────────────────────────

    @Test
    void search_nullQuery_returnsEmpty() {
        assertThat(service.search(null)).isEmpty();
        verifyNoInteractions(assetRepository, marketDataPort, financeAdapter);
    }

    @Test
    void search_blankQuery_returnsEmpty() {
        assertThat(service.search("   ")).isEmpty();
        verifyNoInteractions(assetRepository, marketDataPort, financeAdapter);
    }

    @Test
    void search_dbHit_returnsMappedResults() {
        Asset apple = Asset.create("AAPL", "Apple Inc.", Sector.TECHNOLOGY, AssetType.STOCK);
        when(assetRepository.searchByQuery(eq("AAPL"), any(PageRequest.class)))
                .thenReturn(List.of(apple));
        when(marketDataPort.getLatestPrice("AAPL")).thenReturn(new BigDecimal("150.00"));

        List<AssetSearchResultDto> results = service.search("aapl");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().ticker()).isEqualTo("AAPL");
        assertThat(results.getFirst().name()).isEqualTo("Apple Inc.");
        assertThat(results.getFirst().currentPrice()).isEqualByComparingTo("150.00");
        verifyNoInteractions(financeAdapter);
    }

    @Test
    void search_dbMissShortTicker_probesYahoo() {
        when(assetRepository.searchByQuery(eq("TSLA"), any(PageRequest.class)))
                .thenReturn(List.of());
        when(financeAdapter.fetchMetadata("TSLA")).thenReturn(Optional.of(
                new AssetMetadataDto("TSLA", "Tesla Inc.", "EQUITY", "Consumer Cyclical")
        ));
        when(marketDataPort.getLatestPrice("TSLA")).thenReturn(new BigDecimal("200.00"));

        List<AssetSearchResultDto> results = service.search("tsla");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().ticker()).isEqualTo("TSLA");
        assertThat(results.getFirst().currentPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void search_dbMissLongQuery_skipsYahoo() {
        when(assetRepository.searchByQuery(eq("TOOLONG"), any(PageRequest.class)))
                .thenReturn(List.of());

        List<AssetSearchResultDto> results = service.search("toolong");

        assertThat(results).isEmpty();
        verifyNoInteractions(financeAdapter);
    }

    @Test
    void search_dbMissYahooMiss_returnsEmpty() {
        when(assetRepository.searchByQuery(eq("ZZZZ"), any(PageRequest.class)))
                .thenReturn(List.of());
        when(financeAdapter.fetchMetadata("ZZZZ")).thenReturn(Optional.empty());

        List<AssetSearchResultDto> results = service.search("zzzz");

        assertThat(results).isEmpty();
    }

    @Test
    void search_deduplicatesByTicker() {
        // If DB somehow returns duplicate tickers, only one should appear
        Asset apple1 = Asset.create("AAPL", "Apple Inc.", Sector.TECHNOLOGY, AssetType.STOCK);
        Asset apple2 = Asset.create("AAPL", "Apple Inc.", Sector.TECHNOLOGY, AssetType.STOCK);
        when(assetRepository.searchByQuery(eq("AAPL"), any(PageRequest.class)))
                .thenReturn(List.of(apple1, apple2));
        when(marketDataPort.getLatestPrice("AAPL")).thenReturn(new BigDecimal("150.00"));

        List<AssetSearchResultDto> results = service.search("AAPL");

        assertThat(results).hasSize(1);
    }

    @Test
    void search_priceZero_returnsNullPrice() {
        Asset apple = Asset.create("AAPL", "Apple Inc.", Sector.TECHNOLOGY, AssetType.STOCK);
        when(assetRepository.searchByQuery(eq("AAPL"), any(PageRequest.class)))
                .thenReturn(List.of(apple));
        when(marketDataPort.getLatestPrice("AAPL")).thenReturn(BigDecimal.ZERO);

        List<AssetSearchResultDto> results = service.search("AAPL");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().currentPrice()).isNull(); // zero price treated as unavailable
    }

    @Test
    void search_priceFetchThrows_returnsNullPrice() {
        Asset apple = Asset.create("AAPL", "Apple Inc.", Sector.TECHNOLOGY, AssetType.STOCK);
        when(assetRepository.searchByQuery(eq("AAPL"), any(PageRequest.class)))
                .thenReturn(List.of(apple));
        when(marketDataPort.getLatestPrice("AAPL")).thenThrow(new RuntimeException("timeout"));

        List<AssetSearchResultDto> results = service.search("AAPL");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().currentPrice()).isNull();
    }

    @Test
    void search_queryCaseNormalized() {
        when(assetRepository.searchByQuery(eq("AAPL"), any(PageRequest.class)))
                .thenReturn(List.of());

        service.search("  AaPl  ");

        verify(assetRepository).searchByQuery(eq("AAPL"), any(PageRequest.class));
    }
}
