package StockMainAction.view.main;

import org.jfree.data.Range;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChartDomainRangeControllerTest {
    @Test
    public void latestWindowReturnsAutoRangeWhenSeriesIsShorterThanVisibleCandles() {
        OHLCSeries series = new OHLCSeries("K");
        KlineOhlcAggregator.applyTick(series, 10.0, 1_000L, -1, 10);

        ChartDomainRangeController.DomainDecision decision =
                ChartDomainRangeController.latestWindow(series, 20);

        assertTrue(decision.autoRange());
        assertEquals(1, decision.candleCount());
    }

    @Test
    public void latestWindowReturnsLastNCandleDomain() {
        OHLCSeries series = new OHLCSeries("K");
        for (int i = 1; i <= 10; i++) {
            KlineOhlcAggregator.applyTick(series, 10.0 + i, i * 1_000L, -1, 20);
        }

        ChartDomainRangeController.DomainDecision decision =
                ChartDomainRangeController.latestWindow(series, 5);

        assertFalse(decision.autoRange());
        assertEquals(5, decision.candleCount());
        assertEquals(6_000.0, decision.lower(), 0.001);
        assertEquals(10_999.0, decision.upper(), 0.001);
    }

    @Test
    public void fullWindowAddsTwoPeriodMargins() {
        OHLCSeries series = new OHLCSeries("K");
        KlineOhlcAggregator.applyTick(series, 10.0, 10_000L, -10, 10);
        KlineOhlcAggregator.applyTick(series, 11.0, 20_000L, -10, 10);

        ChartDomainRangeController.DomainDecision decision =
                ChartDomainRangeController.fullWindowWithMargin(series, -10);

        assertFalse(decision.autoRange());
        assertEquals(-10_000.0, decision.lower(), 0.001);
        assertEquals(40_999.0, decision.upper(), 0.001);
    }

    @Test
    public void priceRangeUsesVisibleOhlcAndPadding() {
        OHLCSeries series = new OHLCSeries("K");
        KlineOhlcAggregator.applyTick(series, 10.0, 1_000L, -1, 10);
        KlineOhlcAggregator.applyTick(series, 12.0, 2_000L, -1, 10);
        KlineOhlcAggregator.applyTick(series, 8.0, 3_000L, -1, 10);

        Range range = ChartDomainRangeController.priceRange(series, 2, true);

        assertEquals(7.68, range.getLowerBound(), 0.001);
        assertEquals(12.32, range.getUpperBound(), 0.001);
    }

    @Test
    public void priceRangeExpandsFlatPrices() {
        OHLCSeries series = new OHLCSeries("K");
        KlineOhlcAggregator.applyTick(series, 10.0, 1_000L, -1, 10);

        Range range = ChartDomainRangeController.priceRange(series, 20, false);

        assertEquals(8.84, range.getLowerBound(), 0.001);
        assertEquals(11.16, range.getUpperBound(), 0.001);
        assertNull(ChartDomainRangeController.priceRange(null, 20, false));
    }

    @Test
    public void autoVisibleCandlesIsClampedByWidth() {
        assertEquals(42, ChartDomainRangeController.autoVisibleCandlesForWidth(100, 7));
        assertEquals(128, ChartDomainRangeController.autoVisibleCandlesForWidth(900, 7));
        assertEquals(180, ChartDomainRangeController.autoVisibleCandlesForWidth(10_000, 7));
    }
}
