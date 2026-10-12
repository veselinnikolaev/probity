package me.veselin.probity.auth;

import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class LoginIntegrationTest extends BaseAuthIntegrationTest {

    @Test
    void login_withValidCredentials_returnsUsernameAndRole() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "%s", "password": "%s"}
                                """, ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.username").isString())
                .andExpect(jsonPath("$.role").exists())
                .andExpect(jsonPath("$.role").isString());
    }

    @Test
    void login_withEmailAsIdentifier_returnsUsernameAndRole() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "%s", "password": "%s"}
                                """, ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").exists());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "%s", "password": "WrongPass123!"}
                                """, ADMIN_USERNAME)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void login_withNonExistentUser_returns401() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "ghost", "password": "%s"}
                                """, ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void login_withBlankIdentifier_returns400() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "", "password": "%s"}
                                """, ADMIN_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.identifier").exists());
    }

    @Test
    void login_withBlankPassword_returns400() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "%s", "password": ""}
                                """, ADMIN_USERNAME)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void login_withMissingBody_returns400() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
    }

    @Test
    void login_withValidCredentials_setsCookies() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "%s", "password": "%s"}
                                """, ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(Token.ACCESS.getCookieName()))
                .andExpect(cookie().httpOnly(Token.ACCESS.getCookieName(), true))
                .andExpect(cookie().exists(Token.REFRESH.getCookieName()))
                .andExpect(cookie().httpOnly(Token.REFRESH.getCookieName(), true))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").exists());
    }
}
