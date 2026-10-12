package me.veselin.probity.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.bff.security.filter.jwt.UserPrincipal;
import me.veselin.probity.bff.util.ConditionalGetSupport;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.portfolio.dto.CompositionEntryDto;
import me.veselin.probity.portfolio.dto.CorrelationMatrixDto;
import me.veselin.probity.portfolio.dto.PortfolioDto;
import me.veselin.probity.portfolio.dto.PortfolioSummaryDto;
import me.veselin.probity.portfolio.dto.PositionDto;
import me.veselin.probity.portfolio.dto.RiskAlertDto;
import me.veselin.probity.portfolio.dto.RiskMetricsDto;
import me.veselin.probity.portfolio.dto.VaRReportDto;
import me.veselin.probity.portfolio.dto.VolatilityPointDto;
import me.veselin.probity.portfolio.port.portfolio.PortfolioDashboardQueryPort;
import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.portfolio.port.portfolio.PortfolioRiskQueryPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Portfolios", description = "Portfolio query endpoints")
@RestController
@RequiredArgsConstructor
@Slf4j
/**
 * Controller for portfolio query endpoints with conditional GET support.
 */
public class PortfolioQueryController {

    private final PortfolioQueryPort portfolioQueryPort;
    private final PortfolioDashboardQueryPort portfolioDashboardQueryPort;
    private final PortfolioRiskQueryPort portfolioRiskQueryPort;

    /**
     * GET /portfolios
     * Lists all portfolios for the authenticated user.
     *
     * @param principal authenticated user principal
     * @return list of portfolio DTOs
     */
    @Operation(summary = "List all portfolios")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolios returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping(ApiRoutes.Portfolios.PORTFOLIOS)
    public ResponseEntity<List<PortfolioDto>> getPortfolios(@AuthenticationPrincipal UserPrincipal principal) {
        log.debug("Fetching portfolios for user: {}", principal.id());
        return ResponseEntity.ok(portfolioQueryPort.getPortfolios(principal.id()));
    }

