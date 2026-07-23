package StockMainAction.view.main;

import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KlineOhlcAggregatorTest {
    @Test
    public void firstTickCreatesSingleOhlcBar() {
        OHLCSeries series = new OHLCSeries("K");

        KlineOhlcAggregator.Result result =
                KlineOhlcAggregator.applyTick(series, 10.5, 1_234L, -1, 10);

        assertTrue(result.changed());
        assertFalse(result.startedNewBar());
        assertFalse(result.trimmed());
        assertEquals(1_000L, result.currentBarXMillis());
        assertNull(result.closedBarXMillis());
        assertEquals(1, series.getItemCount());
        assertOhlc(series, 0, 10.5, 10.5, 10.5, 10.5);
    }

    @Test
    public void sameBucketUpdatesHighLowAndCloseWithoutAddingBar() {
        OHLCSeries series = new OHLCSeries("K");

        KlineOhlcAggregator.applyTick(series, 10.0, 1_000L, -1, 10);
        KlineOhlcAggregator.applyTick(series, 11.2, 1_500L, -1, 10);
        KlineOhlcAggregator.applyTick(series, 9.8, 1_700L, -1, 10);

        assertEquals(1, series.getItemCount());
        assertOhlc(series, 0, 10.0, 11.2, 9.8, 9.8);
    }

    @Test
    public void nextBucketStartsNewBarFromPreviousCloseAndReportsClosedCandle() {
        OHLCSeries series = new OHLCSeries("K");
        AtomicLong closedX = new AtomicLong(-1L);
        AtomicReference<Double> closedClose = new AtomicReference<>();

        KlineOhlcAggregator.applyTick(series, 10.0, 1_000L, -1, 10);
        KlineOhlcAggregator.applyTick(series, 10.8, 1_500L, -1, 10);
        KlineOhlcAggregator.Result result = KlineOhlcAggregator.applyTick(
                series, 11.5, 2_000L, -1, 10,
                (xMillis, close) -> {
                    closedX.set(xMillis);
                    closedClose.set(close);
                });

        assertTrue(result.startedNewBar());
        assertEquals(Long.valueOf(1_000L), result.closedBarXMillis());
        assertEquals(Double.valueOf(10.8), result.closedBarClose());
        assertEquals(1_000L, closedX.get());
        assertEquals(10.8, closedClose.get(), 0.001);
        assertEquals(2, series.getItemCount());
        assertOhlc(series, 1, 10.8, 11.5, 10.8, 11.5);
    }

    @Test
    public void maxBarsTrimsOldestBarsAfterMutation() {
        OHLCSeries series = new OHLCSeries("K");

        KlineOhlcAggregator.applyTick(series, 10.0, 1_000L, -1, 2);
        KlineOhlcAggregator.applyTick(series, 11.0, 2_000L, -1, 2);
        KlineOhlcAggregator.Result result =
                KlineOhlcAggregator.applyTick(series, 12.0, 3_000L, -1, 2);

        assertTrue(result.trimmed());
        assertEquals(2, series.getItemCount());
        assertEquals(2_000L, KlineOhlcAggregator.itemXMillis((OHLCItem) series.getDataItem(0)));
        assertEquals(3_000L, KlineOhlcAggregator.itemXMillis((OHLCItem) series.getDataItem(1)));
    }

    @Test
    public void minuteAndSecondKeysAlignToExpectedBucket() {
        assertEquals(10_000L, KlineOhlcAggregator.alignTimestampMillis(12_345L, -10));
        assertEquals(120_000L, KlineOhlcAggregator.alignTimestampMillis(179_999L, 1));
        assertEquals(0L, KlineOhlcAggregator.alignTimestampMillis(119_999L, 2));
    }

    private static void assertOhlc(OHLCSeries series, int index,
            double open, double high, double low, double close) {
        OHLCItem item = (OHLCItem) series.getDataItem(index);
        assertEquals(open, item.getOpenValue(), 0.001);
        assertEquals(high, item.getHighValue(), 0.001);
        assertEquals(low, item.getLowValue(), 0.001);
        assertEquals(close, item.getCloseValue(), 0.001);
    }
}
