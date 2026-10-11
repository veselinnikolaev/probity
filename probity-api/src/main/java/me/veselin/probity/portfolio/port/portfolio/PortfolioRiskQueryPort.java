package me.veselin.probity.portfolio.port.portfolio;

import me.veselin.probity.portfolio.dto.CorrelationMatrixDto;
import me.veselin.probity.portfolio.dto.RiskMetricsDto;
import me.veselin.probity.portfolio.dto.VaRReportDto;
import me.veselin.probity.portfolio.dto.VolatilityPointDto;

import java.util.List;
import java.util.UUID;

/**
 * Inbound query port for portfolio risk analytics use cases.
 */
public interface PortfolioRiskQueryPort {

    List<VolatilityPointDto> getVolatility(UUID id, String range, UUID userId);

    RiskMetricsDto getRiskMetrics(UUID id, String range, UUID userId);

    CorrelationMatrixDto getCorrelationMatrix(UUID id, String range, UUID userId);

    VaRReportDto getVaRReport(UUID id, double confidenceLevel, int timeHorizonDays, UUID userId);
}
