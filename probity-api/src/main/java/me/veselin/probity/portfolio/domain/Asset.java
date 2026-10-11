package me.veselin.probity.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.veselin.probity.common.audit.BaseEntityWithActive;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.portfolio.enumeration.Sector;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

/**
 * Reference-data aggregate in the portfolio context that represents a tradable instrument.
 * Maintains normalized ticker identity and static classification metadata.
 */
@Entity
@Table(
        name = "assets",
        uniqueConstraints = @UniqueConstraint(name = "uq_assets_ticker", columnNames = "ticker")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Required by JPA; use factory method for creation
@SQLRestriction("active = true")
public class Asset extends BaseEntityWithActive {

    @Column(nullable = false, unique = true, length = 20)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Sector sector;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private AssetType type;


    public static Asset create(String ticker, String name, Sector sector, AssetType type) {
        Asset asset = new Asset();
        asset.ticker = Objects.requireNonNull(ticker, "ticker must not be null").toUpperCase();
        asset.name   = Objects.requireNonNull(name,   "name must not be null");
        asset.sector = Objects.requireNonNull(sector, "sector must not be null");
        asset.type   = Objects.requireNonNull(type,   "type must not be null");
        return asset;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Asset that)) return false;
        // DB-identity equality: safe after first persist
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        // Constant is intentional: consistent before and after persist
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Asset{ticker='%s', type=%s}".formatted(ticker, type);
    }
}