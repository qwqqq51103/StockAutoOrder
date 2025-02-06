package OrderManagement;

import Core.Order;
import Core.OrderBook;
import OrderManagement.OrderBookListener;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * 新的訂單檢視器視窗，用於特定需求的訂單顯示
 */
public class OrderViewer extends JFrame implements OrderBookListener {

    private JTabbedPane tabbedPane;
    private OrderBook orderBook;

    // 分頁名稱
    private static final String ALL_TYPES = "所有類型 (New)";
    private static final String RETAIL_INVESTOR = "散戶 (New)";
    private static final String MAIN_FORCE = "主力 (New)";
    private static final String PERSONAL = "個人戶 (New)";

    // 各分頁的 OrderBookTable
    private NewOrderBookTable allTypesTable;
    private NewOrderBookTable retailTable;
    private NewOrderBookTable mainForceTable;
    private NewOrderBookTable personalTable;

    // 手動選擇面板的組件
    private JComboBox<String> traderTypeComboBox;
    private JButton refreshButton;

    public OrderViewer(OrderBook orderBook) {
        this.orderBook = orderBook;
        this.orderBook.addOrderBookListener(this); // 註冊為監聽者
        initializeUI();
    }

    private void initializeUI() {
        setTitle("新的訂單檢視器");
        setSize(1000, 700);
        setLocationRelativeTo(null); // 居中顯示
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 創建分頁面板
        tabbedPane = new JTabbedPane();

        // 定義表格的列名
        String[] columnNames = {"訂單編號", "交易者類型", "類型", "數量", "價格", "時間"};

        // 初始化各分頁的 NewOrderBookTable
        // 根據需要設置是否使用進度條和是否根據類型設置顏色
        allTypesTable = new NewOrderBookTable(new Object[0][columnNames.length], columnNames, true, true);
        retailTable = new NewOrderBookTable(new Object[0][columnNames.length], columnNames, true, true);
        mainForceTable = new NewOrderBookTable(new Object[0][columnNames.length], columnNames, true, true);
        personalTable = new NewOrderBookTable(new Object[0][columnNames.length], columnNames, true, true);

        // 添加分頁到分頁面板
        tabbedPane.addTab(ALL_TYPES, createAllTypesPanel(columnNames));
        tabbedPane.addTab(RETAIL_INVESTOR, retailTable.getScrollPane());
        tabbedPane.addTab(MAIN_FORCE, mainForceTable.getScrollPane());
        tabbedPane.addTab(PERSONAL, personalTable.getScrollPane());

        // 添加分頁面板到主框架
        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        // 初始加載所有訂單
        loadAllOrders();
    }

    /**
     * 創建「所有類型 (New)」分頁面板，包含 JComboBox 和刷新按鈕
     *
     * @param columnNames 表格的列名
     * @return 包含選擇面板和表格的面板
     */
    private JPanel createAllTypesPanel(String[] columnNames) {
        JPanel panel = new JPanel(new BorderLayout());

        // 上方的選擇面板
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("選擇交易者類型: "));

        String[] traderTypes = {"所有類型", "RETAIL_INVESTOR", "MAIN_FORCE", "PERSONAL"};
        traderTypeComboBox = new JComboBox<>(traderTypes);
        topPanel.add(traderTypeComboBox);

        refreshButton = new JButton("刷新");
        topPanel.add(refreshButton);

        panel.add(topPanel, BorderLayout.NORTH);

        // 訂單表格初始化
        panel.add(allTypesTable.getScrollPane(), BorderLayout.CENTER);

        // 添加事件處理器
        traderTypeComboBox.addActionListener(e -> loadOrdersForAllTypes());
        refreshButton.addActionListener(e -> loadOrdersForAllTypes());

