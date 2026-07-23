package StockMainAction.view.main;

import org.jfree.data.Range;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

/**
 * 依目前可視時間窗計算成交量 Y 軸範圍。
 */
public final class VisibleVolumeRangeCalculator {
    private static final double DEFAULT_PADDING_MULTIPLIER = 1.18;
    private static final double DEFAULT_FALLBACK_UPPER = 1.0;

    private VisibleVolumeRangeCalculator() {
    }

    public record AxisRange(double lower, double upper, double maxVolume, int visiblePoints) {
    }

    public static AxisRange calculate(XYSeries series, Range visibleDomain) {
        return calculate(series, visibleDomain, DEFAULT_PADDING_MULTIPLIER, DEFAULT_FALLBACK_UPPER);
    }

    public static AxisRange calculate(XYSeries series, Range visibleDomain,
            double paddingMultiplier, double fallbackUpper) {
        if (series == null || visibleDomain == null || series.getItemCount() == 0) {
            return new AxisRange(0.0, Math.max(1.0, fallbackUpper), 0.0, 0);
        }
        double max = 0.0;
        int visiblePoints = 0;
        for (int i = 0; i < series.getItemCount(); i++) {
            XYDataItem item = series.getDataItem(i);
            if (item == null || item.getX() == null || item.getY() == null) {
                continue;
            }
            double x = item.getX().doubleValue();
            if (x < visibleDomain.getLowerBound() || x > visibleDomain.getUpperBound()) {
                continue;
            }
            double value = item.getY().doubleValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            visiblePoints++;
            max = Math.max(max, value);
        }
        double upper = max <= 0.0 ? Math.max(1.0, fallbackUpper) : max * Math.max(1.0, paddingMultiplier);
        return new AxisRange(0.0, upper, max, visiblePoints);
    }
}
