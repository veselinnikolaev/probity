package me.veselin.probity.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import me.veselin.probity.bff.security.filter.idempotency.IdempotencyRecord;
import me.veselin.probity.bff.security.filter.idempotency.IdempotencyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

/**
 * Round-trip coverage for the production Redis JSON serializer.
 *
 * <p>{@link IdempotencyFilter} stores {@link IdempotencyRecord} — a final Java record — through an
 * {@code Object}-typed {@code RedisTemplate} and casts the deserialized value back to {@link
 * IdempotencyRecord}. That cast only survives if the serializer embeds polymorphic type metadata
 * for final types; without it Jackson produces a {@code LinkedHashMap} and the replay path fails
 * with {@link ClassCastException}. This test uses the real {@code @Bean} factory method, not a
 * re-created mapper, so it fails if the production typing configuration regresses.
 */
class RedisJsonSerializerConfigurationTest {

  private final GenericJackson2JsonRedisSerializer serializer =
      new RedisJsonSerializerConfiguration().redisJsonSerializer();

  @Test
  void idempotencyRecord_roundTripsAsRecordNotMap() {
    IdempotencyRecord original =
        new IdempotencyRecord(IdempotencyStatus.COMPLETED, 200, "{\"result\":\"ok\"}");

    byte[] bytes = serializer.serialize(original);
    Object restored = serializer.deserialize(bytes);

    IdempotencyRecord record = assertInstanceOf(IdempotencyRecord.class, restored);
    assertEquals(IdempotencyStatus.COMPLETED, record.status());
    assertEquals(200, record.statusCode());
    assertEquals("{\"result\":\"ok\"}", record.responseBody());
  }

  @Test
  void pendingIdempotencyRecord_roundTrips() {
    IdempotencyRecord original = new IdempotencyRecord(IdempotencyStatus.PENDING, 0, null);

    Object restored = serializer.deserialize(serializer.serialize(original));

    IdempotencyRecord record = assertInstanceOf(IdempotencyRecord.class, restored);
    assertEquals(IdempotencyStatus.PENDING, record.status());
    assertEquals(0, record.statusCode());
    assertEquals(null, record.responseBody());
  }
}
