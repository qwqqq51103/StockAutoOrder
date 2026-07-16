package StockMainAction;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import StockMainAction.model.core.MatchingMode;

/**
 * 撮合引擎面板 - 用於控制和顯示撮合引擎的狀態和設置
 */
public class MatchingEnginePanel extends JPanel {

    private JComboBox<MatchingMode> matchingModeComboBox;
    private JButton applyButton;
    private JLabel currentModeLabel;
    private JTextArea descriptionArea;

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

        // 主控區（當前模式 + 模式選擇）
        JPanel mainPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        // 1. 當前模式標籤
        JPanel currentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currentModeLabel = new JLabel("當前撮合模式: 台股撮合（價格時間優先）");
        currentModeLabel.setFont(currentModeLabel.getFont().deriveFont(Font.BOLD));
        currentPanel.add(currentModeLabel);

        // 2. 撮合模式選擇
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(new JLabel("撮合模式:"));
        matchingModeComboBox = new JComboBox<>(MatchingMode.values());
        applyButton = new JButton("應用");
        modePanel.add(matchingModeComboBox);
        modePanel.add(applyButton);

        // 3. 說明提示（取代原先永久禁用的隨機/流動性控件）
        JPanel notePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel noteLabel = new JLabel(
                "<html><i>本版本固定使用「台股連續撮合」規則，隨機切換與流動性設定已移除。</i></html>");
        noteLabel.setForeground(new Color(100, 100, 100));
        notePanel.add(noteLabel);

        // 4. 描述文字區
        descriptionArea = new JTextArea(5, 30);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descScrollPane = new JScrollPane(descriptionArea);

        // 幫助按鈕
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton helpButton = new JButton("說明");
        helpButton.addActionListener(e -> showHelpDialog());
        buttonPanel.add(helpButton);

        // 組合面板
        mainPanel.add(currentPanel);
        mainPanel.add(modePanel);
        mainPanel.add(notePanel);

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
            case TWSE_STRICT:
                return "台股撮合（連續交易）：價格優先、時間優先；成交必須交叉（買價≥賣價）；成交價以被動方（簿內較早者）的委託價為準；並遵守價格跳動單位（tick size）。";
            default:
                return "台股撮合（固定模式）。";
        }
    }

    /**
     * 顯示撮合機制說明對話框
     */
    private void showHelpDialog() {
        String helpText
                = "撮合機制說明：\n\n"
                + "本版本已固定使用『台股撮合（連續交易）』，並停用舊的模擬撮合模式。\n\n"
                + "台股撮合要點：\n"
                + "1. 成交必須交叉：買價 ≥ 賣價\n"
                + "2. 價格優先、時間優先\n"
                + "3. 成交價以被動方（簿內較早者）的委託價為準\n"
                + "4. 價格遵守 tick size（跳動單位）\n\n"
                + "備註：『隨機切換』與『流動性滑桿』不再影響撮合規則（避免偏離交易所邏輯）。";

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

    /** 隨機切換已移除，固定回傳 false */
    public boolean isRandomModeSwitchingEnabled() { return false; }

    /** 隨機切換已移除，固定回傳 0 */
    public double getRandomModeSwitchingProbability() { return 0.0; }

    /** 流動性滑桿已移除，固定回傳中性值 1.0 */
    public double getLiquidityFactor() { return 1.0; }
}
