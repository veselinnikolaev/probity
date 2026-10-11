package me.veselin.probity.assistant.tools;

import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.port.portfolio.PortfolioDashboardQueryPort;
import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.portfolio.port.portfolio.PortfolioRiskQueryPort;
import me.veselin.probity.simulation.mapper.SimulationMapper;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioToolsTest {

    @Mock
    private PortfolioQueryPort portfolioQueryPort;

    @Mock
    private PortfolioDashboardQueryPort dashboardQueryPort;

    @Mock
    private PortfolioRiskQueryPort riskQueryPort;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private SimulationMapper simulationMapper;

    @Mock
    private MarketDataPort marketDataPort;

    private PortfolioTools portfolioTools;

    @BeforeEach
    void setUp() {
        portfolioTools = new PortfolioTools(
                portfolioQueryPort,
                dashboardQueryPort,
                riskQueryPort,
                simulationRepository,
                simulationMapper,
                marketDataPort
        );
    }

    @Test
    void getPortfolios_withValidUserId_returnsFormattedResponse() {
        UUID userId = UUID.randomUUID();
        when(portfolioQueryPort.getPortfolios(userId)).thenReturn(List.of());

        String result = portfolioTools.getPortfolios(userId.toString());

        assertThat(result).contains("No portfolios found.");
    }

    @Test
    void getPortfolios_withInvalidUserId_throwsAssistantToolException() {
        String result = portfolioTools.getPortfolios("invalid-uuid");

        assertThat(result).contains("Invalid user ID format.");
    }

    @Test
    void getPortfolios_withNullUserId_throwsAssistantToolException() {
        String result = portfolioTools.getPortfolios(null);

        assertThat(result).contains("User ID is required.");
    }

    @Test
    void getPortfolioDetail_withValidIds_returnsFormattedResponse() {
        UUID userId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        when(dashboardQueryPort.getSummary(eq(portfolioId), any(), eq(userId))).thenReturn(null);
        when(dashboardQueryPort.getPositions(eq(portfolioId), eq(userId))).thenReturn(List.of());

        String result = portfolioTools.getPortfolioDetail(userId.toString(), portfolioId.toString(), "1M");

        assertThat(result).contains("No portfolio summary data found");
    }

    @Test
    void getPortfolioDetail_withInvalidUserId_throwsAssistantToolException() {
        String result = portfolioTools.getPortfolioDetail("invalid", UUID.randomUUID().toString(), "1M");

        assertThat(result).contains("Invalid user ID format.");
    }

    @Test
    void getPortfolioDetail_withInvalidPortfolioId_throwsAssistantToolException() {
        String result = portfolioTools.getPortfolioDetail(UUID.randomUUID().toString(), "invalid", "1M");

        assertThat(result).contains("Invalid portfolio ID format.");
    }

    @Test
    void getRiskMetrics_withValidIds_returnsFormattedResponse() {
        UUID userId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        when(riskQueryPort.getRiskMetrics(any(), any(), eq(userId))).thenReturn(null);

        String result = portfolioTools.getRiskMetrics(userId.toString(), portfolioId.toString(), "3M");

        assertThat(result).contains("No risk metrics data found");
    }

    @Test
    void getRiskMetrics_withInvalidUserId_throwsAssistantToolException() {
        String result = portfolioTools.getRiskMetrics("invalid", UUID.randomUUID().toString(), "3M");

        assertThat(result).contains("Invalid user ID format.");
    }

    @Test
    void getCorrelationMatrix_withValidIds_returnsFormattedResponse() {
        UUID userId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        when(riskQueryPort.getCorrelationMatrix(any(), any(), eq(userId))).thenReturn(null);

        String result = portfolioTools.getCorrelationMatrix(userId.toString(), portfolioId.toString(), "3M");

        assertThat(result).isNotNull();
    }

    @Test
    void getCorrelationMatrix_withInvalidUserId_throwsAssistantToolException() {
        String result = portfolioTools.getCorrelationMatrix("invalid", UUID.randomUUID().toString(), "3M");

        assertThat(result).contains("Invalid user ID format.");
    }

    @Test
    void getLatestSimulation_withValidIds_returnsFormattedResponse() {
        UUID userId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        when(simulationRepository.findByPortfolioIdAndUserId(any(), eq(userId))).thenReturn(List.of());

        String result = portfolioTools.getLatestSimulation(userId.toString(), portfolioId.toString());

        assertThat(result).contains("No simulation results found");
    }

    @Test
    void getLatestSimulation_withInvalidUserId_throwsAssistantToolException() {
        String result = portfolioTools.getLatestSimulation("invalid", UUID.randomUUID().toString());

        assertThat(result).contains("Invalid user ID format.");
    }

    @Test
    void getMarketData_withValidTicker_returnsFormattedResponse() {
        when(marketDataPort.getHistoricalBars(any(), any(), any())).thenReturn(List.of());

        String result = portfolioTools.getMarketData("AAPL");

        assertThat(result).contains("No market data found");
    }

    @Test
    void getMarketData_withNullTicker_throwsAssistantToolException() {
        String result = portfolioTools.getMarketData(null);

        assertThat(result).contains("Ticker symbol is required.");
    }

    @Test
    void getMarketData_withBlankTicker_throwsAssistantToolException() {
        String result = portfolioTools.getMarketData("");

        assertThat(result).contains("Ticker symbol is required.");
    }
}
