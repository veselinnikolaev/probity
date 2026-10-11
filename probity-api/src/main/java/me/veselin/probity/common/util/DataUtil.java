package me.veselin.probity.common.util;

import me.veselin.probity.marketdata.domain.PriceBar;
import me.veselin.probity.portfolio.domain.PortfolioPosition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DataUtil {

    private DataUtil() {}

    public static double lastOrZero(List<Double> list) {
        return list.isEmpty() ? 0.0 : list.getLast();
    }

    public static double firstOrZero(List<Double> list) {
        return list.isEmpty() ? 0.0 : list.getFirst();
    }

    public static double previousOrCurrent(List<Double> list) {
        return list.size() > 1 ? list.get(list.size() - 2) : lastOrZero(list);
    }

    public static double safePct(double base, double delta) {
        return base != 0.0 ? (delta / base) * 100 : 0.0;
    }

    public static List<Double> toReturnsFromBars(List<PriceBar> bars) {
        if (bars.size() < 2) return List.of();

        List<Double> result = new ArrayList<>(bars.size() - 1);
        for (int i = 1; i < bars.size(); i++) {
            result.add(bars.get(i).dailyReturn(bars.get(i - 1)));
        }
        return result;
    }

    public static Map<LocalDate, Double> buildValueSeries(
            List<PortfolioPosition> positions,
            Map<String, List<PriceBar>> barsByTicker) {

        Map<LocalDate, Double> valueByDate = new TreeMap<>();

        for (PortfolioPosition pos : positions) {
            String ticker = pos.getAsset().getTicker();
            List<PriceBar> bars = barsByTicker.get(ticker);

            if (bars == null) continue;

            double qty = pos.getQuantity().doubleValue();

            for (PriceBar bar : bars) {
                valueByDate.merge(
                        bar.getBarDate(),
                        bar.getAdjClose().doubleValue() * qty,
                        Double::sum
                );
            }
        }

        return valueByDate;
    }

    public static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public static double variance(List<Double> values, double mean) {
        return values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0.0);
    }

    public static double stdDev(List<Double> values) {
        return Math.sqrt(variance(values, mean(values)));
    }
}
