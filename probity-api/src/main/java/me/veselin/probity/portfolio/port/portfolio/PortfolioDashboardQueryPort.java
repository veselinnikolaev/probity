package me.veselin.probity.portfolio.port.portfolio;

import me.veselin.probity.portfolio.dto.CompositionEntryDto;
import me.veselin.probity.portfolio.dto.PortfolioSummaryDto;
import me.veselin.probity.portfolio.dto.PositionDto;
import me.veselin.probity.portfolio.dto.RiskAlertDto;

import java.util.List;
import java.util.UUID;

/**
 * Inbound query port for portfolio dashboard read use cases.
 */
public interface PortfolioDashboardQueryPort {

    PortfolioSummaryDto getSummary(UUID id, String range, UUID userId);

    List<PositionDto> getPositions(UUID id, UUID userId);

    List<CompositionEntryDto> getComposition(UUID id, UUID userId);

    List<RiskAlertDto> getAlerts(UUID id, UUID userId);
}
