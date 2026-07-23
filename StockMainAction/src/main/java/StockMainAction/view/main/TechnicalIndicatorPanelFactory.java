package StockMainAction.view.main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

/** Builds the event-driven technical indicator panels used by MainView. */
public final class TechnicalIndicatorPanelFactory {
    private static final Dimension INDICATOR_CHART_MIN_SIZE = new Dimension(480, 260);
    private static final Color READY_GREEN = new Color(0, 150, 0);

    private TechnicalIndicatorPanelFactory() { }

    public static JPanel macdPanel(
            JFreeChart chart,
            Consumer<ChartPanel> enableChartInteraction,
            BiConsumer<ChartPanel, String> setupChartInteraction) {
        return indicatorPanel(chart, "MACD", paramPanel(new String[][]{
                {"短期EMA:", "12"},
                {"長期EMA:", "26"},
                {"信號線:", "9"}
        }), enableChartInteraction, setupChartInteraction);
    }

    public static JPanel bollingerPanel(
            JFreeChart chart,
            Consumer<ChartPanel> enableChartInteraction,
            BiConsumer<ChartPanel, String> setupChartInteraction) {
        return indicatorPanel(chart, "布林帶", paramPanel(new String[][]{
                {"SMA週期:", "20"},
                {"標準差倍數:", "2.0"}
        }), enableChartInteraction, setupChartInteraction);
    }

    public static JPanel kdjPanel(
            JFreeChart chart,
            Consumer<ChartPanel> enableChartInteraction,
            BiConsumer<ChartPanel, String> setupChartInteraction) {
        return indicatorPanel(chart, "KDJ", paramPanel(new String[][]{
                {"N週期:", "9"},
                {"K週期:", "3"},
                {"D週期:", "3"}
        }), enableChartInteraction, setupChartInteraction);
    }

    private static JPanel indicatorPanel(
            JFreeChart chart,
            String title,
            JPanel paramPanel,
            Consumer<ChartPanel> enableChartInteraction,
            BiConsumer<ChartPanel, String> setupChartInteraction) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(paramPanel, BorderLayout.NORTH);

        if (chart == null) {
            panel.add(new JLabel(title + "圖表尚未初始化"), BorderLayout.CENTER);
            return panel;
        }

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setMinimumSize(INDICATOR_CHART_MIN_SIZE);
        if (enableChartInteraction != null) {
            enableChartInteraction.accept(chartPanel);
        }
        if (setupChartInteraction != null) {
            setupChartInteraction.accept(chartPanel, title);
        }
        panel.add(chartPanel, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel paramPanel(String[][] entries) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (String[] entry : entries) {
            panel.add(new JLabel(entry[0]));
            panel.add(new JLabel(entry[1]));
            panel.add(Box.createHorizontalStrut(10));
        }
        JLabel statusLabel = new JLabel("● 自動更新中");
        statusLabel.setForeground(READY_GREEN);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(statusLabel);
        return panel;
    }
}
