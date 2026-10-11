package me.veselin.probity.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.bff.dto.simulation.RunSimulationRequest;
import me.veselin.probity.bff.dto.simulation.SimulationResponse;
import me.veselin.probity.bff.security.filter.jwt.UserPrincipal;
import me.veselin.probity.bff.util.ConditionalGetSupport;
import me.veselin.probity.common.domain.event.DomainEventPublisher;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.simulation.dto.SimulationData;
import me.veselin.probity.simulation.dto.SimulationStatusResponse;
import me.veselin.probity.simulation.port.SimulationPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Tag(name = "Simulations", description = "Monte Carlo portfolio simulations")
@RestController
@RequiredArgsConstructor
@Slf4j
public class SimulationController {

    private final SimulationPort simulationPort;
    private final MarketDataPort marketDataPort;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * POST /simulations/run
     * Runs a new Monte Carlo simulation and persists the result.
     *
     * @param request simulation parameters
     * @param principal authenticated user principal
     * @param idempotencyKey unique key for idempotent requests
     * @param ucb URI components builder for Location header
     * @return 201 CREATED with simulation result
     */
    @Operation(summary = "Run Monte Carlo simulation")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Simulation created",
            headers = @Header(name = "X-Cache", description = "Present with value 'Idempotent-Hit' if response was served from cache")),
        @ApiResponse(responseCode = "400", description = "Invalid request or empty portfolio"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "409", description = "Identical request currently processing")
    })
    @PostMapping(ApiRoutes.Simulations.RUN)
    public ResponseEntity<SimulationResponse> run(
            @Valid @RequestBody RunSimulationRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Unique key for idempotent requests. Prevents duplicate simulation runs.", required = true)
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            UriComponentsBuilder ucb) {

        log.info("Running simulation for portfolio: {}, user: {}, simulations: {}, horizon: {} days, confidence: {}",
                request.portfolioId(), principal.id(), request.numberOfSimulations(), request.timeHorizonDays(), request.confidenceLevel());
        SimulationData data = simulationPort.runSimulation(
                request.portfolioId(),
                request.numberOfSimulations(),
                request.timeHorizonDays(),
                request.confidenceLevel(),
                request.assumedReturnPercent(),
                request.assumedVolatilityPercent(),
                principal.id()
        );

        URI location = ucb.path(ApiRoutes.Simulations.SIMULATION)
                .buildAndExpand(data.id())
                .toUri();

        log.info("Simulation completed successfully: {}, portfolio: {}", data.id(), request.portfolioId());
        return ResponseEntity.created(location).body(mapToResponse(data));
    }

    /**
     * POST /simulations/run-async
     * Initiates an asynchronous Monte Carlo simulation.
     * Returns 202 Accepted with Location header for status polling.
     *
     * @param request simulation parameters
     * @param principal authenticated user principal
     * @param idempotencyKey unique key for idempotent requests
     * @param ucb URI components builder for Location header
     * @return 202 ACCEPTED with status location
     */
    @Operation(summary = "Run Monte Carlo simulation asynchronously")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Simulation accepted for processing",
            headers = @Header(name = "Location", description = "URL to poll for simulation status")),
        @ApiResponse(responseCode = "400", description = "Invalid request or empty portfolio"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "409", description = "Identical request currently processing")
    })
    @PostMapping(ApiRoutes.Simulations.RUN_ASYNC)
    public ResponseEntity<SimulationStatusResponse> runAsync(
            @Valid @RequestBody RunSimulationRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Unique key for idempotent requests. Prevents duplicate simulation runs.", required = true)
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            UriComponentsBuilder ucb) {

        log.info("Initiating async simulation for portfolio: {}, user: {}, simulations: {}, horizon: {} days, confidence: {}",
                request.portfolioId(), principal.id(), request.numberOfSimulations(), request.timeHorizonDays(), request.confidenceLevel());

        // Capture market data snapshot at request time for stale-price safety
        Map<String, BigDecimal> marketDataSnapshot = captureMarketDataSnapshot(request.portfolioId(), principal.id());

        SimulationStatusResponse statusResponse = simulationPort.runSimulationAsync(
                request.portfolioId(),
                request.numberOfSimulations(),
                request.timeHorizonDays(),
                request.confidenceLevel(),
                request.assumedReturnPercent(),
                request.assumedVolatilityPercent(),
                principal.id(),
                idempotencyKey
        );

        // Publish Kafka event with market data snapshot
        // The event is published by the application service after creating the PENDING record
        // We need to publish the event with the market data snapshot
        publishSimulationRequestedEvent(statusResponse.getId(), request, principal.id(), marketDataSnapshot, idempotencyKey);

        URI statusLocation = ucb.path(ApiRoutes.Simulations.STATUS)
                .buildAndExpand(statusResponse.getId())
                .toUri();

        log.info("Async simulation accepted: {}, portfolio: {}", statusResponse.getId(), request.portfolioId());
        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, statusLocation.toString())
                .body(statusResponse);
    }

    /**
     * GET /simulations/{id}/status
     * Gets the current status of a simulation.
     * Returns status and result if COMPLETED.
     */
    @Operation(summary = "Get simulation status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status returned"),
        @ApiResponse(responseCode = "304", description = "Not modified"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Simulation not found")
    })
    @GetMapping(ApiRoutes.Simulations.STATUS)
    public ResponseEntity<SimulationStatusResponse> getStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
            String ifNoneMatch) {

        log.debug("Fetching simulation status: {} for user: {}", id, principal.id());
        SimulationStatusResponse statusResponse = simulationPort.getSimulationStatus(id, principal.id());
        String etag = buildStatusEtag(id, statusResponse);
        if (etag.equals(ifNoneMatch)) {
            log.debug("Simulation status not modified: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .body(statusResponse);
    }

    /**
     * GET /simulations/{id}
     * Fetches a previously run simulation result by its ID.
     */
    @Operation(summary = "Get simulation by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Simulation returned"),
        @ApiResponse(responseCode = "304", description = "Not modified"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Simulation not found")
    })
    @GetMapping(ApiRoutes.Simulations.SIMULATION)
    public ResponseEntity<SimulationResponse> getSimulation(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
            String ifNoneMatch) {

        log.debug("Fetching simulation: {} for user: {}", id, principal.id());
        String etag = "\"" + id + "\"";

        if (etag.equals(ifNoneMatch)) {
            log.debug("Simulation not modified: {}", id);
            return ConditionalGetSupport.notModifiedETag(etag);
        }

        SimulationData data = simulationPort.getSimulation(id, principal.id());
        return ConditionalGetSupport.okImmutable(mapToResponse(data), etag);
    }

    /**
     * GET /simulations?portfolioId={portfolioId}
     * Lists all simulations for a portfolio, most recent first.
     */
    @Operation(summary = "List simulations for portfolio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Simulations returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping(ApiRoutes.Simulations.SIMULATIONS)
    public ResponseEntity<List<SimulationResponse>> listSimulations(
            @RequestParam UUID portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.debug("Listing simulations for portfolio: {}, user: {}", portfolioId, principal.id());
        return ResponseEntity.ok(simulationPort.listSimulations(portfolioId, principal.id())
                .stream()
                .map(this::mapToResponse)
                .toList());
    }

    private String buildStatusEtag(UUID id, SimulationStatusResponse statusResponse) {
        if (statusResponse == null) {
            return "\"" + id + "\"";
        }
        String updatedAt = statusResponse.getUpdatedAt() == null
                ? "null"
                : statusResponse.getUpdatedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString();
        String status = statusResponse.getStatus() == null ? "null" : statusResponse.getStatus();
        String composite = id.toString() + "|" + status + "|" + updatedAt;
        return "\"" + java.util.Objects.hash(composite) + "\"";
    }

    /**
     * Captures current market prices for all positions in a portfolio.
     * Used to prevent stale-price execution in async simulations.
     */
    private Map<String, BigDecimal> captureMarketDataSnapshot(UUID portfolioId, UUID userId) {
        try {
            // Get portfolio with positions to know which tickers to fetch
            var portfolio = simulationPort.getSimulationPortfolio(portfolioId, userId);
            if (portfolio != null && portfolio.getPositions() != null) {
                return portfolio.getPositions().stream()
                        .collect(Collectors.toMap(
                                pos -> pos.getAsset().getTicker(),
                                pos -> marketDataPort.getLatestPrice(pos.getAsset().getTicker())
                        ));
            }
        } catch (Exception e) {
            log.warn("Failed to capture market data snapshot for portfolio {}: {}", portfolioId, e.getMessage());
        }
        return Map.of();
    }

    /**
     * Publishes the simulation requested event to Kafka with market data snapshot.
     * This is called after the PENDING record is created by the application service.
     */
    private void publishSimulationRequestedEvent(UUID simulationId, RunSimulationRequest request, UUID userId,
                                                 Map<String, BigDecimal> marketDataSnapshot, String idempotencyKey) {
        // Publish event with the simulationId (used as Kafka key) and market data snapshot
        // The simulationId is the same one used for the PENDING record
        SimulationRequestedEvent event = new SimulationRequestedEvent(
                simulationId,
                request.portfolioId(),
                userId,
                request.numberOfSimulations(),
                request.timeHorizonDays(),
                request.confidenceLevel(),
                request.assumedReturnPercent(),
                request.assumedVolatilityPercent(),
                marketDataSnapshot,
                Instant.now()
        );
        domainEventPublisher.publish(event);
        log.debug("Published SimulationRequestedEvent to Kafka: simulationId={}", simulationId);
    }

    private SimulationResponse mapToResponse(SimulationData data) {
        return new SimulationResponse(
                data.id(),
                data.portfolioId(),
                data.createdAt(),
                new SimulationResponse.Parameters(
                        data.parameters().numberOfSimulations(),
                        data.parameters().timeHorizonDays(),
                        data.parameters().confidenceLevel()
                ),
                data.currentPortfolioValue(),
                data.allPaths(),
                data.percentileSeries(),
                new SimulationResponse.Statistics(
                        data.statistics().expectedFinalValue(),
                        data.statistics().medianFinalValue(),
                        data.statistics().stdDeviation(),
                        data.statistics().minValue(),
                        data.statistics().maxValue()
                ),
                new SimulationResponse.Outcomes(
                        data.outcomes().probabilityOf10PercentLoss(),
                        data.outcomes().probabilityOf20PercentLoss(),
                        data.outcomes().valueAtRisk95(),
                        data.outcomes().conditionalValueAtRisk95()
                ),
                data.distribution()
        );
    }
}