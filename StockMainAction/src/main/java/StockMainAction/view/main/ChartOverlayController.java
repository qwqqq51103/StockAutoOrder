package StockMainAction.view.main;

import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.xy.XYSeries;

/**
 * K 線疊加指標的純計算與 series upsert helper。
 */
public final class ChartOverlayController {
    private ChartOverlayController() {
    }

    public static double smaAt(OHLCSeries series, int period, int indexInclusive) {
        if (series == null || series.getItemCount() == 0) {
            return Double.NaN;
        }
        int end = Math.min(series.getItemCount() - 1, Math.max(0, indexInclusive));
        int safePeriod = Math.max(1, period);
        int start = Math.max(0, end - safePeriod + 1);
        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= end; i++) {
            OHLCItem item = (OHLCItem) series.getDataItem(i);
            double close = item.getCloseValue();
            if (Double.isFinite(close)) {
                sum += close;
                count++;
            }
        }
        if (count == 0) {
            return Double.NaN;
        }
        return sum / count;
    }

    public static double ema(double close, double previousEma, int period) {
        if (!Double.isFinite(close)) {
            return Double.NaN;
        }
        double base = Double.isFinite(previousEma) ? previousEma : close;
        double multiplier = 2.0 / (Math.max(1, period) + 1.0);
        return close * multiplier + base * (1.0 - multiplier);
    }

    public static boolean shouldUpdateCurrent(boolean isNewCandle, long nowMillis,
            long lastRecomputeMillis, long minIntervalMillis) {
        return isNewCandle || (nowMillis - lastRecomputeMillis) >= Math.max(50L, minIntervalMillis);
    }

    public static boolean updateOrAdd(XYSeries series, long xMillis, double yValue) {
        if (series == null || !Double.isFinite(yValue)) {
            return false;
        }
        int count = series.getItemCount();
        if (count > 0) {
            Number lastX = series.getX(count - 1);
            if (lastX != null && lastX.longValue() == xMillis) {
                series.updateByIndex(count - 1, yValue);
                return true;
            }
        }
        series.add(xMillis, yValue, false);
        return true;
    }
}
