package me.veselin.probity.auth.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.auth.enumeration.UserStatus;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.dto.LoginCommand;
import me.veselin.probity.auth.dto.RegisterCommand;
import me.veselin.probity.auth.dto.AuthResult;
import me.veselin.probity.auth.dto.UserRegisteredEvent;
import me.veselin.probity.auth.exception.UnauthorizedException;
import me.veselin.probity.auth.jwt.JwtService;
import me.veselin.probity.auth.port.AuthCommandPort;
import me.veselin.probity.auth.repository.UserRepository;
import me.veselin.probity.common.exception.ConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * Implements authentication state transitions for login, registration,
 * token refresh rotation, and logout invalidation.
 */
public class UserCommandService implements AuthCommandPort {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Authenticates a user and issues a fresh access/refresh token pair.
     * Uses constant-time password verification to prevent username-enumeration timing attacks.
     * Always performs bcrypt comparison regardless of user existence to maintain uniform execution time.
     */
    public AuthResult login(LoginCommand request) {
        User user = userRepository.findByUsernameOrEmail(request.identifier(), request.identifier())
                .orElse(null);


        // Constant-time verification: always run bcrypt even if user not found
        // This prevents timing attacks that could reveal whether a username exists
        String hashToCheck = user != null ? user.getPassword() : "$2a$10$dummyhashtopreventtimingattack00000000000000000000000";
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !passwordMatches) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION){
            throw new UnauthorizedException("User is not verified");
        }

        String username = user.getUsername();
        String role = user.getRole().name();
        String accessToken = jwtService.generateAccessJwt(
                username,
                Map.of("role", role)
        );
        String refreshToken = jwtService.generateRefreshJwt(
                username,
                Map.of("role", role)
        );

        jwtService.saveRefreshToken(refreshToken);
        return new AuthResult(username, role, accessToken, refreshToken);
    }

    /**
     * Creates a new user account after enforcing unique username and email constraints.
     * Triggers email verification process.
     */
    public void register(RegisterCommand request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = User.create(request.firstName(), request.lastName(),
                request.username(), request.email(), hashedPassword);
        userRepository.save(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getEmail()));
    }

    /**
     * Invalidates provided tokens so the current session cannot be reused.
     */
    public void logout(String accessToken, String refreshToken) {
        if (accessToken == null && refreshToken == null) {
            throw new UnauthorizedException("No tokens provided");
        }

        if (refreshToken != null) {
            Claims claims = jwtService.extractClaims(refreshToken); // throws if invalid
            String stored = jwtService.getRefreshTokenByJti(claims.getId());
            if (stored == null) {
                throw new UnauthorizedException("Invalid refresh token");
            }
            jwtService.deleteRefreshTokenByJti(claims.getId());
        }

        if (accessToken != null) {
            jwtService.blacklistToken(accessToken); // throws if invalid/malformed
        }
    }

    /**
     * Rotates a refresh token and returns a new token pair for the same principal.
     */
    public AuthResult refresh(String incomingRefreshToken) {
        Claims claims = jwtService.extractClaims(incomingRefreshToken);
        String username = claims.getSubject();

        String stored = jwtService.getRefreshTokenByJti(claims.getId());

        if (stored == null || !stored.equals(incomingRefreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (!jwtService.isTokenValid(incomingRefreshToken, username)) {
            throw new UnauthorizedException("Refresh token expired");
        }

        jwtService.deleteRefreshTokenByJti(claims.getId()); // ← delete old

        String role = claims.get("role", String.class);
        String newAccessToken = jwtService.generateAccessJwt(username, Map.of("role", role));
        String newRefreshToken = jwtService.generateRefreshJwt(username, Map.of("role", role));
        jwtService.saveRefreshToken(newRefreshToken);

        return new AuthResult(username, role, newAccessToken, newRefreshToken);
    }
}
