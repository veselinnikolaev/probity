package me.veselin.probity.marketdata.repository;

import me.veselin.probity.marketdata.domain.PriceBar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceBarRepository extends JpaRepository<PriceBar, UUID> {

    List<PriceBar> findByTickerAndBarDateBetweenOrderByBarDateAsc(
            String symbol, LocalDate from, LocalDate to
    );

    Optional<PriceBar> findTopByTickerOrderByBarDateDesc(String ticker);

    @Query("""
                SELECT MAX(p.barDate) FROM PriceBar p
                WHERE p.ticker = :ticker
                  AND p.barDate BETWEEN :from AND :to
            """)
    Optional<LocalDate> findMaxBarDate(
            String ticker,
            LocalDate from,
            LocalDate to);

    boolean existsByTickerAndBarDate(String symbol, LocalDate barDate);

    @Query("SELECT p.barDate FROM PriceBar p WHERE p.ticker = :ticker AND p.barDate BETWEEN :from AND :to")
    List<LocalDate> findBarDatesByTickerAndBarDateBetween(
            String ticker,
            LocalDate from,
            LocalDate to);

    @Query("""
                SELECT MIN(p.barDate)
                FROM PriceBar p
                WHERE p.ticker = :ticker
                  AND p.barDate BETWEEN :from AND :to
            """)
    Optional<LocalDate> findMinBarDate(String ticker, LocalDate from, LocalDate to);
}