    /**
     * GET /portfolios/{id}
     * Fetches a specific portfolio by ID.
     *
     * @param id portfolio UUID
     * @param principal authenticated user principal
     * @return portfolio DTO
     * @throws me.veselin.probity.portfolio.exception.PortfolioNotFoundException if portfolio not found
     */
    @Operation(summary = "Get portfolio by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolio returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.PORTFOLIO)
    public ResponseEntity<PortfolioDto> getPortfolio(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        log.debug("Fetching portfolio: {} for user: {}", id, principal.id());
        return ResponseEntity.ok(portfolioQueryPort.getPortfolio(id, principal.id()));
    }

    /**
     * GET /portfolios/{id}/summary
     * Fetches portfolio summary with performance metrics over the specified range.
     *
     * @param id portfolio UUID
     * @param range time range (e.g., "7d", "30d", "90d", "1y")
     * @param principal authenticated user principal
     * @return portfolio summary DTO
     */
    @Operation(summary = "Get portfolio summary")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.SUMMARY)
    public ResponseEntity<PortfolioSummaryDto> getSummary(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "90d") String range,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.debug("Fetching summary for portfolio: {}, range: {}", id, range);
        return ResponseEntity.ok(portfolioDashboardQueryPort.getSummary(id, range, principal.id()));
    }

    /**
     * GET /portfolios/{id}/positions
     * Lists all positions in a portfolio.
     *
     * @param id portfolio UUID
     * @param principal authenticated user principal
     * @return list of position DTOs
     */
    @Operation(summary = "List portfolio positions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Positions returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.POSITIONS)
    public ResponseEntity<List<PositionDto>> getPositions(@PathVariable UUID id,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        log.debug("Fetching positions for portfolio: {}", id);
        return ResponseEntity.ok(portfolioDashboardQueryPort.getPositions(id, principal.id()));
    }

    /**
     * GET /portfolios/{id}/composition
     * Fetches portfolio composition breakdown by asset.
     *
     * @param id portfolio UUID
     * @param principal authenticated user principal
     * @return list of composition entry DTOs
     */
    @Operation(summary = "Get portfolio composition")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Composition returned"),
        @ApiResponse(responseCode = "304", description = "Not modified"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.COMPOSITION)
    public ResponseEntity<List<CompositionEntryDto>> getComposition(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false)
            String ifModifiedSinceStr) {

        log.debug("Fetching composition for portfolio: {}, ifModifiedSince: {}", id, ifModifiedSinceStr);
        Instant lastModified = portfolioQueryPort.getLastModified(id, principal.id());

        if (ConditionalGetSupport.isNotModified(lastModified, ifModifiedSinceStr)) {
            log.debug("Portfolio composition not modified: {}", id);
            return ConditionalGetSupport.notModified(lastModified);
        }

        return ConditionalGetSupport.ok(
                portfolioDashboardQueryPort.getComposition(id, principal.id()),
                lastModified
        );
    }

    /**
     * GET /portfolios/{id}/volatility
     * Fetches rolling volatility points for the portfolio over the specified range.
     *
     * @param id portfolio UUID
     * @param range time range (e.g., "7d", "30d", "90d", "1y")
     * @param principal authenticated user principal
     * @return list of volatility point DTOs
     */
    @Operation(summary = "Get portfolio volatility")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Volatility data returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.VOLATILITY)
    public ResponseEntity<List<VolatilityPointDto>> getVolatility(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "90d") String range,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.debug("Fetching volatility for portfolio: {}, range: {}", id, range);
        return ResponseEntity.ok(portfolioRiskQueryPort.getVolatility(id, range, principal.id()));
    }

    /**
     * GET /portfolios/{id}/alerts
     * Fetches risk alerts for the portfolio.
     *
     * @param id portfolio UUID
     * @param principal authenticated user principal
     * @return list of risk alert DTOs
     */
    @Operation(summary = "Get portfolio risk alerts")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alerts returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.ALERTS)
    public ResponseEntity<List<RiskAlertDto>> getAlerts(@PathVariable UUID id,
                                                         @AuthenticationPrincipal UserPrincipal principal) {

        log.debug("Fetching alerts for portfolio: {}", id);
        return ResponseEntity.ok(portfolioDashboardQueryPort.getAlerts(id, principal.id()));
    }

    /**
     * GET /portfolios/{id}/risk-metrics
     * Fetches risk metrics (volatility, Sharpe ratio, max drawdown, etc.) over the specified range.
     *
     * @param id portfolio UUID
     * @param range time range (e.g., "7d", "30d", "90d", "1y")
     * @param principal authenticated user principal
     * @return risk metrics DTO
     */
    @Operation(summary = "Get portfolio risk metrics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Risk metrics returned"),
        @ApiResponse(responseCode = "304", description = "Not modified"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.RISK_METRICS)
    public ResponseEntity<RiskMetricsDto> getRiskMetrics(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "90d") String range,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false)
            String ifModifiedSinceStr) {

        log.debug("Fetching risk metrics for portfolio: {}, range: {}", id, range);
        Instant lastModified = portfolioQueryPort.getLastModified(id, principal.id());

        if (ConditionalGetSupport.isNotModified(lastModified, ifModifiedSinceStr)) {
            log.debug("Risk metrics not modified: {}", id);
            return ConditionalGetSupport.notModified(lastModified);
        }

        return ConditionalGetSupport.ok(
                portfolioRiskQueryPort.getRiskMetrics(id, range, principal.id()),
                lastModified
        );
    }

    /**
     * GET /portfolios/{id}/correlation
     * Fetches the correlation matrix between portfolio assets over the specified range.
     *
     * @param id portfolio UUID
     * @param range time range (e.g., "7d", "30d", "90d", "1y")
     * @param principal authenticated user principal
     * @return correlation matrix DTO
     */
    @Operation(summary = "Get asset correlation matrix")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Correlation matrix returned"),
        @ApiResponse(responseCode = "304", description = "Not modified"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.CORRELATION)
    public ResponseEntity<CorrelationMatrixDto> getCorrelation(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "90d") String range,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false)
            String ifModifiedSinceStr) {

        log.debug("Fetching correlation matrix for portfolio: {}, range: {}", id, range);
        Instant lastModified = portfolioQueryPort.getLastModified(id, principal.id());

        if (ConditionalGetSupport.isNotModified(lastModified, ifModifiedSinceStr)) {
            log.debug("Correlation matrix not modified: {}", id);
            return ConditionalGetSupport.notModified(lastModified);
        }

        return ConditionalGetSupport.ok(
                portfolioRiskQueryPort.getCorrelationMatrix(id, range, principal.id()),
                lastModified
        );
    }

    /**
     * GET /portfolios/{id}/var
     * Fetches Value at Risk (VaR) report for the portfolio.
     *
     * @param id portfolio UUID
     * @param confidenceLevel confidence level for VaR (e.g., 0.95 for 95%)
     * @param timeHorizonDays time horizon in days
     * @param principal authenticated user principal
     * @return VaR report DTO
     */
    @Operation(summary = "Get Value at Risk report")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "VaR report returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping(ApiRoutes.Portfolios.VAR)
    public ResponseEntity<VaRReportDto> getVaRReport(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0.95") double confidenceLevel,
            @RequestParam(defaultValue = "1") int timeHorizonDays,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.debug("Fetching VaR report for portfolio: {}, confidence: {}, horizon: {} days", id, confidenceLevel, timeHorizonDays);
        return ResponseEntity.ok(
                portfolioRiskQueryPort.getVaRReport(id, confidenceLevel, timeHorizonDays, principal.id())
        );
    }
}