package StockMainAction.view.chart;

import java.util.List;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/** Stateless dataset retention policy extracted from the Swing window. */
public final class ChartDataLimiter {
    private ChartDataLimiter() { }

    public static void trim(XYPlot plot, int maxPoints) {
        requireValidLimit(maxPoints);
        for (int datasetIndex = 0; datasetIndex < plot.getDatasetCount(); datasetIndex++) {
            XYDataset dataset = plot.getDataset(datasetIndex);
            if (dataset instanceof XYSeriesCollection collection) {
                for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
                    trim(collection.getSeries(seriesIndex), maxPoints);
                }
            }
        }
    }

    public static void trim(CategoryPlot plot, int maxPoints) {
        requireValidLimit(maxPoints);
        CategoryDataset dataset = plot.getDataset();
        if (!(dataset instanceof DefaultCategoryDataset categoryDataset)) return;

        List<?> columnKeys = categoryDataset.getColumnKeys();
        while (columnKeys.size() > maxPoints) {
            Comparable<?> oldestKey = requireComparable(columnKeys.get(0));
            for (Object rowKeyValue : categoryDataset.getRowKeys()) {
                categoryDataset.removeValue(requireComparable(rowKeyValue), oldestKey);
            }
            columnKeys = categoryDataset.getColumnKeys();
        }
    }

    private static void trim(XYSeries series, int maxPoints) {
        boolean notificationsEnabled = series.getNotify();
        series.setNotify(false);
        try {
            while (series.getItemCount() > maxPoints) series.remove(0);
        } finally {
            series.setNotify(notificationsEnabled);
        }
    }

    private static Comparable<?> requireComparable(Object value) {
        if (value instanceof Comparable<?> comparable) return comparable;
        throw new IllegalStateException("Dataset key is not comparable");
    }

    private static void requireValidLimit(int maxPoints) {
        if (maxPoints < 1) throw new IllegalArgumentException("maxPoints must be positive");
    }
}
