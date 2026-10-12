package me.veselin.probity.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.common.util.DataUtil;
import me.veselin.probity.common.util.DateRangeUtil;
import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.marketdata.port.MarketDataPort;
import me.veselin.probity.portfolio.domain.Portfolio;
import me.veselin.probity.portfolio.domain.PortfolioPosition;
import me.veselin.probity.portfolio.dto.CorrelationMatrixDto;
import me.veselin.probity.portfolio.dto.RiskMetricsDto;
import me.veselin.probity.portfolio.dto.VaRReportDto;
import me.veselin.probity.portfolio.dto.VolatilityPointDto;
import me.veselin.probity.portfolio.enumeration.DateRange;
import me.veselin.probity.portfolio.port.portfolio.PortfolioRiskQueryPort;
import me.veselin.probity.portfolio.service.portfolio.PortfolioDataHelper.PortfolioTimeSeries;
import me.veselin.probity.risk.dto.PositionWeight;
import me.veselin.probity.risk.port.RiskPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class PortfolioRiskQueryService implements PortfolioRiskQueryPort {

    private static final int VAR_LOOKBACK_DAYS = 60;             // Historical data for VaR report and distribution
    private static final int VAR_SPARKLINE_DAYS = 30;            // Historical points for VaR sparkline
    private static final int VAR_ROLLING_WINDOW_DAYS = 20;       // Rolling window for VaR sparkline calculation (trading days)
    private static final int VOLATILITY_WINDOW_DAYS = 20;

    private final PortfolioQueryService portfolioQueryService;
    private final MarketDataPort marketDataPort;
    private final RiskPort riskPort;
    private final PortfolioDataHelper portfolioDataHelper;

    // ── Risk metrics ──────────────────────────────────────────────────────────

    /**
     * Returns medium-horizon risk metrics and their trend deltas.
     */
    @Override
    public RiskMetricsDto getRiskMetrics(UUID portfolioId, String range, UUID userId) {
        log.debug("Building risk metrics portfolioId={} range={}", portfolioId, range);

        Portfolio portfolio = portfolioQueryService.loadPortfolioWithPositions(portfolioId, userId);
        List<PortfolioPosition> positions = portfolio.getPositions();
        if (positions.isEmpty()) return RiskMetricsDto.empty();

        DateRangeUtil.DateRangeResult dateRange = DateRangeUtil.calculateRangeFromDateRangeEnum(DateRange.fromValue(range));
        PortfolioTimeSeries ts = portfolioDataHelper.buildTimeSeries(positions, dateRange.from(), dateRange.to());
        if (ts.isEmpty()) return RiskMetricsDto.empty();

        List<Double> values = ts.values();
        List<Double> returns = ts.returns();

        double maxDrawdown = riskPort.maxDrawdown(values);
        List<PositionWeight> positionWeights = positions.stream()
                .map(p -> new PositionWeight(
                        p.getAsset().getTicker(),
                        p.getQuantity(),
                        ts.barsByTicker().getOrDefault(p.getAsset().getTicker(), List.of()).isEmpty()
                                ? java.math.BigDecimal.ZERO
                                : ts.barsByTicker().get(p.getAsset().getTicker()).getLast().getAdjClose()
                ))
                .toList();
        double concentration = riskPort.computeHHI(positionWeights, ts.barsByTicker());

        // Beta stubbed until benchmark series is wired — returns neutral 1.0
        double beta = 1.0;

        // Deltas — same half-split pattern as getSummary
        int mid = returns.size() / 2;
        double maxDrawdownDelta = 0.0;

        if (mid > 0) {
            List<Double> firstValues = values.subList(0, mid + 1);
            List<Double> secondValues = values.subList(mid, values.size());
            maxDrawdownDelta = riskPort.maxDrawdown(secondValues) - riskPort.maxDrawdown(firstValues);
            // Concentration doesn't change over sub-periods (it's a snapshot),
            // so delta is 0 until you store historical snapshots — leave as 0.0
        }

        // Sparklines — 7-point rolling windows
        List<Double> drawdownSpark = portfolioDataHelper.buildRollingMetric(values, 7, riskPort::maxDrawdown);
        List<Double> betaSpark = Collections.nCopies(7, 1.0); // stubbed
        List<Double> concSpark = Collections.nCopies(7, concentration);

        return new RiskMetricsDto(
                beta, 0.0,
                maxDrawdown, maxDrawdownDelta,
                concentration, 0.0,
                drawdownSpark, betaSpark, concSpark
        );
    }

    // ── VaR report ────────────────────────────────────────────────────────────

    /**
     * Builds a VaR report with portfolio-level, per-asset, and distribution breakdowns.
     */
    @Override
    public VaRReportDto getVaRReport(UUID portfolioId, double confidenceLevel,
                                     int timeHorizonDays, UUID userId) {
        log.debug("Building VaR report portfolioId={} cl={} horizon={}",
                portfolioId, confidenceLevel, timeHorizonDays);

        Portfolio portfolio = portfolioQueryService.loadPortfolioWithPositions(portfolioId, userId);
        List<PortfolioPosition> positions = portfolio.getPositions();
        if (positions.isEmpty()) return VaRReportDto.empty(confidenceLevel, timeHorizonDays);

        DateRangeUtil.DateRangeResult dateRange = DateRangeUtil.calculateHistoricalRange(VAR_LOOKBACK_DAYS);
        PortfolioTimeSeries ts = portfolioDataHelper.buildTimeSeries(positions, dateRange.from(), dateRange.to());
        if (ts.isEmpty()) return VaRReportDto.empty(confidenceLevel, timeHorizonDays);

        VaRContext context = calculateVaRMetrics(ts, confidenceLevel, timeHorizonDays);
        List<VaRReportDto.AssetVaRDto> assetBreakdown = buildAssetBreakdown(positions, ts, context, timeHorizonDays);
        List<Double> spark = generateSparklines(ts.values(), context.valueAtRisk());
        List<VaRReportDto.ReturnBucketDto> distribution = buildDistribution(ts.returns());

        VaRReportDto.MethodologyDto methodology = new VaRReportDto.MethodologyDto(
                "VaR = μ + Z × σ",
                context.vol() * 100,
                context.z(),
                context.meanDailyReturn() * 100
        );

        return new VaRReportDto(
                context.valueAtRisk(),
                confidenceLevel,
                timeHorizonDays,
                methodology,
                spark,
                assetBreakdown,
                distribution
        );
    }

    /**
     * Calculates portfolio-level VaR metrics from time series data.
     */
    private VaRContext calculateVaRMetrics(PortfolioTimeSeries ts, double confidenceLevel, int timeHorizonDays) {
        List<Double> portfolioValues = ts.values();
        List<Double> dailyReturns = ts.returns();

        double currentValue = DataUtil.lastOrZero(portfolioValues);
        double vol = riskPort.annualisedVolatility(dailyReturns);

        double z = getZScore(confidenceLevel);
        double dailyVol = vol / Math.sqrt(PortfolioDataHelper.TRADING_DAYS);
        double horizonVol = dailyVol * Math.sqrt(timeHorizonDays);
        double meanDailyReturn = dailyReturns.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);

        double varPct = meanDailyReturn - z * horizonVol;
        double valueAtRisk = varPct * currentValue;

        return new VaRContext(currentValue, vol, z, dailyVol, horizonVol, meanDailyReturn, valueAtRisk);
    }

    /**
     * Returns the Z-score for a given confidence level.
     */
    private double getZScore(double confidenceLevel) {
        return switch ((int) Math.round(confidenceLevel * 100)) {
            case 90 -> 1.282; // 90% confidence z-score
            case 99 -> 2.326; // 99% confidence z-score
            default -> 1.645; // 95% confidence z-score (default)
        };
    }

    /**
     * Builds per-asset VaR breakdown with contributions.
     */
    private List<VaRReportDto.AssetVaRDto> buildAssetBreakdown(
            List<PortfolioPosition> positions, PortfolioTimeSeries ts, VaRContext context, int timeHorizonDays) {

        Map<String, Double> latestPrices = ts.barsByTicker().entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getLast().getAdjClose().doubleValue()
                ));

        double totalValue = portfolioDataHelper.computeTotalValue(positions, latestPrices);

        List<VaRReportDto.AssetVaRDto> assetBreakdown = positions.stream().map(pos -> {
            String ticker = pos.getAsset().getTicker();
            List<PriceBar> bars = ts.barsByTicker().getOrDefault(ticker, List.of());
            double posValue = latestPrices.getOrDefault(ticker, 0.0) * pos.getQuantity().doubleValue();
            double weight = totalValue > 0 ? (posValue / totalValue) * 100 : 0.0;

            if (bars.size() < 2) {
                return new VaRReportDto.AssetVaRDto(ticker, weight, 0, 0, 0);
            }

            List<Double> assetReturns = DataUtil.toReturnsFromBars(bars);
            double assetVol = riskPort.annualisedVolatility(assetReturns);
            double assetDailyVol = assetVol / Math.sqrt(PortfolioDataHelper.TRADING_DAYS);
            double assetHorizonVol = assetDailyVol * Math.sqrt(timeHorizonDays);
            double assetMean = assetReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double assetVarPct = assetMean - context.z() * assetHorizonVol;
            double individualVar = assetVarPct * posValue;
            double contribution = (weight / 100.0) * context.valueAtRisk();

            return new VaRReportDto.AssetVaRDto(ticker, weight, individualVar, contribution, 0.0);
        }).toList();

        // Back-fill percentOfTotal
        double totalContribution = assetBreakdown.stream()
                .mapToDouble(a -> Math.abs(a.contribution())).sum();

        return assetBreakdown.stream()
                .map(a -> new VaRReportDto.AssetVaRDto(
                        a.ticker(), a.weight(), a.individualVar(), a.contribution(),
                        totalContribution > 0 ? (Math.abs(a.contribution()) / totalContribution) * 100 : 0.0
                )).toList();
    }

    /**
     * Generates historical VaR sparkline using rolling window calculations.
     */
    private List<Double> generateSparklines(List<Double> portfolioValues, double valueAtRisk) {
        List<Double> spark = new ArrayList<>();
        int sparkPoints = Math.min(VAR_SPARKLINE_DAYS, portfolioValues.size());
        int startIdx = portfolioValues.size() - sparkPoints;

        for (int i = startIdx; i < portfolioValues.size(); i++) {
            int windowStart = Math.max(0, i - VAR_ROLLING_WINDOW_DAYS);
            if (i <= windowStart) {
                spark.add(Math.abs(valueAtRisk));
                continue;
            }
            List<Double> windowValues = portfolioValues.subList(windowStart, i + 1);
            List<Double> windowReturns = calculateWindowReturns(windowValues);
            if (windowReturns.size() < 2) {
                spark.add(Math.abs(valueAtRisk));
                continue;
            }
            double windowVar = riskPort.var95(portfolioValues.get(i), windowReturns);
            spark.add(Math.abs(windowVar));
        }
        return spark;
    }

    /**
     * Calculates returns from a window of values.
     */
    private List<Double> calculateWindowReturns(List<Double> windowValues) {
        List<Double> windowReturns = new ArrayList<>();
        for (int j = 1; j < windowValues.size(); j++) {
            double prev = windowValues.get(j - 1);
            if (prev != 0) windowReturns.add((windowValues.get(j) - prev) / prev);
        }
        return windowReturns;
    }

    /**
     * Builds return distribution histogram from daily returns.
     */
    private List<VaRReportDto.ReturnBucketDto> buildDistribution(List<Double> dailyReturns) {
        double[] breakpoints = {-0.10, -0.08, -0.06, -0.04, -0.02, 0.00, 0.02, 0.04, 0.06, 0.08, 0.10};
        String[] labels = {
                "-10% to -8%", "-8% to -6%", "-6% to -4%", "-4% to -2%", "-2% to 0%",
                "0% to 2%", "2% to 4%", "4% to 6%", "6% to 8%", "8% to 10%"
        };
        int[] counts = new int[labels.length];

        for (double r : dailyReturns) {
            for (int i = 0; i < breakpoints.length - 1; i++) {
                if (r >= breakpoints[i] && r < breakpoints[i + 1]) {
                    counts[i]++;
                    break;
                }
            }
        }

        List<VaRReportDto.ReturnBucketDto> distribution = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            boolean isNegativeReturn = i < (labels.length / 2);
            distribution.add(new VaRReportDto.ReturnBucketDto(labels[i], counts[i], isNegativeReturn));
        }
        return distribution;
    }

    /**
     * Value object holding VaR calculation context.
     */
    private record VaRContext(
            double currentValue,
            double vol,
            double z,
            double dailyVol,
            double horizonVol,
            double meanDailyReturn,
            double valueAtRisk
    ) {}

    // ── Correlation matrix ────────────────────────────────────────────────────

    /**
     * Calculates an asset return correlation matrix for the selected range.
     */
    @Override
    public CorrelationMatrixDto getCorrelationMatrix(UUID portfolioId, String range, UUID userId) {
        log.debug("Building correlation matrix portfolioId={} range={}", portfolioId, range);

        Portfolio portfolio = portfolioQueryService.loadPortfolioWithPositions(portfolioId, userId);
        List<PortfolioPosition> positions = portfolio.getPositions();
        if (positions.size() < 2) {
            List<String> tickers = positions.stream()
                    .map(p -> p.getAsset().getTicker()).toList();
            return new CorrelationMatrixDto(tickers, List.of(List.of(1.0)));
        }

        DateRangeUtil.DateRangeResult dateRange = DateRangeUtil.calculateRangeFromDateRangeEnum(DateRange.fromValue(range));

        List<String> tickers = positions.stream()
                .map(p -> p.getAsset().getTicker())
                .distinct()
                .toList();

        Map<String, List<Double>> returnsByTicker = tickers.stream()
                .collect(Collectors.toMap(
                        ticker -> ticker,
                        ticker -> DataUtil.toReturnsFromBars(
                                marketDataPort.getHistoricalBars(ticker, dateRange.from(), dateRange.to()))
                ));

        List<List<Double>> matrix = new ArrayList<>();
        for (String row : tickers) {
            List<Double> matrixRow = new ArrayList<>();
            for (String col : tickers) {
                matrixRow.add(row.equals(col) ? 1.0
                        : riskPort.pearsonCorrelation(returnsByTicker.get(row), returnsByTicker.get(col)));
            }
            matrix.add(matrixRow);
        }

        return new CorrelationMatrixDto(tickers, matrix);
    }

    // ── Volatility ────────────────────────────────────────────────────────────

    /**
     * Computes rolling volatility points for the selected analysis range.
     */
    @Override
    public List<VolatilityPointDto> getVolatility(UUID id, String range, UUID principalId) {
        log.debug("Calculating volatility portfolioId={} range={}", id, range);

        Portfolio portfolio = portfolioQueryService.loadPortfolioWithPositions(id, principalId);
        List<PortfolioPosition> positions = portfolio.getPositions();

        DateRangeUtil.DateRangeResult dateRange = DateRangeUtil.calculateRangeFromDateRangeEnum(DateRange.fromValue(range));
        PortfolioTimeSeries ts = portfolioDataHelper.buildTimeSeries(positions, dateRange.from(), dateRange.to());

        List<Double> values = ts.values();
        List<LocalDate> dates = ts.dates();

        List<Double> rollingVol = riskPort.rollingVolatility(values, VOLATILITY_WINDOW_DAYS);

        // rollingVol is shorter than values by (window - 1) bars.
        // Align to the tail of the dates list so each point maps to the correct date.
        int offset = dates.size() - rollingVol.size();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

        List<VolatilityPointDto> result = new ArrayList<>(rollingVol.size());
        for (int i = 0; i < rollingVol.size(); i++) {
            Double vol = rollingVol.get(i);
            if (vol == null) continue;
            result.add(new VolatilityPointDto(dates.get(offset + i).format(fmt), vol));
        }

        return result;
    }
}