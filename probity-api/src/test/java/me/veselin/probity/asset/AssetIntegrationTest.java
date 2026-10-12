package me.veselin.probity.asset;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.RateLimitTestConfig;
import me.veselin.probity.auth.BaseAuthIntegrationTest;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;


import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(RateLimitTestConfig.class)
public class AssetIntegrationTest extends BaseAuthIntegrationTest {

    // ── GET /assets/search ─────────────────────────────────────────────────────

    @Test
    void search_withValidTicker_returnsResults() throws Exception {
        AuthResult authResult = login();

        mockMvc.perform(get(ApiRoutes.Assets.SEARCH)
                        .param("q", "AAPL")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), authResult.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].ticker").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void search_withBlankQuery_returnsEmptyArray() throws Exception {
        AuthResult authResult = login();

        mockMvc.perform(get(ApiRoutes.Assets.SEARCH)
                        .param("q", "")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), authResult.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void search_withoutQuery_returnsEmptyArray() throws Exception {
        AuthResult authResult = login();

        mockMvc.perform(get(ApiRoutes.Assets.SEARCH)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), authResult.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void search_resultContainsRequiredFields() throws Exception {
        AuthResult authResult = login();

        mockMvc.perform(get(ApiRoutes.Assets.SEARCH)
                        .param("q", "GOOGL")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), authResult.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").isString())
                .andExpect(jsonPath("$[0].name").isString())
                .andExpect(jsonPath("$[0].sector").exists())
                .andExpect(jsonPath("$[0].type").exists());
    }
}
