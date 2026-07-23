package StockMainAction.view.main;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 技術指標與圖上標記的設定分頁。
 *
 * <p>此元件只管理表單控件；實際套用到 renderer、series 與圖表更新仍由 MainView callback 處理。</p>
 */
public class IndicatorSettingsPanel extends JPanel {
    private final JCheckBox showSma5CheckBox;
    private final JCheckBox showSma10CheckBox;
    private final JCheckBox showEma12CheckBox;
    private final JCheckBox showVwapCheckBox;
    private final JCheckBox showAvwapCheckBox;
    private final JCheckBox showSignalMarkersCheckBox;
    private final JCheckBox showBigMarkersCheckBox;
    private final JCheckBox showTickImbalanceMarkersCheckBox;
    private final JCheckBox autoHideMarkersCheckBox;
    private final JCheckBox lockRangeToOhlcCheckBox;
    private final JSpinner sma5PeriodSpinner;
    private final JSpinner sma10PeriodSpinner;
    private final JSpinner ema12PeriodSpinner;
    private final JSpinner smaLineWidthSpinner;
    private final JSpinner emaLineWidthSpinner;
    private final JSpinner bigOrderThresholdSpinner;
    private final JSpinner markerMaxCandlesSpinner;
    private final JSpinner markerAlphaSpinner;

