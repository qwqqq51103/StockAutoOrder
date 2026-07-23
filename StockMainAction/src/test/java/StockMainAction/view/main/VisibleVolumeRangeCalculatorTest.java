package StockMainAction.view.main;

import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VisibleVolumeRangeCalculatorTest {
    @Test
    public void calculatesRangeFromOnlyVisibleVolumePoints() {
        XYSeries volume = new XYSeries("volume");
        volume.add(1_000L, 100.0);
        volume.add(2_000L, 250.0);
        volume.add(3_000L, 1_000.0);

        VisibleVolumeRangeCalculator.AxisRange range =
                VisibleVolumeRangeCalculator.calculate(volume, new Range(1_000L, 2_000L));

        assertEquals(0.0, range.lower(), 0.001);
        assertEquals(295.0, range.upper(), 0.001);
        assertEquals(250.0, range.maxVolume(), 0.001);
        assertEquals(2, range.visiblePoints());
    }

    @Test
    public void fallsBackToUnitRangeWhenVisibleWindowHasNoVolume() {
        XYSeries volume = new XYSeries("volume");
        volume.add(1_000L, 100.0);

        VisibleVolumeRangeCalculator.AxisRange range =
                VisibleVolumeRangeCalculator.calculate(volume, new Range(2_000L, 3_000L));

        assertEquals(0.0, range.lower(), 0.001);
        assertEquals(1.0, range.upper(), 0.001);
        assertEquals(0.0, range.maxVolume(), 0.001);
        assertEquals(0, range.visiblePoints());
    }

    @Test
    public void ignoresInvalidVolumeValues() {
        XYSeries volume = new XYSeries("volume");
        volume.add(1_000L, Double.NaN);
        volume.add(2_000L, 50.0);

        VisibleVolumeRangeCalculator.AxisRange range =
                VisibleVolumeRangeCalculator.calculate(volume, new Range(1_000L, 2_000L));

        assertEquals(59.0, range.upper(), 0.001);
        assertEquals(50.0, range.maxVolume(), 0.001);
        assertEquals(1, range.visiblePoints());
    }
}
