package me.veselin.probity.auth.port;

/**
 * Inbound port for email verification workflows.
 */
public interface EmailVerificationPort {
    /**
     * Sends a verification email to the specified address.
     */
    void sendVerificationEmail(String email);

    /**
     * Verifies a user's email address using a token sent via email.
     */
    void verify(String token);

    /**
     * Resends the verification email for an unverified account.
     */
    void resendVerification(String email);
}
