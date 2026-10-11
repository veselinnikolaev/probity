package me.veselin.probity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableCaching
@EnableAsync
public class ProbityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProbityApplication.class, args);
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
