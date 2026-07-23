package StockMainAction.view.main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Main-window game status strip.
 *
 * <p>The strip keeps game-critical state visible without forcing the player to
 * switch to the separate control window.</p>
 */
public final class MainDashboardStrip extends JPanel {
    private final JLabel modeValue = createValueLabel("新手模式");
    private final JLabel speedValue = createValueLabel("1x");
    private final JLabel seedValue = createValueLabel("-");
    private final JLabel scoreValue = createValueLabel("0");
    private final JLabel rankValue = createValueLabel("-");
    private final JLabel eventValue = createValueLabel("尚無事件");

    public MainDashboardStrip() {
        super(new BorderLayout(10, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(createMetric("模式", modeValue));
        left.add(createMetric("速度", speedValue));
        left.add(createMetric("Seed", seedValue));
        left.add(createMetric("分數", scoreValue));
        left.add(createMetric("評級", rankValue));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JLabel eventLabel = new JLabel("最新事件");
        eventLabel.setFont(eventLabel.getFont().deriveFont(Font.BOLD));
        right.add(eventLabel);
        right.add(eventValue);

        add(left, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    public void update(String mode, String speed, long seed, int score, String rank, String event) {
        Runnable task = () -> {
            modeValue.setText(nonBlank(mode, "新手模式"));
            speedValue.setText(nonBlank(speed, "1x"));
            seedValue.setText(Long.toString(seed));
            scoreValue.setText(Integer.toString(Math.max(0, score)));
            rankValue.setText(nonBlank(rank, "-"));
            eventValue.setText(nonBlank(event, "尚無事件"));
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    public void setMode(String mode) {
        update(mode, speedValue.getText(), parseSeed(), parseScore(), rankValue.getText(), eventValue.getText());
    }

    private long parseSeed() {
        try {
            return Long.parseLong(seedValue.getText());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private int parseScore() {
        try {
            return Integer.parseInt(scoreValue.getText());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static JPanel createMetric(String name, JLabel value) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        JLabel label = new JLabel(name + "：");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label);
        panel.add(value);
        return panel;
    }

    private static JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft JhengHei UI", Font.PLAIN, 13));
        return label;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
