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
import me.veselin.probity.bff.dto.portfolio.*;
import me.veselin.probity.bff.security.filter.jwt.UserPrincipal;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.portfolio.dto.AddPositionCommand;
import me.veselin.probity.portfolio.dto.PortfolioData;
import me.veselin.probity.portfolio.dto.PositionCreatedDto;
import me.veselin.probity.portfolio.dto.UpdatePositionCommand;
import me.veselin.probity.portfolio.port.portfolio.PortfolioCommandPort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Portfolios", description = "Portfolio management commands")
@RestController
@RequiredArgsConstructor
@Slf4j
public class PortfolioCommandController {

    private final PortfolioCommandPort portfolioCommandPort;

    /**
     * POST /portfolios
     * Creates a new portfolio for the authenticated user.
     *
     * @param request portfolio creation request with name
     * @param userPrincipal authenticated user principal
     * @param ucb URI components builder for Location header
     * @param idempotencyKey unique key for idempotent requests
     * @return 201 CREATED with portfolio DTO and Location header
     */
    @Operation(summary = "Create a new portfolio")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Portfolio created",
            headers = @Header(name = "X-Cache", description = "Present with value 'Idempotent-Hit' if response was served from cache")),
        @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key header"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "409", description = "Portfolio name already exists or identical request currently processing")
    })
    @PostMapping(ApiRoutes.Portfolios.PORTFOLIOS)
    public ResponseEntity<PortfolioResponse> create(
            @Valid @RequestBody PortfolioCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Unique key for idempotent requests. Prevents duplicate portfolio creation.", required = true)
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            UriComponentsBuilder ucb) {

        log.info("Creating portfolio for user: {}, name: {}", userPrincipal.id(), request.name());
        PortfolioData data = portfolioCommandPort.create(request.name(), userPrincipal.id());

        URI location = ucb.path(ApiRoutes.Portfolios.PORTFOLIO)
                .buildAndExpand(data.id())
                .toUri();

        log.info("Portfolio created successfully: {}, user: {}", data.id(), userPrincipal.id());
        return ResponseEntity.created(location).body(
                new PortfolioResponse(data.id(), data.name(), data.description())
        );
    }


    /**
     * PATCH /portfolios/{id}
     * Updates portfolio name and description.
     *
     * @param id portfolio UUID
     * @param request portfolio update request with name and description
     * @param principal authenticated user principal
     * @return 200 OK with updated portfolio DTO
     * @throws me.veselin.probity.portfolio.exception.PortfolioNotFoundException if portfolio not found
     * @throws me.veselin.probity.common.exception.ConflictException if portfolio name already exists
     */
    @Operation(summary = "Update portfolio name and description")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolio updated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "409", description = "Portfolio name already exists")
    })
    @PatchMapping(ApiRoutes.Portfolios.PORTFOLIO)
    public ResponseEntity<PortfolioResponse> updatePortfolio(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePortfolioRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("Updating portfolio: {}, user: {}", id, principal.id());
        PortfolioData data = portfolioCommandPort.update(
                id, principal.id(), request.name(), request.description()
        );
        log.info("Portfolio updated successfully: {}", id);
        return ResponseEntity.ok(new PortfolioResponse(data.id(), data.name(), data.description()));
    }

    /**
     * DELETE /portfolios/{id}
     * Soft-deletes a portfolio owned by the authenticated user.
     *
     * @param id portfolio UUID
     * @param principal authenticated user principal
     * @return 204 NO CONTENT
     * @throws me.veselin.probity.portfolio.exception.PortfolioNotFoundException if portfolio not found
     */
    @Operation(summary = "Delete a portfolio")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Portfolio deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @DeleteMapping(ApiRoutes.Portfolios.PORTFOLIO)
    public ResponseEntity<Void> deletePortfolio(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Deleting portfolio: {}, user: {}", id, principal.id());
        portfolioCommandPort.delete(id, principal.id());
        log.info("Portfolio deleted successfully: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /portfolios/{id}/positions
     * Adds a new position to the portfolio.
     *
     * @param id portfolio UUID
     * @param request position creation request with asset ID, quantity, and price
     * @param principal authenticated user principal
     * @param idempotencyKey unique key for idempotent requests
     * @param ucb URI components builder for Location header
     * @return 201 CREATED with position DTO and Location header
     * @throws me.veselin.probity.portfolio.exception.PortfolioNotFoundException if portfolio not found
     * @throws me.veselin.probity.portfolio.exception.AssetNotFoundException if asset not found
     */
    @Operation(summary = "Add a position to portfolio")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Position added",
            headers = @Header(name = "X-Cache", description = "Present with value 'Idempotent-Hit' if response was served from cache")),
        @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key header"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio or asset not found"),
        @ApiResponse(responseCode = "409", description = "Identical request currently processing")
    })
    @PostMapping(ApiRoutes.Portfolios.POSITIONS)
    public ResponseEntity<PositionCreatedDto> addPosition(
            @PathVariable UUID id,
            @Valid @RequestBody PositionCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Unique key for idempotent requests. Prevents duplicate position creation.", required = true)
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            UriComponentsBuilder ucb) {

        log.info("Adding position to portfolio: {}, ticker: {}, quantity: {}", id, request.ticker(), request.quantity());
        AddPositionCommand command = new AddPositionCommand(request.ticker(), request.quantity());
        PositionCreatedDto created = portfolioCommandPort.addPosition(id, command, principal.id());

        URI location = ucb.path(ApiRoutes.Portfolios.POSITION)
                .buildAndExpand(id, created.id())
                .toUri();

        log.info("Position added successfully: {} to portfolio: {}", created.id(), id);
        return ResponseEntity.created(location).body(created);
    }

    /**
     * PUT /portfolios/{id}/positions/{positionId}
     * Updates the quantity of an existing position.
     *
     * @param id portfolio UUID
     * @param positionId position UUID
     * @param request position update request with new quantity
     * @param principal authenticated user principal
     * @return 204 NO CONTENT
     * @throws me.veselin.probity.portfolio.exception.PortfolioNotFoundException if portfolio not found
     * @throws me.veselin.probity.portfolio.exception.PositionNotFoundException if position not found
     */
    @Operation(summary = "Update position quantity")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Position updated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio or position not found")
    })
    @PutMapping(ApiRoutes.Portfolios.POSITION)
    public ResponseEntity<Void> updatePosition(
            @PathVariable UUID id,
            @PathVariable UUID positionId,
            @Valid @RequestBody PositionUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("Updating position: {} in portfolio: {}, quantity: {}", positionId, id, request.quantity());
        UpdatePositionCommand command = new UpdatePositionCommand(request.quantity());
        portfolioCommandPort.updatePosition(id, positionId, command, principal.id());
        log.info("Position updated successfully: {}", positionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /portfolios/{id}/positions/{positionId}
     * Deletes a position from the portfolio.
     *
     * @param id portfolio UUID
     * @param positionId position UUID
     * @param principal authenticated user principal
     * @return 204 NO CONTENT
     * @throws me.veselin.probity.portfolio.exception.PortfolioNotFoundException if portfolio not found
     * @throws me.veselin.probity.portfolio.exception.PositionNotFoundException if position not found
     */
    @Operation(summary = "Delete a position")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Position deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Portfolio or position not found")
    })
    @DeleteMapping(ApiRoutes.Portfolios.POSITION)
    public ResponseEntity<Void> deletePosition(
            @PathVariable UUID id,
            @PathVariable UUID positionId,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("Deleting position: {} from portfolio: {}", positionId, id);
        portfolioCommandPort.deletePosition(id, positionId, principal.id());
        log.info("Position deleted successfully: {}", positionId);
        return ResponseEntity.noContent().build();
    }
}