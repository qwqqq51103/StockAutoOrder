package StockMainAction.view.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Test;

public class ChartDataLimiterTest {
    @Test
    public void trimsOldestXyItemsAndRestoresNotifications() {
        XYSeries series = new XYSeries("price");
        for (int i = 0; i < 10; i++) series.add(i, i * 2);
        XYPlot plot = new XYPlot();
        plot.setDataset(new XYSeriesCollection(series));

        ChartDataLimiter.trim(plot, 3);

        assertEquals(3, series.getItemCount());
        assertEquals(7, series.getX(0).intValue());
        assertTrue(series.getNotify());
    }

    @Test
    public void trimsOldestCategoryColumnsAcrossRows() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int i = 0; i < 5; i++) {
            dataset.addValue(i, "buy", "t" + i);
            dataset.addValue(i, "sell", "t" + i);
        }
        CategoryPlot plot = new CategoryPlot();
        plot.setDataset(dataset);

        ChartDataLimiter.trim(plot, 2);

        assertEquals(2, dataset.getColumnCount());
        assertEquals("t3", dataset.getColumnKey(0));
        assertEquals(2, dataset.getRowCount());
    }
}
