package StockMainAction;

import StockMainAction.model.core.MatchingMode;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Hashtable;

/**
 * 撮合引擎面板 - 用於控制和顯示撮合引擎的狀態和設置
 */
public class MatchingEnginePanel extends JPanel {

    private JComboBox<MatchingMode> matchingModeComboBox;
    private JButton applyButton;
    private JLabel currentModeLabel;
    private JTextArea descriptionArea;
    private JCheckBox randomModeCheckbox;
    private JSlider probabilitySlider;
    private JSlider liquiditySlider;

    /**
     * 構造函數 - 初始化撮合引擎面板
     */
    public MatchingEnginePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "撮合引擎設置",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        // 創建主面板
        JPanel mainPanel = new JPanel(new GridLayout(4, 1, 5, 5));

        // 1. 當前模式標籤
        JPanel currentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currentModeLabel = new JLabel("當前撮合模式: 標準 (價格優先時間優先)");
        currentPanel.add(currentModeLabel);

        // 2. 撮合模式選擇
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel modeLabel = new JLabel("撮合模式:");
        matchingModeComboBox = new JComboBox<>(MatchingMode.values());
        applyButton = new JButton("應用");

        modePanel.add(modeLabel);
        modePanel.add(matchingModeComboBox);
        modePanel.add(applyButton);

        // 3. 隨機模式設置
        JPanel randomPanel = new JPanel(new BorderLayout());
        JPanel randomCheckPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        randomModeCheckbox = new JCheckBox("隨機切換模式", false);
        JLabel probabilityLabel = new JLabel("切換概率:");

        randomCheckPanel.add(randomModeCheckbox);
        randomCheckPanel.add(probabilityLabel);

        probabilitySlider = new JSlider(0, 100, 15);
        probabilitySlider.setMajorTickSpacing(25);
        probabilitySlider.setMinorTickSpacing(5);
        probabilitySlider.setPaintTicks(true);
        probabilitySlider.setPaintLabels(true);
        probabilitySlider.setEnabled(false);

        randomModeCheckbox.addActionListener(e -> {
            boolean selected = randomModeCheckbox.isSelected();
            probabilitySlider.setEnabled(selected);
        });

        randomPanel.add(randomCheckPanel, BorderLayout.NORTH);
        randomPanel.add(probabilitySlider, BorderLayout.CENTER);

        // 4. 流動性設置
        JPanel liquidityPanel = new JPanel(new BorderLayout());
        JPanel liquidityLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel liquidityLabel = new JLabel("市場流動性:");
        liquidityLabelPanel.add(liquidityLabel);

        liquiditySlider = new JSlider(50, 200, 100);
        liquiditySlider.setMajorTickSpacing(50);
        liquiditySlider.setMinorTickSpacing(10);
        liquiditySlider.setPaintTicks(true);
        liquiditySlider.setPaintLabels(true);

        // 添加標籤
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        labelTable.put(50, new JLabel("低"));
        labelTable.put(100, new JLabel("中"));
        labelTable.put(150, new JLabel("高"));
        labelTable.put(200, new JLabel("極高"));
        liquiditySlider.setLabelTable(labelTable);

        liquidityPanel.add(liquidityLabelPanel, BorderLayout.NORTH);
        liquidityPanel.add(liquiditySlider, BorderLayout.CENTER);

        // 5. 描述文字區
        descriptionArea = new JTextArea(5, 30);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descScrollPane = new JScrollPane(descriptionArea);

        // 6. 幫助按鈕
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton helpButton = new JButton("說明");
        helpButton.addActionListener(e -> showHelpDialog());
        buttonPanel.add(helpButton);

        // 組合面板
        mainPanel.add(currentPanel);
        mainPanel.add(modePanel);
        mainPanel.add(randomPanel);
        mainPanel.add(liquidityPanel);

        // 更新描述文字
        updateDescription();

        // 監聽模式選擇變化
        matchingModeComboBox.addActionListener(e -> updateDescription());

        // 添加到主面板
        add(mainPanel, BorderLayout.NORTH);
        add(descScrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * 根據選擇的撮合模式更新描述文字
     */
    private void updateDescription() {
        MatchingMode selectedMode = (MatchingMode) matchingModeComboBox.getSelectedItem();
        if (selectedMode != null) {
            descriptionArea.setText(getDescriptionForMode(selectedMode));
        }
    }

    /**
     * 獲取指定撮合模式的描述文字
     */
    private String getDescriptionForMode(MatchingMode mode) {
        switch (mode) {
            case PRICE_TIME:
                return "價格優先、時間優先模式：優先撮合價格最優的訂單，如有多個價格相同的訂單，則優先撮合較早提交的訂單。這是最標準的撮合機制。";
            case VOLUME_WEIGHTED:
                return "成交量加權模式：在價格優先的基礎上，對大單進行一定程度的優先處理，使大單更容易成交。";
            case MARKET_PRESSURE:
                return "市場壓力模式：根據市場買賣力量的對比來調整撮合優先級，當買壓大時優先執行買單，賣壓大時優先執行賣單。";
            case RANDOM:
                return "隨機撮合模式：在滿足價格條件的訂單中隨機選擇進行撮合，模擬市場的不確定性。";
            default:
                return "標準模式：使用傳統的價格優先、時間優先撮合機制。";
        }
    }

    /**
     * 顯示撮合機制說明對話框
     */
    private void showHelpDialog() {
        String helpText
                = "撮合機制說明：\n\n"
                + "1. 標準撮合：最基本的撮合方式，買價≥賣價即成交，成交價取中間。\n\n"
                + "2. 價格時間優先：同等價格下先到先得，成交價格會偏向先到訂單。\n\n"
                + "3. 成交量加權：大單具有議價能力，大單優先且影響成交價的加權比例。\n\n"
                + "4. 市場壓力模式：考慮整體買賣壓力失衡，買單多時賣方有議價優勢。\n\n"
                + "5. 隨機模式：增加一定的市場噪聲和不確定性，偶爾允許小幅價差交易。\n\n"
                + "流動性設置：\n"
                + "- 低：交易量較小，每次成交數量有限\n"
                + "- 中：正常流動性\n"
                + "- 高：流動性好，大筆訂單較易成交\n"
                + "- 極高：幾乎所有訂單都能快速成交";

        JTextArea textArea = new JTextArea(helpText);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 350));

        JOptionPane.showMessageDialog(this, scrollPane, "撮合機制說明",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 設置應用按鈕的動作監聽器
     */
    public void setApplyButtonListener(ActionListener listener) {
        applyButton.addActionListener(listener);
    }

    /**
     * 獲取當前選擇的撮合模式
     */
    public MatchingMode getSelectedMatchingMode() {
        return (MatchingMode) matchingModeComboBox.getSelectedItem();
    }

    /**
     * 更新當前撮合模式顯示
     */
    public void updateCurrentMode(MatchingMode mode) {
        currentModeLabel.setText("當前撮合模式: " + mode.toString());
    }

    /**
     * 獲取隨機模式切換狀態
     */
    public boolean isRandomModeSwitchingEnabled() {
        return randomModeCheckbox.isSelected();
    }

    /**
     * 獲取隨機模式切換概率
     */
    public double getRandomModeSwitchingProbability() {
        return probabilitySlider.getValue() / 100.0;
    }

    /**
     * 獲取流動性因子
     */
    public double getLiquidityFactor() {
        return liquiditySlider.getValue() / 100.0;
    }
}
