// === 3. 快捷交易UI面板 ===
// 位置: view/components/QuickTradePanel.java
package StockMainAction.view.components;

import StockMainAction.model.core.QuickTradeConfig;
import StockMainAction.controller.QuickTradeManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ExecutorService; // [PERF]
import java.util.concurrent.Executors; // [PERF]

/**
 * 快捷交易面板組件
 */
public class QuickTradePanel extends JPanel {

    // UI組件
    private JList<QuickTradeConfig> configList;
    private DefaultListModel<QuickTradeConfig> listModel;
    private JButton[] quickButtons;
    private JLabel currentPriceLabel;
    private JLabel availableFundsLabel;
    private JLabel currentHoldingsLabel;
    private JTextArea previewArea;
    private JButton executeButton;
    private JButton configButton;

    // 狀態變數
    private QuickTradeConfig selectedConfig;
    private double currentPrice = 0.0;
    private double availableFunds = 0.0;
    private int currentHoldings = 0;

    private boolean autoExecute = false;  // 是否自動執行

    // 事件監聽器接口
    public interface QuickTradePanelListener {

        void onQuickTradeExecute(QuickTradeConfig config);

        void onConfigureQuickTrade();

        void onPreviewQuickTrade(QuickTradeConfig config);
    }

    private QuickTradePanelListener listener;

    // [PERF] 背景執行緒
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public QuickTradePanel() {
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("快捷交易"));

        // 創建主要內容面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 上部：快捷按鈕區
        JPanel quickButtonPanel = createQuickButtonPanel();

        // 中部：當前狀態和預覽
        JPanel statusAndPreviewPanel = createStatusAndPreviewPanel();

        // 下部：配置列表和控制按鈕
        JPanel configPanel = createConfigPanel();

        mainPanel.add(quickButtonPanel, BorderLayout.NORTH);
        mainPanel.add(statusAndPreviewPanel, BorderLayout.CENTER);
        mainPanel.add(configPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * 創建快捷按鈕面板
     */
    private JPanel createQuickButtonPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 4, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("快捷按鈕"));

        // 創建8個快捷按鈕
        quickButtons = new JButton[8];
        String[] defaultLabels = {
            "F1: 買100股", "F2: 賣100股", "F3: 50%資金買", "F4: 賣50%持股",
            "Ctrl+B: 全倉買", "Ctrl+S: 全倉賣", "Ctrl+Q: 智能買", "Ctrl+W: 智能賣"
        };

        for (int i = 0; i < 8; i++) {
            quickButtons[i] = new JButton(defaultLabels[i]);
            quickButtons[i].setPreferredSize(new Dimension(120, 40));
            quickButtons[i].setFont(new Font("Microsoft JhengHei", Font.PLAIN, 10));

            // 設置按鈕顏色
            if (i % 2 == 0) {
                // 買入按鈕（綠色系）
                quickButtons[i].setBackground(new Color(220, 255, 220));
                quickButtons[i].setForeground(new Color(0, 100, 0));
            } else {
                // 賣出按鈕（紅色系）
                quickButtons[i].setBackground(new Color(255, 220, 220));
                quickButtons[i].setForeground(new Color(150, 0, 0));
            }

            final int buttonIndex = i;
            quickButtons[i].addActionListener(e -> executeQuickTrade(buttonIndex));

            panel.add(quickButtons[i]);
        }

