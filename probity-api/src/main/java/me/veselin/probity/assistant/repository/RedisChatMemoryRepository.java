package me.veselin.probity.assistant.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.messages.Message;
import java.util.List;

import java.time.Duration;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    @Value("${probity.assistant.memory.key-prefix}")
    private String memoryPrefix;
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(memoryPrefix + "*");
        return keys.stream()
                .map(k -> k.substring(memoryPrefix.length()))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Object> raw = redisTemplate.opsForList().range(key(conversationId), 0, -1);
        if (raw == null) return List.of();
        return raw.stream().map(o -> (Message) o).toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = key(conversationId);
        redisTemplate.delete(key);
        if (!messages.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, messages.toArray());
            redisTemplate.expire(key, TTL);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return memoryPrefix + conversationId;
    }
}
