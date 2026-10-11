package me.veselin.probity.simulation.event;

import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.simulation.config.SimulationProperties;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pauses and later resumes the partition that owns an unclaimable simulation record.
 *
 * <p>This replaces the former whole-consumer {@code nack}: {@link #park} asks the owning
 * container to pause a <em>single</em> {@link TopicPartition} and schedules its resume after
 * {@code probity.simulation.unclaimable-backoff}. On a {@code ConcurrentMessageListenerContainer}
 * the pause is applied to the child container that holds the partition, so sibling partitions
 * on the same consumer keep flowing.
 *
 * <p>Thread-safety is delegated to the container: {@code pausePartition}/{@code resumePartition}
 * only mutate the container's request set and are actioned by the container's own consumer
 * thread on the next poll, so calling them from this component's scheduler thread is safe.
 */
@Slf4j
@Component
public class UnclaimablePartitionParker implements DisposableBean {

    private final KafkaListenerEndpointRegistry registry;
    private final SimulationProperties properties;
    private final ScheduledExecutorService scheduler;

    public UnclaimablePartitionParker(KafkaListenerEndpointRegistry registry, SimulationProperties properties) {
        this.registry = registry;
        this.properties = properties;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "unclaimable-parker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Pauses {@code partition} on the container registered under {@code listenerId} and
     * schedules its resume after the configured backoff.
     *
     * <p>The consumer must already have seeked back to the record's offset when this is
     * called; the pause only gates when that offset is polled again.
     */
    public void park(String listenerId, TopicPartition partition) {
        MessageListenerContainer container = registry.getListenerContainer(listenerId);
        if (container == null) {
            log.error("No Kafka listener container registered under id={}; cannot pause partition {}. "
                    + "The record was seeked back but will be redelivered immediately.",
                    listenerId, partition);
            return;
        }
        long backoffMillis = properties.getUnclaimableBackoff().toMillis();
        container.pausePartition(partition);
        log.info("Paused partition {} on container {} for {} ms after an unclaimable delivery",
                partition, container, backoffMillis);
        scheduler.schedule(() -> {
            try {
                container.resumePartition(partition);
                log.info("Resumed partition {} on container {} after the unclaimable backoff",
                        partition, container);
            } catch (Exception e) {
                log.error("Failed to resume partition {} after the unclaimable backoff", partition, e);
            }
        }, backoffMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }
}