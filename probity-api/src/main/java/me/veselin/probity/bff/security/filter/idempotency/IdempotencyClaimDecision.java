package me.veselin.probity.bff.security.filter.idempotency;

import java.time.Instant;

public record IdempotencyClaimDecision(
    Kind kind, IdempotencyClaim claim, IdempotencyRecordSnapshot completed, Instant retryAt) {
  public enum Kind {
    CLAIMED,
    COMPLETED,
    IN_PROGRESS,
    HASH_MISMATCH
  }

  public static IdempotencyClaimDecision claimed(IdempotencyClaim claim) {
    return new IdempotencyClaimDecision(Kind.CLAIMED, claim, null, null);
  }

  public static IdempotencyClaimDecision completed(IdempotencyRecordSnapshot record) {
    return new IdempotencyClaimDecision(Kind.COMPLETED, null, record, null);
  }

  public static IdempotencyClaimDecision inProgress(Instant retryAt) {
    return new IdempotencyClaimDecision(Kind.IN_PROGRESS, null, null, retryAt);
  }

  public static IdempotencyClaimDecision hashMismatch() {
    return new IdempotencyClaimDecision(Kind.HASH_MISMATCH, null, null, null);
  }
}
