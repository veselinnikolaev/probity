package me.veselin.probity.portfolio.service.portfolio;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.portfolio.enumeration.DateRange;
import me.veselin.probity.portfolio.BasePortfolioIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class PortfolioSummaryIntegrationTest extends BasePortfolioIntegrationTest {

    // ── GET /portfolios/{id}/summary ─────────────────────────────────────────

    @Test
    void getSummary_defaultRange_returnsAllFields() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.SUMMARY, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").isNumber())
                .andExpect(jsonPath("$.totalValueDelta").isNumber())
                .andExpect(jsonPath("$.dailyReturn").isNumber())
                .andExpect(jsonPath("$.dailyReturnDelta").isNumber())
                .andExpect(jsonPath("$.volatility").isNumber())
                .andExpect(jsonPath("$.volatilityDelta").isNumber())
                .andExpect(jsonPath("$.sharpeRatio").isNumber())
                .andExpect(jsonPath("$.sharpeRatioDelta").isNumber())
                .andExpect(jsonPath("$.var95").isNumber())
                .andExpect(jsonPath("$.var95Delta").isNumber())
                .andExpect(jsonPath("$.sparklines").exists())
                .andExpect(jsonPath("$.sparklines.totalValue").isArray())
                .andExpect(jsonPath("$.sparklines.dailyReturn").isArray())
                .andExpect(jsonPath("$.sparklines.volatility").isArray());
    }

    @Test
    void getSummary_thirtyDayRange_returns200() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.SUMMARY, portfolioId)
                        .param("range", DateRange.THIRTY_DAYS.getValue())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").isNumber());
    }

    @Test
    void getSummary_oneYearRange_returns200() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.SUMMARY, portfolioId)
                        .param("range", DateRange.ONE_YEAR.getValue())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").isNumber());
    }

    @Test
    void getSummary_unknownId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.SUMMARY, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSummary_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Portfolios.SUMMARY, portfolioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
