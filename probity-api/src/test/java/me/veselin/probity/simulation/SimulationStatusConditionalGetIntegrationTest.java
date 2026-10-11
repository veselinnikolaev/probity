package me.veselin.probity.simulation;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T5 - conditional GET on {@code GET /simulations/{id}/status}.
 *
 * <p>This endpoint is the one the {@code 202}'s {@code Location} header sends a client to,
 * so it is the whole client contract for the async feature. Today the controller builds its
 * ETag from the path variable alone:
 *
 * <pre>{@code String etag = "\"" + id + "\"";   // SimulationController:175}</pre>
 *
 * which is constant for the row's entire life. A client that follows the protocol correctly
 * — poll, keep the {@code ETag}, send {@code If-None-Match} — therefore receives:
 *
 * <pre>
 * GET …/status                          → 200, ETag: "9f3c…"                  (PENDING)
 * GET …/status  If-None-Match: "9f3c…"   → 304                                  (still PENDING)
 * … the worker finishes …
 * GET …/status  If-None-Match: "9f3c…"   → 304                                  ← WRONG
 * </pre>
 *
 * The row is {@code COMPLETED} and carrying a result, and the client is told its cached copy
 * is current — forever, because the server will never issue a second ETag for that row. The
 * polled result the async API exists to deliver is unreachable for any well-behaved
 * conditional client.
 *
 * <p>The existing suite is green because nothing polls conditionally.
 *
 * <p>Not Kafka-dependent: the state transition is driven straight through the repository, so
 * no broker, no listener and no timing are involved. The listener is dormant by default
 * ({@code application-test.yaml} sets {@code spring.kafka.listener.auto-startup: false}).
 */
class SimulationStatusConditionalGetIntegrationTest extends BaseSimulationIntegrationTest {

    // ── T5: RED on master ────────────────────────────────────────────────────

    @Test
    @DisplayName("T5: a conditional GET after the row leaves PENDING must return 200 and a new ETag")
    void getStatus_afterTheRowChanges_returns200WithAFreshEtag() throws Exception {
        AuthResult auth = login();
        UUID id = seedPendingSimulation();

        // 1. The client's first poll. PENDING, ETag captured.
        MvcResult first = mockMvc.perform(get(ApiRoutes.Simulations.STATUS, id)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String pendingEtag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(pendingEtag, () -> "a 200 on a pollable resource must carry an ETag, "
                + "otherwise the client has nothing to send back in If-None-Match");

        // 2. The worker takes the row. Driven through the repository so the assertion
        //    below exercises the same persisted state production would produce.
        simulationRepository.updateStatus(id, SimulationStatus.COMPLETED);
        Simulation reread = simulationRepository.findById(id).orElseThrow();
        assertNotEquals(SimulationStatus.PENDING, reread.getStatus(),
                "fixture failed: the row must have left PENDING before the second poll");

        // 3. The client's second poll, carrying the ETag from step 1. Asserted by hand so
        //    the failure report names the mechanism instead of just "expected 200".
        MvcResult second = mockMvc.perform(get(ApiRoutes.Simulations.STATUS, id)
                .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                .header(HttpHeaders.IF_NONE_MATCH, pendingEtag))
                .andReturn();

        int secondStatus = second.getResponse().getStatus();
        String completedEtag = second.getResponse().getHeader(HttpHeaders.ETAG);

        assertEquals(200, secondStatus,
                () -> "the second conditional poll answered " + secondStatus + " for a row "
                        + "whose representation had changed (PENDING → "
                        + reread.getStatus() + ", updated_at " + reread.getUpdatedAt() + "). "
                        + "The controller answers " + secondStatus + " before it ever loads the "
                        + "row, because its ETag is built from the path variable alone "
                        + "(\"\\\"\" + id + \"\\\"\") and therefore matches forever. The client "
                        + "holds ETag " + pendingEtag + ", the row is " + reread.getStatus()
                        + ", and the response carries " + completedEtag + " — so the polled "
                        + "result the async API exists to deliver is unreachable for any client "
                        + "that follows the conditional-GET protocol.");

        assertNotEquals(pendingEtag, completedEtag,
                () -> "the row's representation changed (PENDING → " + reread.getStatus()
                        + ", with a result attached) but the ETag did not. A constant ETag "
                        + "derived from the path variable makes every conditional poll after the "
                        + "first return 304. pendingETag=" + pendingEtag
                        + " completedETag=" + completedEtag
                        + " row.updatedAt=" + reread.getUpdatedAt());

        // 4. The new representation is the one the client was waiting for.
        mockMvc.perform(get(ApiRoutes.Simulations.STATUS, id)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header(HttpHeaders.IF_NONE_MATCH, pendingEtag))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ── the guard against the tempting wrong fix ──────────────────────────────

    @Test
    @DisplayName("T5: an unchanged row still answers 304 (conditional GET must survive the fix)")
    void getStatus_whenNothingChanged_returns304() throws Exception {
        AuthResult auth = login();
        UUID id = seedPendingSimulation();

        String firstEtag = mockMvc.perform(get(ApiRoutes.Simulations.STATUS, id)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        assertNotNull(firstEtag, "the first poll must establish an ETag");

        mockMvc.perform(get(ApiRoutes.Simulations.STATUS, id)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header(HttpHeaders.IF_NONE_MATCH, firstEtag))
                .andExpect(status().isNotModified());

        // Green on master and green after the fix, deliberately. Its only job is to fail if
        // the fix is "stop issuing an ETag" — which would trade a correctness bug for a
        // bandwidth bug and make every poll a full 200.
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /** A PENDING row owned by the seeded admin, exactly as run-async hands it to a worker. */
    private UUID seedPendingSimulation() {
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("seeded admin user not found"));

        Simulation pending = Simulation.builder()
                .portfolioId(UUID.fromString(portfolioId))
                .userId(userId)
                .numberOfSimulations(100)
                .timeHorizonDays(10)
                .confidenceLevel(0.95)
                .currentPortfolioValue(BigDecimal.ZERO)
                .resultPayload(null)
                .status(SimulationStatus.PENDING)
                .build();

        return simulationRepository.save(pending).getId();
    }
}
