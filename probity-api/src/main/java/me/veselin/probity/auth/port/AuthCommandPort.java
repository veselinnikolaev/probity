package me.veselin.probity.auth.port;

import me.veselin.probity.auth.dto.LoginCommand;
import me.veselin.probity.auth.dto.RegisterCommand;
import me.veselin.probity.auth.dto.AuthResult;

/**
 * Inbound command port for authentication workflows.
 */
public interface AuthCommandPort {
    /**
     * Authenticates credentials and returns token material for session bootstrap.
     */
    AuthResult login(LoginCommand request);

    /**
     * Registers a new user account with validated identity fields.
     */
    void register(RegisterCommand request);

    /**
     * Rotates a refresh token and issues a new token pair.
     */
    AuthResult refresh(String refreshToken);

    /**
     * Invalidates active authentication artifacts for logout.
     */
    void logout(String accessToken, String refreshToken);
}
