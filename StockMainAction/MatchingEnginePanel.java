package StockMainAction;

import Core.MatchingMode;
import Core.OrderBook;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;

/**
 * 撮合引擎控制面板 - 可集成到主界面
 */
public class MatchingEnginePanel extends JPanel {

    private OrderBook orderBook;
    private JComboBox<MatchingMode> modeComboBox;
    private JCheckBox randomModeCheckbox;
    private JSlider probabilitySlider;
    private JSlider liquiditySlider;

    /**
     * 構造函數
     *
     * @param orderBook 訂單簿實例
     */
    public MatchingEnginePanel(OrderBook orderBook) {
        this.orderBook = orderBook;
        initializeUI();
    }

    /**
     * 初始化UI組件
     */
    private void initializeUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "撮合引擎設置",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        // 創建主面板
        JPanel mainPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        // 1. 撮合模式選擇
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel modeLabel = new JLabel("撮合模式:");
        modeComboBox = new JComboBox<>(MatchingMode.values());
        modeComboBox.setSelectedItem(orderBook.getMatchingMode());
        modeComboBox.addActionListener(e -> {
            MatchingMode selectedMode = (MatchingMode) modeComboBox.getSelectedItem();
            orderBook.setMatchingMode(selectedMode);
        });

        modePanel.add(modeLabel);
        modePanel.add(modeComboBox);

        // 2. 隨機模式設置
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
            orderBook.setRandomModeSwitching(selected,
                    selected ? probabilitySlider.getValue() / 100.0 : 0);
        });

        probabilitySlider.addChangeListener(e -> {
            if (!probabilitySlider.getValueIsAdjusting() && randomModeCheckbox.isSelected()) {
                orderBook.setRandomModeSwitching(true, probabilitySlider.getValue() / 100.0);
            }
        });

        randomPanel.add(randomCheckPanel, BorderLayout.NORTH);
        randomPanel.add(probabilitySlider, BorderLayout.CENTER);

        // 3. 流動性設置
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

        liquiditySlider.addChangeListener(e -> {
            if (!liquiditySlider.getValueIsAdjusting()) {
                orderBook.setLiquidityFactor(liquiditySlider.getValue() / 100.0);
            }
        });

        liquidityPanel.add(liquidityLabelPanel, BorderLayout.NORTH);
        liquidityPanel.add(liquiditySlider, BorderLayout.CENTER);

        // 組合面板
        mainPanel.add(modePanel);
        mainPanel.add(randomPanel);
        mainPanel.add(liquidityPanel);

        // 添加說明按鈕
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton helpButton = new JButton("說明");
        helpButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHelpDialog();
            }
        });
        buttonPanel.add(helpButton);

        // 添加到主面板
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
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
}
