package me.veselin.probity.common.config;

import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
/**
 * Redis connection and template configuration for caching and session storage.
 */
public class RedisConfiguration {
    @Value( "${spring.data.redis.username}")
    private String username;
    @Value( "${spring.data.redis.database}")
    private int database;
    @Value( "${spring.data.redis.password}")
    private String password;
    @Value( "${spring.data.redis.host}")
    private String host;
    @Value( "${spring.data.redis.port}")
    private int port;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    LettuceConnectionFactory lettuceConnectionFactory() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(host, port);
        config.setPassword(password);
        config.setUsername(username);
        config.setDatabase(database);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisClient lettuceRedisClient(LettuceConnectionFactory factory) {
        return (RedisClient) factory.getNativeClient();
    }
}
