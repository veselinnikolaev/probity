package me.veselin.probity.simulation.async;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.event.SimulationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static me.veselin.probity.simulation.async.AsyncKafkaTestSupport.registerDormantListener;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T1 - the request contract of the async entry point.
 *
 * <p>Runs with the listener deliberately dormant ({@code auto-startup=false}) so the
 * assertion on {@code PENDING} is deterministic rather than racing the consumer.
 * This test is expected to pass before and after PR-A; it exists so the reds below
 * cannot be blamed on a broken request path.
 */
class SimulationRunAsyncRequestIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    /**
     * The Kafka hop is stubbed out entirely: the publisher is mocked and the broker address
     * is {@link AsyncKafkaTestSupport#UNREACHABLE_BROKER}, so this class needs no broker at
     * all and no listener in any cached context can reach a row it seeds. T1 is about the
     * request contract, and the request contract does not include the publish succeeding.
     */
    @MockitoBean
    private SimulationEventPublisher simulationEventPublisher;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registerDormantListener(registry);
    }

    @Test
    @DisplayName("T1: POST /simulations/run-async returns 202, a status Location and a PENDING row")
    void runAsync_acceptsRequestAndLeavesTheRowPending() throws Exception {
        AuthResult auth = login();

        MvcResult result = mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN_ASYNC)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "t1-run-async-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(100, 10, 0.95))))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/simulations/")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/status")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.portfolioId").value(portfolioId))
                .andReturn();

        UUID id = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        Simulation row = simulationRepository.findById(id).orElseThrow();
        assertEquals(SimulationStatus.PENDING, row.getStatus(),
                () -> "the request must hand off a PENDING row, not a claimed one: " + diagnostics(id, row));
        assertNull(row.getResultPayload(),
                () -> "no result may exist before a worker runs: " + diagnostics(id, row));

        // The direct evidence that nothing consumed the event: the listener is dormant.
        // (executionAttempts() is deliberately not asserted here - the run-async request
        // itself legitimately reads the portfolio once, to capture the market-data
        // snapshot, so the count would be 1 even with a perfectly idle consumer.)
        assertTrue(describeConsumer().contains("running=false"),
                () -> "no listener may be running while the PENDING row is asserted. "
                        + diagnostics(id, row));
    }

    @Test
    @DisplayName("T1: the Location header points at the status endpoint of the new row")
    void runAsync_locationHeaderAddressesTheStatusEndpoint() throws Exception {
        AuthResult auth = login();

        MvcResult result = mockMvc.perform(withCsrf(post(ApiRoutes.Simulations.RUN_ASYNC)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "t1-run-async-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestBody(100, 10, 0.95))))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID id = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertNotNull(location, "202 must carry a Location header for status polling");
        assertTrue(location.contains("/simulations/" + id + "/status"),
                () -> "Location must address the status endpoint of the row just created, but was: " + location);
    }

    private String runRequestBody(int paths, int days, double confidence) {
        return """
                {
                  "portfolioId": "%s",
                  "numberOfSimulations": %d,
                  "timeHorizonDays": %d,
                  "confidenceLevel": %s
                }
                """.formatted(portfolioId, paths, days, confidence);
    }
}
