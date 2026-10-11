package me.veselin.probity.portfolio.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.veselin.probity.common.audit.BaseEntitySoftDelete;
import me.veselin.probity.portfolio.exception.PositionNotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the portfolio bounded context.
 * Enforces ownership and positive-position invariants for portfolio holdings.
 * Pure domain class - no JPA annotations (split in Phase 1).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio extends BaseEntitySoftDelete {

    private String name;
    private UUID userId;
    private String description;
    private final List<PortfolioPosition> positions = new ArrayList<>();


    public static Portfolio create(String name, UUID userId) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("Portfolio name must not be blank");

        Portfolio p = new Portfolio();
        p.name = name.trim();
        p.userId = userId;
        return p;
    }

    public static Portfolio create(String name, UUID userId, String description) {
        Portfolio p = create(name, userId);
        p.description = description;
        return p;
    }


    /**
     * Adds a position to the portfolio, merging with existing position if asset already held.
     * Enforces strictly positive quantity invariant.
     */
    public PortfolioPosition addPosition(Asset asset, BigDecimal quantity, BigDecimal price) {
        Objects.requireNonNull(asset, "asset must not be null");
        requirePositiveQuantity(quantity, "addPosition");

        return positions.stream()
                .filter(p -> p.getAsset().getId().equals(asset.getId()))
                .findFirst()
                .map(existing -> {
                    existing.adjustQuantity(quantity, price);
                    return existing;
                })
                .orElseGet(() -> {
                    PortfolioPosition p = new PortfolioPosition(this, asset, quantity, price);
                    positions.add(p);
                    return p;
                });
    }

    /**
     * Updates portfolio name and description, enforcing non-blank name invariant.
     */
    public void update(String newName, String description) {
        Objects.requireNonNull(newName, "name must not be null");
        if (newName.isBlank()) throw new IllegalArgumentException("Portfolio name must not be blank");
        this.name = newName.trim();
        this.description = description;
    }

    /**
     * Updates the quantity of an existing position by ID.
     * Enforces strictly positive quantity invariant.
     */
    public void updatePositionQuantity(UUID positionId, BigDecimal newQuantity, BigDecimal price) {
        requirePositiveQuantity(newQuantity, "updatePositionQuantity");

        PortfolioPosition position = positions.stream()
                .filter(p -> p.getId().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new PositionNotFoundException(
                        "Position not found: " + positionId));

        // Delta = newQuantity - current, then adjustQuantity handles validation
        BigDecimal delta = newQuantity.subtract(position.getQuantity());
        position.adjustQuantity(delta, price);
    }

    /**
     * Removes a position from the portfolio by ID.
     */
    public void removePosition(UUID positionId) {
        PortfolioPosition position = positions.stream()
                .filter(p -> p.getId().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new PositionNotFoundException(
                        "Position not found: " + positionId));

        position.softDelete();
        positions.remove(position);
    }

    /**
     * Reduces the quantity of a position for a given asset.
     * Removes the position entirely if quantity reaches zero.
     * Enforces strictly positive quantity invariant.
     */
    public void reducePosition(Asset asset, BigDecimal quantity, BigDecimal price) {
        Objects.requireNonNull(asset, "asset must not be null");
        requirePositiveQuantity(quantity, "reducePosition");

        PortfolioPosition position = findActivePosition(asset);

        BigDecimal remaining = position.getQuantity().subtract(quantity);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            // Full sell — soft-delete the position
            position.softDelete();
            positions.remove(position);
        } else {
            // Partial sell — adjust quantity (negative delta)
            position.adjustQuantity(quantity.negate(), price);
        }
    }

    /**
     * Updates the portfolio name, enforcing non-blank invariant.
     */
    public void rename(String newName) {
        Objects.requireNonNull(newName, "name must not be null");
        if (newName.isBlank()) throw new IllegalArgumentException("Portfolio name must not be blank");
        this.name = newName.trim();
    }

    @Override
    public void softDelete() {
        positions.forEach(PortfolioPosition::softDelete);
        super.softDelete();
    }


    public List<PortfolioPosition> getPositions() {
        return Collections.unmodifiableList(positions);
    }

    /**
     * Package-private accessor for mapper to add positions without validation.
     * Used by PortfolioMapper.toDomain() to reconstruct domain from JPA.
     */
    public List<PortfolioPosition> getPositionsInternal() {
        return positions;
    }


    private PortfolioPosition findActivePosition(Asset asset) {
        return positions.stream()
                .filter(p -> p.getAsset().getId().equals(asset.getId()))
                .findFirst()
                .orElseThrow(() -> new PositionNotFoundException(
                        "No active position for asset '%s' in portfolio '%s'"
                                .formatted(asset.getTicker(), this.name)));
    }

    private static void requirePositiveQuantity(BigDecimal quantity, String operation) {
        Objects.requireNonNull(quantity, "quantity must not be null");
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    operation + ": quantity must be positive, got: " + quantity);
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Portfolio that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Portfolio{name='%s', userId=%s}".formatted(name, userId);
    }
}