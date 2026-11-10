package me.veselin.probity.simulation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import me.veselin.probity.common.domain.event.DispatchGeneration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "simulation_dlt_outbox")
@Getter
@Setter
@NoArgsConstructor
public class SimulationDltOutboxJpaEntity {

  @Id
  @Column(name = "dlt_event_id", nullable = false, updatable = false)
  private UUID dltEventId;

  @Column(name = "simulation_id", updatable = false)
  private UUID simulationId;

  @Column(name = "attempt_id", nullable = false, updatable = false)
  private UUID attemptId;

  @Column(name = "source_topic", nullable = false, updatable = false, length = 249)
  private String sourceTopic;

  @Column(name = "source_partition", nullable = false, updatable = false)
  private int sourcePartition;

  @Column(name = "source_offset", nullable = false, updatable = false)
  private long sourceOffset;

  @Column(name = "source_key", updatable = false)
  private String sourceKey;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "payload", updatable = false)
  private byte[] payload;

  @Column(name = "exception_type", nullable = false, updatable = false, length = 255)
  private String exceptionType;

  @Column(name = "error_description", updatable = false)
  private String errorDescription;

  @Column(name = "failed_at", nullable = false, updatable = false)
  private Instant failedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "claim_token")
  private UUID claimToken;

  @Column(name = "claimed_until")
  private Instant claimedUntil;

  @Column(name = "publish_attempt_count", nullable = false)
  private int publishAttemptCount;

  @Column(name = "last_publish_error")
  private String lastPublishError;

  @Column(name = "dlt_published_at")
  private Instant dltPublishedAt;

  @Column(name = "dispatch_generation")
  private Integer dispatchGeneration;

  @Column(name = "dispatch_generation_raw", length = 64)
  private String dispatchGenerationRaw;

  @Column(name = "dispatch_generation_source", length = 20)
  @Enumerated(EnumType.STRING)
  private DispatchGeneration.Source dispatchGenerationSource;
}
