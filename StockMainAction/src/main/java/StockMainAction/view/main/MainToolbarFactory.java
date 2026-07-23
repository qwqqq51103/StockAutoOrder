package StockMainAction.view.main;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * MainView 頂部工具列工廠。
 *
 * <p>只負責建立 Swing 控件與事件轉發；真正的狀態變更與模型下發由 MainView callback 執行。</p>
 */
public final class MainToolbarFactory {
    private MainToolbarFactory() {
    }

    public static JToolBar create(Config config, Actions actions) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(actions, "actions");

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton themeButton = new JButton("主題");
        themeButton.addActionListener(e -> actions.toggleTheme());
        bar.add(themeButton);
        bar.addSeparator();

        JButton logButton = new JButton("日誌");
        logButton.setToolTipText("開啟日誌查看器（可調整輸出級別/Console）");
        logButton.addActionListener(e -> actions.openLogViewer());
        bar.add(logButton);
        bar.addSeparator();

        JButton fontMinus = new JButton("A-");
        JButton fontPlus = new JButton("A+");
        JButton fontReset = new JButton("A");
        fontMinus.addActionListener(e -> actions.decreaseFont());
        fontPlus.addActionListener(e -> actions.increaseFont());
        fontReset.addActionListener(e -> actions.resetFont());
        bar.add(fontMinus);
        bar.add(fontPlus);
        bar.add(fontReset);
        bar.addSeparator();

        JComboBox<String> performanceComboBox = new JComboBox<>(SettingsTabFactory.PERFORMANCE_MODES);
        performanceComboBox.setSelectedItem(config.performanceMode);
        performanceComboBox.addActionListener(e -> actions.applyPerformanceMode(
                Objects.toString(performanceComboBox.getSelectedItem(), "平衡")));
        bar.add(new JLabel("效能:"));
        bar.add(performanceComboBox);

        JButton commandButton = new JButton("命令(Ctrl+K)");
        commandButton.addActionListener(e -> actions.openCommandPalette());
        bar.addSeparator();
        bar.add(commandButton);

        JButton resetButton = new JButton("重置視窗");
        resetButton.addActionListener(e -> actions.resetCharts());
        bar.add(resetButton);

        bar.addSeparator();
        JComponent slippageStatus = actions.createSlippageStatus();
        if (slippageStatus != null) {
            bar.add(slippageStatus);
        }

        bar.add(new JLabel("撤換間隔:"));
        JSpinner replaceIntervalSpinner = new JSpinner(new SpinnerNumberModel(config.replaceIntervalTicks, 1, 200, 1));
        JButton replaceButton = new JButton("套用撤換");
        replaceButton.addActionListener(e -> actions.applyReplaceInterval(
                ((Number) replaceIntervalSpinner.getValue()).intValue()));
        bar.add(replaceIntervalSpinner);
        bar.add(replaceButton);
        bar.addSeparator();

        bar.add(new JLabel("事件:"));
        JComboBox<String> eventModeComboBox = new JComboBox<>(SettingsTabFactory.EVENT_MODES);
        JSpinner eventWindowSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 600, 10));
        JSpinner eventConsecutiveSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        JSpinner eventThresholdSpinner = new JSpinner(new SpinnerNumberModel(65, 30, 95, 1));
        JButton applyEventButton = new JButton("套用");
        bar.add(eventModeComboBox);
        bar.add(new JLabel("窗"));
        bar.add(eventWindowSpinner);
        bar.add(new JLabel("連"));
        bar.add(eventConsecutiveSpinner);
        bar.add(new JLabel("%"));
        bar.add(eventThresholdSpinner);
        bar.add(applyEventButton);
        applyEventButton.addActionListener(e -> actions.applyEventMode(
                Objects.toString(eventModeComboBox.getSelectedItem(), "一般"),
                ((Number) eventWindowSpinner.getValue()).intValue(),
                ((Number) eventConsecutiveSpinner.getValue()).intValue(),
                ((Number) eventThresholdSpinner.getValue()).intValue()));

        bar.addSeparator();
        bar.add(new JLabel("K線視圖:"));
        FollowState followState = new FollowState(config.autoFollowLatest);
        JButton followButton = new JButton(followText(followState.enabled));
        JSpinner visibleCandlesSpinner = new JSpinner(new SpinnerNumberModel(config.visibleCandles, 10, 200, 5));
        visibleCandlesSpinner.setToolTipText("追隨模式：只顯示最近 N 根K線（可調）");
        visibleCandlesSpinner.setEnabled(followState.enabled);
        applyFollowTooltip(followButton, followState.enabled, config.visibleCandles);
        visibleCandlesSpinner.addChangeListener(e -> {
            int candles = ((Number) visibleCandlesSpinner.getValue()).intValue();
            actions.changeVisibleCandles(candles);
            applyFollowTooltip(followButton, followState.enabled, candles);
        });
        followButton.addActionListener(e -> {
            followState.enabled = !followState.enabled;
            int candles = ((Number) visibleCandlesSpinner.getValue()).intValue();
            followButton.setText(followText(followState.enabled));
            visibleCandlesSpinner.setEnabled(followState.enabled);
            applyFollowTooltip(followButton, followState.enabled, candles);
            actions.setKlineFollow(followState.enabled);
        });
        bar.add(followButton);
        bar.add(new JLabel("最近"));
        bar.add(visibleCandlesSpinner);
        bar.add(new JLabel("根"));

        bar.addSeparator();
        JButton indicatorButton = new JButton("指標設定");
        indicatorButton.addActionListener(e -> actions.openIndicatorSettings());
        bar.add(indicatorButton);

        return bar;
    }

    private static String followText(boolean enabled) {
        return enabled ? "🎯 自動跟隨" : "📊 顯示全部";
    }

    private static void applyFollowTooltip(JButton button, boolean enabled, int visibleCandles) {
        button.setToolTipText(enabled
                ? "當前自動跟隨最近" + visibleCandles + "根K線，點擊切換到顯示全部"
                : "當前顯示全部K線，點擊切換到自動跟隨");
    }

    private static final class FollowState {
        boolean enabled;

        FollowState(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Config {
        public String performanceMode = "平衡";
        public int replaceIntervalTicks = 10;
        public boolean autoFollowLatest;
        public int visibleCandles = 20;
    }

    public interface Actions {
        void toggleTheme();

        void openLogViewer();

        void decreaseFont();

        void increaseFont();

        void resetFont();

        void applyPerformanceMode(String mode);

        void openCommandPalette();

        void resetCharts();

        JComponent createSlippageStatus();

        void applyReplaceInterval(int intervalTicks);

        void applyEventMode(String mode, int window, int consecutive, int threshold);

        void changeVisibleCandles(int visibleCandles);

        void setKlineFollow(boolean followLatest);

        void openIndicatorSettings();
    }
}
