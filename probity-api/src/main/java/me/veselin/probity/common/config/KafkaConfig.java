package me.veselin.probity.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for simulation events.
 * Defines the main topic and dead-letter topic.
 */
@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${probity.kafka.simulation-requested-topic:simulation-requested}")
    private String simulationRequestedTopic;

    @Value("${probity.kafka.simulation-requested-dlt-topic:simulation-requested.DLT}")
    private String simulationRequestedDltTopic;

    @Value("${probity.kafka.simulation-topic-partitions:1}")
    private int simulationTopicPartitions;

    @Value("${probity.kafka.simulation-dlt-retention-ms:604800000}")
    private long simulationDltRetentionMs;

    /**
     * Main topic for simulation requested events.
     * Single partition for ordering guarantee per idempotency key.
     */
    @Bean
    public NewTopic simulationRequestedTopic() {
        return TopicBuilder.name(simulationRequestedTopic)
                .partitions(simulationTopicPartitions)
                .replicas(1)
                .build();
    }

    /**
     * Dead-letter topic for failed simulation requested events.
     * Retains failed messages for manual inspection and replay.
     */
    @Bean
    public NewTopic simulationRequestedDltTopic() {
        return TopicBuilder.name(simulationRequestedDltTopic)
                .partitions(simulationTopicPartitions)
                .replicas(1)
                .config("cleanup.policy", "delete")
                .config("retention.ms", String.valueOf(simulationDltRetentionMs))
                .build();
    }
}