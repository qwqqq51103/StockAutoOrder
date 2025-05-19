package StockMainAction.view;

import Core.Order;
import Core.OrderBook;
import OrderManagement.OrderBookListener;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

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

    // 各分頁的表格模型
    private DefaultTableModel allTypesModel;
    private DefaultTableModel retailModel;
    private DefaultTableModel mainForceModel;
    private DefaultTableModel personalModel;

    // 各分頁的表格
    private JTable allTypesTable;
    private JTable retailTable;
    private JTable mainForceTable;
    private JTable personalTable;

    // 手動選擇面板的組件
    private JComboBox<String> traderTypeComboBox;
    private JButton refreshButton;

    /**
     * 構造函數
     *
     * @param orderBook 訂單簿實例
     */
    public OrderViewer(OrderBook orderBook) {
        this.orderBook = orderBook;
        this.orderBook.addOrderBookListener(this); // 註冊為監聽者
        initializeUI();
    }

    /**
     * 初始化用戶界面
     */
    private void initializeUI() {
        setTitle("訂單檢視器");
        setSize(1000, 700);
        setLocationRelativeTo(null); // 居中顯示
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 創建分頁面板
        tabbedPane = new JTabbedPane();

        // 定義表格的列名
        String[] columnNames = {"訂單編號", "交易者類型", "類型", "數量", "價格", "時間"};

        // 初始化各分頁
        allTypesModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        allTypesTable = new JTable(allTypesModel);
        setupTable(allTypesTable);

        retailModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        retailTable = new JTable(retailModel);
        setupTable(retailTable);

        mainForceModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        mainForceTable = new JTable(mainForceModel);
        setupTable(mainForceTable);

        personalModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        personalTable = new JTable(personalModel);
        setupTable(personalTable);

        // 添加分頁到分頁面板
        tabbedPane.addTab(ALL_TYPES, createAllTypesPanel());
        tabbedPane.addTab(RETAIL_INVESTOR, new JScrollPane(retailTable));
        tabbedPane.addTab(MAIN_FORCE, new JScrollPane(mainForceTable));
        tabbedPane.addTab(PERSONAL, new JScrollPane(personalTable));

        // 添加分頁面板到主框架
        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        // 初始加載所有訂單
        loadAllOrders();
    }

    /**
     * 設置表格的樣式
     *
     * @param table 表格
     */
    private void setupTable(JTable table) {
        table.setRowHeight(25);
        table.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);

        // 設定列寬
        table.getColumnModel().getColumn(0).setPreferredWidth(150); // 訂單編號
        table.getColumnModel().getColumn(1).setPreferredWidth(120); // 交易者類型
        table.getColumnModel().getColumn(2).setPreferredWidth(60);  // 類型
        table.getColumnModel().getColumn(3).setPreferredWidth(60);  // 數量
        table.getColumnModel().getColumn(4).setPreferredWidth(80);  // 價格
        table.getColumnModel().getColumn(5).setPreferredWidth(200); // 時間

        // 設定渲染器
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (column == 2) { // 類型列
                    if ("Buy".equals(value)) {
                        comp.setForeground(Color.RED);
                    } else if ("Sell".equals(value)) {
                        comp.setForeground(Color.GREEN);
                    } else {
                        comp.setForeground(Color.BLACK);
                    }
                } else {
                    comp.setForeground(Color.BLACK);
                }

                if (isSelected) {
                    comp.setBackground(new Color(184, 207, 229));
                } else {
                    comp.setBackground(row % 2 == 0 ? Color.WHITE : new Color(242, 242, 242));
                }

                return comp;
            }
        });
    }

    /**
     * 創建「所有類型」分頁面板，包含過濾功能
     *
     * @return 包含選擇面板和表格的面板
     */
    private JPanel createAllTypesPanel() {
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

        // 訂單表格
        JScrollPane scrollPane = new JScrollPane(allTypesTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 添加事件處理器
        traderTypeComboBox.addActionListener(e -> loadOrdersForAllTypes());
        refreshButton.addActionListener(e -> loadAllOrders());

        return panel;
    }

    /**
     * 加載所有訂單到相應的表格中
     */
    private void loadAllOrders() {
        // 加載所有類型的訂單到「所有類型」分頁
        loadOrdersForAllTypes();

        // 加載散戶的訂單到「散戶」分頁
        loadOrdersForTab(RETAIL_INVESTOR, "RETAIL_INVESTOR", retailModel);

        // 加載主力的訂單到「主力」分頁
        loadOrdersForTab(MAIN_FORCE, "MAIN_FORCE", mainForceModel);

        // 加載個人戶的訂單到「個人戶」分頁
        loadOrdersForTab(PERSONAL, "PERSONAL", personalModel);
    }

    /**
     * 加載所有類型的訂單到「所有類型」分頁
     */
    private void loadOrdersForAllTypes() {
        String selectedType = (String) traderTypeComboBox.getSelectedItem();
        List<Order> ordersToDisplay;

        // 清空表格
        allTypesModel.setRowCount(0);

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

        // 為每個訂單添加一行到表格
        for (Order order : ordersToDisplay) {
            Object[] rowData = {
                order.getId(),
                order.getTrader().getTraderType(),
                order.getType().equalsIgnoreCase("buy") ? "Buy" : "Sell",
                order.getVolume(),
                order.isMarketOrder() ? "市價" : String.format("%.2f", order.getPrice()),
                new java.util.Date(order.getTimestamp()).toString()
            };
            allTypesModel.addRow(rowData);
        }
    }

    /**
     * 根據交易者類型加載訂單到指定的表格模型中
     *
     * @param tabName 分頁名稱
     * @param traderType 交易者類型
     * @param model 目標表格模型
     */
    private void loadOrdersForTab(String tabName, String traderType, DefaultTableModel model) {
        List<Order> ordersToDisplay;

        // 清空表格
        model.setRowCount(0);

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

        // 為每個訂單添加一行到表格
        for (Order order : ordersToDisplay) {
            Object[] rowData = {
                order.getId(),
                order.getTrader().getTraderType(),
                order.getType().equalsIgnoreCase("buy") ? "Buy" : "Sell",
                order.getVolume(),
                order.isMarketOrder() ? "市價" : String.format("%.2f", order.getPrice()),
                new java.util.Date(order.getTimestamp()).toString()
            };
            model.addRow(rowData);
        }
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
