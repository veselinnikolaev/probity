package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.util.DataUtil;
import me.veselin.probity.common.util.SeriesUtil;
import me.veselin.probity.common.util.TradingUtil;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.domain.PortfolioPosition;
import me.veselin.probity.portfolio.dto.CompositionEntryDto;
import me.veselin.probity.portfolio.dto.PortfolioSummaryDto;
import me.veselin.probity.portfolio.dto.PositionDto;
import me.veselin.probity.portfolio.dto.RiskAlertDto;
import me.veselin.probity.portfolio.dto.SparklineDto;
import me.veselin.probity.portfolio.enumeration.AssetType;
import me.veselin.probity.portfolio.enumeration.DateRange;
import me.veselin.probity.portfolio.enumeration.Sector;
import me.veselin.probity.portfolio.port.portfolio.PortfolioDashboardQueryPort;
import me.veselin.probity.portfolio.service.portfolio.PortfolioDataHelper.PortfolioTimeSeries;
import me.veselin.probity.risk.dto.AssetRiskProfile;
import me.veselin.probity.risk.port.RiskPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class PortfolioDashboardQueryService implements PortfolioDashboardQueryPort {

    private static final int VOLATILITY_WINDOW_DAYS = 20;  // Rolling volatility calculation window (trading days)
    private static final int MIN_RETURNS_FOR_METRICS = 5;
    private static final int SECTOR_CONCENTRATION_THRESHOLD = 40; // Sector weight threshold for concentration alerts (%)
    private static final int CRYPTO_EXPOSURE_THRESHOLD = 10;      // Crypto weight threshold for exposure alerts (%)

    private final PortfolioQueryService portfolioQueryService;
    private final RiskPort riskPort;
    private final PortfolioDataHelper portfolioDataHelper;

    // ── Summary ───────────────────────────────────────────────────────────────

    /**
     * Produces headline KPI metrics for a selected date range in the dashboard.
     */
    @Override
    public PortfolioSummaryDto getSummary(UUID id, String range, UUID principalId) {
        log.debug("Building summary portfolioId={} range={}", id, range);

        Portfolio portfolio = portfolioQueryService.loadPortfolioWithPositions(id, principalId);
        List<PortfolioPosition> positions = portfolio.getPositions();
        if (positions.isEmpty()) return PortfolioSummaryDto.empty();

        LocalDate to = TradingUtil.lastCompletedTradingDay();
        LocalDate from = DateRange.fromValue(range).toStartDate(to);

        PortfolioTimeSeries ts = portfolioDataHelper.buildTimeSeries(positions, from, to);
        if (ts.isEmpty()) return PortfolioSummaryDto.empty();

        List<Double> portfolioValues = ts.values();
        List<Double> dailyReturns = ts.returns();

        double currentValue = DataUtil.lastOrZero(portfolioValues);
        double prevValue = DataUtil.previousOrCurrent(portfolioValues);
        double dailyReturn = currentValue - prevValue;
        double startValue = DataUtil.firstOrZero(portfolioValues);
        double totalDeltaPct = DataUtil.safePct(startValue, currentValue - startValue);

        double vol = riskPort.annualisedVolatility(dailyReturns);
        double sharpe = riskPort.sharpeRatio(dailyReturns);
        double var95 = riskPort.var95(currentValue, dailyReturns);

        // Delta metrics — compare second half of period against first half.
        int mid = dailyReturns.size() / 2;
        double dailyReturnDeltaPct = 0.0;
        double volDelta = 0.0;
        double sharpeDelta = 0.0;
        double varDelta = 0.0;
        if (mid > 0) {
            List<Double> firstHalf = dailyReturns.subList(0, mid);
            List<Double> secondHalf = dailyReturns.subList(mid, dailyReturns.size());
            double firstHalfAvg = firstHalf.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double secondHalfAvg = secondHalf.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            dailyReturnDeltaPct = (secondHalfAvg - firstHalfAvg) * 100.0;
            volDelta = riskPort.annualisedVolatility(secondHalf) - riskPort.annualisedVolatility(firstHalf);
            sharpeDelta = riskPort.sharpeRatio(secondHalf) - riskPort.sharpeRatio(firstHalf);
            varDelta = riskPort.var95(currentValue, secondHalf) - riskPort.var95(currentValue, firstHalf);
        }

        List<Double> spark = SeriesUtil.downsample(portfolioValues, PortfolioDataHelper.SPARKLINE_POINTS);
        List<Double> returnSpark = SeriesUtil.downsample(dailyReturns, PortfolioDataHelper.SPARKLINE_POINTS);
        List<Double> volSpark = SeriesUtil.downsample(
                riskPort.rollingVolatilityFromReturns(dailyReturns, VOLATILITY_WINDOW_DAYS),
                PortfolioDataHelper.SPARKLINE_POINTS);

        // buildRollingMetric slices must be returns, not raw values — pass
        // dailyReturns instead of portfolioValues so the lambda receives
        // pre-converted percentage changes without needing toDailyReturns().
        List<Double> sharpeSpark = dailyReturns.size() >= MIN_RETURNS_FOR_METRICS
                ? portfolioDataHelper.buildRollingMetric(dailyReturns, PortfolioDataHelper.SPARKLINE_POINTS, riskPort::sharpeRatio)
                : List.of();

        List<Double> varSpark = dailyReturns.size() >= MIN_RETURNS_FOR_METRICS
                ? portfolioDataHelper.buildRollingMetric(dailyReturns, PortfolioDataHelper.SPARKLINE_POINTS, slice -> riskPort.var95(currentValue, slice))
                : List.of();

        SparklineDto sparkline = new SparklineDto(spark, returnSpark, volSpark, sharpeSpark, varSpark);

        return new PortfolioSummaryDto(
                currentValue, totalDeltaPct,
                dailyReturn, dailyReturnDeltaPct,
                vol, volDelta,
                sharpe, sharpeDelta,
                var95, varDelta,
                sparkline
        );
    }

    // ── Positions ─────────────────────────────────────────────────────────────

    /**
     * Returns per-position analytics including value, change, and contribution metrics.
     */
    @Override
    public List<PositionDto> getPositions(UUID id, UUID principalId) {
        Portfolio portfolio = portfolioQueryService.loadPortfolioWithPositions(id, principalId);
        List<PortfolioPosition> positions = portfolio.getPositions();
        if (positions.isEmpty()) return List.of();

        LocalDate to = TradingUtil.lastCompletedTradingDay();
        LocalDate from = to.minusDays(PortfolioDataHelper.PORTFOLIO_LOOKBACK_DAYS);

        PortfolioTimeSeries ts = portfolioDataHelper.buildTimeSeries(positions, from, to);
        Map<String, ?> barsByTicker = ts.barsByTicker();

        Map<String, Double> latestPrices = ts.barsByTicker().entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getLast().getAdjClose().doubleValue()
                ));

        double totalValue = portfolioDataHelper.computeTotalValue(positions, latestPrices);

        // Per-position volatility needed for weighted contribution
        Map<String, Double> volByTicker = ts.barsByTicker().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> riskPort.annualisedVolatility(DataUtil.toReturnsFromBars(e.getValue()))
                ));

        return positions.stream().map(pos -> {
            String ticker = pos.getAsset().getTicker();
            var bars = ts.barsByTicker().getOrDefault(ticker, List.of());

            if (bars.isEmpty()) {
                return PositionDto.empty(pos, ticker, 0.0);
            }

            var latest = bars.getLast();
            var prev = bars.size() > 1 ? bars.get(bars.size() - 2) : latest;

            double price = latest.getAdjClose().doubleValue();
            double change = latest.dailyReturn(prev) * prev.getAdjClose().doubleValue();
            double changePct = latest.dailyReturn(prev) * 100;
            double posValue = price * pos.getQuantity().doubleValue();

            double weight = totalValue > 0 ? (posValue / totalValue) * 100 : 0.0;
            double vol = volByTicker.getOrDefault(ticker, 0.0);
            double volContribution = weight * vol / 100.0;

            AssetRiskProfile profile = new AssetRiskProfile(pos.getAsset().getType(), pos.getAsset().getType().getBaseRiskScore());
            int score = riskPort.riskScore(profile, vol);
            String level = riskPort.riskLevel(score);

            return new PositionDto(
                    pos.getId().toString(),
                    ticker,
                    pos.getAsset().getName(),
                    pos.getAsset().getType().name(),
                    price,
                    change,
                    changePct,
                    score,
                    level,
                    SeriesUtil.formatVolume(latest.getVolume()),
                    SeriesUtil.formatPositionValue(posValue),
                    pos.getAsset().getSector().getLabel(),
                    weight,
                    vol,
                    volContribution,
                    pos.getQuantity().doubleValue(),
                    pos.getAvgBuyPrice() != null ? pos.getAvgBuyPrice().doubleValue() : price,
                    price,
                    posValue,
                    changePct,
                    volContribution
            );
        }).toList();
    }

    // ── Composition ───────────────────────────────────────────────────────────

    /**
     * Computes sector allocation percentages for portfolio composition charts.
     */
    @Override
    public List<CompositionEntryDto> getComposition(UUID id, UUID principalId) {
        log.debug("Building composition portfolioId={}", id);

        Portfolio portfolio = portfolioQueryService.loadPortfolioWithPositions(id, principalId);
        List<PortfolioPosition> positions = portfolio.getPositions();

        Map<String, Double> priceByTicker = portfolioDataHelper.fetchLatestPrices(positions);
        double totalValue = portfolioDataHelper.computeTotalValue(positions, priceByTicker);
        if (totalValue == 0.0) return List.of();

        Map<Sector, Double> valuesBySector = new LinkedHashMap<>();
        positions.forEach(pos -> {
            double posValue = priceByTicker.getOrDefault(pos.getAsset().getTicker(), 0.0)
                    * pos.getQuantity().doubleValue();
            valuesBySector.merge(pos.getAsset().getSector(), posValue, Double::sum);
        });

        return valuesBySector.entrySet().stream()
                .map(e -> new CompositionEntryDto(
                        e.getKey().getLabel(),
                        (e.getValue() / totalValue) * 100,
                        e.getKey().getColor()
                ))
                .sorted(Comparator.comparingDouble(CompositionEntryDto::value).reversed())
                .toList();
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    /**
     * Generates user-facing risk alerts derived from concentration and exposure checks.
     */
    @Override
    public List<RiskAlertDto> getAlerts(UUID id, UUID principalId) {
        log.debug("Generating risk alerts portfolioId={}", id);

        Portfolio portfolio = portfolioQueryService.loadPortfolioWithPositions(id, principalId);
        List<PortfolioPosition> positions = portfolio.getPositions();

        Map<String, Double> priceByTicker = portfolioDataHelper.fetchLatestPrices(positions);
        double totalValue = portfolioDataHelper.computeTotalValue(positions, priceByTicker);
        if (totalValue == 0.0) return List.of();

        Map<String, Double> sectorWeights = new LinkedHashMap<>();
        positions.forEach(pos -> {
            double weight = (priceByTicker.getOrDefault(pos.getAsset().getTicker(), 0.0)
                    * pos.getQuantity().doubleValue() / totalValue) * 100;
            sectorWeights.merge(pos.getAsset().getSector().getLabel(), weight, Double::sum);
        });

        log.trace("Sector weights: {}", sectorWeights);

        List<RiskAlertDto> alerts = new ArrayList<>();

        sectorWeights.forEach((sector, weight) -> {
            if (weight > SECTOR_CONCENTRATION_THRESHOLD) {
                List<String> affected = positions.stream()
                        .filter(pos -> pos.getAsset().getSector().getLabel().equals(sector))
                        .map(pos -> pos.getAsset().getTicker())
                        .toList();

                alerts.add(new RiskAlertDto(
                        UUID.randomUUID().toString(),
                        "Concentration in %s sector reached %.0f%%".formatted(sector, weight),
                        "high",
                        "just now",
                        affected
                ));
            }
        });

        double cryptoWeight = positions.stream()
                .filter(pos -> pos.getAsset().getType() == AssetType.CRYPTO)
                .mapToDouble(pos -> (priceByTicker.getOrDefault(pos.getAsset().getTicker(), 0.0)
                        * pos.getQuantity().doubleValue() / totalValue) * 100)
                .sum();

        if (cryptoWeight > CRYPTO_EXPOSURE_THRESHOLD) {
            List<String> cryptoTickers = positions.stream()
                    .filter(pos -> pos.getAsset().getType() == AssetType.CRYPTO)
                    .map(pos -> pos.getAsset().getTicker())
                    .toList();

            alerts.add(new RiskAlertDto(
                    UUID.randomUUID().toString(),
                    "Crypto exposure at %.0f%% exceeds 10%% threshold".formatted(cryptoWeight),
                    "moderate",
                    "recently",
                    cryptoTickers
            ));
        }

        if (alerts.isEmpty()) {
            alerts.add(new RiskAlertDto(
                    UUID.randomUUID().toString(),
                    "No active risk alerts — portfolio within normal bounds",
                    "low",
                    "now",
                    List.of()
            ));
        }

        return alerts;
    }
}