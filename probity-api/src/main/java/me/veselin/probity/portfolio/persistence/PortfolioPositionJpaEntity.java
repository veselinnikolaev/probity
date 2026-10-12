package me.veselin.probity.portfolio.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.veselin.probity.common.audit.BaseEntitySoftDelete;
import me.veselin.probity.portfolio.domain.Asset;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * JPA entity for PortfolioPosition.
 * This is the persistence layer representation of the domain PortfolioPosition.
 * Note: Asset is still JPA-coupled (not split in Phase 1), so we reference it directly.
 */
@Entity
@Table(
        name = "portfolio_positions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_position_portfolio_asset",
                columnNames = {"portfolio_id", "asset_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted = false")
public class PortfolioPositionJpaEntity extends BaseEntitySoftDelete {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false, updatable = false)
    private PortfolioJpaEntity portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, updatable = false)
    private Asset asset;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "avg_buy_price", precision = 19, scale = 4)
    private BigDecimal avgBuyPrice;

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setAvgBuyPrice(BigDecimal avgBuyPrice) {
        this.avgBuyPrice = avgBuyPrice;
    }

    public PortfolioPositionJpaEntity(PortfolioJpaEntity portfolio, Asset asset, BigDecimal quantity, BigDecimal avgBuyPrice) {
        this.portfolio = portfolio;
        this.asset = asset;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortfolioPositionJpaEntity that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
