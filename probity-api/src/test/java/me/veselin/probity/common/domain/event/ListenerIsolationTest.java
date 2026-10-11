package me.veselin.probity.common.domain.event;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test that verifies per-listener exception isolation.
 * Registers two listeners for the same event, makes one throw,
 * and asserts that the second listener still ran.
 */
@SpringBootTest(classes = ListenerIsolationTest.TestConfig.class)
@ActiveProfiles("test")
@Slf4j
public class ListenerIsolationTest {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private ThrowingListener throwingListener;

    @Autowired
    private SucceedingListener succeedingListener;

    @Test
    void throwingListenerDoesNotPreventSucceedingListener() {
        // Act
        TestEvent event = new TestEvent();
        applicationEventPublisher.publishEvent(event);

        // Assert
        assertThat(throwingListener.wasCalled()).isTrue();
        assertThat(succeedingListener.wasCalled()).isTrue();
    }

    @Configuration
    static class TestConfig {
        @Bean
        public ThrowingListener throwingListener() {
            return new ThrowingListener();
        }

        @Bean
        public SucceedingListener succeedingListener() {
            return new SucceedingListener();
        }

        @Bean(name = "applicationEventMulticaster")
        public ApplicationEventMulticaster applicationEventMulticaster() {
            org.springframework.context.event.SimpleApplicationEventMulticaster multicaster =
                    new org.springframework.context.event.SimpleApplicationEventMulticaster();
            multicaster.setErrorHandler(throwable -> {
                log.error("Listener threw exception during event processing", throwable);
            });
            return multicaster;
        }
    }

    record TestEvent() implements DomainEvent {}

    @Component
    @Order(1)
    static class ThrowingListener {
        private boolean called = false;

        @EventListener
        public void handle(TestEvent event) {
            called = true;
            throw new RuntimeException("Intentional exception for testing isolation");
        }

        public boolean wasCalled() {
            return called;
        }
    }

    @Component
    @Order(2)
    static class SucceedingListener {
        private boolean called = false;

        @EventListener
        public void handle(TestEvent event) {
            called = true;
        }

        public boolean wasCalled() {
            return called;
        }
    }
}
