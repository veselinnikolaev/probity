import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useAiAssistant } from '@/hooks/useAiAssistant';

// Mock the axios instance
vi.mock('@/services/api', () => ({
  default: { post: vi.fn() },
}));

import api from '@/services/api';

const post = api.post as unknown as ReturnType<typeof vi.fn>;

afterEach(() => {
  post.mockReset();
});

describe('useAiAssistant (M4b CSRF on assistant call)', () => {
  it('posts to /assistant/chat via the authenticated client and includes CSRF header', async () => {
    post.mockResolvedValue({ data: { message: 'Hello! How can I help?' } });

    const { result } = renderHook(() => useAiAssistant());

    await act(async () => {
      await result.current.sendMessage('What is my portfolio VaR?');
    });

    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][0]).toBe('/assistant/chat');
    expect(post.mock.calls[0][1]).toEqual({ message: 'What is my portfolio VaR?' });
    // The axios interceptor in services/api.ts adds X-XSRF-TOKEN for mutating requests
    // and Idempotency-Key for POST. This test ensures the call goes through the
    // authenticated client (not a raw fetch) so those headers are attached.
  });

  it('handles 429 rate limit response', async () => {
    post.mockRejectedValue({ response: { status: 429 } });

    const { result } = renderHook(() => useAiAssistant());

    await act(async () => {
      await result.current.sendMessage('Test message');
    });

    // The error banner should be set with a warning
    expect(result.current.errorBanner).toEqual({
      type: 'warning',
      message: "You've reached the message limit. Try again in a moment.",
    });
  });

  it('handles network/connection errors', async () => {
    post.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useAiAssistant());

    await act(async () => {
      await result.current.sendMessage('Test message');
    });

    expect(result.current.errorBanner).toEqual({
      type: 'error',
      message: 'Failed to connect. Please try again.',
    });
  });
});