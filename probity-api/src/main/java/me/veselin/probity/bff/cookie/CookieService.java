package me.veselin.probity.bff.cookie;

import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.common.util.ApiRoutes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
/**
 * Encapsulates HTTP cookie policies for auth token transport in the BFF layer.
 */
public class CookieService {
    @Value("${probity.jwt.access.expiration.seconds}")
    private long accessExpirationTime;
    @Value("${probity.jwt.refresh.expiration.seconds}")
    private long refreshExpirationTime;

    @Value("${probity.cookie.same-site}")
    private String sameSite;

    @Value("${probity.cookie.secure}")
    private Boolean secure;

    /**
     * Builds the secure access-token cookie returned after successful authentication.
     */
    public ResponseCookie buildAccessCookie(String token) {
        return ResponseCookie.from(Token.ACCESS.getCookieName(), token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ofSeconds(accessExpirationTime))
                .sameSite(sameSite)
                .build();
    }

    /**
     * Builds the refresh-token cookie scoped to the refresh endpoint.
     */
    public ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from(Token.REFRESH.getCookieName(), token)
                .httpOnly(true)
                .secure(secure)
                .path(ApiRoutes.Auth.REFRESH)  // only sent to /refresh — not every request
                .maxAge(Duration.ofSeconds(refreshExpirationTime))
                .sameSite(sameSite)
                .build();
    }

    /**
     * Expires the access-token cookie client-side during logout.
     */
    public ResponseCookie clearAccessCookie() {
        return ResponseCookie.from(Token.ACCESS.getCookieName(), "")
                .httpOnly(true).secure(secure).path("/").maxAge(0).build();
    }

    /**
     * Expires the refresh-token cookie client-side during logout.
     */
    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(Token.REFRESH.getCookieName(), "")
                .httpOnly(true).secure(secure).path(ApiRoutes.Auth.REFRESH).maxAge(0).build();
    }
}
