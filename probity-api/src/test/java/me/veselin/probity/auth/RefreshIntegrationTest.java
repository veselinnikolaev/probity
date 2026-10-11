package me.veselin.probity.auth;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class RefreshIntegrationTest extends BaseAuthIntegrationTest {

    @Test
    void refresh_withValidRefreshToken_returnsNewAccessToken() throws Exception {
        AuthResult tokens = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.REFRESH)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), tokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.username").isString())
                .andExpect(jsonPath("$.role").exists())
                .andExpect(jsonPath("$.role").isString())
                .andExpect(cookie().exists(Token.ACCESS.getCookieName()))
                .andExpect(cookie().exists(Token.REFRESH.getCookieName()));
    }

    @Test
    void refresh_rotatesRefreshToken() throws Exception {
        AuthResult tokens = login();

        MvcResult refreshResult = mockMvc.perform(withCsrf(post(ApiRoutes.Auth.REFRESH)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), tokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.username").isString())
                .andExpect(jsonPath("$.role").exists())
                .andExpect(jsonPath("$.role").isString())
                .andReturn();

        String newRefreshToken = Arrays.stream(refreshResult.getResponse().getCookies())
                .filter(c -> Token.REFRESH.getCookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst().orElseThrow();

        // old token rejected
        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.REFRESH)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());

        // new token works
        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.REFRESH)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), newRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.username").isString())
                .andExpect(jsonPath("$.role").exists())
                .andExpect(jsonPath("$.role").isString());
    }

    @Test
    void refresh_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.REFRESH)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), "notavalidtoken"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid token"));
    }

    @Test
    void refresh_afterLogout_returns401() throws Exception {
        AuthResult tokens = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.LOGOUT)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), tokens.refreshToken()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.REFRESH)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), tokens.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid refresh token"));
    }

    @Test
    void refresh_withMissingCookie_returns401() throws Exception {
        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.REFRESH)))
                .andExpect(status().isUnauthorized());
    }
}