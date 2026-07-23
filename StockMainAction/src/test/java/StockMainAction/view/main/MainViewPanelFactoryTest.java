package StockMainAction.view.main;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MainViewPanelFactoryTest {
    @Test
    public void ohlcInfoLabelHasReadableDefaults() {
        JLabel label = MainViewPanelFactory.ohlcInfoLabel();

        assertEquals(" ", label.getText());
        assertTrue(label.isOpaque());
        assertNotNull(label.getBorder());
    }

    @Test
    public void layeredChartContainsChartAndOhlcLabel() {
        XYSeries series = new XYSeries("s");
        JFreeChart chart = ChartFactory.createXYLineChart("t", "x", "y", new XYSeriesCollection(series));
        ChartPanel chartPanel = new ChartPanel(chart);
        JLabel label = MainViewPanelFactory.ohlcInfoLabel();

        JLayeredPane pane = MainViewPanelFactory.layeredChartWithOhlc(chartPanel, label);

        assertEquals(2, pane.getComponentCount());
        assertEquals(chartPanel, pane.getComponent(1));
        assertEquals(label, pane.getComponent(0));
    }

    @Test
    public void statusBarThemeToggleInvokesCallback() {
        AtomicInteger calls = new AtomicInteger();
        JPanel statusBar = MainViewPanelFactory.statusBar(new JLabel("value"), calls::incrementAndGet);

        JToggleButton toggle = findToggle(statusBar);
        toggle.doClick();

        assertEquals(1, calls.get());
    }

    @Test
    public void signalItemCreatesNameAndCounterLabels() {
        JPanel item = MainViewPanelFactory.signalItem("▲ 多頭訊號", Color.RED,
                new Font("Dialog", Font.PLAIN, 12),
                new Font("Dialog", Font.BOLD, 12));

        assertEquals(2, item.getComponentCount());
        assertTrue(item.getComponent(0) instanceof JLabel);
        assertTrue(item.getComponent(1) instanceof JLabel);
        assertEquals("0", ((JLabel) item.getComponent(1)).getText());
        assertEquals(Color.RED, item.getComponent(1).getForeground());
    }

    @Test
    public void separatorUsesCompactStatusBarSize() {
        JPanel separator = MainViewPanelFactory.separator();

        assertEquals(new Dimension(1, 25), separator.getPreferredSize());
        assertEquals(new Color(200, 200, 200), separator.getBackground());
    }

    private static JToggleButton findToggle(Component component) {
        if (component instanceof JToggleButton) {
            return (JToggleButton) component;
        }
        if (component instanceof JPanel) {
            JPanel panel = (JPanel) component;
            for (Component child : panel.getComponents()) {
                JToggleButton found = findToggle(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
