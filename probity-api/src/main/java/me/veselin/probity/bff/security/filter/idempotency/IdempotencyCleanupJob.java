package me.veselin.probity.bff.security.filter.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {
  private final DurableIdempotencyService service;

  @Scheduled(fixedDelayString = "${probity.idempotency.cleanup-interval:1h}")
  public void deleteExpiredRecords() {
    service.deleteExpired(500);
  }
}
