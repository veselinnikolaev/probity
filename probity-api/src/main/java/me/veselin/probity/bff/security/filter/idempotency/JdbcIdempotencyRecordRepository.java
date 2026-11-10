package me.veselin.probity.bff.security.filter.idempotency;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcIdempotencyRecordRepository implements IdempotencyRecordRepository {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public int insertClaim(
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
      long retentionSeconds) {
    return jdbcTemplate.update(
        """
        INSERT INTO idempotency_record
          (id, user_id, scope_type, scope_id, idempotency_key, request_method,
           request_path, request_hash, state, lease_token, lease_expires_at, expires_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS',
                ?, now() + (? * interval '1 second'),
                now() + (? * interval '1 second'))
        ON CONFLICT (user_id, scope_type, scope_id, idempotency_key) DO NOTHING
        """,
        id,
        userId,
        scopeType,
        scopeId,
        key,
        method,
        path,
        requestHash,
        leaseToken,
        leaseSeconds,
        retentionSeconds);
  }

  @Override
  public IdempotencyRecordSnapshot findForUpdate(
      UUID userId, String scopeType, UUID scopeId, String key) {
    var rows =
        jdbcTemplate.query(
            """
            SELECT id, request_hash, state, response_status, response_body,
                   response_truncated, response_reconstruction_metadata,
                   response_location, response_content_type,
                   lease_expires_at, lease_token
              FROM idempotency_record
             WHERE user_id = ? AND scope_type = ? AND scope_id = ? AND idempotency_key = ?
             FOR UPDATE
            """,
            (rs, rowNum) ->
                new IdempotencyRecordSnapshot(
                    rs.getObject("id", UUID.class),
                    rs.getString("request_hash").trim(),
                    rs.getString("state"),
                    (Integer) rs.getObject("response_status"),
                    rs.getString("response_body"),
                    rs.getBoolean("response_truncated"),
                    rs.getString("response_reconstruction_metadata"),
                    rs.getString("response_location"),
                    rs.getString("response_content_type"),
                    rs.getTimestamp("lease_expires_at") == null
                        ? null
                        : rs.getTimestamp("lease_expires_at").toInstant(),
                    rs.getObject("lease_token", UUID.class)),
            userId,
            scopeType,
            scopeId,
            key);
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public Instant databaseNow() {
    return jdbcTemplate.queryForObject(
        "SELECT now()", (rs, rowNum) -> rs.getTimestamp(1).toInstant());
  }

  @Override
  public int takeOverExpiredClaim(
      UUID id, UUID previousLeaseToken, UUID replacementLeaseToken, long leaseSeconds) {
    return jdbcTemplate.update(
        """
        UPDATE idempotency_record
           SET lease_token = ?, lease_expires_at = now() + (? * interval '1 second'),
               updated_at = now()
         WHERE id = ? AND state = 'IN_PROGRESS' AND lease_token = ?
           AND lease_expires_at < now()
        """,
        replacementLeaseToken,
        leaseSeconds,
        id,
        previousLeaseToken);
  }

  @Override
  public boolean ownsInProgressClaim(IdempotencyClaim claim) {
    var rows =
        jdbcTemplate.query(
            """
            SELECT state, lease_token
              FROM idempotency_record
             WHERE id = ?
             FOR UPDATE
            """,
            (rs, rowNum) -> new Object[] {rs.getString("state"), rs.getObject("lease_token", UUID.class)},
            claim.id());
    return rows.size() == 1
        && "IN_PROGRESS".equals(rows.get(0)[0])
        && claim.leaseToken().equals(rows.get(0)[1]);
  }

  @Override
  public int complete(
      IdempotencyClaim claim,
      int status,
      String body,
      boolean truncated,
      String reconstructionMetadata,
      String location,
      String contentType) {
    return jdbcTemplate.update(
        """
        UPDATE idempotency_record
           SET state = 'COMPLETED', response_status = ?, response_body = ?,
               response_truncated = ?, response_reconstruction_metadata = ?,
               response_location = ?, response_content_type = ?,
               lease_token = NULL, lease_expires_at = NULL, updated_at = now()
         WHERE id = ? AND lease_token = ? AND state = 'IN_PROGRESS'
        """,
        status,
        body,
        truncated,
        reconstructionMetadata,
        location,
        contentType,
        claim.id(),
        claim.leaseToken());
  }

  @Override
  public int deleteIfOwned(IdempotencyClaim claim) {
    return jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE id = ? AND lease_token = ? AND state = 'IN_PROGRESS'",
        claim.id(),
        claim.leaseToken());
  }

  @Override
  public int deleteExpired(int limit) {
    return jdbcTemplate.update(
        """
        DELETE FROM idempotency_record
         WHERE id IN (
           SELECT id FROM idempotency_record
            WHERE expires_at < now()
              AND (state <> 'IN_PROGRESS' OR lease_expires_at < now())
            ORDER BY expires_at
            LIMIT ?
         )
        """,
        limit);
  }
}
