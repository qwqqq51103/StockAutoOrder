package StockMainAction.view.main;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MainViewFormattersTest {
    @Test
    public void slippageStatusClampsPercent() {
        assertEquals("滑價保護: 0%", MainViewFormatters.slippageStatus(-5));
        assertEquals("滑價保護: 12%", MainViewFormatters.slippageStatus(12));
        assertEquals("滑價保護: 50%", MainViewFormatters.slippageStatus(80));
    }

    @Test
    public void volumeBucketLabelUsesSecondsForSecondPeriodsAndMinutesForMinutePeriods() {
        String secondLabel = MainViewFormatters.volumeBucketLabel(1_000L, -1);
        String minuteLabel = MainViewFormatters.volumeBucketLabel(60_000L, 1);

        assertTrue(secondLabel.matches("\\d{2}:\\d{2}:\\d{2}"));
        assertTrue(minuteLabel.matches("\\d{2}:\\d{2}"));
    }

    @Test
    public void ohlcChangeFormatsSignedAbsoluteAndPercentChange() {
        assertEquals("+1.50 (+15.00%)", MainViewFormatters.ohlcChange(10.0, 11.5));
        assertEquals("-1.00 (-10.00%)", MainViewFormatters.ohlcChange(10.0, 9.0));
    }

    @Test
    public void ohlcHtmlContainsCoreValues() {
        String html = MainViewFormatters.ohlcHtml("09:00:00", 10.0, 12.0, 9.0, 11.0);

        assertTrue(html.contains("09:00:00"));
        assertTrue(html.contains("O: 10.00"));
        assertTrue(html.contains("H: 12.00"));
        assertTrue(html.contains("L: 9.00"));
        assertTrue(html.contains("C: 11.00"));
    }
}
