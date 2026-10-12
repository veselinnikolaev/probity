package me.veselin.probity.portfolio.repository;

import me.veselin.probity.portfolio.domain.Asset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findByTicker(String ticker);

    @Query("""
        SELECT a FROM Asset a
        WHERE UPPER(a.ticker) LIKE CONCAT(:q, '%')
           OR UPPER(a.name)   LIKE CONCAT('%', :q, '%')
        ORDER BY
            CASE WHEN UPPER(a.ticker) = :q THEN 0
                 WHEN UPPER(a.ticker) LIKE CONCAT(:q, '%') THEN 1
                 ELSE 2 END,
            a.ticker
        """)
    List<Asset> searchByQuery(@Param("q") String query, Pageable pageable);
}
