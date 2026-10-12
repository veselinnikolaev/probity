package me.veselin.probity.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import me.veselin.probity.common.domain.event.DomainEvent;
import me.veselin.probity.simulation.config.SimulationFailedRecordRecoverer;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer and error handling configuration for the simulation worker.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final int MAX_RETRIES = 2; // retries after the first attempt; total attempts = MAX_RETRIES + 1

    /**
     * Source and dead-letter topics for simulation-requested events.
     *
     * <p>Read as top-level {@code probity.kafka.*} properties (same source {@link KafkaConfig}
     * uses for the {@code NewTopic} beans), not {@code spring.kafka.properties.*} — the
     * recoverer must map the exact topic the listener consumes from and the publisher sends to.
     */
    @Value("${probity.kafka.simulation-requested-topic:simulation-requested}")
    private String simulationRequestedTopic;

    @Value("${probity.kafka.simulation-requested-dlt-topic:simulation-requested.DLT}")
    private String simulationRequestedDltTopic;

    @Bean
    @Primary
    public ProducerFactory<String, DomainEvent> domainEventProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        if (kafkaProperties.getProducer() != null && kafkaProperties.getProducer().getProperties() != null) {
            configProps.putAll(kafkaProperties.getProducer().getProperties());
        }
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, DomainEvent> kafkaTemplate(ProducerFactory<String, DomainEvent> domainEventProducerFactory) {
        return new KafkaTemplate<>(domainEventProducerFactory);
    }

    @Bean
    public ProducerFactory<Object, byte[]> deadLetterProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        if (kafkaProperties.getProducer() != null && kafkaProperties.getProducer().getProperties() != null) {
            configProps.putAll(kafkaProperties.getProducer().getProperties());
        }

        configProps.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        configProps.putIfAbsent(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.putIfAbsent(ProducerConfig.MAX_BLOCK_MS_CONFIG, 60000L);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<Object, byte[]> deadLetterKafkaTemplate(ProducerFactory<Object, byte[]> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
    }
    @Bean
    public CommonErrorHandler simulationKafkaErrorHandler(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer,
            SimulationRepository simulationRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        DeadLetterPublishingRecoverer delegate = deadLetterPublishingRecoverer;
        SimulationFailedRecordRecoverer recoverer = new SimulationFailedRecordRecoverer(delegate,
                simulationRepository,
                objectMapper,
                meterRegistry);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        errorHandler.addNotRetryableExceptions(
                me.veselin.probity.simulation.exception.EmptyPortfolioException.class,
                me.veselin.probity.simulation.exception.InsufficientMarketDataException.class,
                me.veselin.probity.portfolio.exception.PortfolioNotFoundException.class,
                me.veselin.probity.portfolio.exception.PositionNotFoundException.class,
                me.veselin.probity.portfolio.exception.AssetNotFoundException.class,
                me.veselin.probity.simulation.exception.SimulationNotFoundException.class,
                org.springframework.security.access.AccessDeniedException.class,
                IllegalArgumentException.class
        );

        return errorHandler;
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<Object, byte[]> deadLetterKafkaTemplate) {
        Map<String, String> dltTopics = new HashMap<>();
        dltTopics.put(simulationRequestedTopic, simulationRequestedDltTopic);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate, (r, e) -> {
            String target = dltTopics.get(r.topic());
            if (target == null) {
                target = r.topic() + ".DLT";
            }
            return new org.apache.kafka.common.TopicPartition(target, r.partition());
        });
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(5));
        return recoverer;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            org.springframework.kafka.core.ConsumerFactory<Object, Object> kafkaConsumerFactory,
            CommonErrorHandler commonErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, kafkaConsumerFactory);
        factory.setCommonErrorHandler(commonErrorHandler);
        return factory;
    }

    @Bean
    public org.springframework.kafka.core.KafkaTemplate<String, Object> stringObjectKafkaTemplate(org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {
        java.util.Map<String, Object> configProps = new java.util.HashMap<>();
        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        if (kafkaProperties.getProducer() != null && kafkaProperties.getProducer().getProperties() != null) {
            configProps.putAll(kafkaProperties.getProducer().getProperties());
        }
        return new org.springframework.kafka.core.KafkaTemplate<>(new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(configProps));
    }

}
