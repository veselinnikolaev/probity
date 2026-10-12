package me.veselin.probity.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOffExecution;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    @Test
    void backoffIsBoundedToThreeAttemptsTotal() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);

        assertThat(backOff.getInitialInterval()).isEqualTo(1000L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);

        BackOffExecution execution = backOff.start();
        assertThat(execution.nextBackOff()).isEqualTo(1000L);
        assertThat(execution.nextBackOff()).isEqualTo(2000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }
}
