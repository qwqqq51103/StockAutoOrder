package StockMainAction.view.main;

import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KlineDatasetSwitcherTest {
    @Test
    public void periodSecondsAndMultiplierHandleSecondAndMinuteKeys() {
        assertEquals(10, KlineDatasetSwitcher.periodSeconds(-10));
        assertEquals(300, KlineDatasetSwitcher.periodSeconds(5));
        assertEquals(Integer.valueOf(6), KlineDatasetSwitcher.multiplierIfWholeMultiple(-10, -60));
        assertEquals(Integer.valueOf(6), KlineDatasetSwitcher.multiplierIfWholeMultiple(-10, 1));
        assertNull(KlineDatasetSwitcher.multiplierIfWholeMultiple(-30, -10));
    }

    @Test
    public void aggregateOhlcCombinesSourceBarsIntoTargetBars() {
        OHLCSeries source = new OHLCSeries("source");
        KlineOhlcAggregator.applyTick(source, 10.0, 1_000L, -1, 10);
        KlineOhlcAggregator.applyTick(source, 12.0, 2_000L, -1, 10);
        KlineOhlcAggregator.applyTick(source, 9.0, 3_000L, -1, 10);
        KlineOhlcAggregator.applyTick(source, 11.0, 4_000L, -1, 10);
        OHLCSeries target = new OHLCSeries("target");

        int count = KlineDatasetSwitcher.aggregateOhlc(source, target, -2, 2);

        assertEquals(2, count);
        OHLCItem first = (OHLCItem) target.getDataItem(0);
        assertEquals(10.0, first.getOpenValue(), 0.001);
        assertEquals(12.0, first.getHighValue(), 0.001);
        assertEquals(10.0, first.getLowValue(), 0.001);
        assertEquals(12.0, first.getCloseValue(), 0.001);
        OHLCItem second = (OHLCItem) target.getDataItem(1);
        assertEquals(12.0, second.getOpenValue(), 0.001);
        assertEquals(12.0, second.getHighValue(), 0.001);
        assertEquals(9.0, second.getLowValue(), 0.001);
        assertEquals(11.0, second.getCloseValue(), 0.001);
    }

    @Test
    public void realignMarkerSeriesAlignsAndDeduplicatesByPeriodBucket() {
        XYSeries markers = new XYSeries("markers");
        markers.add(1_100L, 10.0);
        markers.add(1_900L, 11.0);
        markers.add(2_100L, 12.0);

        int count = KlineDatasetSwitcher.realignMarkerSeries(markers, -1);

        assertEquals(2, count);
        assertEquals(1_000L, markers.getX(0).longValue());
        assertEquals(10.0, markers.getY(0).doubleValue(), 0.001);
        assertEquals(2_000L, markers.getX(1).longValue());
        assertEquals(12.0, markers.getY(1).doubleValue(), 0.001);
    }
}
