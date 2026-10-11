package me.veselin.probity.portfolio.service.portfolio;

import jakarta.servlet.http.Cookie;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.portfolio.BasePortfolioIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class PortfolioCommandIntegrationTest extends BasePortfolioIntegrationTest {

    // ── POST /portfolios ─────────────────────────────────────────────────────

    @Test
    void createPortfolio_validName_returns201WithLocation() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "My New Portfolio"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/portfolios/")))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("My New Portfolio"));
    }

    @Test
    void createPortfolio_duplicateName_returns409() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-duplicate-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Duplicate Portfolio"}
                                """)))
                .andExpect(status().isCreated());

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-duplicate-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Duplicate Portfolio"}
                                """)))
                .andExpect(status().isConflict());
    }

    @Test
    void createPortfolio_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .header("Idempotency-Key", "test-key-no-auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Unauthorized Portfolio"}
                            """)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPortfolio_emptyName_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-empty-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPortfolio_missingName_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-missing-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPortfolio_invalidJson_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-invalid-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid: json}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPortfolio_missingIdempotencyKey_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Test Portfolio"}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPortfolio_invalidIdempotencyKeyFormat_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Test Portfolio"}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPortfolio_duplicateIdempotencyKey_returnsCachedResponse() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-duplicate-idempotency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Idempotent Portfolio"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Idempotent Portfolio"));

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-duplicate-idempotency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Different Name"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache", "Idempotent-Hit"))
                .andExpect(jsonPath("$.name").value("Idempotent Portfolio"));
    }

    @Test
    void createPortfolio_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-no-csrf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Test Portfolio"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── POST /portfolios/{id}/positions ──────────────────────────────────────

    @Test
    void addPosition_validTicker_returns201WithLocation() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-position-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": 5}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/positions/")))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.quantity").isNumber());
    }

    @Test
    void addPosition_unknownPortfolioId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-position-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": 5}
                                """)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addPosition_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .header("Idempotency-Key", "test-key-no-auth-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ticker": "AAPL", "quantity": 5}
                            """)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addPosition_missingTicker_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-missing-ticker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 5}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addPosition_emptyTicker_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-empty-ticker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "", "quantity": 5}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addPosition_missingQuantity_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-missing-quantity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL"}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addPosition_negativeQuantity_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-negative-quantity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": -5}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addPosition_zeroQuantity_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-zero-quantity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": 0}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addPosition_invalidQuantityType_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-invalid-quantity-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": "five"}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addPosition_missingIdempotencyKey_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": 5}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addPosition_duplicateIdempotencyKey_returnsCachedResponse() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-duplicate-position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "MSFT", "quantity": 10}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("MSFT"));

        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-duplicate-position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "TSLA", "quantity": 20}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache", "Idempotent-Hit"))
                .andExpect(jsonPath("$.ticker").value("MSFT"));
    }

    @Test
    void addPosition_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(post(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .header("Idempotency-Key", "test-key-no-csrf-position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": 5}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── PUT /portfolios/{id}/positions/{positionId} ──────────────────────────

    @Test
    void updatePosition_validQuantity_returns204() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 20}
                                """)))
                .andExpect(status().isNoContent());
    }

    @Test
    void updatePosition_unknownPortfolioId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, UUID.randomUUID(), positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 20}
                                """)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePosition_unknownPositionId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, portfolioId, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 20}
                                """)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePosition_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"quantity": 20}
                            """)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePosition_missingQuantity_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePosition_negativeQuantity_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": -10}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePosition_zeroQuantity_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 0}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePosition_invalidQuantityType_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": "twenty"}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePosition_invalidJson_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid: json}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePosition_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(put(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 20}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /portfolios/{id}/positions/{positionId} ───────────────────────

    @Test
    void deletePosition_existingPosition_returns204() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(delete(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletePosition_subsequentGet_reflectsRemoval() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(delete(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isNoContent());

        // AAPL (positionId) is gone — only GOOGL should remain
        mockMvc.perform(get(ApiRoutes.Portfolios.POSITIONS, portfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticker").value("GOOGL"));
    }

    @Test
    void deletePosition_unknownPortfolioId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(delete(ApiRoutes.Portfolios.POSITION, UUID.randomUUID(), positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePosition_unknownPositionId_returns404() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(delete(ApiRoutes.Portfolios.POSITION, portfolioId, UUID.randomUUID())
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePosition_noAuth_returns401() throws Exception {
        mockMvc.perform(withCsrf(delete(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletePosition_missingCsrfToken_returns403() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(delete(ApiRoutes.Portfolios.POSITION, portfolioId, positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePosition_invalidPortfolioIdFormat_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(delete("/api/v1/portfolios/invalid-uuid/positions/" + positionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletePosition_invalidPositionIdFormat_returns400() throws Exception {
        AuthResult auth = login();

        mockMvc.perform(withCsrf(delete("/api/v1/portfolios/" + portfolioId + "/positions/invalid-uuid")
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), auth.accessToken()))))
                .andExpect(status().isBadRequest());
    }

    // ── Cross-user access tests ───────────────────────────────────────────────

    @Test
    void addPosition_toAnotherUsersPortfolio_returns404() throws Exception {
        AuthResult userAuth = registerAndLogin("user1", "user1@test.com", "Password123!");
        AuthResult otherUserAuth = registerAndLogin("user2", "user2@test.com", "Password123!");

        // Create portfolio for user1
        MvcResult portfolioResult = mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), userAuth.accessToken()))
                        .header("Idempotency-Key", "test-key-cross-user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "User1 Portfolio"}
                                """)))
                .andExpect(status().isCreated())
                .andReturn();

        String user1PortfolioId = objectMapper.readTree(portfolioResult.getResponse().getContentAsString())
                .get("id").asText();

        // Try to add position to user1's portfolio as user2
        mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, user1PortfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), otherUserAuth.accessToken()))
                        .header("Idempotency-Key", "test-key-cross-user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": 5}
                                """)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePosition_inAnotherUsersPortfolio_returns404() throws Exception {
        AuthResult userAuth = registerAndLogin("user1", "user1@test.com", "Password123!");
        AuthResult otherUserAuth = registerAndLogin("user2", "user2@test.com", "Password123!");

        // Create portfolio and position for user1
        MvcResult portfolioResult = mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), userAuth.accessToken()))
                        .header("Idempotency-Key", "test-key-cross-user-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "User1 Portfolio"}
                                """)))
                .andExpect(status().isCreated())
                .andReturn();

        String user1PortfolioId = objectMapper.readTree(portfolioResult.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult positionResult = mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, user1PortfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), userAuth.accessToken()))
                        .header("Idempotency-Key", "test-key-cross-user-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": 10}
                                """)))
                .andExpect(status().isCreated())
                .andReturn();

        String user1PositionId = objectMapper.readTree(positionResult.getResponse().getContentAsString())
                .get("id").asText();

        // Try to update position in user1's portfolio as user2
        mockMvc.perform(withCsrf(put(ApiRoutes.Portfolios.POSITION, user1PortfolioId, user1PositionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), otherUserAuth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 20}
                                """)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePosition_inAnotherUsersPortfolio_returns404() throws Exception {
        AuthResult userAuth = registerAndLogin("user1", "user1@test.com", "Password123!");
        AuthResult otherUserAuth = registerAndLogin("user2", "user2@test.com", "Password123!");

        // Create portfolio and position for user1
        MvcResult portfolioResult = mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.PORTFOLIOS)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), userAuth.accessToken()))
                        .header("Idempotency-Key", "test-key-cross-user-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "User1 Portfolio"}
                                """)))
                .andExpect(status().isCreated())
                .andReturn();

        String user1PortfolioId = objectMapper.readTree(portfolioResult.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult positionResult = mockMvc.perform(withCsrf(post(ApiRoutes.Portfolios.POSITIONS, user1PortfolioId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), userAuth.accessToken()))
                        .header("Idempotency-Key", "test-key-cross-user-6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "AAPL", "quantity": 10}
                                """)))
                .andExpect(status().isCreated())
                .andReturn();

        String user1PositionId = objectMapper.readTree(positionResult.getResponse().getContentAsString())
                .get("id").asText();

        // Try to delete position in user1's portfolio as user2
        mockMvc.perform(withCsrf(delete(ApiRoutes.Portfolios.POSITION, user1PortfolioId, user1PositionId)
                        .cookie(new Cookie(Token.ACCESS.getCookieName(), otherUserAuth.accessToken()))))
                .andExpect(status().isNotFound());
    }
}