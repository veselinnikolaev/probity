package me.veselin.probity.bff.security.filter.jwt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.auth.jwt.JwtService;
import me.veselin.probity.auth.port.AuthQueryPort;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;

class JwtAuthenticationFilterTest {
  @Test
  void rejectsRefreshTokenPresentedAsBearerAuthentication() throws Exception {
    JwtService jwtService = mock(JwtService.class);
    AuthQueryPort authQueryPort = mock(AuthQueryPort.class);
    AuthenticationEntryPoint entryPoint = mock(AuthenticationEntryPoint.class);
    JwtAuthenticationFilter filter =
        new JwtAuthenticationFilter(jwtService, authQueryPort, entryPoint);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    request.addHeader("Authorization", "Bearer refresh-token");
    when(jwtService.isTokenType("refresh-token", Token.ACCESS.getType())).thenReturn(false);

    filter.doFilter(request, response, chain);

    verify(entryPoint).commence(eq(request), eq(response), any());
    verify(jwtService, never()).extractUsername("refresh-token");
    verify(authQueryPort, never()).getByUsername(any());
  }
}
