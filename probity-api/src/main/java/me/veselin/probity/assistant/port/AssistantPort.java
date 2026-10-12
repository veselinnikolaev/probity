package me.veselin.probity.assistant.port;

import java.util.UUID;

public interface AssistantPort {
    String chat(UUID userId, String message);
}
