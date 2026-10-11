package me.veselin.probity;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import me.veselin.probity.bff.security.rate_limit.store.BucketStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@TestConfiguration
public class RateLimitTestConfig {

    @Bean
    @Primary
    public BucketStore noOpBucketStore() {
        return (key, annotation) -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(1_000_000L)
                        .refillGreedy(1_000_000L, Duration.ofSeconds(1))
                        .build())
                .build();
    }
}