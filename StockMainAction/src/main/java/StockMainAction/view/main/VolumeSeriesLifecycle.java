package StockMainAction.view.main;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import java.util.Map;

/**
 * 成交量時間序列的 bucket 對齊、累加、裁切與移動平均生命週期。
 */
public final class VolumeSeriesLifecycle {
    private VolumeSeriesLifecycle() {
    }

    public static long alignTimestampMillis(long timestampMillis, int periodKey) {
        return KlineOhlcAggregator.alignTimestampMillis(timestampMillis, periodKey);
    }

    public static boolean upsertVolume(XYSeries series, long alignedMillis, int volume, int maxPoints) {
        if (series == null) {
            return false;
        }
        int existingIndex = series.indexOf(alignedMillis);
        if (existingIndex >= 0) {
            Number existingVolume = series.getY(existingIndex);
            int newVolume = (existingVolume != null ? existingVolume.intValue() : 0) + volume;
            series.updateByIndex(existingIndex, newVolume);
        } else {
            series.add(alignedMillis, volume, false);
        }
        ChartMarkerLifecycle.trimToMaxPoints(series, maxPoints);
        return true;
    }

    public static int updateAllPeriodVolumes(Map<Integer, XYSeries> periodToVolume,
            int[] klineSeconds, int[] klineMinutes, int volume, long timestampMillis, int maxPoints) {
        int updated = 0;
        if (periodToVolume == null) {
            return updated;
        }
        if (klineSeconds != null) {
            for (int seconds : klineSeconds) {
                int key = -Math.max(1, seconds);
                XYSeries series = periodToVolume.get(key);
                if (upsertVolume(series, alignTimestampMillis(timestampMillis, key), volume, maxPoints)) {
                    updated++;
                }
            }
        }
        if (klineMinutes != null) {
            for (int minutes : klineMinutes) {
                int key = Math.max(1, minutes);
                XYSeries series = periodToVolume.get(key);
                if (upsertVolume(series, alignTimestampMillis(timestampMillis, key), volume, maxPoints)) {
                    updated++;
                }
            }
        }
        return updated;
    }

    public static int aggregateVolumeData(XYSeries sourceVolume, XYSeries targetVolume,
            int targetPeriodKey, int multiplier) {
        if (sourceVolume == null || targetVolume == null || sourceVolume.getItemCount() == 0) {
            return 0;
        }
        int safeMultiplier = Math.max(1, multiplier);
        targetVolume.clear();
        int sourceCount = sourceVolume.getItemCount();
        for (int i = 0; i < sourceCount; i += safeMultiplier) {
            double totalVolume = 0.0;
            long alignedMillis = 0L;
            int aggregatedBars = 0;
            for (int j = 0; j < safeMultiplier && (i + j) < sourceCount; j++) {
                XYDataItem item = sourceVolume.getDataItem(i + j);
                if (item == null || item.getX() == null || item.getY() == null) {
                    continue;
                }
                if (aggregatedBars == 0) {
                    alignedMillis = alignTimestampMillis(item.getX().longValue(), targetPeriodKey);
                }
                double volume = item.getY().doubleValue();
                if (Double.isFinite(volume)) {
                    totalVolume += volume;
                }
                aggregatedBars++;
            }
            if (aggregatedBars > 0) {
                targetVolume.add(alignedMillis, totalVolume, false);
            }
        }
        targetVolume.fireSeriesChanged();
        return targetVolume.getItemCount();
    }

    public static void recalculateMovingAverages(XYSeries volumeSeries, XYSeries ma5, XYSeries ma10,
            int maxPoints) {
        if (volumeSeries == null || ma5 == null || ma10 == null) {
            return;
        }
        ma5.clear();
        ma10.clear();
        int count = volumeSeries.getItemCount();
        for (int i = 0; i < count; i++) {
            Number x = volumeSeries.getX(i);
            ma5.add(x, movingAverageAt(volumeSeries, i, 5), false);
            ma10.add(x, movingAverageAt(volumeSeries, i, 10), false);
        }
        ChartMarkerLifecycle.trimToMaxPoints(ma5, maxPoints);
        ChartMarkerLifecycle.trimToMaxPoints(ma10, maxPoints);
        ma5.fireSeriesChanged();
        ma10.fireSeriesChanged();
    }

    private static double movingAverageAt(XYSeries series, int indexInclusive, int period) {
        double sum = 0.0;
        int count = 0;
        int start = Math.max(0, indexInclusive - Math.max(1, period) + 1);
        for (int i = start; i <= indexInclusive; i++) {
            Number y = series.getY(i);
            if (y == null) {
                continue;
            }
            double value = y.doubleValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            sum += value;
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }
}