        return panel;
    }

    /**
     * 創建狀態和預覽面板
     */
    private JPanel createStatusAndPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 左側：當前狀態
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("當前狀態"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        statusPanel.add(new JLabel("當前價格:"), gbc);
        gbc.gridx = 1;
        currentPriceLabel = new JLabel("0.00");
        currentPriceLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        statusPanel.add(currentPriceLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        statusPanel.add(new JLabel("可用資金:"), gbc);
        gbc.gridx = 1;
        availableFundsLabel = new JLabel("0.00");
        statusPanel.add(availableFundsLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        statusPanel.add(new JLabel("當前持股:"), gbc);
        gbc.gridx = 1;
        currentHoldingsLabel = new JLabel("0");
        statusPanel.add(currentHoldingsLabel, gbc);

        // 右側：交易預覽
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder("交易預覽"));

        previewArea = new JTextArea(6, 30);
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        previewArea.setBackground(new Color(250, 250, 250));
        previewArea.setText("請選擇快捷交易配置...");

        JScrollPane previewScrollPane = new JScrollPane(previewArea);
        previewPanel.add(previewScrollPane, BorderLayout.CENTER);

        // 執行按鈕
        executeButton = new JButton("執行交易");
        executeButton.setPreferredSize(new Dimension(100, 30));
        executeButton.setFont(new Font("Microsoft JhengHei", Font.BOLD, 12));
        executeButton.setEnabled(false);
        executeButton.addActionListener(e -> executeSelectedTrade());
        // [UX] Enter 送單 / Esc 清空
        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        previewArea.getInputMap(JComponent.WHEN_FOCUSED).put(enter, "doExec");
        previewArea.getActionMap().put("doExec", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ executeSelectedTrade(); }
        });
        previewArea.getInputMap(JComponent.WHEN_FOCUSED).put(esc, "doClear");
        previewArea.getActionMap().put("doClear", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ clearPreview(); }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(executeButton);
        previewPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 組合左右面板
        panel.add(statusPanel, BorderLayout.WEST);
        panel.add(previewPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 創建配置面板
     */
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("快捷交易配置"));

        // 配置列表
        listModel = new DefaultListModel<>();
        configList = new JList<>(listModel);
        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setCellRenderer(new QuickTradeConfigRenderer());
        configList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedConfig = configList.getSelectedValue();
                updatePreview();
            }
        });

        JScrollPane listScrollPane = new JScrollPane(configList);
        listScrollPane.setPreferredSize(new Dimension(400, 100));

        // 控制按鈕
        JPanel controlPanel = new JPanel(new FlowLayout());

        configButton = new JButton("配置管理");
        configButton.addActionListener(e -> {
            if (listener != null) {
                listener.onConfigureQuickTrade();
            }
        });

        JButton helpButton = new JButton("快捷鍵說明");
        helpButton.addActionListener(e -> showHotkeyHelp());

        JButton refreshButton = new JButton("刷新預覽");
        refreshButton.addActionListener(e -> updatePreview());

        controlPanel.add(configButton);
        controlPanel.add(helpButton);
        controlPanel.add(refreshButton);

        panel.add(listScrollPane, BorderLayout.CENTER);
        panel.add(controlPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 自定義配置渲染器
     */
    private class QuickTradeConfigRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof QuickTradeConfig) {
                QuickTradeConfig config = (QuickTradeConfig) value;

                // 方案一：如果 QuickTradeConfig 有 getTradeType() 方法
                String tradeTypeDisplay;
                try {
                    tradeTypeDisplay = config.getTradeType().getDisplayName();
                } catch (Exception e) {
                    // 如果 getTradeType() 不存在，使用 isBuy() 作為備選
                    tradeTypeDisplay = config.isBuy() ? "買入" : "賣出";
                }

                String text = String.format("%s [%s] - %s",
                        config.getName(),
                        config.getHotkey().isEmpty() ? "無快捷鍵" : config.getHotkey(),
                        tradeTypeDisplay
                );
                setText(text);

                // 根據交易類型設置顏色（只在未選中時）
                if (!isSelected) {
                    if (config.isBuy()) {
                        setForeground(new Color(0, 120, 0));  // 深綠色，更好的可讀性
                    } else {
                        setForeground(new Color(180, 0, 0));  // 深紅色，更好的可讀性
                    }
                }
            }

            return this;
        }
    }

    /**
     * 執行快捷交易
     */
    private void executeQuickTrade(int buttonIndex) {
        System.out.println("executeQuickTrade called with index: " + buttonIndex);

        // 檢查 listener 是否為 null
        if (listener == null) {
            System.err.println("ERROR: listener is null!");
            return;
        }

        // 根據按鈕索引獲取對應的預設配置
        QuickTradeConfig config = getQuickButtonConfig(buttonIndex);
        if (config != null) {
            System.out.println("Config found: " + config.getName());

            // 預覽交易
            selectedConfig = config;
            updatePreview();

            // 如果啟用了自動執行，直接執行
            if (config.isAutoExecute()) {
                System.out.println("Auto-executing trade...");
                listener.onQuickTradeExecute(config);
            } else {
                // 否則等待用戶確認
                System.out.println("Waiting for user confirmation...");
                executeButton.setEnabled(true);
            }
        } else {
            System.err.println("ERROR: config is null for index " + buttonIndex);
        }
    }

    /**
     * 獲取快捷按鈕對應的配置
     */
    private QuickTradeConfig getQuickButtonConfig(int buttonIndex) {
        // 根據按鈕索引返回預設配置
        switch (buttonIndex) {
            case 0: // F1: 買100股
                return createDefaultConfig("買100股", "F1", true, 100, 0);
            case 1: // F2: 賣100股
                return createDefaultConfig("賣100股", "F2", false, 100, 0);
            case 2: // F3: 50%資金買
                return createDefaultConfig("50%資金買", "F3", true, 0, 50);
            case 3: // F4: 賣50%持股
                return createDefaultConfig("賣50%持股", "F4", false, 0, 50);
            case 4: // Ctrl+B: 全倉買
                return createDefaultConfig("全倉買", "Ctrl+B", true, 0, 100);
            case 5: // Ctrl+S: 全倉賣
                return createDefaultConfig("全倉賣", "Ctrl+S", false, 0, 100);
            case 6: // Ctrl+Q: 智能買
                return createDefaultConfig("智能買", "Ctrl+Q", true, 0, 30);
            case 7: // Ctrl+W: 智能賣
                return createDefaultConfig("智能賣", "Ctrl+W", false, 0, 30);
            default:
                return null;
        }
    }

    /**
     * 創建預設配置
     */
    private QuickTradeConfig createDefaultConfig(String name, String hotkey, boolean isBuy,
            int fixedQuantity, int percentage) {

        // 根據參數決定交易類型
        QuickTradeConfig.QuickTradeType tradeType;
        if (fixedQuantity > 0) {
            tradeType = QuickTradeConfig.QuickTradeType.FIXED_QUANTITY;
        } else if (percentage == 100) {
            tradeType = isBuy ? QuickTradeConfig.QuickTradeType.ALL_IN : QuickTradeConfig.QuickTradeType.ALL_OUT;
        } else if (percentage > 0) {
            tradeType = isBuy ? QuickTradeConfig.QuickTradeType.PERCENTAGE_FUNDS : QuickTradeConfig.QuickTradeType.PERCENTAGE_HOLDINGS;
        } else {
            // 對於智能交易
            tradeType = isBuy ? QuickTradeConfig.QuickTradeType.SMART_BUY : QuickTradeConfig.QuickTradeType.SMART_SELL;
        }

        // 使用正確的建構函數
        QuickTradeConfig config = new QuickTradeConfig(
                name,
                tradeType,
                QuickTradeConfig.PriceStrategy.CURRENT_PRICE, // 預設使用當前價
                isBuy
        );

        // 設置其他屬性
        config.setHotkey(hotkey);
        config.setFixedQuantity(fixedQuantity);
        config.setPercentage(percentage);
        config.setAutoExecute(true);

        return config;
    }

    /**
     * 執行選中的交易
     */
    private void executeSelectedTrade() {
        if (selectedConfig != null && listener != null) {
            QuickTradeConfig cfg = selectedConfig;
            executeButton.setEnabled(false);
            executor.submit(() -> {
                try {
                    listener.onQuickTradeExecute(cfg); // [PERF] 背景執行下單
                } finally {
                    SwingUtilities.invokeLater(() -> updatePreview());
                }
            });
        }
    }

    /**
     * 更新交易預覽
     */
    private void updatePreview() {
        if (selectedConfig == null) {
            previewArea.setText("請選擇快捷交易配置...");
            executeButton.setEnabled(false);
            return;
        }

        StringBuilder preview = new StringBuilder();
        preview.append("=== 交易預覽 ===\n");
        preview.append(String.format("配置名稱: %s\n", selectedConfig.getName()));
        preview.append(String.format("交易類型: %s\n", selectedConfig.isBuy() ? "買入" : "賣出"));

        // 計算交易數量和金額
        int quantity = calculateTradeQuantity(selectedConfig);
        double amount = quantity * currentPrice;

        preview.append(String.format("交易數量: %d 股\n", quantity));
        preview.append(String.format("預估金額: %.2f 元\n", amount));

        // 計算交易後狀態
        if (selectedConfig.isBuy()) {
            preview.append(String.format("交易後資金: %.2f 元\n", availableFunds - amount));
            preview.append(String.format("交易後持股: %d 股\n", currentHoldings + quantity));
        } else {
            preview.append(String.format("交易後資金: %.2f 元\n", availableFunds + amount));
            preview.append(String.format("交易後持股: %d 股\n", currentHoldings - quantity));
        }

        // 風險提醒
        if (selectedConfig.isBuy() && amount > availableFunds) {
            preview.append("\n⚠️ 警告: 資金不足！");
        } else if (!selectedConfig.isBuy() && quantity > currentHoldings) {
            preview.append("\n⚠️ 警告: 持股不足！");
        } else {
            preview.append("\n✅ 可以執行此交易");
            executeButton.setEnabled(true);
        }

        previewArea.setText(preview.toString());
    }

    /**
     * 計算交易數量
     */
    private int calculateTradeQuantity(QuickTradeConfig config) {
        if (config.getFixedQuantity() > 0) {
            return config.getFixedQuantity();
        } else if (config.getPercentage() > 0) {
            if (config.isBuy()) {
                // 買入：根據可用資金百分比計算
                double funds = availableFunds * config.getPercentage() / 100.0;
                return (int) (funds / currentPrice);
            } else {
                // 賣出：根據持股百分比計算
                return (int) (currentHoldings * config.getPercentage() / 100.0);
            }
        }
        return 0;
    }

    /**
     * 顯示快捷鍵說明
     */
    private void showHotkeyHelp() {
        String helpText
                = "=== 快捷鍵說明 ===\n\n"
                + "F1 - 買入100股\n"
                + "F2 - 賣出100股\n"
                + "F3 - 用50%資金買入\n"
                + "F4 - 賣出50%持股\n"
                + "Ctrl+B - 全倉買入\n"
                + "Ctrl+S - 全倉賣出\n"
                + "Ctrl+Q - 智能買入(30%資金)\n"
                + "Ctrl+W - 智能賣出(30%持股)\n\n"
                + "注意：快捷鍵需要在主視窗獲得焦點時才能使用";

        JOptionPane.showMessageDialog(this, helpText, "快捷鍵說明",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // === 公開方法 ===
    /**
     * 設置事件監聽器
     */
    public void setListener(QuickTradePanelListener listener) {
        this.listener = listener;
    }

    /**
     * 更新當前價格
     */
    public void updateCurrentPrice(double price) {
        this.currentPrice = price;
        currentPriceLabel.setText(String.format("%.2f", price));
        currentPriceLabel.setForeground(price > 0 ? Color.BLACK : Color.RED);
        updatePreview();
    }

    /**
     * 更新可用資金
     */
    public void updateAvailableFunds(double funds) {
        this.availableFunds = funds;
        availableFundsLabel.setText(String.format("%.2f", funds));
        updatePreview();
    }

    /**
     * 更新當前持股
     */
    public void updateCurrentHoldings(int holdings) {
        this.currentHoldings = holdings;
        currentHoldingsLabel.setText(String.valueOf(holdings));
        updatePreview();
    }

    /**
     * 載入快捷交易配置列表
     */
    public void loadQuickTradeConfigs(List<QuickTradeConfig> configs) {
        listModel.clear();
        for (QuickTradeConfig config : configs) {
            listModel.addElement(config);
        }

        // 如果有配置，選擇第一個
        if (!configs.isEmpty()) {
            configList.setSelectedIndex(0);
        }
    }

    /**
     * 註冊快捷鍵
     */
    public void registerHotkeys(JFrame parentFrame) {
        // 註冊功能鍵
        registerHotkey(parentFrame, "F1", KeyEvent.VK_F1, 0, () -> executeQuickTrade(0));
        registerHotkey(parentFrame, "F2", KeyEvent.VK_F2, 0, () -> executeQuickTrade(1));
        registerHotkey(parentFrame, "F3", KeyEvent.VK_F3, 0, () -> executeQuickTrade(2));
        registerHotkey(parentFrame, "F4", KeyEvent.VK_F4, 0, () -> executeQuickTrade(3));

        // 註冊組合鍵
        registerHotkey(parentFrame, "Ctrl+B", KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK,
                () -> executeQuickTrade(4));
        registerHotkey(parentFrame, "Ctrl+S", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK,
                () -> executeQuickTrade(5));
        registerHotkey(parentFrame, "Ctrl+Q", KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK,
                () -> executeQuickTrade(6));
        registerHotkey(parentFrame, "Ctrl+W", KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK,
                () -> executeQuickTrade(7));
    }

    /**
     * 註冊單個快捷鍵
     */
    private void registerHotkey(JFrame frame, String name, int keyCode, int modifiers,
            Runnable action) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers);
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(keyStroke, name);
        frame.getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /**
     * 啟用/禁用快捷交易功能
     */
    public void setQuickTradeEnabled(boolean enabled) {
        for (JButton button : quickButtons) {
            button.setEnabled(enabled);
        }
        executeButton.setEnabled(enabled && selectedConfig != null);
        configButton.setEnabled(enabled);
    }

    /**
     * 清除預覽
     */
    public void clearPreview() {
        selectedConfig = null;
        previewArea.setText("請選擇快捷交易配置...");
        executeButton.setEnabled(false);
        configList.clearSelection();
    }

    /**
     * 重置所有狀態
     */
    public void reset() {
        currentPrice = 0.0;
        availableFunds = 0.0;
        currentHoldings = 0;

        currentPriceLabel.setText("0.00");
        availableFundsLabel.setText("0.00");
        currentHoldingsLabel.setText("0");

        clearPreview();
    }
}
