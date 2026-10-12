package me.veselin.probity.simulation.async;

import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.risk.dto.AssetRiskProfile;
import me.veselin.probity.risk.dto.DistributionStatistics;
import me.veselin.probity.risk.enumeration.RiskLevel;
import me.veselin.probity.risk.port.RiskPort;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import me.veselin.worker.WorkerApplication;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Base for every test in the async (Kafka) simulation slice, in the worker module.
 *
 * <p>The api hosted this slice on top of its full web/auth/redis test stack. The worker
 * has none of that: this base boots {@link WorkerApplication} on the {@code test} profile
 * against a throwaway Postgres (the same {@code @ServiceConnection} container the api's
 * {@code BaseIntegrationTest} uses, here without Redis) and the shared Testcontainers
 * Kafka broker from {@link AsyncKafkaTestSupport}.
 *
 * <p>The workspace is seeded with raw SQL against fixed fixture ids, because the api's
 * auth command port (which used to mint the user and the portfolio) is not on the worker
 * classpath. The seeded rows must satisfy exactly the same invariants the api slice
 * relied on: a verified user owning a portfolio with 10 AAPL @ 148 and 5 GOOGL @ 130,
 * where the AAPL/GOOGL asset rows are the V7 seed rows (looked up by ticker, never
 * re-inserted).
 */
@SpringBootTest(classes = WorkerApplication.class)
@ActiveProfiles("test")
public abstract class BaseAsyncSimulationIntegrationTest {

    // ── fixtures (fixed ids, idempotent across every class sharing the JVM database) ──

    private static final UUID SEED_USER_UUID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PORTFOLIO_UUID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID AAPL_POSITION_UUID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID GOOGL_POSITION_UUID = UUID.fromString("30000000-0000-0000-0000-000000000002");

    /** The portfolio both simulation paths read, fixed across the whole slice. */
    protected static final String portfolioId = PORTFOLIO_UUID.toString();

    /** The user that owns the seeded portfolio; every seeded simulation/event uses it. */
    protected static final UUID seededUserId = SEED_USER_UUID;

    /** Matches the api's BaseIntegrationTest container, minus Redis. */
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("probity_test")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    // ── spies, mocks and collaborators ──────────────────────────────────────

