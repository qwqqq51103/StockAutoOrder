package StockMainAction.view.main;

import java.awt.Component;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Test;

import static org.junit.Assert.*;

public class TechnicalIndicatorPanelFactoryTest {
    @Test
    public void macdPanelWiresChartCallbacks() {
        AtomicInteger enableCalls = new AtomicInteger();
        AtomicInteger setupCalls = new AtomicInteger();
        JPanel panel = TechnicalIndicatorPanelFactory.macdPanel(
                chart("MACD"),
                chartPanel -> enableCalls.incrementAndGet(),
                (chartPanel, title) -> {
                    assertEquals("MACD", title);
                    setupCalls.incrementAndGet();
                });

        assertNotNull(find(panel, ChartPanel.class));
        assertEquals(1, enableCalls.get());
        assertEquals(1, setupCalls.get());
    }

    @Test
    public void nullChartReturnsReadablePlaceholder() {
        JPanel panel = TechnicalIndicatorPanelFactory.kdjPanel(null, null, null);

        assertNull(findOrNull(panel, ChartPanel.class));
        assertTrue(panel.getComponentCount() >= 2);
    }

    private static JFreeChart chart(String title) {
        XYSeries series = new XYSeries("x");
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);
        return ChartFactory.createXYLineChart(
                title, "時間", "值", dataset,
                PlotOrientation.VERTICAL, true, true, false);
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        T found = findOrNull(root, type);
        if (found == null) {
            throw new AssertionError("Component not found: " + type.getName());
        }
        return found;
    }

    private static <T extends Component> T findOrNull(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof JPanel panel) {
            for (Component child : panel.getComponents()) {
                T found = findOrNull(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
