package me.veselin.probity.settings;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.BaseAuthIntegrationTest;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class UserSettingsIntegrationTest extends BaseAuthIntegrationTest {

    // ── GET /users/me ─────────────────────────────────────────────────────────

    @Test
    void getProfile_authenticated_returnsProfile() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.firstName").value(ADMIN_FIRST_NAME))
                .andExpect(jsonPath("$.lastName").value(ADMIN_LAST_NAME))
                .andExpect(jsonPath("$.memberSince").exists());
    }

    @Test
    void getProfile_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Users.ME))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /users/me ───────────────────────────────────────────────────────

    @Test
    void updateProfile_validData_returnsUpdatedProfile() throws Exception {
        AuthResult auth = registerAndLogin("updateuser", "update@probity.test", "Password123!");

        mockMvc.perform(withCsrf(patch(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Updated\", \"lastName\": \"Name\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Name"));
    }

    @Test
    void updateProfile_missingFirstName_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(patch(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\": \"Name\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.firstName").exists());
    }

    @Test
    void updateProfile_missingLastName_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(patch(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Updated\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.lastName").exists());
    }

    @Test
    void updateProfile_emptyFirstName_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(patch(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"\", \"lastName\": \"Name\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.firstName").exists());
    }

    @Test
    void updateProfile_firstNameTooLong_returns400() throws Exception {
        AuthResult auth = login();
        String longName = "a".repeat(101);

        mockMvc.perform(withCsrf(patch(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"firstName": "%s", "lastName": "Name"}
                                """, longName))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.firstName").exists());
    }

    @Test
    void updateProfile_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(patch(ApiRoutes.Users.ME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Updated\", \"lastName\": \"Name\"}")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(patch(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Updated\", \"lastName\": \"Name\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_invalidJson_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(patch(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid: json}")))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /users/me/password ─────────────────────────────────────────────────

    @Test
    void changePassword_validData_returns204() throws Exception {
        AuthResult auth = registerAndLogin("pwuser", "pw@probity.test", "Password123!");

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PASSWORD)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Password123!", "newPassword": "NewPassword456!", "confirmPassword": "NewPassword456!"}
                                """)))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns401() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PASSWORD)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "WrongPassword123!", "newPassword": "NewPassword456!", "confirmPassword": "NewPassword456!"}
                                """)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_missingCurrentPassword_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PASSWORD)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "NewPassword456!", "confirmPassword": "NewPassword456!"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.currentPassword").exists());
    }

    @Test
    void changePassword_missingNewPassword_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PASSWORD)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Password123!", "confirmPassword": "NewPassword456!"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    @Test
    void changePassword_missingConfirmPassword_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PASSWORD)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Password123!", "newPassword": "NewPassword456!"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.confirmPassword").exists());
    }

    @Test
    void changePassword_passwordsDoNotMatch_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PASSWORD)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Password123!", "newPassword": "NewPassword456!", "confirmPassword": "DifferentPassword789!"}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_newPasswordTooShort_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PASSWORD)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Password123!", "newPassword": "Short1!", "confirmPassword": "Short1!"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    @Test
    void changePassword_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Password123!", "newPassword": "NewPassword456!", "confirmPassword": "NewPassword456!"}
                                """)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(put(ApiRoutes.Users.PASSWORD)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Password123!", "newPassword": "NewPassword456!", "confirmPassword": "NewPassword456!"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── GET /users/me/preferences ─────────────────────────────────────────────

    @Test
    void getPreferences_authenticated_returnsPreferences() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Users.PREFERENCES)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency").exists())
                .andExpect(jsonPath("$.defaultConfidenceLevel").exists())
                .andExpect(jsonPath("$.defaultTimeHorizon").exists());
    }

    @Test
    void getPreferences_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Users.PREFERENCES))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /users/me/preferences ───────────────────────────────────────────────

    @Test
    void updatePreferences_validData_returnsUpdatedPreferences() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PREFERENCES)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "EUR", "defaultConfidenceLevel": 90, "defaultTimeHorizon": "1m"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency").value("EUR"))
                .andExpect(jsonPath("$.defaultConfidenceLevel").value(90))
                .andExpect(jsonPath("$.defaultTimeHorizon").value("1m"));
    }

    @Test
    void updatePreferences_missingCurrency_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PREFERENCES)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultConfidenceLevel": 90, "defaultTimeHorizon": "60d"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.defaultCurrency").exists());
    }

    @Test
    void updatePreferences_missingConfidenceLevel_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PREFERENCES)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "EUR", "defaultTimeHorizon": "60d"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.defaultConfidenceLevel").exists());
    }

    @Test
    void updatePreferences_missingTimeHorizon_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PREFERENCES)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "EUR", "defaultConfidenceLevel": 90}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.defaultTimeHorizon").exists());
    }

    @Test
    void updatePreferences_emptyCurrency_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PREFERENCES)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "", "defaultConfidenceLevel": 90, "defaultTimeHorizon": "1m"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.defaultCurrency").exists());
    }

    @Test
    void updatePreferences_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(put(ApiRoutes.Users.PREFERENCES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "EUR", "defaultConfidenceLevel": 90, "defaultTimeHorizon": "60d"}
                                """)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePreferences_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(put(ApiRoutes.Users.PREFERENCES)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "EUR", "defaultConfidenceLevel": 90, "defaultTimeHorizon": "60d"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── GET /users/me/sessions ────────────────────────────────────────────────

    @Test
    void getSessions_authenticated_returnsSessions() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(get(ApiRoutes.Users.SESSIONS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getSessions_noAuth_returns401() throws Exception {
        mockMvc.perform(get(ApiRoutes.Users.SESSIONS))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /users/me/sessions/{sessionId} ─────────────────────────────────

    @Test
    void revokeSession_validSessionId_returns204() throws Exception {
        AuthResult auth = login();

        // First get sessions to find a valid session ID
        String response = mockMvc.perform(get(ApiRoutes.Users.SESSIONS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var sessions = objectMapper.readTree(response);
        if (!sessions.isEmpty()) {
            String sessionId = sessions.get(0).get("sessionId").asText();

            mockMvc.perform(withCsrf(delete(ApiRoutes.Users.SESSION, sessionId)
                            .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void revokeSession_unknownSessionId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(delete(ApiRoutes.Users.SESSION, "00000000-0000-0000-0000-000000000000")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeSession_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(delete(ApiRoutes.Users.SESSION, "some-session-id")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeSession_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(delete(ApiRoutes.Users.SESSION, "some-session-id")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeSession_invalidSessionIdFormat_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(delete("/api/v1/users/me/sessions/invalid-uuid-format")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /users/me ───────────────────────────────────────────────────────

    @Test
    void deleteAccount_authenticated_returns204() throws Exception {
        AuthResult auth = registerAndLogin("deleteuser", "delete@probity.test", "DeletePassword123!");

        mockMvc.perform(withCsrf(delete(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAccount_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(delete(ApiRoutes.Users.ME)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(delete(ApiRoutes.Users.ME)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isForbidden());
    }
}
