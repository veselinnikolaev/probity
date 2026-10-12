package me.veselin.probity.assistant.exception;

/**
 * Thrown by PortfolioTools when an operation fails.
 * The message is always a safe, user-facing string — never a raw stack trace.
 * Caught in each tool method and returned as a plain string to the AI.
 */
public class AssistantToolException extends RuntimeException {

    public AssistantToolException(String safeMessage) {
        super(safeMessage);
    }

    public AssistantToolException(String safeMessage, Throwable cause) {
        super(safeMessage, cause);
    }
}