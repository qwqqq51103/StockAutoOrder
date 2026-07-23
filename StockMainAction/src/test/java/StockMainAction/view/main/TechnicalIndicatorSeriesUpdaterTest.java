package StockMainAction.view.main;

import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import static org.junit.Assert.*;

public class TechnicalIndicatorSeriesUpdaterTest {
    @Test
    public void updateTripleAddsOrUpdatesThreeSeriesTogether() {
        XYSeries first = new XYSeries("first");
        XYSeries second = new XYSeries("second");
        XYSeries third = new XYSeries("third");

        assertTrue(TechnicalIndicatorSeriesUpdater.updateTriple(
                1, 10.0, 20.0, 30.0, first, second, third, 10));
        assertTrue(TechnicalIndicatorSeriesUpdater.updateTriple(
                1, 11.0, 21.0, 31.0, first, second, third, 10));

        assertEquals(1, first.getItemCount());
        assertEquals(11.0, first.getY(0).doubleValue(), 0.001);
        assertEquals(21.0, second.getY(0).doubleValue(), 0.001);
        assertEquals(31.0, third.getY(0).doubleValue(), 0.001);
    }

    @Test
    public void updateTripleRejectsInvalidValuesWithoutPartialWrite() {
        XYSeries first = new XYSeries("first");
        XYSeries second = new XYSeries("second");
        XYSeries third = new XYSeries("third");

        assertFalse(TechnicalIndicatorSeriesUpdater.updateTriple(
                1, Double.NaN, 20.0, 30.0, first, second, third, 10));

        assertEquals(0, first.getItemCount());
        assertEquals(0, second.getItemCount());
        assertEquals(0, third.getItemCount());
    }

    @Test
    public void updateTripleTrimsOldPointsByLimit() {
        XYSeries first = new XYSeries("first");
        XYSeries second = new XYSeries("second");
        XYSeries third = new XYSeries("third");

        for (int i = 0; i < 5; i++) {
            assertTrue(TechnicalIndicatorSeriesUpdater.updateTriple(
                    i, i, i + 10.0, i + 20.0, first, second, third, 3));
        }

        assertEquals(3, first.getItemCount());
        assertEquals(2.0, first.getX(0).doubleValue(), 0.001);
        assertEquals(4.0, first.getX(2).doubleValue(), 0.001);
        assertEquals(3, second.getItemCount());
        assertEquals(3, third.getItemCount());
    }
}
