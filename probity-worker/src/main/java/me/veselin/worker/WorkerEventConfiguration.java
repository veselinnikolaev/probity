package me.veselin.worker;

import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.domain.event.CompositeDomainEventPublisher;
import me.veselin.probity.common.domain.event.DomainEventPublisher;
import me.veselin.probity.common.domain.event.SynchronousDomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

import java.util.List;

/**
 * Domain event wiring for the worker.
 *
 * <p>Mirrors the api's {@code EventConfiguration} minus the Kafka publisher:
 * the worker publishes {@code SimulationCompletedEvent}/{@code SimulationFailedEvent}
 * synchronously in-process only. The {@code SimulationRequestedEventConsumer} is
 * driven directly by Kafka, so nothing is ever put back onto a topic here.
 *
 * <p>Imported explicitly by {@link WorkerApplication} because this class lives in
 * {@code me.veselin.worker} and is therefore outside the {@code me.veselin.probity}
 * component scan.
 */
@Configuration
@Slf4j
public class WorkerEventConfiguration {

    /**
     * Custom ApplicationEventMulticaster with error handler.
     * Bean name must be "applicationEventMulticaster" to override Spring's default.
     */
    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster applicationEventMulticaster() {
        SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
        multicaster.setErrorHandler(throwable -> {
            log.error("Listener threw exception during event processing", throwable);
        });
        return multicaster;
    }

    /**
     * Primary DomainEventPublisher that composites the synchronous publisher only.
     * The composite wrapper is kept so the bean graph matches the api module; the
     * Kafka hop (SimulationEventPublisher) stays in the api as the hand-off.
     */
    @Bean
    @Primary
    public DomainEventPublisher domainEventPublisher(SynchronousDomainEventPublisher synchronousPublisher) {
        log.info("Registering synchronous-only domain event publisher for the simulation worker");
        return new CompositeDomainEventPublisher(List.of(synchronousPublisher));
    }
}