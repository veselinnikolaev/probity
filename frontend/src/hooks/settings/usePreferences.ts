import { useState, useEffect } from 'react';
import { settingsService } from '@/services/settings';
import { PreferencesResponse, UpdatePreferencesRequest } from '@/types/settings';
import { AsyncState, createLoadingState, createSuccessState, createErrorState } from '@/types/shared';
import { useMutation } from '@/hooks/useMutation';

export interface UsePreferencesReturn {
  preferences: AsyncState<PreferencesResponse>;
  updatePreferencesMutation: ReturnType<typeof useMutation<PreferencesResponse, UpdatePreferencesRequest>>;
}

export function usePreferences(): UsePreferencesReturn {
  const [preferences, setPreferences] = useState<AsyncState<PreferencesResponse>>(createLoadingState<PreferencesResponse>());

  // ─── Initial data fetch ─────────────────────────────────────────────────
  useEffect(() => {
    settingsService
      .getPreferences()
      .then((data) => setPreferences(createSuccessState(data)))
      .catch((err) => setPreferences(createErrorState(extractError(err))));
  }, []);

  // ─── Mutations ────────────────────────────────────────────────────────────
  const updatePreferencesMutation = useMutation<PreferencesResponse, UpdatePreferencesRequest>(async (data) => {
    const updated = await settingsService.updatePreferences(data);
    setPreferences(createSuccessState(updated));
    return updated;
  });

  return {
    preferences,
    updatePreferencesMutation,
  };
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function extractError(err: unknown): string {
  if (err instanceof Error) return err.message;
  return 'An unexpected error occurred';
}
