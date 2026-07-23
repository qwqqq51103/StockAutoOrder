package StockMainAction.view.main;

import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChartMarkerLifecycleTest {
    @Test
    public void upsertUpdatesExistingMarkerAndKeepsMaxPointLimit() {
        XYSeries markers = new XYSeries("markers");

        ChartMarkerLifecycle.upsert(markers, 1_000L, 10.0, 2);
        ChartMarkerLifecycle.upsert(markers, 1_000L, 11.0, 2);
        ChartMarkerLifecycle.upsert(markers, 2_000L, 12.0, 2);
        ChartMarkerLifecycle.upsert(markers, 3_000L, 13.0, 2);

        assertEquals(2, markers.getItemCount());
        assertEquals(2_000L, markers.getX(0).longValue());
        assertEquals(3_000L, markers.getX(1).longValue());
        assertEquals(13.0, markers.getY(1).doubleValue(), 0.001);
    }

    @Test
    public void trimToOhlcWindowRemovesMarkersBeforeFirstVisibleBar() {
        OHLCSeries ohlc = new OHLCSeries("K");
        KlineOhlcAggregator.applyTick(ohlc, 10.0, 1_000L, -1, 10);
        KlineOhlcAggregator.applyTick(ohlc, 11.0, 2_000L, -1, 10);

        XYSeries markers = new XYSeries("markers");
        markers.add(500L, 9.0);
        markers.add(1_000L, 10.0);
        markers.add(2_000L, 11.0);

        int removed = ChartMarkerLifecycle.trimToOhlcWindow(ohlc, markers);

        assertEquals(1, removed);
        assertEquals(2, markers.getItemCount());
        assertEquals(1_000L, markers.getX(0).longValue());
    }

    @Test
    public void countOhlcItemsInRangeUsesInclusiveDomain() {
        OHLCSeries ohlc = new OHLCSeries("K");
        KlineOhlcAggregator.applyTick(ohlc, 10.0, 1_000L, -1, 10);
        KlineOhlcAggregator.applyTick(ohlc, 11.0, 2_000L, -1, 10);
        KlineOhlcAggregator.applyTick(ohlc, 12.0, 3_000L, -1, 10);

        assertEquals(2, ChartMarkerLifecycle.countOhlcItemsInRange(ohlc, 1_000L, 2_000L));
        assertEquals(0, ChartMarkerLifecycle.countOhlcItemsInRange(ohlc, 4_000L, 5_000L));
    }

    @Test
    public void markersAllowedHonorsAutoHideThreshold() {
        assertFalse(ChartMarkerLifecycle.markersAllowed(61, true, 60));
        assertTrue(ChartMarkerLifecycle.markersAllowed(61, false, 60));
        assertTrue(ChartMarkerLifecycle.markersAllowed(10, true, 5));
    }
}
