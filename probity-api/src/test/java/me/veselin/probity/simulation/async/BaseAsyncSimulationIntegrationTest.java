package me.veselin.probity.simulation.async;

import me.veselin.probity.portfolio.port.portfolio.PortfolioQueryPort;
import me.veselin.probity.simulation.BaseSimulationIntegrationTest;
import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

/**
 * Slim api-side base for the one async test that stays in the api.
 *
 * <p>The full async base moved to probity-worker: it boots {@code WorkerApplication} and
 * seeds its own workspace, neither of which exists on the api classpath. This class keeps
 * only the observation surface the request-contract test needs — the execution counter,
 * the row diagnostics and the listener-container description — on top of the existing
 * web/auth/redis stack via {@link BaseSimulationIntegrationTest}.
 */
public abstract class BaseAsyncSimulationIntegrationTest extends BaseSimulationIntegrationTest {

    /**
     * Invocation count means "an execution actually started". Kept in lockstep with the
     * worker base's counter so diagnostics read identically whichever module hosts a test.
     */
    protected static final String EXECUTED_ON = "loadPortfolioWithPositionsAndAssets";

    /** Spied so a test can count real executions; the real PortfolioQueryService delegates. */
    @MockitoSpyBean
    protected PortfolioQueryPort portfolioQueryPort;

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private org.springframework.core.env.Environment environment;

    /** The group this class's own listener listens as. */
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

    /**
     * Everything a failure report needs: what the row looks like, how many executions
     * ran, and what the listener container is doing. (The worker base also reports the
     * committed consumer-group offset; the api-side half of the slice never commits one
     * — its listener is dormant — so it is omitted here.)
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