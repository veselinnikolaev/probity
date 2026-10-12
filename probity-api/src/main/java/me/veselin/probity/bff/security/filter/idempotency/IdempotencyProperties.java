package me.veselin.probity.bff.security.filter.idempotency;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for idempotency filter.
 * <p>
 * Loaded from application.yaml under {@code probity.idempotency} namespace.
 */
@Data
@Component
@ConfigurationProperties(prefix = "probity.idempotency")
public class IdempotencyProperties {

    /**
     * Redis key prefix for idempotency records.
     */
    private String keyPrefix = "probity:idempotency:";

    /**
     * Time-to-live configuration for idempotency keys.
     */
    private Ttl ttl = new Ttl();

    /**
     * Key validation configuration.
     */
    private Key key = new Key();

    @Data
    public static class Ttl {
        /**
         * Time-to-live in hours for idempotency keys.
         */
        private int hours = 24;
    }

    @Data
    public static class Key {
        /**
         * Minimum length for idempotency keys.
         */
        private int minLength = 8;

        /**
         * Maximum length for idempotency keys.
         */
        private int maxLength = 256;

        /**
         * Regex pattern for valid idempotency key characters.
         */
        private String pattern = "^[a-zA-Z0-9_-]+$";
    }
}
