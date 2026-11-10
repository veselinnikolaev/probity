package me.veselin.probity.bff.security.filter.idempotency;

import java.time.Instant;
import java.util.UUID;

public interface IdempotencyRecordRepository {

  int insertClaim(
      UUID id,
      UUID userId,
      String scopeType,
      UUID scopeId,
      String key,
      String method,
      String path,
      String requestHash,
      UUID leaseToken,
      long leaseSeconds,
      long retentionSeconds);

  IdempotencyRecordSnapshot findForUpdate(
      UUID userId, String scopeType, UUID scopeId, String key);

  Instant databaseNow();

  int takeOverExpiredClaim(
      UUID id, UUID previousLeaseToken, UUID replacementLeaseToken, long leaseSeconds);

  boolean ownsInProgressClaim(IdempotencyClaim claim);

  int complete(
      IdempotencyClaim claim,
      int status,
      String body,
      boolean truncated,
      String reconstructionMetadata,
      String location,
      String contentType);

  int deleteIfOwned(IdempotencyClaim claim);

  int deleteExpired(int limit);
}
