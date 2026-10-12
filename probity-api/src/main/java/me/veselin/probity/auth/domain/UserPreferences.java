package me.veselin.probity.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.veselin.probity.common.audit.BaseEntity;

import java.util.Objects;

/**
 * Stores per-user UI and calculation defaults.
 *
 * Lifecycle: no soft-delete, no archive. This row is owned by User — when the
 * user is soft-deleted, @SQLRestriction on User makes them unreachable, so
 * preferences become unreachable too. ON DELETE CASCADE in the FK handles
 * physical cleanup if a hard-delete job ever runs.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferences extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "default_currency", nullable = false, length = 10)
    private String defaultCurrency;

    @Column(name = "default_confidence_level", nullable = false)
    private int defaultConfidenceLevel;

    @Column(name = "default_time_horizon", nullable = false, length = 10)
    private String defaultTimeHorizon;


    public static UserPreferences createDefaults(User user) {
        Objects.requireNonNull(user, "user must not be null");

        UserPreferences prefs = new UserPreferences();
        prefs.user = user;
        prefs.defaultCurrency = "usd";
        prefs.defaultConfidenceLevel = 95;
        prefs.defaultTimeHorizon = "1d";
        return prefs;
    }


    public void update(String currency, int confidenceLevel, String timeHorizon) {
        this.defaultCurrency = Objects.requireNonNull(currency, "currency must not be null");
        this.defaultConfidenceLevel = confidenceLevel;
        this.defaultTimeHorizon = Objects.requireNonNull(timeHorizon, "timeHorizon must not be null");
    }


    /**
     * Natural key equality: two UserPreferences are the same if they belong
     * to the same user. The surrogate UUID is not used because this entity
     * has no independent existence outside its owning User.
     *
     * Null-safe: returns false if either user association is unresolved,
     * which guards against detached or unpersisted instances.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPreferences that)) return false;
        if (this.user == null || that.user == null) return false;
        if (this.user.getId() == null || that.user.getId() == null) return false;
        return this.user.getId().equals(that.user.getId());
    }

    /**
     * Consistent with equals: hash on the owning user's ID.
     * Returns 0 for unresolved associations — safe but degrades HashMap
     * performance; in practice UserPreferences is never put in a collection.
     */
    @Override
    public int hashCode() {
        if (user == null || user.getId() == null) return 0;
        return Objects.hash(user.getId());
    }

    /**
     * Deliberately excludes the user association to avoid proxy initialisation
     * outside a transaction (LazyInitializationException in log contexts).
     */
    @Override
    public String toString() {
        return "UserPreferences{userId=%s, currency=%s, confidenceLevel=%d, timeHorizon=%s}"
                .formatted(
                        user != null ? user.getId() : "unset",
                        defaultCurrency,
                        defaultConfidenceLevel,
                        defaultTimeHorizon
                );
    }
}