package me.veselin.probity.common.domain.event;

/**
 * Interface for publishing domain events.
 * Implementations can be synchronous or asynchronous.
 */
public interface DomainEventPublisher {
    /**
     * Publishes a domain event to all registered listeners.
     * @param event The event to publish
     */
    void publish(DomainEvent event);
}
