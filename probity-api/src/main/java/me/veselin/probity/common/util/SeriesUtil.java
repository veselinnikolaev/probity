package me.veselin.probity.common.util;

import java.util.ArrayList;
import java.util.List;

public final class SeriesUtil {

    private SeriesUtil() {}

    public static String formatVolume(Long volume) {
        if (volume == null) return "N/A";
        if (volume >= 1_000_000) return "%.1fM".formatted(volume / 1_000_000.0);
        if (volume >= 1_000) return "%.1fK".formatted(volume / 1_000.0);
        return volume.toString();
    }

    public static String formatPositionValue(double value) {
        if (value >= 1_000_000) return String.format("$%.2fM", value / 1_000_000);
        if (value >= 1_000)     return String.format("$%.2fK", value / 1_000);
        return String.format("$%.2f", value);
    }

    public static List<Double> downsample(List<Double> series, int targetPoints) {
        if (series.size() <= targetPoints) return series;
        var result = new ArrayList<Double>(targetPoints);
        double step = (double) (series.size() - 1) / (targetPoints - 1);
        for (int i = 0; i < targetPoints; i++) {
            result.add(series.get((int) Math.round(i * step))); // null passes through fine
        }
        return result;
    }
}
