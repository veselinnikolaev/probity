package me.veselin.probity;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
  @Autowired RedisTemplate<String, Object> redisTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  // shared across ALL test classes in the JVM — started once
  @ServiceConnection
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("probity_test")
          .withUsername("test")
          .withPassword("test");

  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.2.4")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "test")
          .withStartupTimeout(Duration.ofSeconds(60));

  static {
    postgres.start();
    redis.start();
  }

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "test");
    registry.add("spring.data.redis.database", () -> 1);
  }

  @BeforeEach
  void cleanRedis() {
    cleanDatabase();
    Objects.requireNonNull(redisTemplate.getConnectionFactory())
        .getConnection()
        .serverCommands()
        .flushDb();
    afterSetUp();
  }

  /**
   * Resets every test-owned table before each test. The suite shares one Postgres container per
   * JVM and tests commit real rows, so without this, fixed-name seeds (e.g. "Test Portfolio")
   * collide across classes on {@code uq_portfolio_name_user} and leftover outbox rows break
   * deletes on {@code simulations} via {@code fk_simulation_outbox_dispatch_generation}.
   *
   * <p>Order respects the enforced foreign keys: {@code simulation_outbox -> simulations},
   * {@code simulations -> portfolios/users}, {@code portfolio_positions -> portfolios/assets},
   * {@code portfolios -> users}. Tables without enforced FKs
   * ({@code simulation_paths}, {@code simulation_dlt_outbox}, {@code idempotency_record},
   * {@code price_bars}) are cleared first. Migration-seeded reference rows in {@code assets}
   * are removed exactly as the previous per-class cleanups did; tests re-seed what they need.
   */
  private void cleanDatabase() {
    for (String table :
        List.of(
            "simulation_outbox",
            "simulation_dlt_outbox",
            "simulation_paths",
            "simulations",
            "idempotency_record",
            "portfolio_positions",
            "portfolios",
            "price_bars",
            "user_preferences",
            "users",
            "assets")) {
      jdbcTemplate.execute("DELETE FROM " + table);
    }
  }

  protected void afterSetUp() {}
}
