package me.veselin.probity.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import me.veselin.probity.BaseIntegrationTest;
import me.veselin.probity.RateLimitTestConfig;
import me.veselin.probity.auth.dto.RegisterCommand;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.auth.port.AuthCommandPort;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.repository.UserRepository;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.common.exception.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Import(RateLimitTestConfig.class)
public abstract class BaseAuthIntegrationTest extends BaseIntegrationTest {
    @Autowired
    AuthCommandPort authCommandPort;
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;

    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_EMAIL = "admin@probity.test";
    protected static final String ADMIN_PASSWORD = "Password123!";
    protected static final String ADMIN_FIRST_NAME = "Admin";
    protected static final String ADMIN_LAST_NAME = "User";

    protected AuthResult login() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "%s", "password": "%s"}
                                """, ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String username = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("username").asText();
        String role = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("role").asText();
        String accessToken = getCookieValue(result, Token.ACCESS.getCookieName());
        String refreshToken = getCookieValue(result, Token.REFRESH.getCookieName());

        return new AuthResult(username, role, accessToken, refreshToken);
    }

    protected AuthResult registerAndLogin(String username, String email, String password) throws Exception {
        try {
            authCommandPort.register(new RegisterCommand("Test", "User", username, email, password));
        } catch (ConflictException ignored) {
        }

        userRepository.findByUsername(username).ifPresent(user -> {
            user.verify();
            userRepository.save(user);
        });

        MvcResult result = mockMvc.perform(post(ApiRoutes.Auth.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"identifier": "%s", "password": "%s"}
                                """, username, password)))
                .andExpect(status().isOk())
                .andReturn();

        String role = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("role").asText();
        String accessToken = getCookieValue(result, Token.ACCESS.getCookieName());
        String refreshToken = getCookieValue(result, Token.REFRESH.getCookieName());

        return new AuthResult(username, role, accessToken, refreshToken);
    }

    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get(ApiRoutes.Auth.CSRF))
                .andExpect(status().isOk())
                .andReturn();

        String csrfToken = Arrays.stream(csrfResult.getResponse().getCookies())
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No XSRF-TOKEN cookie returned"));

        return builder
                .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                .header("X-XSRF-TOKEN", csrfToken);
    }

    private String getCookieValue(MvcResult result, String name) {
        return Arrays.stream(result.getResponse().getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void afterSetUp() {
        try {
            authCommandPort.register(new RegisterCommand("Admin", "User", ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD));
        } catch (ConflictException ignored) {
        }
        
        userRepository.findByUsername(ADMIN_USERNAME).ifPresent(user -> {
            user.verify();
            userRepository.save(user);
        });
    }
}