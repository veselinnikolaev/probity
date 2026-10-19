import { useState, useEffect } from 'react';
import { settingsService } from '@/services/settings';
import { ProfileResponse, UpdateProfileRequest, ChangePasswordRequest } from '@/types/settings';
import { AsyncState, createLoadingState, createSuccessState, createErrorState } from '@/types/shared';
import { useMutation } from '@/hooks/useMutation';

export interface UseProfileReturn {
  profile: AsyncState<ProfileResponse>;
  updateProfileMutation: ReturnType<typeof useMutation<ProfileResponse, UpdateProfileRequest>>;
  changePasswordMutation: ReturnType<typeof useMutation<void, ChangePasswordRequest>>;
  deleteAccountMutation: ReturnType<typeof useMutation<void>>;
}

export function useProfile(): UseProfileReturn {
  const [profile, setProfile] = useState<AsyncState<ProfileResponse>>(createLoadingState<ProfileResponse>());

  // ─── Initial data fetch ─────────────────────────────────────────────────
  useEffect(() => {
    settingsService
      .getProfile()
      .then((data) => setProfile(createSuccessState(data)))
      .catch((err) => setProfile(createErrorState(extractError(err))));
  }, []);

  // ─── Mutations ────────────────────────────────────────────────────────────
  const updateProfileMutation = useMutation<ProfileResponse, UpdateProfileRequest>(async (data) => {
    const updated = await settingsService.updateProfile(data);
    setProfile(createSuccessState(updated));
    return updated;
  });

  const changePasswordMutation = useMutation<void, ChangePasswordRequest>(async (data) => {
    await settingsService.changePassword(data);
  });

  const deleteAccountMutation = useMutation<void>(async () => {
    await settingsService.deleteAccount();
  });

  return {
    profile,
    updateProfileMutation,
    changePasswordMutation,
    deleteAccountMutation,
  };
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function extractError(err: unknown): string {
  if (err instanceof Error) return err.message;
  return 'An unexpected error occurred';
}
