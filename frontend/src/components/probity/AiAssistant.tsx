import React, { useState, useRef, useEffect, useCallback } from 'react';
import { MessageCircle, X, Sparkles } from 'lucide-react';
import styles from './ai-assistant.module.css';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

interface ErrorBanner {
  type: 'warning' | 'error';
  message: string;
}

const SUGGESTION_CHIPS = [
  "What's my portfolio VaR?",
  'Show correlation matrix',
  'Summarize my risk exposure',
];

const AiAssistant: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorBanner, setErrorBanner] = useState<ErrorBanner | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  const adjustTextareaHeight = useCallback(() => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = 'auto';
      const newHeight = Math.min(textarea.scrollHeight, 72);
      textarea.style.height = `${newHeight}px`;
    }
  }, []);

  const sendMessage = useCallback(async (messageText: string) => {
    if (!messageText.trim()) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: messageText,
      timestamp: new Date(),
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsLoading(true);
    setErrorBanner(null);

    if (textareaRef.current) {
      textareaRef.current.style.height = '40px';
    }

    try {
      const response = await fetch('/api/v1/assistant/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ message: messageText }),
      });

      if (response.status === 429) {
        setErrorBanner({
          type: 'warning',
          message: "You've reached the message limit. Try again in a moment.",
        });
        setIsLoading(false);
        return;
      }

      if (!response.ok) {
        setErrorBanner({
          type: 'error',
          message: 'Failed to get response. Please try again.',
        });
        setIsLoading(false);
        return;
      }

      const data = await response.json();

      const assistantMessage: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: data.message || data.response,
        timestamp: new Date(),
      };

      setMessages(prev => [...prev, assistantMessage]);
    } catch (error) {
      setErrorBanner({
        type: 'error',
        message: 'Failed to connect. Please try again.',
      });
    } finally {
      setIsLoading(false);
    }
  }, []);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage(inputValue);
    }
  };

  const handleSuggestionClick = (suggestion: string) => {
    sendMessage(suggestion);
  };

  const formatTime = (date: Date) => {
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });
  };

  const formatMessageContent = (content: string) => {
    const numberRegex = /(\d+(?:[.,]\d+)?(?:\s*%)?)/g;
    const parts = content.split(numberRegex);

    return parts.map((part, idx) =>
      numberRegex.test(part) ?
        <span key={idx} style={{ fontFamily: 'var(--font-mono)' }}>{part}</span> :
        part
    );
  };

  const isEmpty = messages.length === 0;

  return (
    <>
      <button
        className={styles.fab}
        onClick={() => setIsOpen(!isOpen)}
        aria-label="Open AI Assistant"
      >
        {isOpen ? (
          <X size={24} />
        ) : (
          <MessageCircle size={24} />
        )}
      </button>

      {isOpen && (
        <div className={styles.drawer}>
          <div className={styles.header}>
            <h2 className={styles.title}>Probity Assistant</h2>
            <button
              className={styles.closeButton}
              onClick={() => setIsOpen(false)}
              aria-label="Close drawer"
            >
              ×
            </button>
          </div>

          <p className={styles.caption}>10 messages per minute</p>

          <div className={styles.messageContainer}>
            {isEmpty ? (
              <div className={styles.emptyState}>
                <Sparkles className={styles.sparkleIcon} />
                <h3 className={styles.emptyTitle}>Ask about your portfolio</h3>
                <p className={styles.emptyDescription}>
                  I can analyze risk, explain simulations, and pull live metrics.
                </p>
                <div className={styles.suggestionChips}>
                  {SUGGESTION_CHIPS.map(chip => (
                    <button
                      key={chip}
                      className={styles.chip}
                      onClick={() => handleSuggestionClick(chip)}
                    >
                      {chip}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <>
                {messages.map(msg => (
                  <div
                    key={msg.id}
                    className={`${styles.message} ${
                      msg.role === 'user'
                        ? styles.userMessage
                        : styles.assistantMessage
                    }`}
                  >
                    <div className={styles.messageBubble}>
                      {msg.role === 'user' ? msg.content : formatMessageContent(msg.content)}
                    </div>
                    <div className={styles.timestamp}>
                      {formatTime(msg.timestamp)}
                    </div>
                  </div>
                ))}
                {isLoading && (
                  <div className={styles.loadingContainer}>
                    <div className={styles.typingDots}>
                      <span className={styles.dot}></span>
                      <span className={styles.dot}></span>
                      <span className={styles.dot}></span>
                    </div>
                  </div>
                )}
                <div ref={messagesEndRef} />
              </>
            )}
          </div>

          {errorBanner && (
            <div
              className={`${styles.errorBanner} ${
                errorBanner.type === 'warning'
                  ? styles.warningBanner
                  : styles.dangerBanner
              }`}
            >
              {errorBanner.message}
            </div>
          )}

          <div className={styles.inputRow}>
            <textarea
              ref={textareaRef}
              className={styles.textarea}
              placeholder="Ask about your portfolio..."
              value={inputValue}
              onChange={e => {
                setInputValue(e.target.value);
                adjustTextareaHeight();
              }}
              onKeyDown={handleKeyDown}
              rows={1}
              maxLength={500}
              disabled={isLoading}
            />
            <button
              className={styles.sendButton}
              onClick={() => sendMessage(inputValue)}
              disabled={!inputValue.trim() || isLoading}
              aria-label="Send message"
            >
              Send
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default AiAssistant;
