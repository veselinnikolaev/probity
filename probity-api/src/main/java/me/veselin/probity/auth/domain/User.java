package me.veselin.probity.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.veselin.probity.auth.enumeration.UserStatus;
import me.veselin.probity.common.audit.BaseEntitySoftDelete;
import me.veselin.probity.auth.enumeration.Role;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

/**
 * Aggregate root for the auth context representing an application identity.
 * Preserves credential ownership and role assignment invariants.
 */
@Entity
@Table(
        name = "users",
        indexes = @Index(name = "idx_users_email", columnList = "email")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted = false")
public class User extends BaseEntitySoftDelete implements Serializable {

    @Column(name = "username", length = 100, nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private UserStatus status;

    public static User create(String firstName, String lastName,
                              String username, String email, String hashedPassword) {
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(hashedPassword, "hashedPassword must not be null");

        User user = new User();
        user.firstName = firstName.trim();
        user.lastName = lastName.trim();
        user.username = username.trim();
        user.email = email.toLowerCase().trim();
        user.password = hashedPassword;
        user.role = Role.USER;
        user.status = UserStatus.PENDING_VERIFICATION;
        return user;
    }

    public void changePassword(String newHashedPassword) {
        this.password = Objects.requireNonNull(newHashedPassword, "hashedPassword must not be null");
    }

    public void updateProfile(String firstName, String lastName) {
        this.firstName = Objects.requireNonNull(firstName, "firstName must not be null").trim();
        this.lastName = Objects.requireNonNull(lastName, "lastName must not be null").trim();
    }

    public void promoteToAdmin() {
        this.role = Role.ADMIN;
    }

    public void verify() {
        this.status = UserStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "User{id=%s, firstName=%s, lastName=%s, role=%s}".formatted(getId(), firstName, lastName, role);
    }
}