        return panel;
    }

    /**
     * 加載所有訂單到相應的表格中
     */
    private void loadAllOrders() {
        // 加載所有類型的訂單到「所有類型 (New)」分頁
        loadOrdersForAllTypes();

        // 加載散戶的訂單到「散戶 (New)」分頁
        loadOrdersForTab(RETAIL_INVESTOR, RETAIL_INVESTOR, retailTable);

        // 加載主力的訂單到「主力 (New)」分頁
        loadOrdersForTab(MAIN_FORCE, MAIN_FORCE, mainForceTable);

        // 加載個人戶的訂單到「個人戶 (New)」分頁
        loadOrdersForTab(PERSONAL, PERSONAL, personalTable);
    }

    /**
     * 加載所有類型的訂單到「所有類型 (New)」分頁
     */
    private void loadOrdersForAllTypes() {
        String selectedType = (String) traderTypeComboBox.getSelectedItem();
        List<Order> ordersToDisplay;

        if ("所有類型".equalsIgnoreCase(selectedType)) {
            // 創建快照以避免修改時的併發問題
            List<Order> buyOrders = new ArrayList<>(orderBook.getBuyOrders());
            List<Order> sellOrders = new ArrayList<>(orderBook.getSellOrders());
            ordersToDisplay = new ArrayList<>();
            ordersToDisplay.addAll(buyOrders);
            ordersToDisplay.addAll(sellOrders);
        } else {
            // 創建快照以避免修改時的併發問題
            List<Order> buyOrders = new ArrayList<>(orderBook.getBuyOrdersByTraderType(selectedType));
            List<Order> sellOrders = new ArrayList<>(orderBook.getSellOrdersByTraderType(selectedType));
            ordersToDisplay = new ArrayList<>();
            ordersToDisplay.addAll(buyOrders);
            ordersToDisplay.addAll(sellOrders);
        }

        // 調試輸出，查看載入了多少訂單
        //System.out.println("載入 " + ordersToDisplay.size() + " 筆訂單到「所有類型」分頁");

        // 準備表格數據
        Object[][] tableData = new Object[ordersToDisplay.size()][6];
        for (int i = 0; i < ordersToDisplay.size(); i++) {
            Order order = ordersToDisplay.get(i);
            tableData[i][0] = order.getId();
            tableData[i][1] = order.getTrader().getTraderType();
            tableData[i][2] = order.getType().equalsIgnoreCase("buy") ? "Buy" : "Sell";
            tableData[i][3] = order.getVolume();
            tableData[i][4] = String.format("%.2f", order.getPrice());
            tableData[i][5] = new java.util.Date(order.getTimestamp()).toString();
        }

        // 更新表格數據
        allTypesTable.updateData(tableData);
    }

    /**
     * 根據交易者類型加載訂單到指定的表格中
     *
     * @param tabName 分頁名稱
     * @param traderType 交易者類型
     * @param table 目標表格
     */
    private void loadOrdersForTab(String tabName, String traderType, NewOrderBookTable table) {
        List<Order> ordersToDisplay;

        if (traderType == null) { // 加載所有類型
            List<Order> buyOrders = orderBook.getBuyOrders();
            List<Order> sellOrders = orderBook.getSellOrders();
            ordersToDisplay = new ArrayList<>();
            ordersToDisplay.addAll(buyOrders);
            ordersToDisplay.addAll(sellOrders);
        } else { // 加載指定類型
            List<Order> buyOrders = orderBook.getBuyOrdersByTraderType(traderType);
            List<Order> sellOrders = orderBook.getSellOrdersByTraderType(traderType);
            ordersToDisplay = new ArrayList<>();
            ordersToDisplay.addAll(buyOrders);
            ordersToDisplay.addAll(sellOrders);
        }

        // 準備表格數據
        Object[][] tableData = new Object[ordersToDisplay.size()][6];
        for (int i = 0; i < ordersToDisplay.size(); i++) {
            Order order = ordersToDisplay.get(i);
            tableData[i][0] = order.getId();
            tableData[i][1] = order.getTrader().getTraderType();
            tableData[i][2] = order.getType().equalsIgnoreCase("buy") ? "Buy" : "Sell";
            tableData[i][3] = order.getVolume();
            tableData[i][4] = String.format("%.2f", order.getPrice());
            tableData[i][5] = new java.util.Date(order.getTimestamp()).toString();
        }

        // 更新表格數據
        table.updateData(tableData);
    }

    /**
     * 當 OrderBook 更新時，自動刷新所有分頁的表格數據
     */
    @Override
    public void onOrderBookUpdated() {
        SwingUtilities.invokeLater(this::loadAllOrders);
    }

    /**
     * 覆蓋 dispose 方法，取消註冊監聽者
     */
    @Override
    public void dispose() {
        super.dispose();
        orderBook.removeOrderBookListener(this);
    }
}
