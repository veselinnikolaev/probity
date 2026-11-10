package me.veselin.probity.simulation.event;

import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.domain.event.DomainEvent;
import me.veselin.probity.common.domain.event.DomainEventPublisher;
import me.veselin.probity.common.domain.event.SimulationRequestedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Async implementation of DomainEventPublisher using Kafka. Publishes simulation requested events
 * to Kafka topic for async processing. Other events are ignored (handled by synchronous publisher).
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
      String key =
          simulationEvent
              .simulationId()
              .toString(); // Use simulationId as message key for partitioning
      log.debug(
          "Publishing SimulationRequestedEvent to Kafka: simulationId={}",
          simulationEvent.simulationId());

      SimulationRequestedEvent generationOneEvent =
          simulationEvent.dispatchGeneration() == null
              ? simulationEvent.withDispatchGeneration(1)
              : simulationEvent;
      Message<DomainEvent> message =
          MessageBuilder.withPayload((DomainEvent) generationOneEvent)
              .setHeader(KafkaHeaders.TOPIC, simulationRequestedTopic)
              .setHeader(KafkaHeaders.KEY, key)
              .setHeader(
                  "dispatch-generation",
                  Integer.toString(generationOneEvent.dispatchGeneration())
                      .getBytes(StandardCharsets.US_ASCII))
              .build();
      CompletableFuture<SendResult<String, DomainEvent>> future = kafkaTemplate.send(message);
      future.whenComplete(
          (result, ex) -> {
            if (ex != null) {
              log.error(
                  "Failed to publish SimulationRequestedEvent to Kafka: simulationId={}",
                  simulationEvent.simulationId(),
                  ex);
            } else {
              log.debug(
                  "Successfully published SimulationRequestedEvent to Kafka: simulationId={}, partition={}, offset={}",
                  simulationEvent.simulationId(),
                  result.getRecordMetadata().partition(),
                  result.getRecordMetadata().offset());
            }
          });
    } else {
      // For other events (PortfolioUpdatedEvent, SimulationCompletedEvent, SimulationFailedEvent),
      // delegate to synchronous publisher (handled by CompositeDomainEventPublisher)
      log.debug(
          "Event type not configured for async publishing, skipping Kafka: {}",
          event.getClass().getSimpleName());
    }
  }
}
