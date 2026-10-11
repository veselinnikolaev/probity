package me.veselin.probity.common.domain.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helper component for capturing domain events during tests.
 */
@Component
public class TestEventCapture {
    private final List<DomainEvent> capturedEvents = new ArrayList<>();

    @EventListener
    public void captureEvent(DomainEvent event) {
        capturedEvents.add(event);
    }

    public List<DomainEvent> getCapturedEvents() {
        return new ArrayList<>(capturedEvents);
    }

    public void clear() {
        capturedEvents.clear();
    }
}
