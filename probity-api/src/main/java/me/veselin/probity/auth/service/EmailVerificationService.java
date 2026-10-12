package me.veselin.probity.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.auth.enumeration.UserStatus;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.exception.UnauthorizedException;
import me.veselin.probity.auth.port.EmailVerificationPort;
import me.veselin.probity.auth.repository.UserRepository;
import me.veselin.probity.notification.port.NotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
/**
 * Service for email verification workflows using Redis for token storage.
 */
public class EmailVerificationService implements EmailVerificationPort {
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationPort notificationPort;
    private final UserRepository userRepository;

    @Value("${probity.verify.email.prefix}")
    private String verifyEmailPrefix;

    @Value("${probity.verify.email.expiration.seconds}")
    private long verifyEmailExpirationSeconds;

    @Value("${probity.verify.user.prefix}")
    private String verifyUserPrefix;

    @Value("${probity.verify.user.max-retries}")
    private int maxTokenRetries;

    @Value("${probity.app.frontend-url}")
    private String frontendUrl;

    /**
     * Sends a verification email to the specified address.
     * Silently no-ops if the account is already active.
     */
    @Override
    public void sendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        sendVerificationEmailForUser(user);
    }

    /**
     * Verifies a user's email address using a token sent via email.
     * Invalidates the token after successful verification.
     */
    @Override
    public void verify(String token) {
        String key = verifyEmailPrefix + token;
        String userId = redisTemplate.opsForValue().get(key);

        if (userId == null) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        user.verify();
        userRepository.save(user);

        redisTemplate.delete(key);
        redisTemplate.delete(verifyUserPrefix + userId);
    }

    /**
     * Resends the verification email for an unverified account.
     * Silently no-ops if the account is already active.
     */
    @Override
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }

        deleteExistingTokenForUser(user.getId().toString());
        sendVerificationEmailForUser(user);
    }

    /**
     * Generates a unique verification token and sends it via email.
     * Uses Redis setIfAbsent to prevent token collisions with retry loop.
     * TTL ensures tokens expire automatically after configured duration.
     */
    private void sendVerificationEmailForUser(User user) {
        String userTokenKey = verifyUserPrefix + user.getId();
        String token = null;

        // Retry loop: generate random UUIDs until we find one not already in Redis
        for (int attempt = 0; attempt < maxTokenRetries; attempt++) {
            String candidate = UUID.randomUUID().toString();
            String key = verifyEmailPrefix + candidate;

            Boolean absent = redisTemplate.opsForValue()
                    .setIfAbsent(key, user.getId().toString(), verifyEmailExpirationSeconds, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(absent)) {
                token = candidate;
                break;
            }
        }

        if (token == null) {
            throw new IllegalStateException("Failed to generate unique verification token after " + maxTokenRetries + " attempts");
        }

        // Store reverse mapping: userId -> token for cleanup on resend
        redisTemplate.opsForValue()
                .set(userTokenKey, token, verifyEmailExpirationSeconds, TimeUnit.SECONDS);

        String firstName = user.getFirstName() != null ? user.getFirstName() : "there";
        notificationPort.sendEmail(user.getEmail(), "Verify your Probity account", buildHtml(firstName, token), "text/html");
    }

    /**
     * Deletes existing verification tokens for a user to prevent token reuse on resend.
     */
    private void deleteExistingTokenForUser(String userId) {
        String userTokenKey = verifyUserPrefix + userId;
        String existingToken = redisTemplate.opsForValue().get(userTokenKey);

        if (existingToken != null) {
            redisTemplate.delete(verifyEmailPrefix + existingToken);
            redisTemplate.delete(userTokenKey);
        }
    }

    private String buildHtml(String firstName, String token) {
        String link = frontendUrl + "/verify?token=" + token;
        return """
                <p>Hi %s,</p>
                <p>Click the button below to verify your Probity account. This link expires in 24 hours.</p>
                <p><a href="%s" style="background:#6366f1;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none;">Verify account</a></p>
                <p>If you didn't create an account, you can ignore this email.</p>
                """.formatted(firstName, link);
    }
}