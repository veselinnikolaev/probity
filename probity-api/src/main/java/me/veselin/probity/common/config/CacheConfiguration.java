package me.veselin.probity.common.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
/** Redis cache configuration with JSON serialization for complex types. */
public class CacheConfiguration {
  @Bean
  public RedisCacheManager cacheManager(
      RedisConnectionFactory connectionFactory,
      org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer serializer) {
    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
  }
}
