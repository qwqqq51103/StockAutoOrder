package StockMainAction.view.main;

import StockMainAction.model.MainForceStrategyWithOrderBook.MainForceLimitConfig;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 主力策略風控限制設定面板。
 *
 * <p>這裡只負責表單與 config 轉換，不直接操作 MainForce 或市場模型。</p>
 */
public class MainForceLimitsPanel extends JPanel {
    private final JSpinner spAccMin;
    private final JSpinner spAccMax;
    private final JSpinner spMkMin;
    private final JSpinner spMkMax;
    private final JSpinner spDistMin;
    private final JSpinner spDistMax;
    private final JSpinner spWashMin;
    private final JSpinner spWashMax;
    private final JSpinner spRepl;
    private final JSpinner spMgmt;
    private final JSpinner spDevIdle;
    private final JSpinner spDevAcc;
    private final JSpinner spDevMk;
    private final JSpinner spDevDist;
    private final JSpinner spDevWash;
    private final JSpinner spAgeIdle;
    private final JSpinner spAgeAcc;
    private final JSpinner spAgeMk;
    private final JSpinner spAgeDist;
    private final JSpinner spAgeWash;
    private final JSpinner spMkBuyBelow;
    private final JSpinner spMkSellAbove;
    private final JSpinner spDistSellAbove;
    private final JSpinner spWashBuyBelow;
    private final JSpinner spWashSellAbove;
    private final JSpinner spRwExp;
    private final JSpinner spRwU;
    private final JSpinner spRwDd;
    private final JSpinner spRwVol;
    private final JSpinner spRwTr;
    private final JSpinner spFullU;
    private final JSpinner spFullDd;
    private final JSpinner spFullVol;
    private final JSpinner spFullTr;
    private final JSpinner spReliefMax;
    private final JSpinner spReliefSlope;

