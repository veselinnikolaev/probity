package me.veselin.probity.auth;

import me.veselin.probity.common.util.ApiRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RegisterIntegrationTest extends BaseAuthIntegrationTest {

    protected static final String NEW_USER_USERNAME = "newuser";
    protected static final String NEW_USER_EMAIL = "newuser@probity.test";
    protected static final String NEW_USER_PASSWORD = "Password123!";

    @Test
    void register_withValidData_returns200() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "firstName": "New",
                                    "lastName": "User",
                                    "username": "%s",
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """, NEW_USER_USERNAME, NEW_USER_EMAIL, NEW_USER_PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void register_withDuplicateUsername_returns409() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "firstName": "Other",
                                    "lastName": "User",
                                    "username": "%s",
                                    "email": "other@probity.test",
                                    "password": "%s"
                                }
                                """, ADMIN_USERNAME, NEW_USER_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username already taken"));
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "firstName": "Other",
                                    "lastName": "Admin",
                                    "username": "otheradmin",
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """, ADMIN_EMAIL, NEW_USER_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "firstName": "Test",
                                    "lastName": "User",
                                    "username": "%s",
                                    "email": "notanemail",
                                    "password": "%s"
                                }
                                """, NEW_USER_USERNAME, NEW_USER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void register_withWeakPassword_returns400() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "firstName": "Test",
                                    "lastName": "User",
                                    "username": "%s",
                                    "email": "%s",
                                    "password": "weakpassword"
                                }
                                """, NEW_USER_USERNAME, NEW_USER_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void register_withInvalidUsername_returns400() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "firstName": "Test",
                                    "lastName": "User",
                                    "username": "invalid user!",
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """, NEW_USER_EMAIL, NEW_USER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").exists());
    }

    @Test
    void register_withUsernameTooShort_returns400() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "firstName": "Test",
                                    "lastName": "User",
                                    "username": "ab",
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """, NEW_USER_EMAIL, NEW_USER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").exists());
    }

    @Test
    void register_withMissingBody_returns400() throws Exception {
        mockMvc.perform(post(ApiRoutes.Auth.REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
    }
}