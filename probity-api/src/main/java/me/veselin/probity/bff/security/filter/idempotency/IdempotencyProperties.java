package me.veselin.probity.bff.security.filter.idempotency;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for idempotency filter.
 *
 * <p>Loaded from application.yaml under {@code probity.idempotency} namespace.
 */
@Data
@Component
@ConfigurationProperties(prefix = "probity.idempotency")
public class IdempotencyProperties {

  /** Redis key prefix for idempotency records. */
  private String keyPrefix = "probity:idempotency:";

  /** Time-to-live configuration for idempotency keys. */
  private Ttl ttl = new Ttl();

  /** PostgreSQL lease used to recover a process that abandoned a claim. */
  private Duration lease = Duration.ofSeconds(30);

  /** Maximum serialized response body retained in the durable record. */
  private int responseMaxBytes = 16 * 1024;

  /** Enables the durable path for the explicitly opted-in async simulation endpoint. */
  private boolean enabled = true;

  /** Batch interval for the retention cleanup job. */
  private String cleanupInterval = "1h";

  /** Key validation configuration. */
  private Key key = new Key();

  @PostConstruct
  void validate() {
    if (lease.isNegative() || lease.isZero() || lease.compareTo(Duration.ofMinutes(10)) > 0) {
      throw new IllegalStateException("probity.idempotency.lease must be between 1 second and 10 minutes");
    }
    if (responseMaxBytes < 0 || responseMaxBytes > 16 * 1024) {
      throw new IllegalStateException(
          "probity.idempotency.response-max-bytes must be between 0 and 16384");
    }
    if (ttl.hours <= 0) {
      throw new IllegalStateException("probity.idempotency.ttl.hours must be positive");
    }
  }

  @Data
  public static class Ttl {
    /** Retention in hours for request-scoped idempotency keys. */
    private int hours = 72;
  }

  @Data
  public static class Key {
    /** Minimum length for idempotency keys. */
    private int minLength = 8;

    /** Maximum length for idempotency keys. */
    private int maxLength = 256;

    /** Regex pattern for valid idempotency key characters. */
    private String pattern = "^[a-zA-Z0-9_-]+$";
  }
}
