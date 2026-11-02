package me.veselin.test.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Test-time configuration that provides a {@code CacheManager} bean
 * for test caching support.
 * <p>
 * The {@code simulationKafkaTemplate} and {@code producerFactory} beans have been
 * removed from this configuration to avoid name conflicts with the production
 * {@link me.veselin.probity.common.config.KafkaConsumerConfig#kafkaTemplate} bean.
 * Tests that need a Kafka template should use the {@code stringObjectKafkaTemplate}
 * from {@link me.veselin.worker.config.KafkaConsumerConfig} or the base test support.
 */
@Configuration
public class KafkaTestConfiguration {

    /** CacheManager for test caching support. */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("test");
    }
}