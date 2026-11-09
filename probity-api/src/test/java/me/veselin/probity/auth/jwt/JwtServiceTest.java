package me.veselin.probity.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.jsonwebtoken.Claims;
import java.util.Map;
import me.veselin.probity.auth.enumeration.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService(mock(RedisTemplate.class));
    ReflectionTestUtils.setField(
        jwtService, "secret", "a sufficiently long signing key for tests 123");
    ReflectionTestUtils.setField(jwtService, "issuer", "probity-test");
    ReflectionTestUtils.setField(jwtService, "accessExpirationTime", 900L);
    ReflectionTestUtils.setField(jwtService, "refreshExpirationTime", 3600L);
    jwtService.setKey();
  }

  @Test
  void issuedTokensHaveDistinctTokenTypesAndRequireTheConfiguredIssuer() {
    String access = jwtService.generateAccessJwt("vesko", Map.of("role", "USER"));
    String refresh = jwtService.generateRefreshJwt("vesko", Map.of("role", "USER"));

    Claims accessClaims = jwtService.extractClaims(access);
    Claims refreshClaims = jwtService.extractClaims(refresh);

    assertThat(accessClaims.getIssuer()).isEqualTo("probity-test");
    assertThat(accessClaims.get("token_type", String.class)).isEqualTo(Token.ACCESS.getType());
    assertThat(refreshClaims.get("token_type", String.class)).isEqualTo(Token.REFRESH.getType());
    assertThat(jwtService.isTokenType(access, Token.ACCESS.getType())).isTrue();
    assertThat(jwtService.isTokenType(refresh, Token.ACCESS.getType())).isFalse();
  }
}
