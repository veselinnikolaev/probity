package me.veselin.probity.portfolio.service.portfolio;

import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.repository.UserRepository;
import me.veselin.probity.common.domain.event.DomainEvent;
import me.veselin.probity.common.domain.event.PortfolioUpdatedEvent;
import me.veselin.probity.common.domain.event.TestEventCapture;
import me.veselin.probity.portfolio.BasePortfolioIntegrationTest;
import me.veselin.probity.portfolio.domain.Asset;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.dto.AddPositionCommand;
import me.veselin.probity.portfolio.dto.UpdatePositionCommand;
import me.veselin.probity.portfolio.repository.AssetRepository;
import me.veselin.probity.portfolio.persistence.PortfolioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for portfolio event publishing.
 * Verifies that PortfolioUpdatedEvent is fired for all portfolio mutations.
 */
@Transactional
public class PortfolioEventPublishingTest extends BasePortfolioIntegrationTest {

    @Autowired
    private PortfolioCommandService portfolioCommandService;

    @Autowired
    private TestEventCapture testEventCapture;

    @BeforeEach
    void setupTestEventCapture() {
        testEventCapture.clear();
    }

    @Test
    void createPortfolio_firesPortfolioUpdatedEvent() {
        // Arrange - use seeded user from base class
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        // Act
        portfolioCommandService.create("New Test Portfolio", userId);

        // Assert
        List<DomainEvent> events = testEventCapture.getCapturedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PortfolioUpdatedEvent.class);

        PortfolioUpdatedEvent event = (PortfolioUpdatedEvent) events.get(0);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.portfolioId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void addPosition_firesPortfolioUpdatedEvent() {
        // Arrange - use seeded portfolio from base class
        UUID portfolioUuid = UUID.fromString(portfolioId);
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        // Act
        portfolioCommandService.addPosition(
                portfolioUuid,
                new AddPositionCommand("MSFT", BigDecimal.valueOf(5)),
                userId
        );

        // Assert
        List<DomainEvent> events = testEventCapture.getCapturedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PortfolioUpdatedEvent.class);

        PortfolioUpdatedEvent event = (PortfolioUpdatedEvent) events.get(0);
        assertThat(event.portfolioId()).isEqualTo(portfolioUuid);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void updatePosition_firesPortfolioUpdatedEvent() {
        // Arrange - use seeded portfolio from base class
        UUID portfolioUuid = UUID.fromString(portfolioId);
        UUID positionUuid = UUID.fromString(positionId);
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        // Act
        portfolioCommandService.updatePosition(
                portfolioUuid,
                positionUuid,
                new UpdatePositionCommand(BigDecimal.valueOf(20)),
                userId
        );

        // Assert
        List<DomainEvent> events = testEventCapture.getCapturedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PortfolioUpdatedEvent.class);

        PortfolioUpdatedEvent event = (PortfolioUpdatedEvent) events.get(0);
        assertThat(event.portfolioId()).isEqualTo(portfolioUuid);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void deletePosition_firesPortfolioUpdatedEvent() {
        // Arrange - use seeded portfolio from base class
        UUID portfolioUuid = UUID.fromString(portfolioId);
        UUID positionUuid = UUID.fromString(positionId);
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        // Act
        portfolioCommandService.deletePosition(portfolioUuid, positionUuid, userId);

        // Assert
        List<DomainEvent> events = testEventCapture.getCapturedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PortfolioUpdatedEvent.class);

        PortfolioUpdatedEvent event = (PortfolioUpdatedEvent) events.get(0);
        assertThat(event.portfolioId()).isEqualTo(portfolioUuid);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void updatePortfolio_firesPortfolioUpdatedEvent() {
        // Arrange - use seeded portfolio from base class
        UUID portfolioUuid = UUID.fromString(portfolioId);
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        // Act
        portfolioCommandService.update(portfolioUuid, userId, "Updated Name", "Updated Description");

        // Assert
        List<DomainEvent> events = testEventCapture.getCapturedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PortfolioUpdatedEvent.class);

        PortfolioUpdatedEvent event = (PortfolioUpdatedEvent) events.get(0);
        assertThat(event.portfolioId()).isEqualTo(portfolioUuid);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void deletePortfolio_firesPortfolioUpdatedEvent() {
        // Arrange - use seeded portfolio from base class
        UUID portfolioUuid = UUID.fromString(portfolioId);
        UUID userId = userRepository.findByUsername(ADMIN_USERNAME)
                .map(User::getId)
                .orElseThrow();

        // Act
        portfolioCommandService.delete(portfolioUuid, userId);

        // Assert
        List<DomainEvent> events = testEventCapture.getCapturedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PortfolioUpdatedEvent.class);

        PortfolioUpdatedEvent event = (PortfolioUpdatedEvent) events.get(0);
        assertThat(event.portfolioId()).isEqualTo(portfolioUuid);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isNotNull();
    }
}
