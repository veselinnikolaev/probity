package me.veselin.probity.bff.security.filter.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.auth.jwt.JwtService;
import me.veselin.probity.auth.port.AuthQueryPort;
import me.veselin.probity.auth.exception.UnauthorizedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthQueryPort authQueryPort;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(token);

            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                String jti = jwtService.extractJti(token);
                if (jti == null || jwtService.isBlacklisted(jti)) {
                    authenticationEntryPoint.commence(request, response,
                            new InsufficientAuthenticationException("Token has been revoked"));
                    return;
                }

                User user = authQueryPort.getByUsername(username);

                UserPrincipal principal = new UserPrincipal(
                        user.getId(),
                        user.getUsername(),
                        user.getRole().name()
                );

                if (jwtService.isTokenValid(token, username)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (JwtException | UnauthorizedException e) {
            authenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException(e.getMessage()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. try cookie first (browser clients)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (Token.ACCESS.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // 2. fall back to Authorization header (mobile, service-to-service)
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        return null;
    }
}
