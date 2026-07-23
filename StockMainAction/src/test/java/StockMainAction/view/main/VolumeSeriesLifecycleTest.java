package StockMainAction.view.main;

import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class VolumeSeriesLifecycleTest {
    @Test
    public void upsertVolumeAccumulatesSameBucketAndTrimsOldestPoints() {
        XYSeries volume = new XYSeries("volume");

        VolumeSeriesLifecycle.upsertVolume(volume, 1_000L, 10, 2);
        VolumeSeriesLifecycle.upsertVolume(volume, 1_000L, 15, 2);
        VolumeSeriesLifecycle.upsertVolume(volume, 2_000L, 20, 2);
        VolumeSeriesLifecycle.upsertVolume(volume, 3_000L, 30, 2);

        assertEquals(2, volume.getItemCount());
        assertEquals(2_000L, volume.getX(0).longValue());
        assertEquals(3_000L, volume.getX(1).longValue());
        assertEquals(30, volume.getY(1).intValue());
    }

    @Test
    public void updateAllPeriodVolumesUpdatesSecondAndMinuteSeries() {
        Map<Integer, XYSeries> map = new HashMap<>();
        map.put(-1, new XYSeries("1s"));
        map.put(-10, new XYSeries("10s"));
        map.put(1, new XYSeries("1m"));

        int updated = VolumeSeriesLifecycle.updateAllPeriodVolumes(
                map, new int[]{1, 10}, new int[]{1}, 5, 12_345L, 300);

        assertEquals(3, updated);
        assertEquals(12_000L, map.get(-1).getX(0).longValue());
        assertEquals(10_000L, map.get(-10).getX(0).longValue());
        assertEquals(0L, map.get(1).getX(0).longValue());
    }

    @Test
    public void aggregateVolumeDataGroupsByTargetPeriod() {
        XYSeries source = new XYSeries("source");
        source.add(1_000L, 10.0);
        source.add(2_000L, 20.0);
        source.add(11_000L, 30.0);
        source.add(12_000L, 40.0);
        XYSeries target = new XYSeries("target");

        int count = VolumeSeriesLifecycle.aggregateVolumeData(source, target, -10, 2);

        assertEquals(2, count);
        assertEquals(0L, target.getX(0).longValue());
        assertEquals(30.0, target.getY(0).doubleValue(), 0.001);
        assertEquals(10_000L, target.getX(1).longValue());
        assertEquals(70.0, target.getY(1).doubleValue(), 0.001);
    }

    @Test
    public void recalculateMovingAveragesCalculatesRollingMa5AndMa10() {
        XYSeries volume = new XYSeries("volume");
        for (int i = 1; i <= 10; i++) {
            volume.add(i * 1_000L, i * 10.0);
        }
        XYSeries ma5 = new XYSeries("ma5");
        XYSeries ma10 = new XYSeries("ma10");

        VolumeSeriesLifecycle.recalculateMovingAverages(volume, ma5, ma10, 600);

        assertEquals(10, ma5.getItemCount());
        assertEquals(10.0, ma5.getY(0).doubleValue(), 0.001);
        assertEquals(80.0, ma5.getY(9).doubleValue(), 0.001);
        assertEquals(55.0, ma10.getY(9).doubleValue(), 0.001);
    }
}
