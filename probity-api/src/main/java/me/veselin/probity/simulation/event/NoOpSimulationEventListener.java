package me.veselin.probity.simulation.event;

import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.domain.event.SimulationCompletedEvent;
import me.veselin.probity.common.domain.event.SimulationFailedEvent;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * No-op event listener for simulation events.
 * Demonstrates @EventListener registration for domain events.
 * In production, this would be replaced by actual event handlers.
 */
@Component
@Slf4j
public class NoOpSimulationEventListener {

    @EventListener
    public void handleSimulationRequested(SimulationRequestedEvent event) {
        log.debug("No-op handler for SimulationRequestedEvent: {}", event.simulationId());
    }

    @EventListener
    public void handleSimulationCompleted(SimulationCompletedEvent event) {
        log.debug("No-op handler for SimulationCompletedEvent: {}", event.simulationId());
    }

    @EventListener
    public void handleSimulationFailed(SimulationFailedEvent event) {
        log.debug("No-op handler for SimulationFailedEvent: portfolioId={}", event.portfolioId());
    }
}
