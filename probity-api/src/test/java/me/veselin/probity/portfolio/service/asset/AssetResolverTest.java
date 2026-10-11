package me.veselin.probity.portfolio.service.asset;

import me.veselin.probity.marketdata.finance.FinanceAdapter;
import me.veselin.probity.portfolio.domain.Asset;
import me.veselin.probity.portfolio.dto.AssetMetadataDto;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.portfolio.enumeration.Sector;
import me.veselin.probity.portfolio.exception.AssetNotFoundException;
import me.veselin.probity.portfolio.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetResolverTest {

    @Mock AssetRepository assetRepository;
    @Mock FinanceAdapter financeAdapter;

    @InjectMocks AssetResolver resolver;

    // ── resolve() ─────────────────────────────────────────────────────────────

    @Test
    void resolve_existsInDb_returnsWithoutYahooCall() {
        Asset existing = Asset.create("AAPL", "Apple Inc.", Sector.TECHNOLOGY, AssetType.STOCK);
        when(assetRepository.findByTicker("AAPL")).thenReturn(Optional.of(existing));

        Asset result = resolver.resolve("aapl");

        assertThat(result.getTicker()).isEqualTo("AAPL");
        verifyNoInteractions(financeAdapter);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void resolve_notInDb_fetchesFromYahooAndSaves() {
        when(assetRepository.findByTicker("TSLA")).thenReturn(Optional.empty());
        when(financeAdapter.fetchMetadata("TSLA")).thenReturn(Optional.of(
                new AssetMetadataDto("TSLA", "Tesla Inc.", "EQUITY", "Consumer Cyclical")
        ));
        Asset saved = Asset.create("TSLA", "Tesla Inc.", Sector.CONSUMER, AssetType.STOCK);
        when(assetRepository.save(any())).thenReturn(saved);

        Asset result = resolver.resolve("tsla");

        assertThat(result.getTicker()).isEqualTo("TSLA");
        verify(assetRepository).save(any(Asset.class));
    }

    @Test
    void resolve_tickerUppercased() {
        Asset existing = Asset.create("MSFT", "Microsoft", Sector.TECHNOLOGY, AssetType.STOCK);
        when(assetRepository.findByTicker("MSFT")).thenReturn(Optional.of(existing));

        resolver.resolve("msft");

        verify(assetRepository).findByTicker("MSFT");
    }

    @Test
    void resolve_yahooReturnsEmpty_throwsAssetNotFoundException() {
        when(assetRepository.findByTicker("FAKE")).thenReturn(Optional.empty());
        when(financeAdapter.fetchMetadata("FAKE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("fake"))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("FAKE");
    }

    @Test
    void resolve_etfQuoteType_mappedToEtfAssetType() {
        when(assetRepository.findByTicker("SPY")).thenReturn(Optional.empty());
        when(financeAdapter.fetchMetadata("SPY")).thenReturn(Optional.of(
                new AssetMetadataDto("SPY", "SPDR S&P 500", "ETF", null)
        ));
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        when(assetRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        resolver.resolve("SPY");

        assertThat(captor.getValue().getType()).isEqualTo(AssetType.ETF);
    }

    @Test
    void resolve_cryptoQuoteType_mappedToCryptoAssetType() {
        when(assetRepository.findByTicker("BTC-USD")).thenReturn(Optional.empty());
        when(financeAdapter.fetchMetadata("BTC-USD")).thenReturn(Optional.of(
                new AssetMetadataDto("BTC-USD", "Bitcoin", "CRYPTOCURRENCY", null)
        ));
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        when(assetRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        resolver.resolve("BTC-USD");

        assertThat(captor.getValue().getType()).isEqualTo(AssetType.CRYPTO);
    }

    @Test
    void resolve_nullSector_mappedToUnknown() {
        when(assetRepository.findByTicker("SPY")).thenReturn(Optional.empty());
        when(financeAdapter.fetchMetadata("SPY")).thenReturn(Optional.of(
                new AssetMetadataDto("SPY", "SPDR S&P 500", "ETF", null)
        ));
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        when(assetRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        resolver.resolve("SPY");

        assertThat(captor.getValue().getSector()).isEqualTo(Sector.UNKNOWN);
    }

    @Test
    void resolve_technologySector_mappedCorrectly() {
        when(assetRepository.findByTicker("AAPL")).thenReturn(Optional.empty());
        when(financeAdapter.fetchMetadata("AAPL")).thenReturn(Optional.of(
                new AssetMetadataDto("AAPL", "Apple Inc.", "EQUITY", "Technology")
        ));
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        when(assetRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        resolver.resolve("AAPL");

        assertThat(captor.getValue().getSector()).isEqualTo(Sector.TECHNOLOGY);
    }

    @Test
    void resolve_unknownSector_mappedToUnknown() {
        when(assetRepository.findByTicker("XYZ")).thenReturn(Optional.empty());
        when(financeAdapter.fetchMetadata("XYZ")).thenReturn(Optional.of(
                new AssetMetadataDto("XYZ", "Some Corp", "EQUITY", "Totally Made Up Sector")
        ));
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        when(assetRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        resolver.resolve("XYZ");

        assertThat(captor.getValue().getSector()).isEqualTo(Sector.UNKNOWN);
    }
}
