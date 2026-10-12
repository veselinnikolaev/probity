package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.portfolio.domain.PortfolioPosition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Domain service for portfolio valuation operations.
 * Provides abstract portfolio valuation decoupled from market data internals.
 * This service belongs to the portfolio bounded context and can be used by other contexts
 * (like simulation) without exposing market data implementation details.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioValuationService {

    private final PortfolioPriceFetcher priceFetcher;

    /**
     * Computes the current total value of a portfolio by fetching latest prices
     * for all positions and calculating position values.
     *
     * @param positions the portfolio positions
     * @return total portfolio value
     */
    public BigDecimal computeCurrentValue(List<PortfolioPosition> positions) {
        Map<String, Double> latestPrices = priceFetcher.fetchLatestPrices(positions);
        double totalValue = priceFetcher.computeTotalValue(positions, latestPrices);
        return BigDecimal.valueOf(totalValue);
    }

    /**
     * Computes the current value of a portfolio given pre-fetched prices.
     * Useful when prices are already available to avoid redundant fetches.
     *
     * @param positions the portfolio positions
     * @param priceByTicker map of ticker to price
     * @return total portfolio value
     */
    public BigDecimal computeValueWithPrices(
            List<PortfolioPosition> positions, 
            Map<String, Double> priceByTicker) {
        double totalValue = priceFetcher.computeTotalValue(positions, priceByTicker);
        return BigDecimal.valueOf(totalValue);
    }

    /**
     * Computes the current value of a portfolio given pre-fetched prices as BigDecimal.
     * Used for async simulations with market data snapshot.
     *
     * @param positions the portfolio positions
     * @param priceByTicker map of ticker to price
     * @return total portfolio value
     */
    public BigDecimal computeValueWithBigDecimalPrices(
            List<PortfolioPosition> positions, 
            Map<String, BigDecimal> priceByTicker) {
        return positions.stream()
                .map(pos -> priceByTicker.getOrDefault(pos.getAsset().getTicker(), BigDecimal.ZERO)
                        .multiply(pos.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
