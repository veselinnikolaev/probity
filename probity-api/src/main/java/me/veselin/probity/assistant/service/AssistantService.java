package me.veselin.probity.assistant.service;

import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.assistant.port.AssistantPort;
import me.veselin.probity.assistant.repository.RedisChatMemoryRepository;
import me.veselin.probity.assistant.tools.PortfolioTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
/**
 * Service for AI-powered portfolio risk analysis assistant.
 */
public class AssistantService implements AssistantPort {

    private static final String SYSTEM_PROMPT = """
            You are Probity Assistant, a portfolio risk analyst embedded in the Probity platform.

            RULES — follow strictly:
            - ALWAYS call the appropriate tool before answering any question that involves numbers or data.
            - NEVER invent, estimate, or guess portfolio values, prices, VaR, or simulation results.
            - If a tool returns no data, say so clearly in one sentence.
            - Keep answers to 2-4 sentences unless the user explicitly asks for detail.
            - Format numbers: currency with 2 decimal places (e.g. $12,345.67), percentages with 1 decimal (e.g. 4.2%).
            - Only answer questions about: portfolios, risk metrics, Monte Carlo simulations, and market data.
            - For anything outside that scope, respond: "I can only help with portfolio and risk analysis."
            - Do not repeat the user's question back to them.
            - Do not add unsolicited disclaimers or caveats unless the data genuinely warrants it.
            """;

    private final ChatClient chatClient;

    /**
     * Configures the AI advisor with memory persistence and portfolio tools.
     * Sets up three-step advisor configuration: memory rules, global registration, and per-request conversation ID.
     */
    public AssistantService(ChatClient.Builder builder, PortfolioTools portfolioTools,
                            RedisChatMemoryRepository chatMemoryRepository,
                            @Value("${probity.assistant.chat-memory.max-messages:20}") int maxChatMemoryMessages) {

        // 1. Configure the memory rules and storage ONCE globally with Redis persistence
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxChatMemoryMessages)
                .build();

        // 2. Register the advisor globally to the ChatClient
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(portfolioTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * Processes a user message through the AI assistant with portfolio tool access.
     * Uses conversation ID to maintain per-user chat history in Redis.
     */
    @Override
    public String chat(UUID userId, String message) {
        log.debug("Assistant chat userId={}", userId);

        return chatClient.prompt()
                .user(message)
                // 3. Dynamically tell the advisor WHICH user's memory bucket to read/write for this specific request
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId.toString()))
                .call()
                .content();
    }
}