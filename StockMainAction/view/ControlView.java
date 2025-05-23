// === 更新的ControlView.java - 分頁式設計 ===
package StockMainAction.view;

import StockMainAction.MatchingEnginePanel;
import StockMainAction.view.components.PriceAlertPanel;
import StockMainAction.view.components.PersonalStatsPanel;
import StockMainAction.view.components.QuickTradePanel;
import javax.swing.*;
import java.awt.*;

/**
 * 控制視圖 - 分頁式設計
 */
public class ControlView extends JFrame {

    // UI組件
    private JButton stopButton, limitBuyButton, limitSellButton;
    private JButton marketBuyButton, marketSellButton, cancelOrderButton, viewOrdersButton;
    private JLabel userStockLabel, userCashLabel, userAvgPriceLabel, userTargetPrice;
    private MatchingEnginePanel matchingEnginePanel;
    private PriceAlertPanel priceAlertPanel;
    private PersonalStatsPanel personalStatsPanel;
    private QuickTradePanel quickTradePanel;

    // 分頁面板
    private JTabbedPane tabbedPane;

    public ControlView() {
        initializeUI();
    }

    private void initializeUI() {
        setTitle("股票市場模擬 - 控制視窗");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(700, 750);
        setLocationRelativeTo(null);

        // 創建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 創建頂部面板（顯示用戶資訊和系統控制）
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(createUserInfoPanel(), BorderLayout.CENTER);
        topPanel.add(createSystemPanel(), BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 創建分頁面板
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));

        // 添加各個分頁
        addTradingTab();        // 交易操作分頁
        addQuickTradeTab();     // 快捷交易分頁
        addPriceAlertTab();     // 價格提醒分頁
        addPersonalStatsTab();  // 個人統計分頁
        addMatchingEngineTab(); // 撮合引擎分頁

