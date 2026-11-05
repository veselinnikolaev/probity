package me.veselin.probity.simulation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationDltOutboxJpaRepository
        extends JpaRepository<SimulationDltOutboxJpaEntity, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO simulation_dlt_outbox (
                dlt_event_id, simulation_id, attempt_id,
                source_topic, source_partition, source_offset, source_key,
                payload, exception_type, error_description, failed_at, created_at
            ) VALUES (
                :dltEventId, :simulationId, :attemptId,
                :sourceTopic, :sourcePartition, :sourceOffset, :sourceKey,
                :payload, :exceptionType, :errorDescription, :failedAt, now()
            )
            ON CONFLICT (source_topic, source_partition, source_offset, attempt_id)
            DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("dltEventId") UUID dltEventId,
            @Param("simulationId") UUID simulationId,
            @Param("attemptId") UUID attemptId,
            @Param("sourceTopic") String sourceTopic,
            @Param("sourcePartition") int sourcePartition,
            @Param("sourceOffset") long sourceOffset,
            @Param("sourceKey") String sourceKey,
            @Param("payload") byte[] payload,
            @Param("exceptionType") String exceptionType,
            @Param("errorDescription") String errorDescription,
            @Param("failedAt") Instant failedAt);

    @Query(value = """
            SELECT d.dlt_event_id, d.source_topic, d.source_partition,
                   d.source_offset, d.source_key, d.payload, d.claim_token,
                   d.claimed_until, d.publish_attempt_count
              FROM simulation_dlt_outbox d
             WHERE d.dlt_published_at IS NULL
               AND (d.claimed_until IS NULL OR d.claimed_until < :now)
             ORDER BY d.created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Object[]> lockPendingBatch(@Param("limit") int limit, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE simulation_dlt_outbox
               SET claim_token = :claimToken,
                   claimed_until = :claimedUntil
             WHERE dlt_event_id = :eventId
               AND dlt_published_at IS NULL
               AND (claim_token IS NULL OR claimed_until < :now)
            """, nativeQuery = true)
    int claim(@Param("eventId") UUID eventId,
              @Param("claimToken") UUID claimToken,
              @Param("claimedUntil") Instant claimedUntil,
              @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE simulation_dlt_outbox
               SET dlt_published_at = now(),
                   claim_token = NULL,
                   claimed_until = NULL
             WHERE dlt_event_id = :eventId
               AND claim_token = :claimToken
               AND dlt_published_at IS NULL
            """, nativeQuery = true)
    int markPublished(@Param("eventId") UUID eventId,
                      @Param("claimToken") UUID claimToken);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE simulation_dlt_outbox
               SET claim_token = NULL,
                   claimed_until = :retryAt,
                   publish_attempt_count = publish_attempt_count + 1,
                   last_publish_error = :error
             WHERE dlt_event_id = :eventId
               AND claim_token = :claimToken
            """, nativeQuery = true)
    int recordFailure(@Param("eventId") UUID eventId,
                      @Param("claimToken") UUID claimToken,
                      @Param("retryAt") Instant retryAt,
                      @Param("error") String error);

    Optional<SimulationDltOutboxJpaEntity> findBySourceTopicAndSourcePartitionAndSourceOffsetAndAttemptId(
            String sourceTopic, int sourcePartition, long sourceOffset, UUID attemptId);

    List<SimulationDltOutboxJpaEntity> findBySimulationIdOrderByFailedAtDesc(UUID simulationId);
}
