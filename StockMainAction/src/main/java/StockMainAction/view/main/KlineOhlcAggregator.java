package StockMainAction.view.main;

import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.ohlc.OHLCSeries;

import java.util.Date;

/**
 * 將即時價格 tick 聚合成 K 線 OHLC。
 *
 * <p>此類只處理資料生命週期，不直接碰 Swing component，方便單元測試 MainView 的 K 線行為。</p>
 */
public final class KlineOhlcAggregator {
    private KlineOhlcAggregator() {
    }

    @FunctionalInterface
    public interface CandleClosedHandler {
        void onClosed(long candleXMillis, double candleClose);
    }

    public record Result(
            boolean changed,
            boolean startedNewBar,
            boolean trimmed,
            long currentBarXMillis,
            Long closedBarXMillis,
            Double closedBarClose
    ) {
        static Result unchanged(long currentBarXMillis) {
            return new Result(false, false, false, currentBarXMillis, null, null);
        }
    }

    public static Result applyTick(OHLCSeries series, double price, long timestampMillis,
            int periodKey, int maxBars) {
        return applyTick(series, price, timestampMillis, periodKey, maxBars, null);
    }

    public static Result applyTick(OHLCSeries series, double price, long timestampMillis,
            int periodKey, int maxBars, CandleClosedHandler closedHandler) {
        long currentBarXMillis = alignTimestampMillis(timestampMillis, periodKey);
        if (series == null || !Double.isFinite(price)) {
            return Result.unchanged(currentBarXMillis);
        }

        boolean previousNotify = series.getNotify();
        boolean startedNewBar = false;
        boolean trimmed = false;
        Long closedBarXMillis = null;
        Double closedBarClose = null;

        try {
            series.setNotify(false);
            RegularTimePeriod period = periodFor(timestampMillis, periodKey);
            if (series.getItemCount() == 0) {
                series.add(period, price, price, price, price);
            } else {
                OHLCItem lastItem = (OHLCItem) series.getDataItem(series.getItemCount() - 1);
                if (lastItem.getPeriod().equals(period)) {
                    double open = lastItem.getOpenValue();
                    double high = Math.max(lastItem.getHighValue(), price);
                    double low = Math.min(lastItem.getLowValue(), price);
                    series.remove(lastItem.getPeriod());
                    series.add(period, open, high, low, price);
                } else {
                    startedNewBar = true;
                    closedBarXMillis = itemXMillis(lastItem);
                    closedBarClose = lastItem.getCloseValue();
                    if (closedHandler != null) {
                        closedHandler.onClosed(closedBarXMillis, closedBarClose);
                    }
                    double newOpen = closedBarClose;
                    double newHigh = Math.max(newOpen, price);
                    double newLow = Math.min(newOpen, price);
                    series.add(period, newOpen, newHigh, newLow, price);
                }
            }
            trimmed = trimToMaxBars(series, maxBars) > 0;
        } finally {
            series.setNotify(previousNotify);
        }

        return new Result(true, startedNewBar, trimmed, currentBarXMillis, closedBarXMillis, closedBarClose);
    }

    public static RegularTimePeriod periodFor(long timestampMillis, int periodKey) {
        long aligned = alignTimestampMillis(timestampMillis, periodKey);
        if (periodKey < 0) {
            return new Second(new Date(aligned));
        }
        return new Minute(new Date(aligned));
    }

    public static long alignTimestampMillis(long timestampMillis, int periodKey) {
        long bucketMillis = bucketMillis(periodKey);
        return timestampMillis - Math.floorMod(timestampMillis, bucketMillis);
    }

    public static long itemXMillis(OHLCItem item) {
        return item.getPeriod().getFirstMillisecond();
    }

    public static int trimToMaxBars(OHLCSeries series, int maxBars) {
        if (series == null) {
            return 0;
        }
        int safeMaxBars = Math.max(1, maxBars);
        int removed = 0;
        while (series.getItemCount() > safeMaxBars) {
            series.remove(0);
            removed++;
        }
        return removed;
    }

    private static long bucketMillis(int periodKey) {
        if (periodKey < 0) {
            return 1000L * Math.max(1, -periodKey);
        }
        if (periodKey > 0) {
            return 60_000L * Math.max(1, periodKey);
        }
        return 1000L;
    }
}
