package me.veselin.probity.assistant.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.assistant.exception.AssistantToolException;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.port.portfolio.PortfolioDashboardQueryPort;
import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.portfolio.port.portfolio.PortfolioRiskQueryPort;
import me.veselin.probity.simulation.mapper.SimulationMapper;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioTools {

    private final PortfolioQueryPort portfolioQueryPort;
    private final PortfolioDashboardQueryPort dashboardQueryPort;
    private final PortfolioRiskQueryPort riskQueryPort;
    private final SimulationRepository simulationRepository;
    private final SimulationMapper simulationMapper;
    private final MarketDataPort marketDataPort;

    @Tool(description = """
            Get all portfolios for the user including names, total values,
            position counts, volatility, and Sharpe ratio.
            Call this when the user asks about their portfolios, holdings,
            total value, or portfolio composition.
            """)
    public String getPortfolios(String userId) {
        log.debug("Tool:getPortfolios userId={}", userId);
        try {
            UUID userUuid = parseUserId(userId);
            var portfolios = portfolioQueryPort.getPortfolios(userUuid);
            return ToolResponseFormatter.formatPortfolios(portfolios);
        } catch (AssistantToolException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.warn("Tool:getPortfolios failed", e);
            throw new AssistantToolException("Unable to retrieve portfolios. Please check your portfolio data.", e);
        }
    }

    @Tool(description = """
            Get detailed information about a specific portfolio including
            all individual positions, quantities, current prices, and a
            performance summary over a time range.
            Call this when the user asks about positions in a specific portfolio,
            wants to drill into a portfolio, or asks what stocks/assets they hold.
            Requires portfolioId (UUID string).
            Range options: 1W, 1M, 3M, 6M, 1Y. Default to 1M if not specified.
            """)
    public String getPortfolioDetail(String userId, String portfolioId, String range) {
        log.debug("Tool:getPortfolioDetail userId={} portfolioId={} range={}", userId, portfolioId, range);
        try {
            UUID userUuid = parseUserId(userId);
            UUID portfolioUuid = parsePortfolioId(portfolioId);
            String r = (range == null || range.isBlank()) ? "1M" : range;
            var summary = dashboardQueryPort.getSummary(portfolioUuid, r, userUuid);
            var positions = dashboardQueryPort.getPositions(portfolioUuid, userUuid);
            return ToolResponseFormatter.formatPortfolioDetail(summary, positions);
        } catch (AssistantToolException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.warn("Tool:getPortfolioDetail failed", e);
            throw new AssistantToolException("Unable to retrieve portfolio details. Please verify the portfolio ID.", e);
        }
    }

    @Tool(description = """
            Get comprehensive risk metrics for a specific portfolio including
            Value at Risk (VaR), Sharpe ratio, annualised volatility,
            max drawdown, and risk score.
            Call this when the user asks about risk, VaR, Sharpe ratio,
            volatility, drawdown, how risky their portfolio is,
            or whether their portfolio is performing well on a risk-adjusted basis.
            Requires portfolioId (UUID string).
            Range options: 1W, 1M, 3M, 6M, 1Y. Default to 3M if not specified.
            """)
    public String getRiskMetrics(String userId, String portfolioId, String range) {
        log.debug("Tool:getRiskMetrics userId={} portfolioId={} range={}", userId, portfolioId, range);
        try {
            UUID userUuid = parseUserId(userId);
            UUID portfolioUuid = parsePortfolioId(portfolioId);
            String r = (range == null || range.isBlank()) ? "3M" : range;
            var metrics = riskQueryPort.getRiskMetrics(portfolioUuid, r, userUuid);
            if (metrics == null) return "No risk metrics data found for this portfolio.";
            return ToolResponseFormatter.formatRiskMetrics(metrics);
        } catch (AssistantToolException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.warn("Tool:getRiskMetrics failed", e);
            throw new AssistantToolException("Unable to retrieve risk metrics. Please verify the portfolio ID.", e);
        }
    }

    @Tool(description = """
            Get the asset correlation matrix for a specific portfolio.
            Shows Pearson correlation coefficients between all asset pairs,
            revealing diversification quality.
            Call this when the user asks about correlation, diversification,
            whether their assets move together, or concentration risk.
            Requires portfolioId (UUID string).
            Range options: 1W, 1M, 3M, 6M, 1Y. Default to 3M if not specified.
            """)
    public String getCorrelationMatrix(String userId, String portfolioId, String range) {
        log.debug("Tool:getCorrelationMatrix userId={} portfolioId={} range={}", userId, portfolioId, range);
        try {
            UUID userUuid = parseUserId(userId);
            UUID portfolioUuid = parsePortfolioId(portfolioId);
            String r = (range == null || range.isBlank()) ? "3M" : range;
            var matrix = riskQueryPort.getCorrelationMatrix(portfolioUuid, r, userUuid);
            if (matrix == null) return "No correlation matrix data found for this portfolio.";
            return ToolResponseFormatter.formatCorrelationMatrix(matrix);
        } catch (AssistantToolException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.warn("Tool:getCorrelationMatrix failed", e);
            throw new AssistantToolException("Unable to retrieve correlation matrix. Please verify the portfolio ID.", e);
        }
    }

    @Tool(description = """
            Get the latest Monte Carlo simulation result for a specific portfolio.
            Returns projected values, VaR, CVaR, confidence intervals, and
            worst-case loss probabilities.
            Call this when the user asks about simulations, projections,
            future value, worst-case scenarios, or VaR from simulation.
            Requires portfolioId (UUID string).
            """)
    public String getLatestSimulation(String userId, String portfolioId) {
        log.debug("Tool:getLatestSimulation userId={} portfolioId={}", userId, portfolioId);
        try {
            UUID userUuid = parseUserId(userId);
            UUID portfolioUuid = parsePortfolioId(portfolioId);
            var simulations = simulationRepository.findByPortfolioIdAndUserId(portfolioUuid, userUuid);
            if (simulations.isEmpty()) return "No simulation results found for this portfolio.";
            var latest = simulations.getFirst();
            return ToolResponseFormatter.formatSimulation(simulationMapper.toDto(latest));
        } catch (AssistantToolException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.warn("Tool:getLatestSimulation failed", e);
            throw new AssistantToolException("Unable to retrieve simulation results. Please verify the portfolio ID.", e);
        }
    }

    @Tool(description = """
            Get recent historical price bars for a specific stock ticker symbol.
            Returns OHLCV price data for the last 30 days.
            Call this when the user asks about a specific stock price,
            ticker performance, or market data for an asset they mention by ticker.
            Requires a valid ticker symbol e.g. AAPL, MSFT, TSLA.
            """)
    public String getMarketData(String ticker) {
        log.debug("Tool:getMarketData ticker={}", ticker);
        try {
            if (ticker == null || ticker.isBlank()) {
                throw new AssistantToolException("Ticker symbol is required.");
            }
            var bars = marketDataPort.getHistoricalBars(
                    ticker.toUpperCase(),
                    LocalDate.now().minusDays(30),
                    LocalDate.now());
            if (bars == null || bars.isEmpty()) return "No market data found for ticker: " + ticker;
            var recent = bars.size() > 5 ? bars.subList(bars.size() - 5, bars.size()) : bars;
            return "Recent price bars for " + ticker + ": " + recent;
        } catch (AssistantToolException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.warn("Tool:getMarketData failed ticker={}", ticker, e);
            throw new AssistantToolException("Unable to retrieve market data for " + ticker + ". The ticker may be invalid.", e);
        }
    }

    private UUID parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AssistantToolException("User ID is required.");
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new AssistantToolException("Invalid user ID format.");
        }
    }

    private UUID parsePortfolioId(String portfolioId) {
        if (portfolioId == null || portfolioId.isBlank()) {
            throw new AssistantToolException("Portfolio ID is required.");
        }
        try {
            return UUID.fromString(portfolioId);
        } catch (IllegalArgumentException e) {
            throw new AssistantToolException("Invalid portfolio ID format.");
        }
    }
}