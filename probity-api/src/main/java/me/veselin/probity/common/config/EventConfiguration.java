package me.veselin.probity.common.config;

import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.domain.event.CompositeDomainEventPublisher;
import me.veselin.probity.common.domain.event.DomainEventPublisher;
import me.veselin.probity.common.domain.event.SynchronousDomainEventPublisher;
import me.veselin.probity.simulation.event.SimulationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

import java.util.List;

/**
 * Configuration for domain event publishing.
 * Registers a custom ApplicationEventMulticaster with error handling
 * to isolate listener exceptions and prevent one failing listener from
 * stopping others.
 * Also configures the composite DomainEventPublisher that delegates to
 * both synchronous (in-process) and Kafka publishers.
 */
@Configuration
@Slf4j
public class EventConfiguration {

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
     * Primary DomainEventPublisher that composites multiple publishers.
     * - SynchronousDomainEventPublisher: publishes all events in-process to Spring listeners
     * - SimulationEventPublisher: publishes SimulationRequestedEvent to Kafka for async consumer
     * The composite ensures SimulationRequestedEvent goes to both, others only in-process.
     */
    @Bean
    @Primary
    public DomainEventPublisher domainEventPublisher(
            SynchronousDomainEventPublisher synchronousPublisher,
            SimulationEventPublisher kafkaPublisher) {
        return new CompositeDomainEventPublisher(List.of(synchronousPublisher, kafkaPublisher));
    }
}
