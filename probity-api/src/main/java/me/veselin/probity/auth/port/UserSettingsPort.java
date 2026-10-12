package me.veselin.probity.auth.port;

import me.veselin.probity.auth.dto.PreferencesData;
import me.veselin.probity.auth.dto.ProfileData;
import me.veselin.probity.auth.dto.SessionData;

import java.util.List;
import java.util.UUID;

public interface UserSettingsPort {
    ProfileData getProfile(UUID id);

    ProfileData updateProfile(UUID id, String firstName, String lastName);

    void changePassword(UUID id, String currentPassword, String newPassword, String confirmPassword);

    PreferencesData getPreferences(UUID id);

    PreferencesData updatePreferences(UUID id, String defaultCurrency, int defaultConfidenceLevel, String defaultTimeHorizon);

    List<SessionData> getActiveSessions(UUID id);

    void revokeSession(UUID id, String sessionId);

    void deleteAccount(UUID id, String accessToken, String refreshToken);
}
