package StockMainAction.view.main;

import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * K 線週期切換時的資料聚合與 marker 對齊。
 */
public final class KlineDatasetSwitcher {
    private KlineDatasetSwitcher() {
    }

    public static int periodSeconds(int periodKey) {
        return periodKey < 0 ? Math.max(1, -periodKey) : Math.max(1, periodKey) * 60;
    }

    public static Integer multiplierIfWholeMultiple(int sourcePeriodKey, int targetPeriodKey) {
        int sourceSeconds = periodSeconds(sourcePeriodKey);
        int targetSeconds = periodSeconds(targetPeriodKey);
        if (targetSeconds % sourceSeconds != 0) {
            return null;
        }
        return targetSeconds / sourceSeconds;
    }

    public static int aggregateOhlc(OHLCSeries sourceSeries, OHLCSeries targetSeries,
            int targetPeriodKey, int multiplier) {
        if (sourceSeries == null || targetSeries == null || sourceSeries.getItemCount() == 0) {
            return 0;
        }
        int safeMultiplier = Math.max(1, multiplier);
        targetSeries.clear();
        int sourceCount = sourceSeries.getItemCount();
        for (int i = 0; i < sourceCount; i += safeMultiplier) {
            double open = 0.0;
            double high = Double.NEGATIVE_INFINITY;
            double low = Double.POSITIVE_INFINITY;
            double close = 0.0;
            RegularTimePeriod targetPeriod = null;
            int aggregatedBars = 0;

            for (int j = 0; j < safeMultiplier && (i + j) < sourceCount; j++) {
                OHLCItem sourceItem = (OHLCItem) sourceSeries.getDataItem(i + j);
                if (sourceItem == null) {
                    continue;
                }
                if (aggregatedBars == 0) {
                    open = sourceItem.getOpenValue();
                    long sourceMillis = KlineOhlcAggregator.itemXMillis(sourceItem);
                    long alignedMillis = KlineOhlcAggregator.alignTimestampMillis(sourceMillis, targetPeriodKey);
                    targetPeriod = KlineOhlcAggregator.periodFor(alignedMillis, targetPeriodKey);
                }
                high = Math.max(high, sourceItem.getHighValue());
                low = Math.min(low, sourceItem.getLowValue());
                close = sourceItem.getCloseValue();
                aggregatedBars++;
            }

            if (aggregatedBars > 0 && targetPeriod != null) {
                targetSeries.add(targetPeriod, open, high, low, close);
            }
        }
        return targetSeries.getItemCount();
    }

    public static int realignMarkerSeries(XYSeries series, int periodKey) {
        if (series == null || series.getItemCount() == 0) {
            return 0;
        }
        List<XYDataItem> alignedItems = new ArrayList<>();
        for (int i = 0; i < series.getItemCount(); i++) {
            XYDataItem item = series.getDataItem(i);
            if (item == null || item.getX() == null || item.getY() == null) {
                continue;
            }
            long alignedMillis = KlineOhlcAggregator.alignTimestampMillis(item.getX().longValue(), periodKey);
            if (!containsX(alignedItems, alignedMillis)) {
                alignedItems.add(new XYDataItem(alignedMillis, item.getY()));
            }
        }
        series.clear();
        for (XYDataItem item : alignedItems) {
            series.add(item.getX(), item.getY(), false);
        }
        series.fireSeriesChanged();
        return alignedItems.size();
    }

    private static boolean containsX(List<XYDataItem> items, long xMillis) {
        for (XYDataItem item : items) {
            if (item.getX().longValue() == xMillis) {
                return true;
            }
        }
        return false;
    }
}