    /**
     * Invocation count means "an execution actually started". Named as a constant because it
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

    @MockitoBean
    protected MarketDataPort marketDataPort;

    @MockitoBean
    protected RiskPort riskPort;

    @Autowired
    protected SimulationRepository simulationRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private org.springframework.core.env.Environment environment;

    // ── before / after ──────────────────────────────────────────────────────

    @BeforeEach
    void seedWorkspace() {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password, username, role, status,
                                   created_at, updated_at, deleted, deleted_at, version)
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', now(), now(), false, null, 0)
                ON CONFLICT (id) DO NOTHING
                """, SEED_USER_UUID, "worker-test@probity.test", "unused-in-worker", "worker-admin");

        jdbcTemplate.update("""
                INSERT INTO portfolios (id, user_id, name, description,
                                        created_at, updated_at, deleted, deleted_at, version)
                VALUES (?, ?, 'Test Portfolio', null, now(), now(), false, null, 0)
                ON CONFLICT (id) DO NOTHING
                """, PORTFOLIO_UUID, SEED_USER_UUID);

        // AAPL/GOOGL asset rows are V7 seed data: look up their ids (the ticker is unique)
        // instead of re-inserting, so positions keep pointing at the seeded assets.
        jdbcTemplate.update("""
                INSERT INTO portfolio_positions (id, portfolio_id, asset_id, quantity, avg_buy_price,
                                                 created_at, updated_at, deleted, deleted_at, version)
                VALUES (?, ?, ?, 10.0000, 148.0000, now(), now(), false, null, 0)
                """, AAPL_POSITION_UUID, PORTFOLIO_UUID, assetId("AAPL"));
        jdbcTemplate.update("""
                INSERT INTO portfolio_positions (id, portfolio_id, asset_id, quantity, avg_buy_price,
                                                 created_at, updated_at, deleted, deleted_at, version)
                VALUES (?, ?, ?, 5.0000, 130.0000, now(), now(), false, null, 0)
                """, GOOGL_POSITION_UUID, PORTFOLIO_UUID, assetId("GOOGL"));

        stubRiskPort();
    }

    private UUID assetId(String ticker) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM assets WHERE ticker = ?", UUID.class, ticker);
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
        // running. Re-stub the per-ticker bars here, so the run is not at the mercy of
        // shared DB state.
        List<PriceBar> appleBars = driftingBars("AAPL", 150.00);
        List<PriceBar> googleBars = driftingBars("GOOGL", 132.00);

        when(marketDataPort.getHistoricalBars(anyString(), any(), any())).thenReturn(appleBars);
        when(marketDataPort.getHistoricalBars(eq("AAPL"), any(), any())).thenReturn(appleBars);
        when(marketDataPort.getHistoricalBars(eq("GOOGL"), any(), any())).thenReturn(googleBars);

        when(marketDataPort.getLatestPrice(anyString())).thenReturn(new BigDecimal("150.00"));
        when(marketDataPort.getLatestPrice("AAPL")).thenReturn(new BigDecimal("150.00"));
        when(marketDataPort.getLatestPrice("GOOGL")).thenReturn(new BigDecimal("132.00"));
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

    @AfterEach
    void cleanWorkspace() {
        // Simulations first (FK to portfolios/users), then positions -> portfolios -> user.
        // Asset rows are V7 seed data and are deliberately not deleted.
        simulationRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM portfolio_positions WHERE portfolio_id = ?", PORTFOLIO_UUID);
        jdbcTemplate.update("DELETE FROM portfolios WHERE id = ?", PORTFOLIO_UUID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", SEED_USER_UUID);
    }

    // ── risk-port stubs (same set the api's BasePortfolioIntegrationTest +
    //    BaseSimulationIntegrationTest chain supplied) ───────────────────────

    private void stubRiskPort() {
        when(riskPort.annualisedVolatility(any())).thenReturn(0.18);
        when(riskPort.sharpeRatio(any())).thenReturn(1.2);
        when(riskPort.var95(anyDouble(), any())).thenReturn(220.0);
        when(riskPort.rollingVolatility(any(), anyInt())).thenReturn(List.of(0.15, 0.16, 0.17));
        when(riskPort.rollingVolatilityFromReturns(any(), anyInt())).thenReturn(List.of(0.15, 0.16, 0.17));
        when(riskPort.toDailyReturns(any())).thenReturn(List.of(0.01, -0.005, 0.008));
        AssetRiskProfile profile = new AssetRiskProfile(AssetType.STOCK, AssetType.STOCK.getBaseRiskScore());
        when(riskPort.riskScore(any(AssetRiskProfile.class), anyDouble())).thenReturn(55);
        when(riskPort.riskLevel(anyInt())).thenReturn(RiskLevel.MODERATE.name());
        when(riskPort.maxDrawdown(any())).thenReturn(-0.05);

        // Simulation-specific: distributionStatistics / simulationVaR / conditionalValueAtRisk
        // for a $2160 portfolio (10 AAPL @ $150 + 5 GOOGL @ $132 = $1500 + $660 = $2160).
        DistributionStatistics stubbedStats = new DistributionStatistics(
                2250.0,  // mean — slightly above current
                2200.0,  // median
                180.0,   // stdDev
                1400.0,  // min
                3200.0   // max
        );
        when(riskPort.distributionStatistics(any(double[].class))).thenReturn(stubbedStats);
        when(riskPort.simulationVaR(any(double[].class), anyDouble())).thenReturn(1700.0);
        when(riskPort.conditionalValueAtRisk(any(double[].class), anyDouble())).thenReturn(1550.0);
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    /** Creates a PENDING row, exactly as POST /simulations/run-async does. */
    protected UUID seedPendingSimulation() {
        UUID pid = UUID.fromString(portfolioId);

        Simulation pending = Simulation.builder()
                .portfolioId(pid)
                .userId(SEED_USER_UUID)
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
        return new SimulationRequestedEvent(
                simulationId,
                UUID.fromString(portfolioId),
                SEED_USER_UUID,
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

    /** The group this class's own consumer listens as. */
    protected String currentGroupId() {
        return environment.getProperty("probity.simulation.consumer-group",
                AsyncKafkaTestSupport.DEFAULT_CONSUMER_GROUP);
    }

    /** The source topic this class's own listener consumes from. */
    protected String currentSourceTopic() {
        return environment.getProperty("probity.kafka.simulation-requested-topic",
                AsyncKafkaTestSupport.SOURCE_TOPIC);
    }

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