package me.veselin.probity.assistant.tools;

import me.veselin.probity.portfolio.dto.CorrelationMatrixDto;
import me.veselin.probity.portfolio.dto.PortfolioDto;
import me.veselin.probity.portfolio.dto.PortfolioSummaryDto;
import me.veselin.probity.portfolio.dto.PositionDto;
import me.veselin.probity.portfolio.dto.RiskMetricsDto;
import me.veselin.probity.simulation.dto.SimulationData;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Formats tool responses for AI consumption with structured, human-readable output.
 */
public class ToolResponseFormatter {

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("$#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0%");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    public static String formatPortfolios(List<PortfolioDto> portfolios) {
        if (portfolios.isEmpty()) {
            return "No portfolios found.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Portfolios (").append(portfolios.size()).append("):\n");
        for (PortfolioDto p : portfolios) {
            sb.append("- ").append(p.name())
              .append(": ").append(CURRENCY_FORMAT.format(p.totalValue()))
              .append(", ").append(p.positionsCount()).append(" positions")
              .append(", Volatility: ").append(PERCENT_FORMAT.format(p.volatility()))
              .append(", Sharpe: ").append(DECIMAL_FORMAT.format(p.sharpeRatio()))
              .append("\n");
        }
        return sb.toString().trim();
    }

    public static String formatPortfolioDetail(PortfolioSummaryDto summary, List<PositionDto> positions) {
        if (summary == null) {
            return "No portfolio summary data found for this portfolio.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Portfolio Summary:\n");
        sb.append("Current Value: ").append(CURRENCY_FORMAT.format(summary.totalValue())).append("\n");
        sb.append("Total Return: ").append(PERCENT_FORMAT.format(summary.totalValueDelta() / 100)).append("\n");
        sb.append("Daily Return: ").append(CURRENCY_FORMAT.format(summary.dailyReturn())).append("\n");
        sb.append("Volatility: ").append(PERCENT_FORMAT.format(summary.volatility() / 100)).append("\n");
        sb.append("Sharpe Ratio: ").append(DECIMAL_FORMAT.format(summary.sharpeRatio())).append("\n");
        sb.append("VaR (95%): ").append(CURRENCY_FORMAT.format(summary.var95())).append("\n");
        sb.append("\nPositions (").append(positions.size()).append("):\n");
        for (PositionDto pos : positions) {
            sb.append("- ").append(pos.ticker())
                    .append(": ").append(CURRENCY_FORMAT.format(pos.positionValueRaw()))
                    .append(" (").append(PERCENT_FORMAT.format(pos.weight() / 100)).append(")")
                    .append(", Change: ").append(PERCENT_FORMAT.format(pos.changePercent() / 100))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    public static String formatRiskMetrics(RiskMetricsDto metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("Risk Metrics:\n");
        sb.append("Beta: ").append(DECIMAL_FORMAT.format(metrics.beta())).append("\n");
        sb.append("Max Drawdown: ").append(PERCENT_FORMAT.format(metrics.maxDrawdown() / 100)).append("\n");
        sb.append("Concentration Index: ").append(DECIMAL_FORMAT.format(metrics.concentrationIndex())).append("\n");
        return sb.toString().trim();
    }

    public static String formatCorrelationMatrix(CorrelationMatrixDto matrix) {
        StringBuilder sb = new StringBuilder();
        sb.append("Correlation Matrix:\n");
        List<String> assets = matrix.assets();
        List<List<Double>> data = matrix.matrix();
        
        sb.append("    ");
        for (String asset : assets) {
            sb.append(String.format("%-8s", asset));
        }
        sb.append("\n");
        
        for (int i = 0; i < assets.size(); i++) {
            sb.append(String.format("%-4s", assets.get(i)));
            for (int j = 0; j < assets.size(); j++) {
                sb.append(String.format("%-8.2f", data.get(i).get(j)));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public static String formatSimulation(SimulationData simulation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Monte Carlo Simulation:\n");
        sb.append("Paths: ").append(simulation.parameters().numberOfSimulations()).append("\n");
        sb.append("Horizon: ").append(simulation.parameters().timeHorizonDays()).append(" days\n");
        sb.append("Expected Final Value: ").append(CURRENCY_FORMAT.format(simulation.statistics().expectedFinalValue())).append("\n");
        sb.append("Median Final Value: ").append(CURRENCY_FORMAT.format(simulation.statistics().medianFinalValue())).append("\n");
        sb.append("Std Deviation: ").append(CURRENCY_FORMAT.format(simulation.statistics().stdDeviation())).append("\n");
        sb.append("VaR (95%): ").append(CURRENCY_FORMAT.format(simulation.outcomes().valueAtRisk95())).append("\n");
        sb.append("CVaR (95%): ").append(CURRENCY_FORMAT.format(simulation.outcomes().conditionalValueAtRisk95())).append("\n");
        sb.append("Probability of 10% Loss: ").append(PERCENT_FORMAT.format(simulation.outcomes().probabilityOf10PercentLoss())).append("\n");
        sb.append("Probability of 20% Loss: ").append(PERCENT_FORMAT.format(simulation.outcomes().probabilityOf20PercentLoss())).append("\n");
        return sb.toString().trim();
    }
}
