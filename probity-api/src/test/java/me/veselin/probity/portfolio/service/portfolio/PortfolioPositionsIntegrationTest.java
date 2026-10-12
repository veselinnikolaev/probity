package me.veselin.probity.portfolio.service.portfolio;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.portfolio.enumeration.DateRange;
import me.veselin.probity.portfolio.BasePortfolioIntegrationTest;
import me.veselin.probity.risk.enumeration.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class PortfolioPositionsIntegrationTest extends BasePortfolioIntegrationTest {

    // ── GET /portfolios/{id}/positions ───────────────────────────────────────

    @Test
    void getPositions_seededPortfolio_returnsBothPositions() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].ticker", containsInAnyOrder("AAPL", "GOOGL")))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Apple Inc.", "Alphabet Inc.")))
                .andExpect(jsonPath("$[*].assetType").value(everyItem(is(AssetType.STOCK.name()))))
                .andExpect(jsonPath("$[*].riskLevel").value(everyItem(is(RiskLevel.MODERATE.name()))));
    }

    @Test
    void getPositions_allRequiredFields_presentOnEachPosition() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].ticker").isString())
                .andExpect(jsonPath("$[0].name").isString())
                .andExpect(jsonPath("$[0].assetType").isString())
                .andExpect(jsonPath("$[0].price").isNumber())
                .andExpect(jsonPath("$[0].change").isNumber())
                .andExpect(jsonPath("$[0].changePercent").isNumber())
                .andExpect(jsonPath("$[0].riskScore").isNumber())
                .andExpect(jsonPath("$[0].riskLevel").isString())
                .andExpect(jsonPath("$[0].volume").isString())
                .andExpect(jsonPath("$[0].positionValue").isString())
                .andExpect(jsonPath("$[0].sector").isString())
                .andExpect(jsonPath("$[0].weight").isNumber())
                .andExpect(jsonPath("$[0].volatility").isNumber())
                .andExpect(jsonPath("$[0].volatilityContribution").isNumber())
                .andExpect(jsonPath("$[0].quantity").isNumber())
                .andExpect(jsonPath("$[0].avgBuyPrice").isNumber())
                .andExpect(jsonPath("$[0].currentPrice").isNumber());
    }

    @Test
    void getPositions_weights_eachBetweenZeroAndOneHundred() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weight", allOf(greaterThan(0.0), lessThan(100.0))))
                .andExpect(jsonPath("$[1].weight", allOf(greaterThan(0.0), lessThan(100.0))));
    }

    @Test
    void getPositions_unknownId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.POSITIONS, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPositions_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /portfolios/{id}/composition ─────────────────────────────────────

    @Test
    void getComposition_twoSameSectorPositions_returnsSingleEntry() throws Exception {
        AuthResult auth = login();

        // Both AAPL and GOOGL are TECHNOLOGY — must collapse into one entry at 100%
        mockMvc.perform(get(ApiRoutes.Portfolios.COMPOSITION, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label").value("Technology"))
                .andExpect(jsonPath("$[0].value", closeTo(100.0, 0.01)))
                .andExpect(jsonPath("$[0].color").value("#3B82F6"));
    }

    @Test
    void getComposition_allRequiredFields_present() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.COMPOSITION, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").isString())
                .andExpect(jsonPath("$[0].value").isNumber())
                .andExpect(jsonPath("$[0].color").isString());
    }

    @Test
    void getComposition_unknownId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.COMPOSITION, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComposition_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Portfolios.COMPOSITION, portfolioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /portfolios/{id}/volatility ──────────────────────────────────────

    @Test
    void getVolatility_defaultRange_returnsDateKeyedSeries() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VOLATILITY, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].date").isString())
                .andExpect(jsonPath("$[0].value").isNumber());
    }

    @Test
    void getVolatility_explicitRange_returns200() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VOLATILITY, portfolioId)
                        .param("range", DateRange.ONE_YEAR.getValue())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getVolatility_unknownId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VOLATILITY, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVolatility_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Portfolios.VOLATILITY, portfolioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}