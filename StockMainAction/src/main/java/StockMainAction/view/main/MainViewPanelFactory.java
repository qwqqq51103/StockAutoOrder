package StockMainAction.view.main;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import org.jfree.chart.ChartPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * MainView 的低耦合 Swing panel 建構工具。
 */
public final class MainViewPanelFactory {
    private MainViewPanelFactory() {
    }

    public static JLabel ohlcInfoLabel() {
        JLabel label = new JLabel(" ");
        label.setFont(new Font("Monospaced", Font.BOLD, 12));
        label.setForeground(new Color(60, 60, 60));
        label.setOpaque(true);
        label.setBackground(new Color(255, 255, 255, 230));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return label;
    }

    public static JLayeredPane layeredChartWithOhlc(ChartPanel chartPanel, JLabel ohlcInfoLabel) {
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setMinimumSize(new Dimension(480, 320));
        chartPanel.setBounds(0, 0, 800, 600);
        layeredPane.add(chartPanel, JLayeredPane.DEFAULT_LAYER);
        ohlcInfoLabel.setBounds(10, 10, 400, 80);
        layeredPane.add(ohlcInfoLabel, JLayeredPane.PALETTE_LAYER);
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                Dimension size = layeredPane.getSize();
                chartPanel.setBounds(0, 0, size.width, size.height);
                ohlcInfoLabel.setBounds(10, 10, 400, 80);
            }
        });
        return layeredPane;
    }

    public static JPanel statusBar(JLabel chartValueLabel, Runnable toggleTheme) {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.add(chartValueLabel, BorderLayout.WEST);

        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JToggleButton themeToggleButton = new JToggleButton("暗黑模式");
        themeToggleButton.addActionListener(e -> {
            if (toggleTheme != null) {
                toggleTheme.run();
            }
        });
        rightStatusPanel.add(themeToggleButton);
        statusBar.add(rightStatusPanel, BorderLayout.EAST);
        return statusBar;
    }

    public static JPanel signalItem(String label, Color color, Font labelFont, Font countFont) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);

        String[] parts = label.split(" ", 2);
        String symbol = parts.length > 0 ? parts[0] : "";
        String text = parts.length > 1 ? parts[1] : "";
        String colorHex = String.format("#%02x%02x%02x",
                color.getRed(), color.getGreen(), color.getBlue());
        String htmlLabel = String.format(
                "<html><span style='color:%s; font-weight:bold;'>%s</span> %s</html>",
                colorHex, symbol, text);

        JLabel nameLabel = new JLabel(htmlLabel);
        nameLabel.setFont(labelFont);
        nameLabel.setForeground(new Color(80, 80, 80));

        JLabel countLabel = new JLabel("0");
        countLabel.setFont(countFont);
        countLabel.setForeground(color);

        panel.add(nameLabel);
        panel.add(countLabel);
        return panel;
    }

    public static JPanel separator() {
        JPanel separator = new JPanel();
        separator.setPreferredSize(new Dimension(1, 25));
        separator.setBackground(new Color(200, 200, 200));
        return separator;
    }
}
