package me.veselin.probity.auth.service;

import lombok.RequiredArgsConstructor;
import me.veselin.probity.auth.domain.User;
import me.veselin.probity.auth.port.AuthQueryPort;
import me.veselin.probity.auth.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * Read-side access service for user lookups used by authentication filters.
 */
public class UserQueryService implements AuthQueryPort {
    private final UserRepository userRepository;

    @Cacheable(cacheNames = "user", key = "#username")
    /**
     * Loads a user by username for security principal reconstruction.
     */
    public User getByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(
                () -> new RuntimeException("User not found")
        );
    }
}
