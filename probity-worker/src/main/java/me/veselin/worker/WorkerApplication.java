package me.veselin.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async simulation worker - Phase 4 extraction.
 *
 * <p>Lives in {@code me.veselin.worker} so the class itself escapes the
 * {@code me.veselin.probity} component scan, while {@code scanBasePackages}
 * still picks up every extracted type (simulation consumer, portfolio query,
 * market data, risk, Kafka configuration, domain event publishers).
 *
 * <p>Spring Data and JPA do <em>not</em> follow {@code scanBasePackages}: the
 * auto-configuration package is derived from this class's own location
 * ({@code me.veselin.worker}), so entity and repository scanning are pointed at
 * the extracted tree explicitly — {@code @EntityScan} for the JPA entities
 * (Asset, PriceBar, the portfolio and simulation persistence adapters) and
 * {@code @EnableJpaRepositories} for the Spring Data repositories
 * (AssetRepository, PriceBarRepository, PortfolioJpaRepository,
 * SimulationJpaRepository).
 *
 * <p>Spring Security is excluded entirely: the worker only uses
 * {@code org.springframework.security.access.AccessDeniedException} from
 * {@code spring-security-core} to enforce portfolio ownership and exposes no
 * HTTP API beyond the actuator probe.
 */
@SpringBootApplication(
        scanBasePackages = "me.veselin.probity",
        exclude = {
                SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        })
@EntityScan(basePackages = "me.veselin.probity")
@EnableJpaRepositories(basePackages = "me.veselin.probity")
@Import(WorkerEventConfiguration.class)
@EnableAsync
@EnableCaching
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }

    /**
     * Dedicated CPU-bound executor for Monte Carlo simulations.
     * Uses a bounded thread pool sized to available processors to prevent
     * ForkJoinPool starvation of other async operations.
     * Uses CallerRunsPolicy to handle rejection by executing in the calling thread.
     */
    @Bean
    public Executor simulationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors());
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("simulation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * I/O-bound executor using Java 21 virtual threads.
     * Ideal for blocking I/O operations like HTTP calls to external APIs.
     */
    @Bean
    public Executor ioExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}