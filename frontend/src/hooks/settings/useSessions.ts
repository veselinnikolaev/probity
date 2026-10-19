import { useState, useEffect } from 'react';
import { settingsService } from '@/services/settings';
import { SessionResponse } from '@/types/settings';
import { AsyncState, createLoadingState, createSuccessState, createErrorState } from '@/types/shared';
import { useMutation } from '@/hooks/useMutation';

export interface UseSessionsReturn {
  sessions: AsyncState<SessionResponse[]>;
  revokeSessionMutation: ReturnType<typeof useMutation<void, string>>;
}

export function useSessions(): UseSessionsReturn {
  const [sessions, setSessions] = useState<AsyncState<SessionResponse[]>>(createLoadingState<SessionResponse[]>());

  // ─── Initial data fetch ─────────────────────────────────────────────────
  useEffect(() => {
    settingsService
      .getSessions()
      .then((data) => setSessions(createSuccessState(data)))
      .catch((err) => setSessions(createErrorState(extractError(err))));
  }, []);

  // ─── Mutations ────────────────────────────────────────────────────────────
  const revokeSessionMutation = useMutation<void, string>(async (sessionId) => {
    await settingsService.revokeSession(sessionId);
    setSessions((prev) => ({
      ...prev,
      data: prev.data?.filter((s) => s.sessionId !== sessionId) ?? [],
    }));
  });

  return {
    sessions,
    revokeSessionMutation,
  };
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function extractError(err: unknown): string {
  if (err instanceof Error) return err.message;
  return 'An unexpected error occurred';
}
