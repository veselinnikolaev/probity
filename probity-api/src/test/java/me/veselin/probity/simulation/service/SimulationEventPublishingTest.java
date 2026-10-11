package me.veselin.probity.simulation.service;

import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.repository.UserRepository;
import me.veselin.probity.common.domain.event.DomainEvent;
import me.veselin.probity.common.domain.event.SimulationCompletedEvent;
import me.veselin.probity.common.domain.event.SimulationFailedEvent;
import me.veselin.probity.common.domain.event.TestEventCapture;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.persistence.PortfolioRepository;
import me.veselin.probity.simulation.BaseSimulationIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for simulation event publishing.
 * Verifies that simulation events are fired correctly from MonteCarloSimulationService.run().
 */
@Transactional
public class SimulationEventPublishingTest extends BaseSimulationIntegrationTest {

    @Autowired
    private MonteCarloSimulationService monteCarloSimulationService;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TestEventCapture testEventCapture;

    @BeforeEach
    void setupTestEventCapture() {
        testEventCapture.clear();
    }

    @Test
    void runSimulation_firesSimulationCompletedEvent() {
        // Arrange - use seeded portfolio from base class
        UUID portfolioUuid = UUID.fromString(portfolioId);
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        // Act
        monteCarloSimulationService.run(portfolioUuid, 100, 5, 0.95, null, null, userId);

        // Assert - only SimulationCompletedEvent is published for sync path
        List<DomainEvent> events = testEventCapture.getCapturedEvents();
        assertThat(events).hasSize(1);

        // Event should be SimulationCompletedEvent
        assertThat(events.get(0)).isInstanceOf(SimulationCompletedEvent.class);
        SimulationCompletedEvent completedEvent = (SimulationCompletedEvent) events.get(0);
        assertThat(completedEvent.portfolioId()).isEqualTo(portfolioUuid);
        assertThat(completedEvent.userId()).isEqualTo(userId);
        assertThat(completedEvent.simulationId()).isNotNull();
        assertThat(completedEvent.currentPortfolioValue()).isNotNull();
        assertThat(completedEvent.occurredAt()).isNotNull();
    }

    @Test
    void runSimulationWithEmptyPortfolio_firesSimulationFailedEventAndRethrowsException() {
        // Arrange - create empty portfolio
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        Portfolio portfolio = Portfolio.create("Empty Portfolio", userId);
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        // Act & Assert
        assertThatThrownBy(() ->
                monteCarloSimulationService.run(savedPortfolio.getId(), 100, 5, 0.95, null, null, userId)
        ).isInstanceOf(me.veselin.probity.simulation.exception.EmptyPortfolioException.class)
                .hasMessageContaining("has no positions");

        // Verify events were published - only SimulationFailedEvent for sync failure
        List<DomainEvent> events = testEventCapture.getCapturedEvents();
        assertThat(events).hasSize(1);

        // Event should be SimulationFailedEvent
        assertThat(events.get(0)).isInstanceOf(SimulationFailedEvent.class);
        SimulationFailedEvent failedEvent = (SimulationFailedEvent) events.get(0);
        assertThat(failedEvent.portfolioId()).isEqualTo(savedPortfolio.getId());
        assertThat(failedEvent.userId()).isEqualTo(userId);
        assertThat(failedEvent.simulationId()).isNull(); // No simulation record created for sync failure
        assertThat(failedEvent.errorMessage()).contains("has no positions");
        assertThat(failedEvent.occurredAt()).isNotNull();
    }
}
