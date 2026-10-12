package me.veselin.probity.portfolio.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.veselin.probity.common.audit.BaseEntitySoftDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for Portfolio.
 * This is the persistence layer representation of the domain Portfolio.
 */
@Entity
@Table(
        name = "portfolios",
        indexes = @Index(name = "idx_portfolio_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted = false")
public class PortfolioJpaEntity extends BaseEntitySoftDelete {

    @Column(nullable = false)
    private String name;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(length = 500)
    private String description;

    @OneToMany(
            mappedBy = "portfolio",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private final List<PortfolioPositionJpaEntity> positions = new ArrayList<>();

    public PortfolioJpaEntity(String name, UUID userId, String description) {
        this.name = name;
        this.userId = userId;
        this.description = description;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void addPosition(PortfolioPositionJpaEntity position) {
        this.positions.add(position);
    }

    public void removePosition(PortfolioPositionJpaEntity position) {
        this.positions.remove(position);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortfolioJpaEntity that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
