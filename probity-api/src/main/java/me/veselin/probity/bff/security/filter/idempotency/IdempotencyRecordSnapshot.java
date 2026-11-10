package me.veselin.probity.bff.security.filter.idempotency;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecordSnapshot(
    UUID id,
    String requestHash,
    String state,
    Integer responseStatus,
    String responseBody,
    boolean responseTruncated,
    String responseReconstructionMetadata,
    String responseLocation,
    String responseContentType,
    Instant leaseExpiresAt,
    UUID leaseToken) {}
