package me.veselin.probity.auth;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class LogoutIntegrationTest extends BaseAuthIntegrationTest {

    @Test
    void logout_withValidToken_returns204() throws Exception {
        AuthResult tokens = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.LOGOUT)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), tokens.refreshToken()))))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_blacklistedToken_returns401OnNextRequest() throws Exception {
        AuthResult tokens = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.LOGOUT)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), tokens.refreshToken()))))
                .andExpect(status().isNoContent());

        // access token still in cookie — filter should reject it if blacklisted
        // or simply expire — here we test the refresh token is invalidated
        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.REFRESH)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), tokens.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid refresh token"));
    }

    @Test
    void logout_withNoToken_returns401() throws Exception {
        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.LOGOUT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(withCsrf(post(ApiRoutes.Auth.LOGOUT)
                        .cookie(new Cookie(Token.REFRESH.getCookieName(), "notavalidtoken"))))
                .andExpect(status().isUnauthorized());
    }
}