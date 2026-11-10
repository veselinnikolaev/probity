package me.veselin.probity.bff.security.filter.idempotency;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DurableIdempotencyService {

  private final IdempotencyRecordRepository repository;

  @Transactional
  public IdempotencyClaimDecision claim(
      UUID userId,
      String scopeType,
      UUID scopeId,
      String key,
      String method,
      String path,
      String requestHash,
      Duration lease,
      Duration retention) {
    UUID id = UUID.randomUUID();
    UUID token = UUID.randomUUID();
    int inserted =
        repository.insertClaim(
            id,
            userId,
            scopeType,
            scopeId,
            key,
            method,
            path,
            requestHash,
            token,
            lease.toSeconds(),
            retention.toSeconds());
    if (inserted == 1) {
      return IdempotencyClaimDecision.claimed(new IdempotencyClaim(id, token));
    }

    IdempotencyRecordSnapshot record = repository.findForUpdate(userId, scopeType, scopeId, key);
    if (record == null) {
      return claim(userId, scopeType, scopeId, key, method, path, requestHash, lease, retention);
    }
    if ("COMPLETED".equals(record.state())) {
      return requestHash.equals(record.requestHash())
          ? IdempotencyClaimDecision.completed(record)
          : IdempotencyClaimDecision.hashMismatch();
    }
    if (record.leaseExpiresAt() != null
        && record.leaseExpiresAt().isAfter(repository.databaseNow())) {
      return IdempotencyClaimDecision.inProgress(record.leaseExpiresAt());
    }

    UUID replacementToken = UUID.randomUUID();
    int taken =
        repository.takeOverExpiredClaim(
            record.id(), record.leaseToken(), replacementToken, lease.toSeconds());
    if (taken == 1) {
      return IdempotencyClaimDecision.claimed(
          new IdempotencyClaim(record.id(), replacementToken));
    }
    return claim(userId, scopeType, scopeId, key, method, path, requestHash, lease, retention);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void open(IdempotencyClaim claim) {
    if (!repository.ownsInProgressClaim(claim)) {
      throw new IdempotencyLostException();
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void complete(
      IdempotencyClaim claim,
      int status,
      String body,
      boolean truncated,
      String reconstructionMetadata,
      String location,
      String contentType) {
    int updated =
        repository.complete(
            claim, status, body, truncated, reconstructionMetadata, location, contentType);
    if (updated != 1) {
      throw new IdempotencyLostException();
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deleteIfOwned(IdempotencyClaim claim) {
    repository.deleteIfOwned(claim);
  }

  @Transactional
  public int deleteExpired(int limit) {
    return repository.deleteExpired(limit);
  }

  public static class IdempotencyLostException extends RuntimeException {
    public IdempotencyLostException() {
      super("Idempotency lease was lost before completion");
    }
  }
}
