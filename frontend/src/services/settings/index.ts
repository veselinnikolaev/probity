import api from '@/services/api';
import {ProfileResponse, PreferencesResponse, SessionResponse, UpdatePreferencesRequest, UpdateProfileRequest, ChangePasswordRequest} from '@/types/settings'

// ─── Service ──────────────────────────────────────────────────────────────────

export const settingsService = {
    // Profile
    getProfile(): Promise<ProfileResponse> {
        return api.get<ProfileResponse>('/users/me').then((r) => r.data);
    },

    updateProfile(data: UpdateProfileRequest): Promise<ProfileResponse> {
        return api.patch<ProfileResponse>('/users/me', data).then((r) => r.data);
    },

    // Password
    changePassword(data: ChangePasswordRequest): Promise<void> {
        return api.put('/users/me/password', data).then(() => undefined);
    },

    // Preferences
    getPreferences(): Promise<PreferencesResponse> {
        return api.get<PreferencesResponse>('/users/me/preferences').then((r) => r.data);
    },

    updatePreferences(data: UpdatePreferencesRequest): Promise<PreferencesResponse> {
        return api
            .put<PreferencesResponse>('/users/me/preferences', data)
            .then((r) => r.data);
    },

    // Sessions
    getSessions(): Promise<SessionResponse[]> {
        return api.get<SessionResponse[]>('/users/me/sessions').then((r) => r.data);
    },

    revokeSession(sessionId: string): Promise<void> {
        return api
            .delete(`/users/me/sessions/${sessionId}`)
            .then(() => undefined);
    },

    // Account
    deleteAccount(): Promise<void> {
        return api.delete('/users/me').then(() => undefined);
    },
};
