package StockMainAction.view;

import StockMainAction.MatchingEnginePanel;
import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.core.OrderBook;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Hashtable;

/**
 * 控制視圖 - 提供用戶界面控制元素
 */
public class ControlView extends JFrame {

    // 控制按鈕
    private JButton stopButton;
    private JButton limitBuyButton;
    private JButton limitSellButton;
    private JButton marketBuyButton;
    private JButton marketSellButton;
    private JButton cancelOrderButton;
    private JButton viewOrdersButton;

    // 用戶資訊標籤
    private JLabel userStockLabel;
    private JLabel userCashLabel;
    private JLabel userAvgPriceLabel;
    private JLabel userTargetPrice;

    // 撮合引擎面板
    private MatchingEnginePanel matchingEnginePanel;

    /**
     * 構造函數
     */
    public ControlView() {
        initializeUI();
    }

    /**
     * 初始化UI
     */
    private void initializeUI() {
        setTitle("股票市場模擬 - 控制視窗");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(500, 600);  // 稍微調大窗口以容納撮合引擎面板
        setLocationRelativeTo(null);

        // 創建主面板，使用垂直 BoxLayout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // === 創建用戶資訊面板 ===
        JPanel userInfoPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        userInfoPanel.setBorder(BorderFactory.createTitledBorder("個人資訊"));

        userStockLabel = new JLabel("個人股票數量: 0");
        userCashLabel = new JLabel("個人金錢餘額: 0.00");
        userAvgPriceLabel = new JLabel("個人平均價: 0.00");
        userTargetPrice = new JLabel("個人目標價: 0.00");

        userInfoPanel.add(userStockLabel);
        userInfoPanel.add(userCashLabel);
        userInfoPanel.add(userAvgPriceLabel);
        userInfoPanel.add(userTargetPrice);

        // === 創建交易操作面板 ===
        JPanel tradePanel = new JPanel(new GridLayout(3, 2, 10, 10));
        tradePanel.setBorder(BorderFactory.createTitledBorder("交易操作"));

        limitBuyButton = new JButton("限價買入");
        limitSellButton = new JButton("限價賣出");
        marketBuyButton = new JButton("市價買入");
        marketSellButton = new JButton("市價賣出");
        cancelOrderButton = new JButton("取消訂單");
        viewOrdersButton = new JButton("查看訂單");

        tradePanel.add(limitBuyButton);
        tradePanel.add(limitSellButton);
        tradePanel.add(marketBuyButton);
        tradePanel.add(marketSellButton);
        tradePanel.add(cancelOrderButton);
        tradePanel.add(viewOrdersButton);

        // === 創建系統控制面板 ===
        JPanel systemPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        systemPanel.setBorder(BorderFactory.createTitledBorder("系統控制"));

        stopButton = new JButton("停止");
        systemPanel.add(stopButton);

        // === 初始化撮合引擎面板 ===
        matchingEnginePanel = new MatchingEnginePanel();

        // === 將所有面板添加到主面板 ===
        mainPanel.add(userInfoPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));  // 添加間距
        mainPanel.add(tradePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));  // 添加間距
        mainPanel.add(systemPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));  // 添加間距
        mainPanel.add(matchingEnginePanel);

        // 添加主面板到窗口
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * 更新用戶資訊
     */
    public void updateUserInfo(int stockQuantity, double cash, double avgPrice, double targetPrice) {
        SwingUtilities.invokeLater(() -> {
            userStockLabel.setText("個人股票數量: " + stockQuantity);
            userCashLabel.setText("個人金錢餘額: " + String.format("%.2f", cash));
            userAvgPriceLabel.setText("個人平均價: " + String.format("%.2f", avgPrice));
            userTargetPrice.setText("個人目標價: " + String.format("%.2f", targetPrice));
        });
    }

    // Getter 方法
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
}
