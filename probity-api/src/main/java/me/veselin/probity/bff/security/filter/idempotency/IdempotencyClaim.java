package me.veselin.probity.bff.security.filter.idempotency;

import java.util.UUID;

public record IdempotencyClaim(UUID id, UUID leaseToken) {}
