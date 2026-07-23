package StockMainAction.view.main;

import StockMainAction.model.StockMarketModel.RetailLogicModel;
import StockMainAction.model.StockMarketModel.RetailStrategyConfig;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/**
 * 散戶策略參數設定面板。
 */
public class RetailStrategySettingsPanel extends JPanel {
    private final JComboBox<String> logicCombo;
    private final JSpinner riskPctSpinner;
    private final JSpinner randomPctSpinner;
    private final JSpinner spreadPctSpinner;
    private final JSpinner rsiBuySpinner;
    private final JSpinner rsiSellSpinner;
    private final JSpinner trendEntrySpinner;
    private final JSpinner macdEntrySpinner;
    private final JSpinner minWaitSpinner;
    private final JSpinner lossCooldownSpinner;

    public RetailStrategySettingsPanel(Consumer<RetailStrategyConfig> applyHandler,
            Runnable loadCurrentHandler,
            Runnable resetDefaultsHandler) {
        super(new BorderLayout());

        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("散戶邏輯模型設定（即時生效）"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 8, 6, 8);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;

        logicCombo = new JComboBox<>(new String[]{"混合(預設)", "趨勢追隨", "均值回歸", "保守"});
        riskPctSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.1, 50.0, 0.1));
        randomPctSpinner = new JSpinner(new SpinnerNumberModel(0.5, 0.0, 20.0, 0.1));
        spreadPctSpinner = new JSpinner(new SpinnerNumberModel(0.8, 0.0, 5.0, 0.1));
        rsiBuySpinner = new JSpinner(new SpinnerNumberModel(30.0, 1.0, 50.0, 1.0));
        rsiSellSpinner = new JSpinner(new SpinnerNumberModel(70.0, 50.0, 99.0, 1.0));
        trendEntrySpinner = new JSpinner(new SpinnerNumberModel(0.20, 0.0, 1.0, 0.05));
        macdEntrySpinner = new JSpinner(new SpinnerNumberModel(0.02, 0.0, 1.0, 0.01));
        minWaitSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 200, 1));
        lossCooldownSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 500, 5));

        int row = 0;
        addConfigRow(configPanel, constraints, row++, "模型", logicCombo);
        addConfigRow(configPanel, constraints, row++, "風險/單筆(%)", riskPctSpinner);
        addConfigRow(configPanel, constraints, row++, "隨機交易(%)", randomPctSpinner);
        addConfigRow(configPanel, constraints, row++, "價差上限(%)", spreadPctSpinner);
        addConfigRow(configPanel, constraints, row++, "RSI 買入門檻(<=)", rsiBuySpinner);
        addConfigRow(configPanel, constraints, row++, "RSI 賣出門檻(>=)", rsiSellSpinner);
        addConfigRow(configPanel, constraints, row++, "趨勢門檻(|trend|>)", trendEntrySpinner);
        addConfigRow(configPanel, constraints, row++, "MACD Hist 門檻(|hist|>)", macdEntrySpinner);
        addConfigRow(configPanel, constraints, row++, "最小交易間隔(ticks)", minWaitSpinner);
        addConfigRow(configPanel, constraints, row, "連虧冷卻/每次(ticks)", lossCooldownSpinner);

        JButton applyButton = new JButton("套用");
        JButton loadButton = new JButton("讀取目前設定");
        JButton resetButton = new JButton("重置預設");
        applyButton.addActionListener(e -> {
            if (applyHandler != null) {
                applyHandler.accept(toConfig());
            }
        });
        loadButton.addActionListener(e -> {
            if (loadCurrentHandler != null) {
                loadCurrentHandler.run();
            }
        });
        resetButton.addActionListener(e -> {
            if (resetDefaultsHandler != null) {
                resetDefaultsHandler.run();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(applyButton);
        buttons.add(loadButton);
        buttons.add(resetButton);

        JTextArea help = new JTextArea(
                "提示：\n" +
                "- 趨勢追隨：較少抄底（RSI 買入可能被弱化），更依賴趨勢/動能確認。\n" +
                "- 均值回歸：較依賴 RSI 超買超賣與回到均線附近。\n" +
                "- 價差上限：越小越保守，避免在流動性差時被滑價磨損。\n"
        );
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setBackground(new Color(250, 250, 250));
        help.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(configPanel, BorderLayout.NORTH);
        add(buttons, BorderLayout.CENTER);
        add(new JScrollPane(help), BorderLayout.SOUTH);
    }

    public RetailStrategyConfig toConfig() {
        return new RetailStrategyConfig(
                selectedModel(),
                doubleValue(riskPctSpinner) / 100.0,
                doubleValue(randomPctSpinner) / 100.0,
                doubleValue(spreadPctSpinner) / 100.0,
                doubleValue(rsiBuySpinner),
                doubleValue(rsiSellSpinner),
                doubleValue(trendEntrySpinner),
                doubleValue(macdEntrySpinner),
                intValue(minWaitSpinner),
                intValue(lossCooldownSpinner));
    }

    public void setConfig(RetailStrategyConfig config) {
        RetailStrategyConfig value = config == null ? RetailStrategyConfig.defaults() : config;
        logicCombo.setSelectedItem(labelFor(value.model));
        riskPctSpinner.setValue(value.riskPerTrade * 100.0);
        randomPctSpinner.setValue(value.randomTradeProb * 100.0);
        spreadPctSpinner.setValue(value.spreadLimitRatio * 100.0);
        rsiBuySpinner.setValue(value.rsiBuy);
        rsiSellSpinner.setValue(value.rsiSell);
        trendEntrySpinner.setValue(value.trendEntry);
        macdEntrySpinner.setValue(value.macdHistEntry);
        minWaitSpinner.setValue(value.minTradeWaitTicks);
        lossCooldownSpinner.setValue(value.lossCooldownPerLoss);
    }

    public String selectedLabel() {
        Object selected = logicCombo.getSelectedItem();
        return selected == null ? "混合(預設)" : selected.toString();
    }

    private RetailLogicModel selectedModel() {
        String selected = selectedLabel();
        if ("趨勢追隨".equals(selected)) return RetailLogicModel.TREND_FOLLOW;
        if ("均值回歸".equals(selected)) return RetailLogicModel.MEAN_REVERT;
        if ("保守".equals(selected)) return RetailLogicModel.CONSERVATIVE;
        return RetailLogicModel.MIXED;
    }

    private static String labelFor(RetailLogicModel model) {
        if (model == RetailLogicModel.TREND_FOLLOW) return "趨勢追隨";
        if (model == RetailLogicModel.MEAN_REVERT) return "均值回歸";
        if (model == RetailLogicModel.CONSERVATIVE) return "保守";
        return "混合(預設)";
    }

    private static void addConfigRow(JPanel panel, GridBagConstraints constraints,
            int row, String label, JComponent component) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0.0;
        panel.add(new JLabel(label + ":"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        panel.add(component, constraints);
    }

    private static int intValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static double doubleValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }
}
