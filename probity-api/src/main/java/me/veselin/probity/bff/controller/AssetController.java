package me.veselin.probity.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.bff.security.rate_limit.RateLimit;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.portfolio.dto.AssetSearchResultDto;
import me.veselin.probity.portfolio.port.asset.AssetQueryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Assets", description = "Asset search and lookup")
@RestController
@RequiredArgsConstructor
public class AssetController {

    private final AssetQueryPort assetQueryPort;

    /**
     * GET /api/v1/assets/search?q=AAPL
     * Searches for assets by ticker or name.
     * Phase 1: local Asset table (instant)
     * Phase 2: Yahoo Finance probe if ticker not in DB (handles unknown tickers)
     *
     * @param q search query (ticker or name)
     * @return list of matching asset DTOs
     */
    @Operation(summary = "Search assets by ticker or name")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results returned"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @GetMapping(ApiRoutes.Assets.SEARCH)
    @RateLimit(requests = 30, seconds = 60)
    public ResponseEntity<List<AssetSearchResultDto>> search(
            @RequestParam(name = "q", defaultValue = "") String q) {

        if (q.isBlank()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(assetQueryPort.search(q));
    }
}


