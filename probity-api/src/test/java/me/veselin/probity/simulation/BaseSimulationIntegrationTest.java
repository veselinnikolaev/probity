package me.veselin.probity.simulation;

import me.veselin.probity.portfolio.BasePortfolioIntegrationTest;
import me.veselin.probity.risk.dto.DistributionStatistics;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Base for simulation integration tests.
 *
 * Extends BasePortfolioIntegrationTest so we inherit:
 *   - Running Postgres + Redis containers
 *   - A seeded portfolio with AAPL + GOOGL positions
 *   - Market data and risk port stubs
 *   - CSRF + auth helpers
 *
 * Adds simulation-specific stubs for the three new RiskPort methods
 * and cleans the simulations table after each test.
 */
public abstract class BaseSimulationIntegrationTest extends BasePortfolioIntegrationTest {

    @Autowired
    protected SimulationRepository simulationRepository;

    @Override
    protected void stubRiskPort() {
        super.stubRiskPort();
        stubSimulationRiskPort();
    }

    // Called from BasePortfolioIntegrationTest.stubRiskPort() via override
    private void stubSimulationRiskPort() {
        // distributionStatistics — realistic values for a $2160 portfolio
        // (10 AAPL @ $150 + 5 GOOGL @ $132 = $1500 + $660 = $2160)
        DistributionStatistics stubbedStats = new DistributionStatistics(
                2250.0,  // mean — slightly above current
                2200.0,  // median
                180.0,   // stdDev
                1400.0,  // min
                3200.0   // max
        );
        when(riskPort.distributionStatistics(any(double[].class))).thenReturn(stubbedStats);

        // simulationVaR — threshold portfolio value at 95% CL
        when(riskPort.simulationVaR(any(double[].class), anyDouble())).thenReturn(1700.0);

        // conditionalValueAtRisk — average loss in the tail
        when(riskPort.conditionalValueAtRisk(any(double[].class), anyDouble())).thenReturn(1550.0);
    }

    @AfterEach
    void cleanSimulations() {
        // Delete in correct order to respect foreign keys
        simulationRepository.deleteAll();
    }
}

