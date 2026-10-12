package me.veselin.probity.bff.security.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.bff.security.filter.idempotency.IdempotencyFilter;
import me.veselin.probity.bff.security.filter.jwt.JwtAuthenticationFilter;
import me.veselin.probity.common.util.ApiRoutes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
/**
 * Spring Security configuration for JWT-based authentication and CSRF protection.
 */
public class SecurityConfiguration {
    @Value("${probity.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${probity.cookie.same-site}")
    private String sameSite;

    @Value("${probity.cookie.secure}")
    private Boolean secure;

    @Value("${probity.jwt.secret}")
    private String jwtSecret;

    private final JwtAuthenticationFilter jwtFilter;
    private final IdempotencyFilter idempotencyFilter;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @PostConstruct
    public void validateConfiguration() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret must be configured via JWT_SECRET environment variable. " +
                            "Application cannot start without a valid JWT secret for security reasons."
            );
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
                .headers(headers -> headers.contentTypeOptions(Customizer.withDefaults()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfRequestHandler())
                        .ignoringRequestMatchers(
                                ApiRoutes.Auth.LOGIN,
                                ApiRoutes.Auth.REGISTER,
                                ApiRoutes.Auth.VERIFY,
                                ApiRoutes.Auth.RESEND_VERIFICATION
                        )
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                ApiRoutes.Auth.LOGIN,
                                ApiRoutes.Auth.REGISTER,
                                ApiRoutes.Auth.REFRESH,
                                ApiRoutes.Auth.LOGOUT,
                                ApiRoutes.Auth.CSRF,
                                ApiRoutes.Auth.VERIFY,
                                ApiRoutes.Auth.RESEND_VERIFICATION,
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/error",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs",
                                "/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint));

        return http.build();
    }

    @Bean
    public CsrfTokenRequestAttributeHandler csrfRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null); // disables deferred loading
        return handler;
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite(sameSite)
                .secure(secure)
                .httpOnly(false)
                .maxAge(3600L));
        return csrfRepo;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "If-None-Match",
                "If-Modified-Since",
                "X-XSRF-TOKEN",
                "Idempotency-Key"
        ));
        config.setExposedHeaders(List.of(
                "ETag",
                "Last-Modified",
                "X-RateLimit-Remaining",
                "X-Cache"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
