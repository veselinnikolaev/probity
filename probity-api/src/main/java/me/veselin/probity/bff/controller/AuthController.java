package me.veselin.probity.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.auth.dto.LoginCommand;
import me.veselin.probity.auth.dto.RegisterCommand;
import me.veselin.probity.auth.exception.UnauthorizedException;
import me.veselin.probity.auth.port.EmailVerificationPort;
import me.veselin.probity.bff.cookie.CookieService;
import me.veselin.probity.bff.dto.auth.AuthResponse;
import me.veselin.probity.bff.dto.auth.LoginRequest;
import me.veselin.probity.bff.dto.auth.RegisterRequest;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.port.AuthCommandPort;
import me.veselin.probity.bff.security.rate_limit.RateLimit;
import me.veselin.probity.common.util.ApiRoutes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Authentication and session management")
@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthCommandPort authCommandPort;
    private final EmailVerificationPort emailVerificationPort;
    private final CookieService cookieService;

    /**
     * GET /auth/csrf
     * Fetches or generates a CSRF token for the session.
     *
     * @return empty response with X-XSRF-TOKEN header
     */
    @Operation(summary = "Fetch CSRF token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "CSRF token returned in X-XSRF-TOKEN header")
    })
    @GetMapping(ApiRoutes.Auth.CSRF)
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.ok().build();
    }

    /**
     * POST /auth/register
     * Registers a new user account.
     *
     * @param request registration request with username, email, and password
     * @return 201 CREATED on success
     * @throws me.veselin.probity.common.exception.ConflictException if username or email already exists
     */
    @Operation(summary = "Register a new user account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "409", description = "Username or email already taken"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping(ApiRoutes.Auth.REGISTER)
    @RateLimit(requests = 3, seconds = 60)
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for username: {}, email: {}", request.username(), request.email());
        authCommandPort.register(
                new RegisterCommand(request.firstName(), request.lastName(),
                        request.username(), request.email(), request.password())
        );
        log.info("Registration successful for username: {}", request.username());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * POST /auth/verify
     * Verifies a user's email address using a token sent via email.
     *
     * @param token verification token from the email link
     * @return 204 NO CONTENT on success
     * @throws me.veselin.probity.auth.exception.UnauthorizedException if token is invalid or expired
     */
    @Operation(summary = "Verify email address")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Email verified"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired token"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping(ApiRoutes.Auth.VERIFY)
    @RateLimit(requests = 10, seconds = 60)
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        log.info("Email verification attempt with token: {}", token.substring(0, Math.min(10, token.length())) + "...");
        emailVerificationPort.verify(token);
        log.info("Email verification successful");
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /auth/resend-verification
     * Resends the verification email for an unverified account.
     * Silently no-ops if the account is already active.
     *
     * @param email email address of the unverified account
     * @return 204 NO CONTENT on success
     * @throws me.veselin.probity.auth.exception.UnauthorizedException if no account exists for the email
     */
    @Operation(summary = "Resend verification email")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Verification email sent"),
        @ApiResponse(responseCode = "401", description = "User not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping(ApiRoutes.Auth.RESEND_VERIFICATION)
    @RateLimit(requests = 3, seconds = 300)
    public ResponseEntity<Void> resendVerification(@RequestParam String email) {
        log.info("Resend verification email request for: {}", email);
        emailVerificationPort.resendVerification(email);
        log.info("Verification email resent to: {}", email);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /auth/login
     * Authenticates a user and issues JWT tokens.
     *
     * @param request login request with identifier (username or email) and password
     * @param response HTTP response for setting auth cookies
     * @return auth response with username and role
     * @throws me.veselin.probity.auth.exception.UnauthorizedException if credentials are invalid
     */
    @Operation(summary = "Authenticate and issue JWT cookies")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "403", description = "Email not verified"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping(ApiRoutes.Auth.LOGIN)
    @RateLimit(requests = 5, seconds = 60)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        log.info("Login attempt for identifier: {}", request.identifier());
        AuthResult result = authCommandPort.login(
                new LoginCommand(request.identifier(), request.password())
        );

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.buildAccessCookie(result.accessToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.buildRefreshCookie(result.refreshToken()).toString());

        log.info("Login successful for user: {}, role: {}", result.username(), result.role());
        return ResponseEntity.ok(new AuthResponse(result.username(), result.role()));
    }

    /**
     * POST /auth/refresh
     * Refreshes access token using a valid refresh token.
     *
     * @param refreshToken refresh token from cookie
     * @param response HTTP response for setting new auth cookies
     * @return auth response with username and role
     * @throws me.veselin.probity.auth.exception.UnauthorizedException if refresh token is invalid or missing
     */
    @Operation(summary = "Refresh access token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing refresh token")
    })
    @PostMapping(ApiRoutes.Auth.REFRESH)
    public ResponseEntity<AuthResponse> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken,
                                                HttpServletResponse response) {
        log.info("Token refresh attempt");
        if (refreshToken == null) {
            log.warn("Refresh token missing");
            throw new UnauthorizedException("Refresh token is missing");
        }

        AuthResult result = authCommandPort.refresh(refreshToken);

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.buildAccessCookie(result.accessToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.buildRefreshCookie(result.refreshToken()).toString());  // rotation

        log.info("Token refresh successful for user: {}", result.username());
        return ResponseEntity.ok(new AuthResponse(result.username(), result.role()));
    }

    /**
     * POST /auth/logout
     * Logs out the user by invalidating tokens and clearing cookies.
     *
     * @param refreshToken refresh token from cookie (optional)
     * @param accessToken access token from cookie (optional)
     * @param response HTTP response for clearing auth cookies
     * @return 204 NO CONTENT
     */
    @Operation(summary = "Logout and invalidate tokens")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out")
    })
    @PostMapping(ApiRoutes.Auth.LOGOUT)
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            @CookieValue(value = "access_token", required = false) String accessToken,
            HttpServletResponse response) {
        log.info("Logout request");
        authCommandPort.logout(accessToken, refreshToken);

        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearAccessCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearRefreshCookie().toString());

        log.info("Logout successful");
        return ResponseEntity.noContent().build();
    }
}