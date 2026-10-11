package me.veselin.probity.simulation.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.domain.event.DomainEvent;
import me.veselin.probity.common.domain.event.DomainEventPublisher;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Async implementation of DomainEventPublisher using Kafka.
 * Publishes simulation requested events to Kafka topic for async processing.
 * Other events are ignored (handled by synchronous publisher).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimulationEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    @Value("${probity.kafka.simulation-requested-topic:simulation-requested}")
    private String simulationRequestedTopic;

    @Override
    public void publish(DomainEvent event) {
        if (event instanceof SimulationRequestedEvent simulationEvent) {
            String key = simulationEvent.simulationId().toString(); // Use simulationId as message key for partitioning
            log.debug("Publishing SimulationRequestedEvent to Kafka: simulationId={}", simulationEvent.simulationId());

            CompletableFuture<SendResult<String, DomainEvent>> future = kafkaTemplate.send(simulationRequestedTopic, key, event);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish SimulationRequestedEvent to Kafka: simulationId={}", simulationEvent.simulationId(), ex);
                } else {
                    log.debug("Successfully published SimulationRequestedEvent to Kafka: simulationId={}, partition={}, offset={}",
                            simulationEvent.simulationId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
        } else {
            // For other events (PortfolioUpdatedEvent, SimulationCompletedEvent, SimulationFailedEvent),
            // delegate to synchronous publisher (handled by CompositeDomainEventPublisher)
            log.debug("Event type not configured for async publishing, skipping Kafka: {}", event.getClass().getSimpleName());
        }
    }
}