        // 設置分頁圖標（可選）
        setTabIcons();

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        add(mainPanel);
    }

    /**
     * 創建用戶資訊面板（保持在頂部，不在分頁中）
     */
    private JPanel createUserInfoPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("個人資訊"),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        userStockLabel = new JLabel("個人股票數量: 0");
        userCashLabel = new JLabel("個人金錢餘額: 0.00");
        userAvgPriceLabel = new JLabel("個人平均價: 0.00");
        userTargetPrice = new JLabel("個人目標價: 0.00");

        // 設置字體
        Font infoFont = new Font("Microsoft JhengHei", Font.PLAIN, 12);
        userStockLabel.setFont(infoFont);
        userCashLabel.setFont(infoFont);
        userAvgPriceLabel.setFont(infoFont);
        userTargetPrice.setFont(infoFont);

        panel.add(userStockLabel);
        panel.add(userCashLabel);
        panel.add(userAvgPriceLabel);
        panel.add(userTargetPrice);

        return panel;
    }

    /**
     * 創建系統控制面板（保持在頂部）
     */
    private JPanel createSystemPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("系統控制"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        stopButton = new JButton("停止");
        stopButton.setPreferredSize(new Dimension(120, 35));
        stopButton.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        stopButton.setBackground(new Color(255, 100, 100));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFocusPainted(false);

        panel.add(stopButton);

        return panel;
    }

    /**
     * 添加交易操作分頁
     */
    private void addTradingTab() {
        JPanel tradingPanel = new JPanel(new BorderLayout());
        tradingPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 創建交易按鈕面板
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        // 創建交易按鈕
        limitBuyButton = createTradeButton("限價買入", new Color(0, 150, 0));
        limitSellButton = createTradeButton("限價賣出", new Color(200, 0, 0));
        marketBuyButton = createTradeButton("市價買入", new Color(0, 200, 0));
        marketSellButton = createTradeButton("市價賣出", new Color(255, 0, 0));
        cancelOrderButton = createTradeButton("取消訂單", new Color(100, 100, 100));
        viewOrdersButton = createTradeButton("查看訂單", new Color(0, 100, 200));

        // 排列按鈕
        gbc.gridx = 0;
        gbc.gridy = 0;
        buttonPanel.add(limitBuyButton, gbc);
        gbc.gridx = 1;
        buttonPanel.add(limitSellButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        buttonPanel.add(marketBuyButton, gbc);
        gbc.gridx = 1;
        buttonPanel.add(marketSellButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        buttonPanel.add(cancelOrderButton, gbc);
        gbc.gridx = 1;
        buttonPanel.add(viewOrdersButton, gbc);

        // 添加說明文字
        JPanel descPanel = new JPanel();
        descPanel.setLayout(new BoxLayout(descPanel, BoxLayout.Y_AXIS));
        descPanel.setBorder(BorderFactory.createTitledBorder("交易說明"));
        descPanel.add(new JLabel("• 限價交易：指定價格進行買賣"));
        descPanel.add(new JLabel("• 市價交易：以當前市場價格立即成交"));
        descPanel.add(new JLabel("• 綠色按鈕：買入操作"));
        descPanel.add(new JLabel("• 紅色按鈕：賣出操作"));

        tradingPanel.add(buttonPanel, BorderLayout.CENTER);
        tradingPanel.add(descPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("交易操作", tradingPanel);
    }

    /**
     * 添加快捷交易分頁
     */
    private void addQuickTradeTab() {
        quickTradePanel = new QuickTradePanel();

        // 包裝在滾動面板中
        JScrollPane scrollPane = new JScrollPane(quickTradePanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        tabbedPane.addTab("快捷交易", scrollPane);
    }

    /**
     * 添加價格提醒分頁
     */
    private void addPriceAlertTab() {
        priceAlertPanel = new PriceAlertPanel();

        // 包裝在滾動面板中
        JScrollPane scrollPane = new JScrollPane(priceAlertPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        tabbedPane.addTab("價格提醒", scrollPane);
    }

    /**
     * 添加個人統計分頁
     */
    private void addPersonalStatsTab() {
        personalStatsPanel = new PersonalStatsPanel();

        // 包裝在滾動面板中
        JScrollPane scrollPane = new JScrollPane(personalStatsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        tabbedPane.addTab("個人統計", scrollPane);
    }

    /**
     * 添加撮合引擎分頁
     */
    private void addMatchingEngineTab() {
        matchingEnginePanel = new MatchingEnginePanel();

        // 包裝在滾動面板中
        JScrollPane scrollPane = new JScrollPane(matchingEnginePanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        tabbedPane.addTab("撮合引擎", scrollPane);
    }

    /**
     * 創建統一風格的交易按鈕
     */
    private JButton createTradeButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(150, 60));
        button.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createRaisedBevelBorder());

        // 添加滑鼠效果
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    /**
     * 設置分頁圖標（可選）
     */
    private void setTabIcons() {
        // 可以為每個分頁設置小圖標，讓介面更美觀
        // 例如：
        // ImageIcon tradeIcon = new ImageIcon("icons/trade.png");
        // tabbedPane.setIconAt(0, tradeIcon);

        // 設置分頁提示文字
        tabbedPane.setToolTipTextAt(0, "進行限價和市價交易");
        tabbedPane.setToolTipTextAt(1, "使用快捷鍵快速交易");
        tabbedPane.setToolTipTextAt(2, "設置價格提醒");
        tabbedPane.setToolTipTextAt(3, "查看個人交易統計");
        tabbedPane.setToolTipTextAt(4, "調整撮合引擎參數");
    }

    /**
     * 切換到指定分頁
     */
    public void switchToTab(int index) {
        if (index >= 0 && index < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(index);
        }
    }

    /**
     * 切換到指定名稱的分頁
     */
    public void switchToTab(String tabName) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(tabName)) {
                tabbedPane.setSelectedIndex(i);
                break;
            }
        }
    }

    // 基本更新方法
    public void updateUserInfo(int stockQuantity, double cash, double avgPrice, double targetPrice) {
        SwingUtilities.invokeLater(() -> {
            userStockLabel.setText("個人股票數量: " + stockQuantity);
            userCashLabel.setText("個人金錢餘額: " + String.format("%.2f", cash));
            userAvgPriceLabel.setText("個人平均價: " + String.format("%.2f", avgPrice));
            userTargetPrice.setText("個人目標價: " + String.format("%.2f", targetPrice));
        });
    }

    // Getter方法
    public JButton getStopButton() {
        return stopButton;
    }

    public JButton getLimitBuyButton() {
        return limitBuyButton;
    }

    public JButton getLimitSellButton() {
        return limitSellButton;
    }

    public JButton getMarketBuyButton() {
        return marketBuyButton;
    }

    public JButton getMarketSellButton() {
        return marketSellButton;
    }

    public JButton getCancelOrderButton() {
        return cancelOrderButton;
    }

    public JButton getViewOrdersButton() {
        return viewOrdersButton;
    }

    public MatchingEnginePanel getMatchingEnginePanel() {
        return matchingEnginePanel;
    }

    public PriceAlertPanel getPriceAlertPanel() {
        return priceAlertPanel;
    }

    public PersonalStatsPanel getPersonalStatsPanel() {
        return personalStatsPanel;
    }

    public QuickTradePanel getQuickTradePanel() {
        return quickTradePanel;
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }
}
