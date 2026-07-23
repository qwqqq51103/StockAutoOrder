package StockMainAction.view.main;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * 建立參數設定視窗中不持有交易狀態的簡單分頁。
 *
 * <p>這個類別只負責 Swing 控件配置與 callback 綁定，真正的狀態套用仍由 MainView 決定。</p>
 */
public final class SettingsTabFactory {
    public static final String[] PERFORMANCE_MODES = {"節能", "平衡", "效能"};
    public static final String[] EVENT_MODES = {"一般", "新聞", "財報"};

    private SettingsTabFactory() {
    }

    public static JPanel themeTab(Runnable toggleTheme) {
        JPanel panel = flowPanel();
        JButton themeButton = new JButton("切換主題");
        themeButton.addActionListener(e -> run(toggleTheme));
        panel.add(themeButton);
        panel.add(new JLabel("（套用至整個 UI）"));
        return panel;
    }

    public static JPanel logTab(Runnable openLogViewer) {
        JPanel panel = flowPanel();
        JButton openButton = new JButton("開啟日誌查看器");
        openButton.addActionListener(e -> run(openLogViewer));
        panel.add(openButton);
        panel.add(new JLabel("（在日誌視窗內可調整輸出級別/Console）"));
        return panel;
    }

    public static JPanel performanceTab(PerformanceModeHandler handler) {
        JPanel panel = flowPanel();
        JComboBox<String> comboBox = new JComboBox<>(PERFORMANCE_MODES);
        comboBox.setSelectedItem("平衡");
        comboBox.addActionListener(e -> {
            Object value = comboBox.getSelectedItem();
            if (handler != null && value instanceof String) {
                handler.apply((String) value);
            }
        });
        panel.add(new JLabel("效能模式:"));
        panel.add(comboBox);
        return panel;
    }

    public static JPanel commandTab(Runnable openCommandPalette) {
        JPanel panel = flowPanel();
        JButton commandButton = new JButton("開啟命令面板 (Ctrl+K)");
        commandButton.addActionListener(e -> run(openCommandPalette));
        panel.add(commandButton);
        return panel;
    }

    public static JPanel fontTab(Runnable decrease, Runnable increase, Runnable reset) {
        JPanel panel = flowPanel();
        JButton fontMinus = new JButton("A-");
        JButton fontPlus = new JButton("A+");
        JButton fontReset = new JButton("A");
        fontMinus.addActionListener(e -> run(decrease));
        fontPlus.addActionListener(e -> run(increase));
        fontReset.addActionListener(e -> run(reset));
        panel.add(new JLabel("全域字級:"));
        panel.add(fontMinus);
        panel.add(fontPlus);
        panel.add(fontReset);
        return panel;
    }

    public static JPanel klineFollowTab(boolean autoFollowLatest, int visibleCandles, KlineFollowHandler handler) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 8, 6, 8);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;

        JCheckBox followCheckBox = new JCheckBox("自動跟隨最新K線", autoFollowLatest);
        JSpinner visibleSpinner = new JSpinner(new SpinnerNumberModel(visibleCandles, 5, 500, 5));
        JButton applyButton = new JButton("套用");
        JButton showAllButton = new JButton("顯示全部");

        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(followCheckBox, constraints);
        constraints.gridy = 1;
        panel.add(new JLabel("追隨模式顯示根數(N):"), constraints);
        constraints.gridx = 1;
        panel.add(visibleSpinner, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        panel.add(applyButton, constraints);
        constraints.gridx = 1;
        panel.add(showAllButton, constraints);

        applyButton.addActionListener(e -> {
            if (handler != null) {
                int visible = ((Number) visibleSpinner.getValue()).intValue();
                handler.apply(followCheckBox.isSelected(), visible);
            }
        });
        showAllButton.addActionListener(e -> {
            followCheckBox.setSelected(false);
            if (handler != null) {
                handler.showAll();
            }
        });

        return panel;
    }

    public static JPanel eventModeTab(EventModeHandler handler) {
        JPanel panel = flowPanel();
        JComboBox<String> modeComboBox = new JComboBox<>(EVENT_MODES);
        JSpinner windowSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 600, 10));
        JSpinner consecutiveSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(65, 30, 95, 1));
        JButton applyButton = new JButton("套用");

        panel.add(new JLabel("模式:"));
        panel.add(modeComboBox);
        panel.add(new JLabel("窗"));
        panel.add(windowSpinner);
        panel.add(new JLabel("連"));
        panel.add(consecutiveSpinner);
        panel.add(new JLabel("門檻%"));
        panel.add(thresholdSpinner);
        panel.add(applyButton);

        applyButton.addActionListener(e -> {
            if (handler != null) {
                String mode = Objects.toString(modeComboBox.getSelectedItem(), "一般");
                int window = ((Number) windowSpinner.getValue()).intValue();
                int consecutive = ((Number) consecutiveSpinner.getValue()).intValue();
                int threshold = ((Number) thresholdSpinner.getValue()).intValue();
                handler.apply(mode, window, consecutive, threshold);
            }
        });

        return panel;
    }

    public static JPanel replaceIntervalTab(IntConsumer applyIntervalTicks) {
        JPanel panel = flowPanel();
        panel.add(new JLabel("主力撤換間隔(ticks):"));
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(10, 1, 200, 1));
        JButton applyButton = new JButton("套用");
        applyButton.addActionListener(e -> {
            if (applyIntervalTicks != null) {
                applyIntervalTicks.accept(((Number) spinner.getValue()).intValue());
            }
        });
        panel.add(spinner);
        panel.add(applyButton);
        return panel;
    }

    public static JPanel resetTab(Runnable resetCharts) {
        JPanel panel = flowPanel();
        JButton resetButton = new JButton("重置視窗/圖表");
        resetButton.addActionListener(e -> run(resetCharts));
        panel.add(resetButton);
        return panel;
    }

    private static JPanel flowPanel() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT));
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    @FunctionalInterface
    public interface PerformanceModeHandler {
        void apply(String mode);
    }

    public interface KlineFollowHandler {
        void apply(boolean followLatest, int visibleCandles);

        void showAll();
    }

    @FunctionalInterface
    public interface EventModeHandler {
        void apply(String mode, int window, int consecutive, int threshold);
    }
}
