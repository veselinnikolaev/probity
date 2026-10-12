package me.veselin.probity.common.domain.event;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Base test class for event publishing integration tests.
 * Provides event capture mechanism via @EventListener.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class EventPublishingIntegrationTest {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private final TestEventCapture testEventCapture = new TestEventCapture();

    @AfterEach
    void clearCapturedEvents() {
        testEventCapture.clear();
    }

    protected List<DomainEvent> getCapturedEvents() {
        return testEventCapture.getCapturedEvents();
    }

    protected void publishEvent(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Component
    private static class TestEventCapture {
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
}
