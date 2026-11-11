package me.veselin.probity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regression coverage for the Flyway V25.2 self-deadlock fix.
 *
 * <p>V25.2 creates two indexes with {@code CREATE INDEX CONCURRENTLY}. With Flyway's default
 * transactional PostgreSQL advisory lock, the lock-holding connection's transaction stays open
 * for the whole migration and the concurrent index build waits on it indefinitely (upstream
 * flyway#3508). {@code spring.flyway.postgresql.transactional-lock=false} switches to a
 * session-level advisory lock so no transaction is held across the non-transactional migration.
 *
 * <p>Every run of this suite migrates a fresh Testcontainers database, so a green run proves the
 * migration completed; these assertions additionally prove the indexes are valid (not left
 * INVALID by an interrupted concurrent build), recorded as successful in {@code
 * flyway_schema_history}, and that migration history stayed consistent.
 *
 * <p>Note on names: V25.3 attaches the unique index to its constraint via {@code UNIQUE USING
 * INDEX}, after which PostgreSQL reports the index under the constraint's name ({@code
 * uq_simulations_id_dispatch_generation}, without the {@code _idx} suffix V25.2 created).
 */
class FlywayV252MigrationIntegrationTest extends BaseIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void v252ConcurrentIndexesExistAndAreValid() {
    List<Map<String, Object>> indexes =
        jdbcTemplate.queryForList(
            """
            SELECT c.relname AS index_name, i.indisvalid, i.indisready
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_index i ON i.indexrelid = c.oid
            WHERE n.nspname = 'public'
              AND c.relkind = 'i'
              AND c.relname IN (
                'uq_simulations_id_dispatch_generation',
                'idx_simulation_dlt_simulation_generation')
            """);

    assertThat(indexes)
        .as("both V25.2 indexes must exist")
        .extracting(row -> row.get("index_name"))
        .containsExactlyInAnyOrder(
            "uq_simulations_id_dispatch_generation", "idx_simulation_dlt_simulation_generation");

    assertThat(indexes)
        .as("concurrent index builds must have completed valid, not been interrupted")
        .allSatisfy(
            row -> {
              assertThat(row.get("indisvalid")).isEqualTo(true);
              assertThat(row.get("indisready")).isEqualTo(true);
            });
  }

  @Test
  void v252IsRecordedSuccessfulInFlywayHistory() {
    Integer successCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM flyway_schema_history
            WHERE version = '25.2' AND success = true
            """,
            Integer.class);

    assertThat(successCount).isEqualTo(1);
  }

  @Test
  void noFailedMigrationsInHistory() {
    Integer failedCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class);

    assertThat(failedCount).isZero();
  }
}
