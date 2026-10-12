package me.veselin.probity.portfolio.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.veselin.probity.common.audit.BaseEntitySoftDelete;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Child entity of the `Portfolio` aggregate representing one held instrument.
 * Guarantees strictly positive quantity and weighted-average cost consistency.
 * Pure domain class - no JPA annotations (split in Phase 1).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioPosition extends BaseEntitySoftDelete {

    private Portfolio portfolio;
    private Asset asset;
    private BigDecimal quantity;
    private BigDecimal avgBuyPrice;


    PortfolioPosition(Portfolio portfolio, Asset asset, BigDecimal quantity, BigDecimal price) {
        this.portfolio = portfolio;
        this.asset = asset;
        this.quantity = validateQuantity(quantity);
        this.avgBuyPrice = price;
    }

    /**
     * Public constructor for mapper to reconstruct from JPA without validation.
     */
    public PortfolioPosition(Portfolio portfolio, Asset asset, BigDecimal quantity, BigDecimal avgBuyPrice, UUID id, java.time.Instant createdAt, java.time.Instant updatedAt,	Long version) {
        this.portfolio = portfolio;
        this.asset = asset;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
        setId(id);
        setCreatedAt(createdAt);
        setUpdatedAt(updatedAt);
        setVersion(version);
    }


    /**
     * Adjusts position quantity by delta and recalculates weighted-average cost on buys.
     * Enforces strictly positive quantity invariant.
     */
    void adjustQuantity(BigDecimal delta, BigDecimal price) {
        BigDecimal newQty = this.quantity.add(delta);

        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            // BUY → recalc weighted average
            BigDecimal totalCost =
                    this.avgBuyPrice.multiply(this.quantity)
                            .add(price.multiply(delta));

            this.avgBuyPrice = totalCost.divide(newQty, 4, RoundingMode.HALF_UP);
        }

        this.quantity = validateQuantity(newQty);
    }


    private static BigDecimal validateQuantity(BigDecimal quantity) {
        Objects.requireNonNull(quantity, "quantity must not be null");
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Position quantity must be positive, got: " + quantity);
        }
        return quantity;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortfolioPosition that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PortfolioPosition{asset=%s, quantity=%s}".formatted(
                asset != null ? asset.getTicker() : "?", quantity);
    }
}