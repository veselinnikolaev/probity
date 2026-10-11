package me.veselin.probity.simulation.async;

import me.veselin.probity.auth.domain.User;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.simulation.BaseSimulationIntegrationTest;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Base for every test in the async (Kafka) simulation slice.
 *
 * <p>Two things this base deliberately does <em>not</em> do:
 * <ul>
 *   <li>It does not decide whether the listener starts, or which consumer group it joins.
 *       Both are per-class choices (see {@link AsyncKafkaTestSupport#registerAsyncBroker}),
 *       so a test that must observe the untouched {@code PENDING} row can run with the
 *       consumer dormant, and each live class gets a group it is the sole member of.</li>
 *   <li>It does not touch production code. Tests needing a short lease bind
 *       {@code probity.simulation.claim-lease}; the tests that would otherwise wait out the
 *       five-minute production lease bind a two-second one.</li>
 * </ul>
 */
public abstract class BaseAsyncSimulationIntegrationTest extends BaseSimulationIntegrationTest {

    /**
     * The portfolio read both simulation paths use, and therefore the one call whose
     * invocation count means "an execution actually started". Named as a constant because it
     * appears in three places that must not drift apart: this counter, the failing stub in
     * the retry-budget test, and the diagnostics.
     */
    protected static final String EXECUTED_ON = "loadPortfolioWithPositionsAndAssets";

    /**
     * Spied so a test can count real executions.
     *
     * <p>{@code MonteCarloSimulationService.runAsync} reaches
     * {@code loadPortfolioWithPositionsAndAssets} only after it has been handed a claim it
     * actually holds, so counting this call counts genuine executions — which is what the
     * async invariants are about. Counting {@code runAsync} invocations would not work: a
     * listener that decides not to run must still be counted as "did not execute", and
     * {@code runAsync} is only reached by the executions we want to count.
     */
    @MockitoSpyBean
    protected PortfolioQueryPort portfolioQueryPort;

    @Autowired
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private org.springframework.core.env.Environment environment;

    /**
     * The group this class's own consumer listens as.
     *
     * <p>Read back from the environment rather than held in a field, because the
     * {@code @DynamicPropertySource} value is what the container actually resolved. A field
     * could drift from the property and quietly make the offset probe report the wrong group.
     */
    protected String currentGroupId() {
        return environment.getProperty("probity.simulation.consumer-group",
                AsyncKafkaTestSupport.DEFAULT_CONSUMER_GROUP);
    }

    /**
     * The source topic this class's own listener consumes from.
     *
     * <p>Read back from the environment for the same reason {@link #currentGroupId()} does:
     * the {@code @DynamicPropertySource} value is what the publisher / consumer actually
     * resolved, and a field could drift from it.
     */
    protected String currentSourceTopic() {
        return environment.getProperty("probity.kafka.simulation-requested-topic",
                AsyncKafkaTestSupport.SOURCE_TOPIC);
    }

    @BeforeEach
    void resetExecutionCounter() {
        // Clear invocations only — the spy must keep delegating to the real
        // PortfolioQueryService, and PortfolioQueryPort carries no stubs.
        Mockito.clearInvocations(portfolioQueryPort);
    }

    @BeforeEach
    void seedAsyncMarketData() {
        // The async listener can execute this class's record at any moment during the test,
        // and must never observe empty market data: with the InsufficientMarketDataException
        // gate in MonteCarloSimulationService, an empty series would fail the row instead of
        // running. Re-stub the per-ticker bars here (after the portfolio base's @BeforeEach),
        // so the run is not at the mercy of the shared base's stub or shared DB state.
        List<PriceBar> appleBars = driftingBars("AAPL", 150.00);
        List<PriceBar> googleBars = driftingBars("GOOGL", 132.00);

        when(marketDataPort.getHistoricalBars(anyString(), any(), any())).thenReturn(appleBars);
        when(marketDataPort.getHistoricalBars(eq("AAPL"), any(), any())).thenReturn(appleBars);
        when(marketDataPort.getHistoricalBars(eq("GOOGL"), any(), any())).thenReturn(googleBars);
    }

    private static List<PriceBar> driftingBars(String ticker, double startPrice) {
        List<PriceBar> bars = new ArrayList<>(30);
        for (int i = 0; i < 30; i++) {
            double price = startPrice + i * 0.5;
            LocalDate barDate = LocalDate.now().minusDays(29L - i);
            bars.add(PriceBar.from(ticker, barDate,
                    new BigDecimal(price), new BigDecimal(price + 1.0),
                    new BigDecimal(price - 1.0), new BigDecimal(price),
                    new BigDecimal(price), 1_000_000L));
        }
        return bars;
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    /** Creates a PENDING row, exactly as POST /simulations/run-async does. */
    protected UUID seedPendingSimulation() {
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("seeded admin user not found"));
        UUID pid = UUID.fromString(portfolioId);

        Simulation pending = Simulation.builder()
                .portfolioId(pid)
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

    /** The event the real controller publishes, keyed on the simulation id. */
    protected SimulationRequestedEvent validEventFor(UUID simulationId) {
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("seeded admin user not found"));
        return new SimulationRequestedEvent(
                simulationId,
                UUID.fromString(portfolioId),
                userId,
                100,
                10,
                0.95,
                null,
                null,
                Map.of("AAPL", new BigDecimal("150.00"), "GOOGL", new BigDecimal("132.00")),
                Instant.now());
    }

    /**
     * Publishes and blocks until the broker acknowledges, so the record definitely
     * exists before any assertion runs.
     */
    protected void publish(Object value, UUID simulationId) throws Exception {
        kafkaTemplate.send(currentSourceTopic(), simulationId.toString(), value)
                .get(15, TimeUnit.SECONDS);
    }

    // ── observation ─────────────────────────────────────────────────────────

    /** Number of times real execution started — i.e. the portfolio was actually loaded. */
    protected long executionAttempts() {
        return Mockito.mockingDetails(portfolioQueryPort).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals(EXECUTED_ON))
                .count();
    }

    protected Optional<SimulationStatus> statusOf(UUID simulationId) {
        return simulationRepository.findById(simulationId).map(Simulation::getStatus);
    }

    /**
     * Waits for the row to reach one of {@code expected}, then returns it.
     *
     * <p>On timeout the thrown {@link AssertionError} carries the full diagnostic
     * string, so a failure report names the mechanism instead of just a clock.
     */
    protected Simulation awaitStatus(UUID simulationId, Set<SimulationStatus> expected, Duration timeout) {
        return awaitStatus(simulationId, expected, timeout, () -> "");
    }

    /**
     * As {@link #awaitStatus(UUID, Set, Duration)}, but {@code mechanism} is prefixed to
     * the timeout message. Callers pass the sentence naming the defect under test, so a
     * failure report explains itself without anyone reading the test body.
     *
     * <p>The supplier is invoked only on failure, and only after the wait has elapsed —
     * which matters when the mechanism note reports an observation that is itself an
     * outcome of the wait, such as a committed consumer-group offset.
     */
    protected Simulation awaitStatus(UUID simulationId, Set<SimulationStatus> expected,
                                     Duration timeout, Supplier<String> mechanism) {
        AtomicReference<Simulation> lastSeen = new AtomicReference<>();
        try {
            await().atMost(timeout).pollInterval(Duration.ofMillis(200))
                    .until(() -> simulationRepository.findById(simulationId).map(s -> {
                        lastSeen.set(s);
                        return expected.contains(s.getStatus());
                    }).orElse(false));
        } catch (RuntimeException e) {
            String note = mechanism.get();
            throw new AssertionError(
                    (note.isEmpty() ? "" : note + " ")
                            + "Timed out after " + timeout + " waiting for simulation " + simulationId
                            + " to reach " + expected.stream().map(SimulationStatus::name).collect(Collectors.toSet())
                            + ". Observed: " + diagnostics(simulationId, lastSeen.get()), e);
        }
        return lastSeen.get();
    }

    /**
     * The committed offset of the consumer group for the single source partition, or
     * empty if the group has never committed.
     *
     * <p>This is the direct observable for the ack question in F2: a committed offset
     * that covers a record the consumer must not ack is proof that it acked anyway.
     */
    protected Optional<Long> committedOffset(String groupId) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, AsyncKafkaTestSupport.KAFKA.getBootstrapServers());
        TopicPartition tp = new TopicPartition(currentSourceTopic(), 0);
        try (Admin admin = Admin.create(props)) {
            Map<TopicPartition, OffsetAndMetadata> offsets = admin.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS);
            OffsetAndMetadata offset = offsets.get(tp);
            return offset == null ? Optional.empty() : Optional.of(offset.offset());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Everything a failure report needs to identify the mechanism: what the row looks
     * like, how many executions ran, whether the group committed the offset, and what
     * the listener container is doing.
     */
    protected String diagnostics(UUID simulationId, Simulation observed) {
        String row = observed == null
                ? "row=" + simulationRepository.findById(simulationId)
                        .map(Simulation::getStatus)
                        .map(SimulationStatus::name)
                        .orElse("<missing>")
                : "row=" + observed.getStatus()
                        + " resultPayload=" + (observed.getResultPayload() == null ? "null" : "present")
                        + " updatedAt=" + observed.getUpdatedAt();

        return row
                + " | executions=" + executionAttempts()
                + " | committedOffset=" + committedOffset(currentGroupId())
                + " | consumer=" + describeConsumer();
    }

    protected String describeConsumer() {
        MessageListenerContainer found = listenerEndpointRegistry.getListenerContainers().stream()
                .filter(c -> currentGroupId().equals(c.getGroupId()))
                .findFirst()
                .orElse(null);
        if (found == null) {
            return "absent";
        }
        return "running=" + found.isRunning()
                + " assigned=" + found.getAssignedPartitions();
    }
}