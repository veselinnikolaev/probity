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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class PortfolioRiskIntegrationTest extends BasePortfolioIntegrationTest {

    // ── GET /portfolios/{id}/alerts ──────────────────────────────────────────

    @Test
    void getAlerts_returnsAlertListWithRequiredFields() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.ALERTS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].message").isString())
                .andExpect(jsonPath("$[0].severity").isString());
    }

    @Test
    void getAlerts_allSectorAt100Percent_firesHighConcentrationAlert() throws Exception {
        AuthResult auth = login();

        // Both positions are Technology → single sector at 100% → high alert
        mockMvc.perform(get(ApiRoutes.Portfolios.ALERTS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.severity == 'high')]", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.severity == 'high')].message",
                        hasItem(containsString("Technology"))));
    }

    @Test
    void getAlerts_highSeverityAlert_includesAffectedTickers() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.ALERTS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].affectedAssets").isArray())
                .andExpect(jsonPath("$[0].affectedAssets", hasItem("AAPL")));
    }

    @Test
    void getAlerts_unknownId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.ALERTS, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAlerts_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Portfolios.ALERTS, portfolioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /portfolios/{id}/risk-metrics ────────────────────────────────────

    @Test
    void getRiskMetrics_defaultRange_returnsAllFields() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.RISK_METRICS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beta").isNumber())
                .andExpect(jsonPath("$.betaDelta").isNumber())
                .andExpect(jsonPath("$.maxDrawdown").isNumber())
                .andExpect(jsonPath("$.maxDrawdownDelta").isNumber())
                .andExpect(jsonPath("$.concentrationIndex").isNumber())
                .andExpect(jsonPath("$.concentrationDelta").isNumber())
                .andExpect(jsonPath("$.maxDrawdownSparkline").isArray())
                .andExpect(jsonPath("$.betaSparkline").isArray())
                .andExpect(jsonPath("$.concentrationSparkline").isArray());
    }

    @Test
    void getRiskMetrics_beta_isNeutralOneUntilBenchmarkWired() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.RISK_METRICS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beta", closeTo(1.0, 0.001)));
    }

    @Test
    void getRiskMetrics_explicitRange_returns200() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.RISK_METRICS, portfolioId)
                        .param("range", DateRange.THIRTY_DAYS.getValue())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getRiskMetrics_unknownId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.RISK_METRICS, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRiskMetrics_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Portfolios.RISK_METRICS, portfolioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /portfolios/{id}/correlation ─────────────────────────────────────

    @Test
    void getCorrelation_twoAssets_returns2x2Matrix() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.CORRELATION, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets", hasSize(2)))
                .andExpect(jsonPath("$.matrix", hasSize(2)))
                .andExpect(jsonPath("$.matrix[0]", hasSize(2)))
                .andExpect(jsonPath("$.matrix[1]", hasSize(2)));
    }

    @Test
    void getCorrelation_diagonal_isAlwaysOne() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.CORRELATION, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matrix[0][0]", closeTo(1.0, 0.001)))
                .andExpect(jsonPath("$.matrix[1][1]", closeTo(1.0, 0.001)));
    }

    @Test
    void getCorrelation_assets_containExpectedTickers() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.CORRELATION, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets", hasItems("AAPL", "GOOGL")));
    }

    @Test
    void getCorrelation_explicitRange_returns200() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.CORRELATION, portfolioId)
                        .param("range", DateRange.THIRTY_DAYS.getValue())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getCorrelation_unknownId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.CORRELATION, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCorrelation_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Portfolios.CORRELATION, portfolioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /portfolios/{id}/var-report ──────────────────────────────────────

    @Test
    void getVaRReport_defaultParams_returnsAllTopLevelFields() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VAR, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valueAtRisk").isNumber())
                .andExpect(jsonPath("$.confidenceLevel").isNumber())
                .andExpect(jsonPath("$.timeHorizonDays").isNumber())
                .andExpect(jsonPath("$.historicalSparkline").isArray())
                .andExpect(jsonPath("$.assetBreakdown").isArray())
                .andExpect(jsonPath("$.returnDistribution").isArray());
    }

    @Test
    void getVaRReport_methodology_containsAllFields() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VAR, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methodology.formula").isString())
                .andExpect(jsonPath("$.methodology.portfolioVolatility").isNumber())
                .andExpect(jsonPath("$.methodology.zscore").isNumber())
                .andExpect(jsonPath("$.methodology.expectedReturn").isNumber());
    }

    @Test
    void getVaRReport_params_echoedBackInResponse() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VAR, portfolioId)
                        .param("confidenceLevel", "0.99")
                        .param("timeHorizonDays", "5")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidenceLevel", closeTo(0.99, 0.001)))
                .andExpect(jsonPath("$.timeHorizonDays").value(5));
    }

    @Test
    void getVaRReport_assetBreakdown_containsAllFields() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VAR, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetBreakdown", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.assetBreakdown[0].ticker").isString())
                .andExpect(jsonPath("$.assetBreakdown[0].weight").isNumber())
                .andExpect(jsonPath("$.assetBreakdown[0].individualVar").isNumber())
                .andExpect(jsonPath("$.assetBreakdown[0].contribution").isNumber())
                .andExpect(jsonPath("$.assetBreakdown[0].percentOfTotal").isNumber());
    }

    @Test
    void getVaRReport_returnDistribution_hasExactlyTenBuckets() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VAR, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnDistribution", hasSize(10)))
                .andExpect(jsonPath("$.returnDistribution[0].range").isString())
                .andExpect(jsonPath("$.returnDistribution[0].frequency").isNumber())
                .andExpect(jsonPath("$.returnDistribution[0].isLoss").isBoolean());
    }

    @Test
    void getVaRReport_unknownId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Portfolios.VAR, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVaRReport_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Portfolios.VAR, portfolioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}