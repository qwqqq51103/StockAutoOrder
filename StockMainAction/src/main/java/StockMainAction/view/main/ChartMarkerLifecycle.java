package StockMainAction.view.main;

import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.xy.XYSeries;

/**
 * 管理圖表事件標記的去重、裁切與縮放顯示判斷。
 */
public final class ChartMarkerLifecycle {
    private ChartMarkerLifecycle() {
    }

    public static boolean upsert(XYSeries series, long xMillis, double yValue, int maxPoints) {
        if (series == null || !Double.isFinite(yValue)) {
            return false;
        }
        int index = series.indexOf(xMillis);
        if (index >= 0) {
            series.updateByIndex(index, yValue);
        } else {
            series.add(xMillis, yValue);
        }
        trimToMaxPoints(series, maxPoints);
        return true;
    }

    public static int trimToMaxPoints(XYSeries series, int maxPoints) {
        if (series == null) {
            return 0;
        }
        int safeMaxPoints = Math.max(1, maxPoints);
        int removed = 0;
        while (series.getItemCount() > safeMaxPoints) {
            series.remove(0);
            removed++;
        }
        return removed;
    }

    public static int trimToOhlcWindow(OHLCSeries ohlc, XYSeries... markerSeries) {
        if (ohlc == null || ohlc.getItemCount() == 0 || markerSeries == null) {
            return 0;
        }
        long minXInclusive = KlineOhlcAggregator.itemXMillis((OHLCItem) ohlc.getDataItem(0));
        int removed = 0;
        for (XYSeries series : markerSeries) {
            removed += trimBeforeX(series, minXInclusive);
        }
        return removed;
    }

    public static int trimBeforeX(XYSeries series, long minXInclusive) {
        if (series == null) {
            return 0;
        }
        int removed = 0;
        while (series.getItemCount() > 0) {
            Number x = series.getX(0);
            if (x == null || x.longValue() < minXInclusive) {
                series.remove(0);
                removed++;
            } else {
                break;
            }
        }
        return removed;
    }

    public static int countOhlcItemsInRange(OHLCSeries series, long loMillis, long hiMillis) {
        if (series == null || series.getItemCount() == 0 || hiMillis < loMillis) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < series.getItemCount(); i++) {
            long x = KlineOhlcAggregator.itemXMillis((OHLCItem) series.getDataItem(i));
            if (x >= loMillis && x <= hiMillis) {
                count++;
            }
        }
        return count;
    }

    public static boolean markersAllowed(int visibleCandles, boolean autoHideWhenZoomedOut,
            int maxVisibleCandles) {
        int threshold = Math.max(10, maxVisibleCandles);
        return !(autoHideWhenZoomedOut && visibleCandles > threshold);
    }
}