    public IndicatorSettingsPanel(Config initialConfig, Consumer<Config> applyHandler, Runnable resetAvwapAnchor) {
        super(new BorderLayout(8, 8));
        Config config = Objects.requireNonNull(initialConfig, "initialConfig");
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        showSma5CheckBox = new JCheckBox("SMA5", config.showSma5);
        showSma10CheckBox = new JCheckBox("SMA10", config.showSma10);
        showEma12CheckBox = new JCheckBox("EMA12", config.showEma12);
        showVwapCheckBox = new JCheckBox("VWAP", config.showVwap);
        showAvwapCheckBox = new JCheckBox("Anchored VWAP", config.showAvwap);
        sma5PeriodSpinner = new JSpinner(new SpinnerNumberModel(config.sma5Period, 2, 200, 1));
        sma10PeriodSpinner = new JSpinner(new SpinnerNumberModel(config.sma10Period, 2, 300, 1));
        ema12PeriodSpinner = new JSpinner(new SpinnerNumberModel(config.ema12Period, 2, 300, 1));
        smaLineWidthSpinner = new JSpinner(new SpinnerNumberModel((double) config.smaLineWidth, 0.5, 5.0, 0.1));
        emaLineWidthSpinner = new JSpinner(new SpinnerNumberModel((double) config.emaLineWidth, 0.5, 5.0, 0.1));

        showSignalMarkersCheckBox = new JCheckBox("多/空標記", config.showSignalMarkers);
        showBigMarkersCheckBox = new JCheckBox("大買賣單標記", config.showBigMarkers);
        showTickImbalanceMarkersCheckBox = new JCheckBox("買賣失衡標記", config.showTickImbalanceMarkers);
        autoHideMarkersCheckBox = new JCheckBox("縮小視圖時自動隱藏標記", config.autoHideMarkersWhenZoomedOut);
        bigOrderThresholdSpinner = new JSpinner(new SpinnerNumberModel(config.bigOrderThreshold, 50, 50000, 50));
        markerMaxCandlesSpinner = new JSpinner(new SpinnerNumberModel(config.markersMaxVisibleCandles, 10, 500, 10));
        markerAlphaSpinner = new JSpinner(new SpinnerNumberModel(config.markerAlpha, 40, 255, 5));
        lockRangeToOhlcCheckBox = new JCheckBox("Y軸只依K線（忽略指標極值）", config.lockRangeToOhlc);

        JButton resetAnchorButton = new JButton("重設 AVWAP 起點(使用目前視窗起點)");
        resetAnchorButton.addActionListener(e -> {
            if (resetAvwapAnchor != null) {
                resetAvwapAnchor.run();
            }
        });

        form.add(showSma5CheckBox);
        form.add(sma5PeriodSpinner);
        form.add(showSma10CheckBox);
        form.add(sma10PeriodSpinner);
        form.add(showEma12CheckBox);
        form.add(ema12PeriodSpinner);
        form.add(showVwapCheckBox);
        form.add(new JLabel("(跟隨主圖)"));
        form.add(showAvwapCheckBox);
        form.add(resetAnchorButton);
        form.add(new JLabel("SMA 線寬"));
        form.add(smaLineWidthSpinner);
        form.add(new JLabel("EMA 線寬"));
        form.add(emaLineWidthSpinner);
        form.add(new JSeparator());
        form.add(new JSeparator());
        form.add(showSignalMarkersCheckBox);
        form.add(new JLabel("（多空三角）"));
        form.add(showBigMarkersCheckBox);
        form.add(new JLabel("（大單圓點）"));
        form.add(showTickImbalanceMarkersCheckBox);
        form.add(new JLabel("（Tick失衡點）"));
        form.add(new JLabel("大單門檻(量)"));
        form.add(bigOrderThresholdSpinner);
        form.add(autoHideMarkersCheckBox);
        form.add(new JLabel("（視圖太寬時隱藏）"));
        form.add(new JLabel("標記顯示上限(根)"));
        form.add(markerMaxCandlesSpinner);
        form.add(new JLabel("標記透明度(40~255)"));
        form.add(markerAlphaSpinner);
        form.add(lockRangeToOhlcCheckBox);
        form.add(new JLabel("（修正跑久後K線變一條線）"));

        JButton applyButton = new JButton("套用指標設定");
        applyButton.addActionListener(e -> {
            if (applyHandler != null) {
                applyHandler.accept(toConfig());
            }
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(applyButton);

        add(new JScrollPane(form), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public Config toConfig() {
        Config config = new Config();
        config.showSma5 = showSma5CheckBox.isSelected();
        config.showSma10 = showSma10CheckBox.isSelected();
        config.showEma12 = showEma12CheckBox.isSelected();
        config.showVwap = showVwapCheckBox.isSelected();
        config.showAvwap = showAvwapCheckBox.isSelected();
        config.sma5Period = ((Number) sma5PeriodSpinner.getValue()).intValue();
        config.sma10Period = ((Number) sma10PeriodSpinner.getValue()).intValue();
        config.ema12Period = ((Number) ema12PeriodSpinner.getValue()).intValue();
        config.smaLineWidth = ((Number) smaLineWidthSpinner.getValue()).floatValue();
        config.emaLineWidth = ((Number) emaLineWidthSpinner.getValue()).floatValue();
        config.showSignalMarkers = showSignalMarkersCheckBox.isSelected();
        config.showBigMarkers = showBigMarkersCheckBox.isSelected();
        config.showTickImbalanceMarkers = showTickImbalanceMarkersCheckBox.isSelected();
        config.autoHideMarkersWhenZoomedOut = autoHideMarkersCheckBox.isSelected();
        config.bigOrderThreshold = ((Number) bigOrderThresholdSpinner.getValue()).intValue();
        config.markersMaxVisibleCandles = ((Number) markerMaxCandlesSpinner.getValue()).intValue();
        config.markerAlpha = ((Number) markerAlphaSpinner.getValue()).intValue();
        config.lockRangeToOhlc = lockRangeToOhlcCheckBox.isSelected();
        return config;
    }

    public static class Config {
        public boolean showSma5;
        public boolean showSma10;
        public boolean showEma12;
        public boolean showVwap;
        public boolean showAvwap;
        public int sma5Period;
        public int sma10Period;
        public int ema12Period;
        public float smaLineWidth;
        public float emaLineWidth;
        public boolean showSignalMarkers;
        public boolean showBigMarkers;
        public boolean showTickImbalanceMarkers;
        public boolean autoHideMarkersWhenZoomedOut;
        public int bigOrderThreshold;
        public int markersMaxVisibleCandles;
        public int markerAlpha;
        public boolean lockRangeToOhlc;
    }
}
