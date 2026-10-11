package me.veselin.probity.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.domain.UserPreferences;
import me.veselin.probity.auth.exception.SessionNotFoundException;
import me.veselin.probity.auth.exception.UnauthorizedException;
import me.veselin.probity.auth.jwt.JwtService;
import me.veselin.probity.auth.port.UserSettingsPort;
import me.veselin.probity.auth.repository.UserPreferencesRepository;
import me.veselin.probity.auth.repository.UserRepository;
import me.veselin.probity.auth.dto.PreferencesData;
import me.veselin.probity.auth.dto.ProfileData;
import me.veselin.probity.auth.dto.SessionData;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles all user self-service mutations: profile, password, preferences,
 * session management, and account deletion.
 *
 * Session tracking uses Redis with the key pattern:
 *   session:{userId}:{sessionId}  →  JSON payload (device hint, IP, issued-at)
 *
 * This reuses the existing Redis infrastructure without adding a new table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService implements UserSettingsPort {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ─── Profile ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileData getProfile(UUID userId) {
        User user = requireUser(userId);
        return toProfileData(user);
    }

    @Transactional
    @CacheEvict(cacheNames = "user", key = "#userId")
    public ProfileData updateProfile(UUID userId, String firstName, String lastName) {
        User user = requireUser(userId);
        user.updateProfile(firstName, lastName);
        userRepository.save(user);
        log.info("Profile updated for user {}", userId);
        return toProfileData(user);
    }

    // ─── Password ─────────────────────────────────────────────────────────────

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }

        User user = requireUser(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user {}", userId);
    }

    // ─── Preferences ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PreferencesData getPreferences(UUID userId) {
        User user = requireUser(userId);
        UserPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> UserPreferences.createDefaults(user));
        return toPreferencesData(prefs);
    }

    @Transactional
    public PreferencesData updatePreferences(UUID userId, String defaultCurrency, int defaultConfidenceLevel, String defaultTimeHorizon) {
        User user = requireUser(userId);
        UserPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> UserPreferences.createDefaults(user));

        prefs.update(defaultCurrency, defaultConfidenceLevel, defaultTimeHorizon);
        preferencesRepository.save(prefs);
        log.info("Preferences updated for user {}", userId);
        return toPreferencesData(prefs);
    }

    // ─── Sessions ─────────────────────────────────────────────────────────────

    /**
     * Returns active sessions stored in Redis for this user.
     * Sessions are written by UserCommandService#login via recordSession().
     */
    public List<SessionData> getActiveSessions(UUID userId) {
        String pattern = sessionKeyPrefix(userId) + "*";
        Set<String> keys = jwtService.scanSessionKeys(pattern);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<SessionData> sessions = new ArrayList<>();
        for (String key : keys) {
            String payload = jwtService.getRawSessionPayload(key);
            if (payload == null) continue;
            // payload format: "sessionId|deviceHint|ipAddress|issuedAt"
            String[] parts = payload.split("\\|", 4);
            if (parts.length < 4) continue;
            sessions.add(new SessionData(parts[0], parts[1], parts[2], parts[3]));
        }
        return sessions;
    }

    public void revokeSession(UUID userId, String sessionId) {
        String key = sessionKey(userId, sessionId);
        boolean deleted = jwtService.deleteSessionKey(key);
        if (!deleted) {
            throw new SessionNotFoundException("Session not found or already expired");
        }
        // Also blacklist the refresh token associated with this session
        jwtService.deleteRefreshTokenByJti(sessionId);
        log.info("Session {} revoked for user {}", sessionId, userId);
    }

    // ─── Account Deletion ────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(cacheNames = "user", allEntries = true)
    public void deleteAccount(UUID userId, String currentAccessToken, String currentRefreshToken) {
        User user = requireUser(userId);
        user.softDelete();
        userRepository.save(user);

        // Invalidate all sessions in Redis
        Set<String> sessionKeys = jwtService.scanSessionKeys(sessionKeyPrefix(userId) + "*");
        if (sessionKeys != null) {
            sessionKeys.forEach(jwtService::deleteSessionKey);
        }

        // Invalidate current tokens
        try {
            if (currentRefreshToken != null) {
                var claims = jwtService.extractClaims(currentRefreshToken);
                jwtService.deleteRefreshTokenByJti(claims.getId());
            }
            if (currentAccessToken != null) {
                jwtService.blacklistToken(currentAccessToken);
            }
        } catch (Exception e) {
            log.warn("Token cleanup during account deletion failed for user {}: {}", userId, e.getMessage());
        }

        log.info("Account soft-deleted for user {}", userId);
    }

    // ─── Session key helpers ──────────────────────────────────────────────────

    private String sessionKey(UUID userId, String sessionId) {
        return SESSION_KEY_PREFIX + userId + ":" + sessionId;
    }

    private String sessionKeyPrefix(UUID userId) {
        return SESSION_KEY_PREFIX + userId + ":";
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    private ProfileData toProfileData(User user) {
        String memberSince = user.getCreatedAt() != null
                ? ISO_FORMATTER.format(user.getCreatedAt())
                : null;
        return new ProfileData(
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                memberSince
        );
    }

    private PreferencesData toPreferencesData(UserPreferences prefs) {
        return new PreferencesData(
                prefs.getDefaultCurrency(),
                prefs.getDefaultConfidenceLevel(),
                prefs.getDefaultTimeHorizon()
        );
    }
}