    public MainForceLimitsPanel(MainForceLimitConfig initialConfig,
            Consumer<MainForceLimitConfig> applyHandler,
            Consumer<String> presetLoadedHandler) {
        super(new BorderLayout(8, 8));
        MainForceLimitConfig config = initialConfig == null ? defaultConfig() : initialConfig;

        JPanel form = new JPanel(new GridLayout(0, 4, 8, 6));
        spAccMin = intSpinner(config.accumulateMinTicks, 1, 5000, 1);
        spAccMax = intSpinner(config.accumulateMaxTicks, 1, 5000, 1);
        spMkMin = intSpinner(config.markupMinTicks, 1, 5000, 1);
        spMkMax = intSpinner(config.markupMaxTicks, 1, 5000, 1);
        spDistMin = intSpinner(config.distributeMinTicks, 1, 5000, 1);
        spDistMax = intSpinner(config.distributeMaxTicks, 1, 5000, 1);
        spWashMin = intSpinner(config.washMinTicks, 1, 5000, 1);
        spWashMax = intSpinner(config.washMaxTicks, 1, 5000, 1);
        spRepl = intSpinner(config.replaceIntervalTicks, 1, 2000, 1);
        spMgmt = intSpinner(config.orderManagementIntervalTicks, 1, 2000, 1);
        spDevIdle = doubleSpinner(config.maxDeviationIdle, 0.0, 0.5, 0.01);
        spDevAcc = doubleSpinner(config.maxDeviationAccumulate, 0.0, 0.5, 0.01);
        spDevMk = doubleSpinner(config.maxDeviationMarkup, 0.0, 0.5, 0.01);
        spDevDist = doubleSpinner(config.maxDeviationDistribute, 0.0, 0.5, 0.01);
        spDevWash = doubleSpinner(config.maxDeviationWash, 0.0, 0.5, 0.01);
        spAgeIdle = intSpinner(toSeconds(config.maxAgeIdleMs), 1, 86400, 1);
        spAgeAcc = intSpinner(toSeconds(config.maxAgeAccumulateMs), 1, 86400, 1);
        spAgeMk = intSpinner(toSeconds(config.maxAgeMarkupMs), 1, 86400, 1);
        spAgeDist = intSpinner(toSeconds(config.maxAgeDistributeMs), 1, 86400, 1);
        spAgeWash = intSpinner(toSeconds(config.maxAgeWashMs), 1, 86400, 1);
        spMkBuyBelow = doubleSpinner(config.markupCancelBuyBelowRatio, 0.50, 1.50, 0.01);
        spMkSellAbove = doubleSpinner(config.markupCancelSellAboveRatio, 0.50, 2.00, 0.01);
        spDistSellAbove = doubleSpinner(config.distributeCancelSellAboveRatio, 0.50, 2.00, 0.01);
        spWashBuyBelow = doubleSpinner(config.washCancelBuyBelowRatio, 0.50, 1.50, 0.01);
        spWashSellAbove = doubleSpinner(config.washCancelSellAboveRatio, 0.50, 2.00, 0.01);
        spRwExp = doubleSpinner(config.riskExposureWeight, 0.0, 1.0, 0.01);
        spRwU = doubleSpinner(config.riskUnrealizedWeight, 0.0, 1.0, 0.01);
        spRwDd = doubleSpinner(config.riskDrawdownWeight, 0.0, 1.0, 0.01);
        spRwVol = doubleSpinner(config.riskVolatilityWeight, 0.0, 1.0, 0.01);
        spRwTr = doubleSpinner(config.riskTrendWeight, 0.0, 1.0, 0.01);
        spFullU = doubleSpinner(config.riskUnrealizedLossFull, 0.01, 1.0, 0.01);
        spFullDd = doubleSpinner(config.riskDrawdownFull, 0.01, 1.0, 0.01);
        spFullVol = doubleSpinner(config.riskVolatilityFull, 0.005, 1.0, 0.005);
        spFullTr = doubleSpinner(config.riskTrendDownFull, 0.005, 1.0, 0.005);
        spReliefMax = doubleSpinner(config.riskProfitReliefMax, 0.0, 1.0, 0.01);
        spReliefSlope = doubleSpinner(config.riskProfitReliefSlope, 0.0, 2.0, 0.01);

        addRow(form, "吸籌最短/最長 ticks", spAccMin, "", spAccMax);
        addRow(form, "拉抬最短/最長 ticks", spMkMin, "", spMkMax);
        addRow(form, "出貨最短/最長 ticks", spDistMin, "", spDistMax);
        addRow(form, "洗盤最短/最長 ticks", spWashMin, "", spWashMax);
        addRow(form, "撤換間隔 ticks", spRepl, "訂單管理間隔 ticks", spMgmt);
        addRow(form, "偏離上限 待機", spDevIdle, "偏離上限 吸籌", spDevAcc);
        addRow(form, "偏離上限 拉抬", spDevMk, "偏離上限 出貨", spDevDist);
        addRow(form, "偏離上限 洗盤", spDevWash, "", new JLabel(""));
        addRow(form, "訂單最大秒數 待機", spAgeIdle, "訂單最大秒數 吸籌", spAgeAcc);
        addRow(form, "訂單最大秒數 拉抬", spAgeMk, "訂單最大秒數 出貨", spAgeDist);
        addRow(form, "訂單最大秒數 洗盤", spAgeWash, "", new JLabel(""));
        addRow(form, "拉抬取消買單下界", spMkBuyBelow, "拉抬取消賣單上界", spMkSellAbove);
        addRow(form, "出貨取消賣單上界", spDistSellAbove, "洗盤取消買單下界", spWashBuyBelow);
        addRow(form, "洗盤取消賣單上界", spWashSellAbove, "", new JLabel(""));
        addRow(form, "風險權重 曝險", spRwExp, "風險權重 浮虧", spRwU);
        addRow(form, "風險權重 回撤", spRwDd, "風險權重 波動", spRwVol);
        addRow(form, "風險權重 趨勢", spRwTr, "", new JLabel(""));
        addRow(form, "浮虧滿風險比率", spFullU, "回撤滿風險比率", spFullDd);
        addRow(form, "波動滿風險基準", spFullVol, "下行趨勢滿風險基準", spFullTr);
        addRow(form, "浮盈減風險上限", spReliefMax, "浮盈減風險斜率", spReliefSlope);

        JComboBox<String> presetCombo = new JComboBox<>(new String[]{"保守", "平衡", "激進"});
        JButton presetButton = new JButton("套用預設組合");
        presetButton.addActionListener(e -> {
            String preset = Objects.toString(presetCombo.getSelectedItem(), "平衡");
            applyPreset(preset);
            if (presetLoadedHandler != null) {
                presetLoadedHandler.accept(preset);
            }
        });

        JButton applyButton = new JButton("套用主力限制");
        applyButton.addActionListener(e -> {
            if (applyHandler != null) {
                applyHandler.accept(toConfig());
            }
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(new JLabel("一鍵預設:"));
        bottom.add(presetCombo);
        bottom.add(presetButton);
        bottom.add(applyButton);

        add(new JScrollPane(form), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public MainForceLimitConfig toConfig() {
        MainForceLimitConfig config = new MainForceLimitConfig();
        config.accumulateMinTicks = intValue(spAccMin);
        config.accumulateMaxTicks = intValue(spAccMax);
        config.markupMinTicks = intValue(spMkMin);
        config.markupMaxTicks = intValue(spMkMax);
        config.distributeMinTicks = intValue(spDistMin);
        config.distributeMaxTicks = intValue(spDistMax);
        config.washMinTicks = intValue(spWashMin);
        config.washMaxTicks = intValue(spWashMax);
        config.replaceIntervalTicks = intValue(spRepl);
        config.orderManagementIntervalTicks = intValue(spMgmt);
        config.maxDeviationIdle = doubleValue(spDevIdle);
        config.maxDeviationAccumulate = doubleValue(spDevAcc);
        config.maxDeviationMarkup = doubleValue(spDevMk);
        config.maxDeviationDistribute = doubleValue(spDevDist);
        config.maxDeviationWash = doubleValue(spDevWash);
        config.maxAgeIdleMs = intValue(spAgeIdle) * 1000L;
        config.maxAgeAccumulateMs = intValue(spAgeAcc) * 1000L;
        config.maxAgeMarkupMs = intValue(spAgeMk) * 1000L;
        config.maxAgeDistributeMs = intValue(spAgeDist) * 1000L;
        config.maxAgeWashMs = intValue(spAgeWash) * 1000L;
        config.markupCancelBuyBelowRatio = doubleValue(spMkBuyBelow);
        config.markupCancelSellAboveRatio = doubleValue(spMkSellAbove);
        config.distributeCancelSellAboveRatio = doubleValue(spDistSellAbove);
        config.washCancelBuyBelowRatio = doubleValue(spWashBuyBelow);
        config.washCancelSellAboveRatio = doubleValue(spWashSellAbove);
        config.riskExposureWeight = doubleValue(spRwExp);
        config.riskUnrealizedWeight = doubleValue(spRwU);
        config.riskDrawdownWeight = doubleValue(spRwDd);
        config.riskVolatilityWeight = doubleValue(spRwVol);
        config.riskTrendWeight = doubleValue(spRwTr);
        config.riskUnrealizedLossFull = doubleValue(spFullU);
        config.riskDrawdownFull = doubleValue(spFullDd);
        config.riskVolatilityFull = doubleValue(spFullVol);
        config.riskTrendDownFull = doubleValue(spFullTr);
        config.riskProfitReliefMax = doubleValue(spReliefMax);
        config.riskProfitReliefSlope = doubleValue(spReliefSlope);
        return config;
    }

    public static MainForceLimitConfig defaultConfig() {
        MainForceLimitConfig config = new MainForceLimitConfig();
        config.accumulateMinTicks = 50;
        config.accumulateMaxTicks = 400;
        config.markupMinTicks = 20;
        config.markupMaxTicks = 150;
        config.distributeMinTicks = 30;
        config.distributeMaxTicks = 300;
        config.washMinTicks = 10;
        config.washMaxTicks = 100;
        config.replaceIntervalTicks = 10;
        config.orderManagementIntervalTicks = 20;
        config.maxDeviationIdle = 0.05;
        config.maxDeviationAccumulate = 0.08;
        config.maxDeviationMarkup = 0.15;
        config.maxDeviationDistribute = 0.12;
        config.maxDeviationWash = 0.10;
        config.maxAgeIdleMs = 900_000L;
        config.maxAgeAccumulateMs = 600_000L;
        config.maxAgeMarkupMs = 180_000L;
        config.maxAgeDistributeMs = 240_000L;
        config.maxAgeWashMs = 300_000L;
        config.markupCancelBuyBelowRatio = 0.92;
        config.markupCancelSellAboveRatio = 1.15;
        config.distributeCancelSellAboveRatio = 1.08;
        config.washCancelBuyBelowRatio = 0.85;
        config.washCancelSellAboveRatio = 1.20;
        config.riskExposureWeight = 0.35;
        config.riskUnrealizedWeight = 0.25;
        config.riskDrawdownWeight = 0.20;
        config.riskVolatilityWeight = 0.15;
        config.riskTrendWeight = 0.05;
        config.riskUnrealizedLossFull = 0.12;
        config.riskDrawdownFull = 0.20;
        config.riskVolatilityFull = 0.06;
        config.riskTrendDownFull = 0.05;
        config.riskProfitReliefMax = 0.12;
        config.riskProfitReliefSlope = 0.40;
        return config;
    }

    private void applyPreset(String preset) {
        if ("保守".equals(preset)) {
            spRwExp.setValue(0.20); spRwU.setValue(0.30); spRwDd.setValue(0.25); spRwVol.setValue(0.20); spRwTr.setValue(0.05);
            spFullU.setValue(0.08); spFullDd.setValue(0.12); spFullVol.setValue(0.04); spFullTr.setValue(0.03);
            spReliefMax.setValue(0.06); spReliefSlope.setValue(0.25);
            spRepl.setValue(6); spMgmt.setValue(10);
        } else if ("激進".equals(preset)) {
            spRwExp.setValue(0.50); spRwU.setValue(0.18); spRwDd.setValue(0.12); spRwVol.setValue(0.10); spRwTr.setValue(0.10);
            spFullU.setValue(0.18); spFullDd.setValue(0.30); spFullVol.setValue(0.10); spFullTr.setValue(0.08);
            spReliefMax.setValue(0.20); spReliefSlope.setValue(0.60);
            spRepl.setValue(14); spMgmt.setValue(30);
        } else {
            spRwExp.setValue(0.35); spRwU.setValue(0.25); spRwDd.setValue(0.20); spRwVol.setValue(0.15); spRwTr.setValue(0.05);
            spFullU.setValue(0.12); spFullDd.setValue(0.20); spFullVol.setValue(0.06); spFullTr.setValue(0.05);
            spReliefMax.setValue(0.12); spReliefSlope.setValue(0.40);
            spRepl.setValue(10); spMgmt.setValue(20);
        }
    }

    private static void addRow(JPanel form, String firstLabel, java.awt.Component first,
            String secondLabel, java.awt.Component second) {
        form.add(new JLabel(firstLabel));
        form.add(first);
        form.add(new JLabel(secondLabel));
        form.add(second);
    }

    private static JSpinner intSpinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static JSpinner doubleSpinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static int intValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static double doubleValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private static int toSeconds(long millis) {
        return (int) Math.max(1L, millis / 1000L);
    }
}
