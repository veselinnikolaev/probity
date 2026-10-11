package me.veselin.probity.simulation;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.auth.repository.UserRepository;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SimulationIntegrationTest extends BaseSimulationIntegrationTest {

    @Autowired
    UserRepository userRepository;

    // ── POST /simulations/run ─────────────────────────────────────────────────

    @Test
    void run_validRequest_returns201WithLocationAndPayload() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 200, 30, 0.95))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/simulations/")))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.portfolioId").value(portfolioId))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.parameters.numberOfSimulations").value(200))
                .andExpect(jsonPath("$.parameters.timeHorizonDays").value(30))
                .andExpect(jsonPath("$.parameters.confidenceLevel").value(0.95))
                .andExpect(jsonPath("$.currentPortfolioValue").value(closeTo(2160.0, 1.0)))
                .andExpect(jsonPath("$.statistics.expectedFinalValue").isNumber())
                .andExpect(jsonPath("$.statistics.medianFinalValue").isNumber())
                .andExpect(jsonPath("$.statistics.stdDeviation").isNumber())
                .andExpect(jsonPath("$.statistics.minValue").isNumber())
                .andExpect(jsonPath("$.statistics.maxValue").isNumber())
                .andExpect(jsonPath("$.outcomes.probabilityOf10PercentLoss").isNumber())
                .andExpect(jsonPath("$.outcomes.probabilityOf20PercentLoss").isNumber())
                .andExpect(jsonPath("$.outcomes.valueAtRisk95").isNumber())
                .andExpect(jsonPath("$.outcomes.conditionalValueAtRisk95").isNumber())
                .andExpect(jsonPath("$.allPaths").isArray())
                .andExpect(jsonPath("$.allPaths", hasSize(200)))
                .andExpect(jsonPath("$.percentileSeries").isArray())
                .andExpect(jsonPath("$.percentileSeries", hasSize(7)))
                .andExpect(jsonPath("$.distribution").isArray())
                .andExpect(jsonPath("$.distribution", hasSize(30)));
    }

    @Test
    void run_withExplicitOverrides_usesCallerSuppliedReturnAndVol() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "portfolioId": "%s",
                                  "numberOfSimulations": 100,
                                  "timeHorizonDays": 10,
                                  "confidenceLevel": 0.95,
                                  "assumedReturnPercent": 8.0,
                                  "assumedVolatilityPercent": 20.0
                                }
                                """.formatted(portfolioId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.allPaths", hasSize(100)));
    }

    @Test
    void run_eachPath_containsExpectedNumberOfDayPoints() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 10, 0.95))))
                .andExpect(status().isCreated())
                // Day 0 through day 10 inclusive = 11 points per path
                .andExpect(jsonPath("$.allPaths[0].values", hasSize(11)))
                .andExpect(jsonPath("$.allPaths[0].values[0].dayIndex").value(0))
                .andExpect(jsonPath("$.allPaths[0].values[10].dayIndex").value(10));
    }

    @Test
    void run_dayZeroValue_equalsCurrentPortfolioValue() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 5, 0.95))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allPaths[0].values[0].value").value(closeTo(2160.0, 1.0)))
                .andExpect(jsonPath("$.allPaths[1].values[0].value").value(closeTo(2160.0, 1.0)));
    }

    @Test
    void run_percentileSeries_containsAllSevenBands() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 20, 0.95))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.percentileSeries[0].percentile").value(5))
                .andExpect(jsonPath("$.percentileSeries[1].percentile").value(10))
                .andExpect(jsonPath("$.percentileSeries[2].percentile").value(25))
                .andExpect(jsonPath("$.percentileSeries[3].percentile").value(50))
                .andExpect(jsonPath("$.percentileSeries[4].percentile").value(75))
                .andExpect(jsonPath("$.percentileSeries[5].percentile").value(90))
                .andExpect(jsonPath("$.percentileSeries[6].percentile").value(95));
    }

    @Test
    void run_distribution_allBucketsHaveRequiredFields() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 20, 0.95))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.distribution[0].rangeMin").isNumber())
                .andExpect(jsonPath("$.distribution[0].rangeMax").isNumber())
                .andExpect(jsonPath("$.distribution[0].count").isNumber())
                .andExpect(jsonPath("$.distribution[0].percentage").isNumber());
    }

    @Test
    void run_emptyPortfolio_returns400() throws Exception {
        AuthResult auth = login();
        // Create portfolio without adding a position
        String response = mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-empty-portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Empty Simulation Portfolio"}
                                """)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String emptyPortfolioId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(emptyPortfolioId, 100, 30, 0.95))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_unknownPortfolioId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(UUID.randomUUID().toString(), 100, 30, 0.95))))
                .andExpect(status().isNotFound());
    }

    @Test
    void run_anotherUsersPortfolio_returns403() throws Exception {
        AuthResult other = registerAndLogin("other_user", "other@probity.test", "Password456!");

        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), other.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 30, 0.95))))
                .andExpect(status().isForbidden());
    }

    @Test
    void run_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .header("Idempotency-Key", "test-key-sim-10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 30, 0.95))))
                .andExpect(status().isUnauthorized());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void run_numberOfSimulations_belowMinimum_returns400() throws Exception {
        AuthResult auth = login();
        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 50, 30, 0.95)))) // min=100
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_numberOfSimulations_aboveMaximum_returns400() throws Exception {
        AuthResult auth = login();
        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 99_999, 30, 0.95)))) // max=10000
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_timeHorizonDays_belowMinimum_returns400() throws Exception {
        AuthResult auth = login();
        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 0, 0.95)))) // min=1
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_timeHorizonDays_aboveMaximum_returns400() throws Exception {
        AuthResult auth = login();
        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-14")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 9999, 0.95)))) // max=1260
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_confidenceLevel_belowMinimum_returns400() throws Exception {
        AuthResult auth = login();
        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-15")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 30, 0.50)))) // min=0.80
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_confidenceLevel_aboveMaximum_returns400() throws Exception {
        AuthResult auth = login();
        mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-16")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 30, 1.0)))) // max=0.99
                .andExpect(status().isBadRequest());
    }

    // ── GET /simulations/{id} ─────────────────────────────────────────────────

    @Test
    void get_existingSimulation_returns200WithFullPayload() throws Exception {
        AuthResult auth = login();
        String simulationId = runSimulationAndGetId(auth, 100, 10);

        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATION, simulationId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(simulationId))
                .andExpect(jsonPath("$.portfolioId").value(portfolioId))
                .andExpect(jsonPath("$.statistics.expectedFinalValue").isNumber())
                .andExpect(jsonPath("$.allPaths", hasSize(100)));
    }

    @Test
    void get_unknownSimulationId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATION, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_anotherUsersSimulation_returns404() throws Exception {
        // findByIdAndUserId silently returns empty → 404, not 403
        // This avoids leaking that the simulation exists at all.
        AuthResult admin = login();
        String simulationId = runSimulationAndGetId(admin, 100, 10);

        AuthResult spy = registerAndLogin("spy_user", "spy@probity.test", "Password789!");
        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATION, simulationId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), spy.accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATION, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /simulations?portfolioId= ─────────────────────────────────────────

    @Test
    void list_noSimulationsYet_returnsEmptyArray() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATIONS)
                        .param("portfolioId", portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void list_afterTwoRuns_returnsBothMostRecentFirst() throws Exception {
        AuthResult auth = login();

        runSimulationAndGetId(auth, 100, 5);
        runSimulationAndGetId(auth, 200, 10);  // most recent

        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATIONS)
                        .param("portfolioId", portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].parameters.numberOfSimulations").value(200))
                .andExpect(jsonPath("$[1].parameters.numberOfSimulations").value(100));
    }

    @Test
    void list_onlyReturnsSimulationsForRequestedPortfolio() throws Exception {
        AuthResult auth = login();
        String secondPortfolioId = createPortfolio(auth, "Second Portfolio");

        runSimulationAndGetId(auth, 100, 5);  // against first portfolio only

        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATIONS)
                        .param("portfolioId", secondPortfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATIONS)
                        .param("portfolioId", portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void list_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATIONS)
                        .param("portfolioId", portfolioId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_excludesPendingAndFailed_onlyCompleted() throws Exception {
        AuthResult auth = login();
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        // Create a sync simulation (COMPLETED)
        String completedId = runSimulationAndGetId(auth, 100, 5);

        // Manually insert a PENDING simulation (simulating async creation)
        var pendingSimulation = me.veselin.probity.simulation.domain.Simulation.builder()
                .portfolioId(UUID.fromString(portfolioId))
                .userId(userId)
                .numberOfSimulations(50)
                .timeHorizonDays(10)
                .confidenceLevel(0.95)
                .currentPortfolioValue(BigDecimal.ZERO)
                .resultPayload(null)
                .status(me.veselin.probity.simulation.domain.SimulationStatus.PENDING)
                .build();
        simulationRepository.save(pendingSimulation);

        // Manually insert a FAILED simulation
        var failedSimulation = me.veselin.probity.simulation.domain.Simulation.builder()
                .portfolioId(UUID.fromString(portfolioId))
                .userId(userId)
                .numberOfSimulations(50)
                .timeHorizonDays(10)
                .confidenceLevel(0.95)
                .currentPortfolioValue(BigDecimal.ZERO)
                .resultPayload(null)
                .status(me.veselin.probity.simulation.domain.SimulationStatus.FAILED)
                .build();
        simulationRepository.save(failedSimulation);

        // List should only return the COMPLETED simulation
        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATIONS)
                        .param("portfolioId", portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(completedId));
    }

    // ── Persistence: round-trip ───────────────────────────────────────────────

    @Test
    void run_thenGet_returnsIdenticalPayload() throws Exception {
        AuthResult auth = login();

        String runResponse = mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-17")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, 100, 10, 0.95))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var runJson = objectMapper.readTree(runResponse);
        String id = runJson.get("id").asText();
        double expectedValue = runJson.get("statistics").get("expectedFinalValue").asDouble();
        int pathCount = runJson.get("allPaths").size();

        mockMvc.perform(get(ApiRoutes.Simulations.SIMULATION, id)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.statistics.expectedFinalValue").value(closeTo(expectedValue, 0.01)))
                .andExpect(jsonPath("$.allPaths", hasSize(pathCount)));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String runRequestBody(String portfolioId, int paths, int days, double cl) {
        return """
                {
                  "portfolioId": "%s",
                  "numberOfSimulations": %d,
                  "timeHorizonDays": %d,
                  "confidenceLevel": %s
                }
                """.formatted(portfolioId, paths, days, cl);
    }

    private String runSimulationAndGetId(AuthResult auth, int paths, int days) throws Exception {
        String response = mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-sim-helper-" + paths + "-" + days)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(portfolioId, paths, days, 0.95))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    private String createPortfolio(AuthResult auth, String name) throws Exception {
        String response = mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-portfolio-" + name.hashCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(response).get("id").asText();

        // Add a position to make the portfolio valid for simulation
        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-position-" + name.hashCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticker": "AAPL",
                                  "quantity": 10,
                                  "averageBuyPrice": 150.00
                                }
                                """)))
                .andExpect(status().isCreated());

        return portfolioId;
    }
}
