package me.veselin.probity.auth.repository;

import java.util.Optional;
import java.util.UUID;
import me.veselin.probity.auth.domain.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
  Optional<UserPreferences> findByUserId(UUID userId);
}
