package me.veselin.probity.common.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Synchronous implementation of DomainEventPublisher.
 * Delegates to Spring's ApplicationEventPublisher for listener discovery and invocation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SynchronousDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(DomainEvent event) {
        log.debug("Publishing domain event: {}", event);
        applicationEventPublisher.publishEvent(event);
    }
}
