package StockMainAction.view.main;

import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OhlcInfoPresenterTest {
    @Test
    public void shouldUpdateWhenCandleChangesOrThrottleExpires() {
        assertTrue(OhlcInfoPresenter.shouldUpdate(2_000L, 1_000L, 1_100L, 1_000L, 200L));
        assertFalse(OhlcInfoPresenter.shouldUpdate(1_000L, 1_000L, 1_100L, 1_000L, 200L));
        assertTrue(OhlcInfoPresenter.shouldUpdate(1_000L, 1_000L, 1_250L, 1_000L, 200L));
    }

    @Test
    public void htmlContainsOhlcValuesAndPositiveColor() {
        String html = OhlcInfoPresenter.html("09:00:00", 10.0, 12.0, 9.0, 11.0);

        assertTrue(html.contains("09:00:00"));
        assertTrue(html.contains("+1.00 (+10.00%)"));
        assertTrue(html.contains("#26a69a"));
        assertTrue(html.contains("O: 10.00"));
        assertTrue(html.contains("C: <span"));
    }

    @Test
    public void htmlFromItemUsesOhlcItemValues() {
        OHLCSeries series = new OHLCSeries("K");
        KlineOhlcAggregator.applyTick(series, 10.0, 1_000L, -1, 10);
        KlineOhlcAggregator.applyTick(series, 9.0, 1_500L, -1, 10);

        String html = OhlcInfoPresenter.html((OHLCItem) series.getDataItem(0));

        assertTrue(html.contains("-1.00 (-10.00%)"));
        assertTrue(html.contains("#ef5350"));
        assertTrue(html.contains("L: 9.00"));
    }
}
