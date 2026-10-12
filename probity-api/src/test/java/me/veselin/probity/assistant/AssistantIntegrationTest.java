package me.veselin.probity.assistant;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.RateLimitTestConfig;
import me.veselin.probity.auth.BaseAuthIntegrationTest;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(RateLimitTestConfig.class)
public class AssistantIntegrationTest extends BaseAuthIntegrationTest {

    // ── POST /assistant/chat ───────────────────────────────────────────────────

    @Test
    void chat_missingMessage_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.message").exists());
    }

    @Test
    void chat_emptyMessage_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": ""}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.message").exists());
    }

    @Test
    void chat_whitespaceOnlyMessage_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "   "}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.message").exists());
    }

    @Test
    void chat_messageTooLong_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "%s"}
                                """.formatted("a".repeat(1001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.message").exists());
    }

    @Test
    void chat_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "What is my portfolio risk?"}
                                """)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chat_invalidJson_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid: json}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_missingContentType_returns415() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .content("""
                                {"message": "What is my portfolio risk?"}
                                """)))
                .andExpect(status().isUnsupportedMediaType());
    }

    /* Require valid Antropic API key
    @Test
    void chat_messageExactlyMaxLength_returns200() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "%s"}
                                """.formatted("a".repeat(1000)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").isString());
    }

    @Test
    void chat_validMessage_returns200WithResponse() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "What is my portfolio risk?"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").isString())
                .andExpect(jsonPath("$.response").isNotEmpty());
    }

    @Test
    void chat_specialCharactersInMessage_returns200() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "What about $AAPL and % returns?"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").isString());
    }

    @Test
    void chat_multilineMessage_returns200() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Assistant.CHAT)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Line 1\\nLine 2\\nLine 3"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").isString());
    }*/
}
