package StockMainAction.view.main;

import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChartOverlayControllerTest {
    @Test
    public void smaAtUsesAvailableTrailingCloses() {
        OHLCSeries series = new OHLCSeries("K");
        KlineOhlcAggregator.applyTick(series, 10.0, 1_000L, -1, 10);
        KlineOhlcAggregator.applyTick(series, 20.0, 2_000L, -1, 10);
        KlineOhlcAggregator.applyTick(series, 30.0, 3_000L, -1, 10);

        assertEquals(10.0, ChartOverlayController.smaAt(series, 5, 0), 0.001);
        assertEquals(25.0, ChartOverlayController.smaAt(series, 2, 2), 0.001);
    }

    @Test
    public void emaUsesPreviousEmaAndPeriodMultiplier() {
        assertEquals(11.0, ChartOverlayController.ema(12.0, 10.0, 3), 0.001);
        assertEquals(12.0, ChartOverlayController.ema(12.0, Double.NaN, 3), 0.001);
    }

    @Test
    public void updateOrAddUpdatesOnlyLastMatchingXOtherwiseAppends() {
        XYSeries series = new XYSeries("overlay");

        assertTrue(ChartOverlayController.updateOrAdd(series, 1_000L, 10.0));
        assertTrue(ChartOverlayController.updateOrAdd(series, 1_000L, 11.0));
        assertTrue(ChartOverlayController.updateOrAdd(series, 2_000L, 12.0));
        assertFalse(ChartOverlayController.updateOrAdd(series, 3_000L, Double.NaN));

        assertEquals(2, series.getItemCount());
        assertEquals(11.0, series.getY(0).doubleValue(), 0.001);
        assertEquals(12.0, series.getY(1).doubleValue(), 0.001);
    }

    @Test
    public void shouldUpdateCurrentHonorsNewCandleOrThrottle() {
        assertTrue(ChartOverlayController.shouldUpdateCurrent(true, 1_000L, 999L, 500L));
        assertFalse(ChartOverlayController.shouldUpdateCurrent(false, 1_100L, 1_000L, 500L));
        assertTrue(ChartOverlayController.shouldUpdateCurrent(false, 1_600L, 1_000L, 500L));
    }
}
