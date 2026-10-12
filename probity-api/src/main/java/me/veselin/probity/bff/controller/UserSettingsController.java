package me.veselin.probity.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.auth.dto.PreferencesData;
import me.veselin.probity.auth.dto.ProfileData;
import me.veselin.probity.auth.dto.SessionData;
import me.veselin.probity.auth.port.UserSettingsPort;
import me.veselin.probity.bff.security.filter.jwt.UserPrincipal;
import me.veselin.probity.bff.dto.settings.*;
import me.veselin.probity.common.util.ApiRoutes;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Self-service settings endpoints. All mutations are scoped to the
 * authenticated user — no admin privilege escalation possible here.
 */
@Tag(name = "User Settings", description = "User profile and preferences management")
@RestController
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsPort settingsPort;

    /**
     * GET /users/me
     * Fetches the authenticated user's profile.
     *
     * @param principal authenticated user principal
     * @return profile response
     */
    @Operation(summary = "Get user profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping(ApiRoutes.Users.ME)
    public ResponseEntity<ProfileResponse> getProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        ProfileData data = settingsPort.getProfile(principal.id());
        return ResponseEntity.ok(mapToProfileResponse(data));
    }

    /**
     * PATCH /users/me
     * Updates the authenticated user's profile.
     *
     * @param principal authenticated user principal
     * @param request profile update request
     * @return updated profile response
     */
    @Operation(summary = "Update user profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PatchMapping(ApiRoutes.Users.ME)
    public ResponseEntity<ProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        ProfileData data = settingsPort.updateProfile(principal.id(), request.firstName(), request.lastName());
        return ResponseEntity.ok(mapToProfileResponse(data));
    }

    /**
     * PUT /users/me/password
     * Changes the authenticated user's password.
     *
     * @param principal authenticated user principal
     * @param request password change request
     * @return 204 NO CONTENT
     * @throws me.veselin.probity.auth.exception.UnauthorizedException if current password is invalid
     */
    @Operation(summary = "Change password")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping(ApiRoutes.Users.PASSWORD)
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        settingsPort.changePassword(principal.id(), request.currentPassword(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /users/me/preferences
     * Fetches the authenticated user's preferences.
     *
     * @param principal authenticated user principal
     * @return preferences response
     */
    @Operation(summary = "Get user preferences")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preferences returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping(ApiRoutes.Users.PREFERENCES)
    public ResponseEntity<PreferencesResponse> getPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        PreferencesData data = settingsPort.getPreferences(principal.id());
        return ResponseEntity.ok(mapToPreferencesResponse(data));
    }

    /**
     * PUT /users/me/preferences
     * Updates the authenticated user's preferences.
     *
     * @param principal authenticated user principal
     * @param request preferences update request
     * @return updated preferences response
     */
    @Operation(summary = "Update user preferences")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preferences updated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping(ApiRoutes.Users.PREFERENCES)
    public ResponseEntity<PreferencesResponse> updatePreferences(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        PreferencesData data = settingsPort.updatePreferences(
                principal.id(),
                request.defaultCurrency(),
                request.defaultConfidenceLevel() != null ? request.defaultConfidenceLevel() : 95,
                request.defaultTimeHorizon()
        );
        return ResponseEntity.ok(mapToPreferencesResponse(data));
    }

    /**
     * GET /users/me/sessions
     * Lists all active sessions for the authenticated user.
     *
     * @param principal authenticated user principal
     * @return list of session responses
     */
    @Operation(summary = "List active sessions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessions returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping(ApiRoutes.Users.SESSIONS)
    public ResponseEntity<List<SessionResponse>> getSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(settingsPort.getActiveSessions(principal.id())
                .stream()
                .map(this::mapToSessionResponse)
                .toList());
    }

    /**
     * DELETE /users/me/sessions/{sessionId}
     * Revokes a specific session for the authenticated user.
     *
     * @param principal authenticated user principal
     * @param sessionId session ID to revoke
     * @return 204 NO CONTENT
     */
    @Operation(summary = "Revoke session")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Session revoked"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Session not found")
    })
    @DeleteMapping(ApiRoutes.Users.SESSION)
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID sessionId) {
        settingsPort.revokeSession(principal.id(), sessionId.toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /users/me
     * Deletes the authenticated user's account and all associated data.
     *
     * @param principal authenticated user principal
     * @param accessToken access token from cookie (optional)
     * @param refreshToken refresh token from cookie (optional)
     * @return 204 NO CONTENT
     */
    @Operation(summary = "Delete account")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping(ApiRoutes.Users.ME)
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @CookieValue(name = "access_token", required = false) String accessToken,
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        settingsPort.deleteAccount(principal.id(), accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }

    private ProfileResponse mapToProfileResponse(ProfileData data) {
        return new ProfileResponse(
                data.username(),
                data.email(),
                data.firstName(),
                data.lastName(),
                data.memberSince()
        );
    }

    private PreferencesResponse mapToPreferencesResponse(PreferencesData data) {
        return new PreferencesResponse(
                data.defaultCurrency(),
                data.defaultConfidenceLevel(),
                data.defaultTimeHorizon()
        );
    }

    private SessionResponse mapToSessionResponse(SessionData data) {
        return new SessionResponse(
                data.sessionId(),
                data.deviceHint(),
                data.ipAddress(),
                data.issuedAt()
        );
    }
}