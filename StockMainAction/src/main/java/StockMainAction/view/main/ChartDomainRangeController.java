package StockMainAction.view.main;

import org.jfree.data.Range;
import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.ohlc.OHLCSeries;

/**
 * K 線 domain/range 的純計算工具。
 *
 * <p>MainView 只負責把結果套用到 JFreeChart axis，計算本身集中於此類以便測試。</p>
 */
public final class ChartDomainRangeController {
    private static final int MIN_VISIBLE_CANDLES = 5;
    private static final int MAX_VISIBLE_CANDLES = 500;
    private static final double PRICE_RANGE_PADDING_RATIO = 0.08;

    private ChartDomainRangeController() {
    }

    public record DomainDecision(boolean autoRange, double lower, double upper, int candleCount) {
    }

    public static int clampVisibleCandles(int visibleCandles) {
        return Math.max(MIN_VISIBLE_CANDLES, Math.min(MAX_VISIBLE_CANDLES, visibleCandles));
    }

    public static int autoVisibleCandlesForWidth(int width, int minCandlePixelWidth) {
        int safeWidth = Math.max(300, width);
        int safeMinPixelWidth = Math.max(1, minCandlePixelWidth);
        return Math.max(20, Math.min(180, safeWidth / safeMinPixelWidth));
    }

    public static DomainDecision latestWindow(OHLCSeries series, int visibleCandles) {
        if (series == null || series.getItemCount() == 0) {
            return new DomainDecision(true, 0.0, 0.0, 0);
        }
        int count = series.getItemCount();
        int nVisible = clampVisibleCandles(visibleCandles);
        if (count <= nVisible) {
            return new DomainDecision(true, 0.0, 0.0, count);
        }
        OHLCItem firstVisible = (OHLCItem) series.getDataItem(count - nVisible);
        OHLCItem lastVisible = (OHLCItem) series.getDataItem(count - 1);
        return new DomainDecision(false,
                KlineOhlcAggregator.itemXMillis(firstVisible),
                lastVisible.getPeriod().getLastMillisecond(),
                nVisible);
    }

    public static DomainDecision fullWindowWithMargin(OHLCSeries series, int periodKey) {
        if (series == null || series.getItemCount() == 0) {
            return new DomainDecision(true, 0.0, 0.0, 0);
        }
        OHLCItem first = (OHLCItem) series.getDataItem(0);
        OHLCItem last = (OHLCItem) series.getDataItem(series.getItemCount() - 1);
        long periodMillis = periodMillis(periodKey);
        long margin = periodMillis * 2L;
        return new DomainDecision(false,
                KlineOhlcAggregator.itemXMillis(first) - margin,
                last.getPeriod().getLastMillisecond() + margin,
                series.getItemCount());
    }

    public static Range priceRange(OHLCSeries series, int visibleCandles, boolean followLatest) {
        if (series == null || series.getItemCount() == 0) {
            return null;
        }
        int count = series.getItemCount();
        int nVisible = clampVisibleCandles(visibleCandles);
        int start = followLatest ? Math.max(0, count - nVisible) : 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = start; i < count; i++) {
            OHLCItem item = (OHLCItem) series.getDataItem(i);
            min = Math.min(min, item.getLowValue());
            max = Math.max(max, item.getHighValue());
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return null;
        }
        if (Double.compare(min, max) == 0) {
            min -= 1.0;
            max += 1.0;
        }
        double span = Math.max(1e-6, max - min);
        double padding = span * PRICE_RANGE_PADDING_RATIO;
        return new Range(min - padding, max + padding);
    }

    public static long periodMillis(int periodKey) {
        if (periodKey < 0) {
            return 1000L * Math.max(1, -periodKey);
        }
        if (periodKey > 0) {
            return 60_000L * Math.max(1, periodKey);
        }
        return 1000L;
    }
}
