package me.veselin.probity.common.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Composite DomainEventPublisher that delegates to multiple publishers.
 * - SimulationRequestedEvent: published to BOTH Kafka (for async consumer) and in-process listeners
 * - All other events: published to in-process listeners only
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompositeDomainEventPublisher implements DomainEventPublisher {

    private final List<DomainEventPublisher> publishers;

    @Override
    public void publish(DomainEvent event) {
        for (DomainEventPublisher publisher : publishers) {
            try {
                publisher.publish(event);
            } catch (Exception e) {
                log.error("Publisher {} failed to publish event: {}", publisher.getClass().getSimpleName(), event, e);
            }
        }
    }
}