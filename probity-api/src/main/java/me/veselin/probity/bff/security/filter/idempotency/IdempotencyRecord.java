package me.veselin.probity.bff.security.filter.idempotency;

/**
 * Record representing the state of an idempotent request in Redis.
 * <p>
 * Stores the current status, HTTP status code, and response body for an idempotent request.
 * Used by {@link IdempotencyFilter} to track request state across Redis.
 *
 * @param status the current state (PENDING or COMPLETED)
 * @param statusCode the HTTP status code of the response (0 for PENDING)
 * @param responseBody the response body as JSON string (null for PENDING)
 */
public record IdempotencyRecord(
        IdempotencyStatus status,
        int statusCode,
        String responseBody
) {}
