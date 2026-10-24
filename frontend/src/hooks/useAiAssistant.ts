import { useCallback, useState } from 'react';
import api from '@/services/api';

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

export interface ErrorBanner {
  type: 'warning' | 'error';
  message: string;
}

interface UseAiAssistantReturn {
  messages: Message[];
  setMessages: React.Dispatch<React.SetStateAction<Message[]>>;
  isLoading: boolean;
  errorBanner: ErrorBanner | null;
  sendMessage: (messageText: string) => Promise<void>;
  clearError: () => void;
}

/**
 * Hook encapsulating the AI assistant chat logic.
 * Uses the authenticated axios client so CSRF and Idempotency-Key headers are
 * attached automatically by the interceptor in services/api.ts.
 */
export function useAiAssistant(): UseAiAssistantReturn {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorBanner, setErrorBanner] = useState<ErrorBanner | null>(null);

  const sendMessage = useCallback(async (messageText: string) => {
    if (!messageText.trim()) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: messageText,
      timestamp: new Date(),
    };

    setMessages(prev => [...prev, userMessage]);
    setIsLoading(true);
    setErrorBanner(null);

    try {
      const response = await api.post<{ message?: string; response?: string }>('/assistant/chat', {
        message: messageText,
      });

      const assistantMessage: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: response.data.message || response.data.response,
        timestamp: new Date(),
      };

      setMessages(prev => [...prev, assistantMessage]);
    } catch (error: any) {
      if (error?.response?.status === 429) {
        setErrorBanner({
          type: 'warning',
          message: "You've reached the message limit. Try again in a moment.",
        });
      } else if (error?.response?.status >= 400) {
        setErrorBanner({
          type: 'error',
          message: 'Failed to get response. Please try again.',
        });
      } else {
        setErrorBanner({
          type: 'error',
          message: 'Failed to connect. Please try again.',
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  const clearError = useCallback(() => {
    setErrorBanner(null);
  }, []);

  return {
    messages,
    setMessages,
    isLoading,
    errorBanner,
    sendMessage,
    clearError,
  };
}