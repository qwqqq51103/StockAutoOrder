package StockMainAction.view.main;

import org.jfree.data.xy.XYSeries;

/**
 * Small, testable helper for event-driven technical indicator chart series.
 * The model owns indicator calculation; views only append validated samples.
 */
public final class TechnicalIndicatorSeriesUpdater {
    private TechnicalIndicatorSeriesUpdater() { }

    public static boolean updateTriple(
            int timeStep,
            double first,
            double second,
            double third,
            XYSeries firstSeries,
            XYSeries secondSeries,
            XYSeries thirdSeries,
            int maxPoints) {
        if (!Double.isFinite(first) || !Double.isFinite(second) || !Double.isFinite(third)) {
            return false;
        }
        if (firstSeries == null || secondSeries == null || thirdSeries == null) {
            return false;
        }
        addOrUpdate(firstSeries, timeStep, first, maxPoints);
        addOrUpdate(secondSeries, timeStep, second, maxPoints);
        addOrUpdate(thirdSeries, timeStep, third, maxPoints);
        return true;
    }

    private static void addOrUpdate(XYSeries series, int timeStep, double value, int maxPoints) {
        int existingIndex = findByX(series, timeStep);
        if (existingIndex >= 0) {
            series.updateByIndex(existingIndex, value);
        } else {
            series.add(timeStep, value);
        }
        int safeLimit = Math.max(1, maxPoints);
        while (series.getItemCount() > safeLimit) {
            series.remove(0);
        }
    }

    private static int findByX(XYSeries series, int timeStep) {
        for (int i = 0; i < series.getItemCount(); i++) {
            if (series.getX(i).intValue() == timeStep) {
                return i;
            }
        }
        return -1;
    }
}
