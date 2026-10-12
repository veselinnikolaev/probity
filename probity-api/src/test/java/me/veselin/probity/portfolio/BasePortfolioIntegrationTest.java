package me.veselin.probity.portfolio;

import me.veselin.probity.auth.BaseAuthIntegrationTest;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.domain.Asset;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.portfolio.enumeration.Sector;
import me.veselin.probity.portfolio.repository.AssetRepository;
import me.veselin.probity.portfolio.persistence.PortfolioRepository;
import me.veselin.probity.risk.dto.AssetRiskProfile;
import me.veselin.probity.risk.enumeration.RiskLevel;
import me.veselin.probity.risk.port.RiskPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Shared base for all portfolio integration tests.
 * <p>
 * Seeds two positions (AAPL + GOOGL, both Technology) so that:
 *  - Sector concentration alert fires at 100% → alert tests pass
 *  - Two distinct tickers exist → correlation matrix is 2×2
 *  - Weights are split between two positions → weight assertions are non-trivial
 */
public abstract class BasePortfolioIntegrationTest extends BaseAuthIntegrationTest {

    @Autowired protected PortfolioRepository portfolioRepository;
    @Autowired protected AssetRepository assetRepository;

    @MockitoBean protected MarketDataPort marketDataPort;
    @MockitoBean protected RiskPort riskPort;

    protected String portfolioId;
    protected String positionId;   // first position (AAPL)

    @BeforeEach
    void seedPortfolio() {
        Asset apple  = assetRepository.findByTicker("AAPL")
                .orElseGet(() -> assetRepository.save(
                        Asset.create("AAPL", "Apple Inc.", Sector.TECHNOLOGY, AssetType.STOCK)));
        Asset google = assetRepository.findByTicker("GOOGL")
                .orElseGet(() -> assetRepository.save(
                        Asset.create("GOOGL", "Alphabet Inc.", Sector.TECHNOLOGY, AssetType.STOCK)));

        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        Portfolio portfolio = Portfolio.create("Test Portfolio", userId);
        portfolio.addPosition(apple,  new BigDecimal("10"), new BigDecimal("148.00"));
        portfolio.addPosition(google, new BigDecimal("5"),  new BigDecimal("130.00"));
        portfolio = portfolioRepository.save(portfolio);

        portfolioId = portfolio.getId().toString();
        positionId  = portfolio.getPositions().getFirst().getId().toString();

        stubMarketData(apple, google);
        stubRiskPort();
    }

    @AfterEach
    void cleanDb() {
        // Delete portfolios before assets to maintain foreign key order
        portfolioRepository.deleteAll();
        assetRepository.deleteAll();
    }

    // ── Market-data stubs ────────────────────────────────────────────────────

    private void stubMarketData(Asset apple, Asset google) {
        List<PriceBar> appleBars = List.of(
                PriceBar.from(apple.getTicker(), LocalDate.now().minusDays(2),
                        new BigDecimal("147.00"), new BigDecimal("149.00"),
                        new BigDecimal("146.00"), new BigDecimal("148.00"),
                        new BigDecimal("148.00"), 1_200_000L),
                PriceBar.from(apple.getTicker(), LocalDate.now().minusDays(1),
                        new BigDecimal("148.00"), new BigDecimal("150.00"),
                        new BigDecimal("147.00"), new BigDecimal("149.00"),
                        new BigDecimal("149.00"), 1_300_000L),
                PriceBar.from(apple.getTicker(), LocalDate.now(),
                        new BigDecimal("149.00"), new BigDecimal("151.00"),
                        new BigDecimal("148.00"), new BigDecimal("150.00"),
                        new BigDecimal("150.00"), 1_500_000L)
        );

        List<PriceBar> googleBars = List.of(
                PriceBar.from(google.getTicker(), LocalDate.now().minusDays(2),
                        new BigDecimal("129.00"), new BigDecimal("131.00"),
                        new BigDecimal("128.00"), new BigDecimal("130.00"),
                        new BigDecimal("130.00"), 900_000L),
                PriceBar.from(google.getTicker(), LocalDate.now().minusDays(1),
                        new BigDecimal("130.00"), new BigDecimal("132.00"),
                        new BigDecimal("129.00"), new BigDecimal("131.00"),
                        new BigDecimal("131.00"), 950_000L),
                PriceBar.from(google.getTicker(), LocalDate.now(),
                        new BigDecimal("131.00"), new BigDecimal("133.00"),
                        new BigDecimal("130.00"), new BigDecimal("132.00"),
                        new BigDecimal("132.00"), 1_000_000L)
        );

        when(marketDataPort.getLatestPrice(anyString())).thenReturn(new BigDecimal("150.00"));
        when(marketDataPort.getLatestPrice(apple.getTicker())).thenReturn(new BigDecimal("150.00"));
        when(marketDataPort.getLatestPrice(google.getTicker())).thenReturn(new BigDecimal("132.00"));

        when(marketDataPort.getHistoricalBars(anyString(), any(), any())).thenReturn(appleBars);
        when(marketDataPort.getHistoricalBars(eq(apple.getTicker()),  any(), any())).thenReturn(appleBars);
        when(marketDataPort.getHistoricalBars(eq(google.getTicker()), any(), any())).thenReturn(googleBars);
    }

    // ── Risk-port stubs ──────────────────────────────────────────────────────

    protected void stubRiskPort() {
        when(riskPort.annualisedVolatility(any())).thenReturn(0.18);
        when(riskPort.sharpeRatio(any())).thenReturn(1.2);
        when(riskPort.var95(anyDouble(), any())).thenReturn(220.0);
        when(riskPort.rollingVolatility(any(), anyInt())).thenReturn(List.of(0.15, 0.16, 0.17));
        when(riskPort.rollingVolatilityFromReturns(any(), anyInt())).thenReturn(List.of(0.15, 0.16, 0.17));
        when(riskPort.toDailyReturns(any())).thenReturn(List.of(0.01, -0.005, 0.008));
        AssetRiskProfile profile = new AssetRiskProfile(AssetType.STOCK, AssetType.STOCK.getBaseRiskScore());
        when(riskPort.riskScore(any(AssetRiskProfile.class), anyDouble())).thenReturn(55);
        when(riskPort.riskLevel(anyInt())).thenReturn(RiskLevel.MODERATE.name());
        when(riskPort.maxDrawdown(any())).thenReturn(-0.05);
    }
}