package me.veselin.probity.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import me.veselin.probity.bff.security.filter.idempotency.IdempotencyRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

@Configuration
public class RedisJsonSerializerConfiguration {

  @Bean
  public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
    PolymorphicTypeValidator validator =
        BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(IdempotencyRecord.class)
            .allowIfSubType("me.veselin.probity.auth.domain.")
            .allowIfSubType("me.veselin.probity.auth.enumeration.")
            .allowIfSubType("me.veselin.probity.common.audit.")
            .allowIfSubType("me.veselin.probity.marketdata.domain.")
            .allowIfSubType("me.veselin.probity.bff.security.filter.idempotency.")
            .allowIfSubType("org.springframework.ai.chat.messages.")
            .allowIfSubType("org.springframework.ai.content.")
            .allowIfSubType("java.util.ArrayList")
            .allowIfSubType("java.util.HashMap")
            .allowIfSubType("java.util.LinkedHashMap")
            .allowIfSubType("java.util.HashSet")
            .allowIfSubType("java.util.LinkedHashSet")
            .allowIfSubType("java.util.ImmutableCollections$")
            .allowIfSubType("java.util.Collections$")
            .allowIfSubType("java.util.Arrays$ArrayList")
            .build();

    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // Redis values are stored through Object-typed templates, so include final
    // records (notably IdempotencyRecord) in the type metadata as well.
    mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.EVERYTHING);
    return new GenericJackson2JsonRedisSerializer(mapper);
  }
}
