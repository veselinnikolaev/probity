package me.veselin.probity.auth.port;

import me.veselin.probity.auth.domain.User;

/**
 * Inbound query port for read-only user identity lookups.
 */
public interface AuthQueryPort {
    /**
     * Loads a user by username for authentication and principal reconstruction.
     */
    User getByUsername(String username);
}
