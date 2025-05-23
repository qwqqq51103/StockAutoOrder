package StockMainAction.view;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.controller.listeners.OrderBookListener;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import javax.swing.JFileChooser;
import java.text.SimpleDateFormat;

/**
 * 現代化的訂單檢視器視窗
 */
public class OrderViewer extends JFrame implements OrderBookListener {

    private JTabbedPane tabbedPane;
    private OrderBook orderBook;

    // 狀態統計標籤
    private JLabel totalOrdersLabel;
    private JLabel buyOrdersLabel;
    private JLabel sellOrdersLabel;
    private JLabel lastUpdateLabel;

    // 自動刷新控制
    private JCheckBox autoRefreshCheckBox;
    private Timer refreshTimer;

    // 日期格式化
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    // 分頁名稱
    private static final String ALL_ORDERS = "全部訂單";
    private static final String BUY_ORDERS = "買入訂單";
    private static final String SELL_ORDERS = "賣出訂單";
    private static final String MY_ORDERS = "我的訂單";
    private static final String TRADER_ANALYSIS = "交易者分析";

    // 各分頁的表格模型和表格
    private DefaultTableModel allOrdersModel;
    private DefaultTableModel buyOrdersModel;
    private DefaultTableModel sellOrdersModel;
    private DefaultTableModel myOrdersModel;
    private JTable allOrdersTable;
    private JTable buyOrdersTable;
    private JTable sellOrdersTable;
    private JTable myOrdersTable;

    // 交易者分析面板
    private TraderAnalysisPanel traderAnalysisPanel;

    /**
     * 構造函數
     */
    public OrderViewer(OrderBook orderBook) {
        this.orderBook = orderBook;
        this.orderBook.addOrderBookListener(this);
        initializeUI();
        startAutoRefresh();
    }

    /**
     * 初始化用戶界面
     */
    private void initializeUI() {
        setTitle("訂單管理中心");
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 設置圖標
        try {
            setIconImage(new ImageIcon("icons/orders.png").getImage());
        } catch (Exception e) {
            // 忽略圖標載入失敗
        }

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(245, 245, 245));

        // 添加頂部狀態列
        mainPanel.add(createTopPanel(), BorderLayout.NORTH);

        // 創建分頁面板
        createTabbedPane();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // 添加底部控制列
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // 初始加載數據
        loadAllOrders();
    }

    /**
     * 創建頂部狀態面板
     */
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(60, 63, 65));
        topPanel.setPreferredSize(new Dimension(0, 80));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // 標題
        JLabel titleLabel = new JLabel("訂單管理中心");
        titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);

        // 統計信息面板
        JPanel statsPanel = new JPanel(new GridLayout(2, 2, 20, 5));
        statsPanel.setOpaque(false);

        totalOrdersLabel = createStatsLabel("總訂單數: 0");
        buyOrdersLabel = createStatsLabel("買單: 0", new Color(76, 175, 80));
        sellOrdersLabel = createStatsLabel("賣單: 0", new Color(244, 67, 54));
        lastUpdateLabel = createStatsLabel("最後更新: --:--:--");

        statsPanel.add(totalOrdersLabel);
        statsPanel.add(buyOrdersLabel);
        statsPanel.add(sellOrdersLabel);
        statsPanel.add(lastUpdateLabel);

        topPanel.add(titleLabel, BorderLayout.WEST);
        topPanel.add(statsPanel, BorderLayout.EAST);

        return topPanel;
    }

    /**
     * 創建統計標籤
     */
    private JLabel createStatsLabel(String text) {
        return createStatsLabel(text, Color.WHITE);
    }

    private JLabel createStatsLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        label.setForeground(color);
        return label;
    }

    /**
     * 創建分頁面板
     */
    private void createTabbedPane() {
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        tabbedPane.setBackground(Color.WHITE);

        // 設置分頁UI
        UIManager.put("TabbedPane.selected", new Color(66, 165, 245));

        // 定義表格列名
        String[] columnNames = {"訂單編號", "交易者", "類型", "數量", "價格", "時間", "狀態"};

        // 創建全部訂單分頁
        allOrdersModel = createTableModel(columnNames);
        allOrdersTable = createStyledTable(allOrdersModel);
        tabbedPane.addTab(ALL_ORDERS, createIcon("all"), createTablePanel(allOrdersTable));

        // 創建買入訂單分頁
        buyOrdersModel = createTableModel(columnNames);
        buyOrdersTable = createStyledTable(buyOrdersModel);
        tabbedPane.addTab(BUY_ORDERS, createIcon("buy"), createTablePanel(buyOrdersTable));

        // 創建賣出訂單分頁
        sellOrdersModel = createTableModel(columnNames);
        sellOrdersTable = createStyledTable(sellOrdersModel);
        tabbedPane.addTab(SELL_ORDERS, createIcon("sell"), createTablePanel(sellOrdersTable));

        // 創建我的訂單分頁
        myOrdersModel = createTableModel(columnNames);
        myOrdersTable = createStyledTable(myOrdersModel);
        tabbedPane.addTab(MY_ORDERS, createIcon("my"), createTablePanel(myOrdersTable));

        // 創建交易者分析分頁
        traderAnalysisPanel = new TraderAnalysisPanel();
        tabbedPane.addTab(TRADER_ANALYSIS, createIcon("analysis"), traderAnalysisPanel);

        // 添加分頁切換監聽器
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() == 4) { // 交易者分析分頁
                updateTraderAnalysis();
            }
        });
    }

    /**
     * 創建圖標（placeholder）
     */
    private Icon createIcon(String type) {
        // 創建簡單的彩色圖標
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                switch (type) {
                    case "buy":
                        g2.setColor(new Color(76, 175, 80));
                        break;
                    case "sell":
                        g2.setColor(new Color(244, 67, 54));
                        break;
                    case "my":
                        g2.setColor(new Color(33, 150, 243));
                        break;
                    case "analysis":
                        g2.setColor(new Color(156, 39, 176));
                        break;
                    default:
                        g2.setColor(new Color(158, 158, 158));
                }

                g2.fillOval(x, y, 16, 16);
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }

    /**
     * 創建表格模型
     */
    private DefaultTableModel createTableModel(String[] columnNames) {
        return new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 3) {
                    return Integer.class; // 數量列
                }                // 移除價格列的 Double 類型定義，因為可能是 "市價" 字串
                return String.class;
            }
        };
    }

    /**
     * 創建美化的表格
     */
    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);

        // 基本設置
        table.setRowHeight(35);
        table.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        // 表頭樣式
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        header.setBackground(new Color(238, 238, 238));
        header.setForeground(new Color(33, 33, 33));
        header.setPreferredSize(new Dimension(0, 40));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(224, 224, 224)));

        // 設置列寬
        table.getColumnModel().getColumn(0).setPreferredWidth(120); // 訂單編號
        table.getColumnModel().getColumn(1).setPreferredWidth(100); // 交易者
        table.getColumnModel().getColumn(2).setPreferredWidth(60);  // 類型
        table.getColumnModel().getColumn(3).setPreferredWidth(80);  // 數量
        table.getColumnModel().getColumn(4).setPreferredWidth(80);  // 價格
        table.getColumnModel().getColumn(5).setPreferredWidth(120); // 時間
        table.getColumnModel().getColumn(6).setPreferredWidth(80);  // 狀態

        // 自定義渲染器
        table.setDefaultRenderer(Object.class, new ModernTableCellRenderer());

        return table;
    }

    /**
     * 現代化的表格單元渲染器
     */
    private class ModernTableCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            Component comp = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            // 設置對齊方式
            setHorizontalAlignment(column >= 3 && column <= 4 ? CENTER : LEFT);

            // 設置邊框和內邊距
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

            // 設置顏色
            if (isSelected) {
                setBackground(new Color(232, 240, 254));
                setForeground(new Color(13, 71, 161));
            } else {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 250, 250));
                setForeground(Color.BLACK);

                // 特殊列的顏色
                if (column == 2) { // 類型列
                    String type = value.toString();
                    if ("買入".equals(type) || "Buy".equals(type)) {
                        setForeground(new Color(76, 175, 80));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if ("賣出".equals(type) || "Sell".equals(type)) {
                        setForeground(new Color(244, 67, 54));
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                } else if (column == 6) { // 狀態列
                    setForeground(new Color(255, 152, 0));
                }
            }

            return comp;
        }
    }

    /**
     * 創建表格面板
     */
    private JPanel createTablePanel(JTable table) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        // 添加搜索/過濾工具欄
        JPanel toolBar = createTableToolBar(table);
        panel.add(toolBar, BorderLayout.NORTH);

        // 表格滾動面板
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.WHITE);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 創建表格工具欄
     */
    private JPanel createTableToolBar(JTable table) {
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolBar.setBackground(Color.WHITE);
        toolBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(224, 224, 224)));

        // 搜索框
        JTextField searchField = new JTextField(20);
        searchField.putClientProperty("JTextField.placeholderText", "搜索訂單...");

        // 搜索按鈕
        JButton searchButton = new JButton("搜索");
        searchButton.setFocusPainted(false);

        // 過濾下拉框
        JComboBox<String> filterCombo = new JComboBox<>(
                new String[]{"所有交易者", "散戶", "主力", "個人戶"}
        );

        // 導出按鈕
        JButton exportButton = new JButton("導出");
        exportButton.setFocusPainted(false);
        exportButton.addActionListener(e -> exportTableData(table));

        // 搜索功能
        searchButton.addActionListener(e -> searchTable(table, searchField.getText()));
        searchField.addActionListener(e -> searchTable(table, searchField.getText()));

        // 過濾功能
        filterCombo.addActionListener(e -> filterTable(table, filterCombo.getSelectedItem().toString()));

        toolBar.add(new JLabel("搜索: "));
        toolBar.add(searchField);
        toolBar.add(searchButton);
        toolBar.add(Box.createHorizontalStrut(20));
        toolBar.add(new JLabel("過濾: "));
        toolBar.add(filterCombo);
        toolBar.add(Box.createHorizontalStrut(20));
        toolBar.add(exportButton);

        return toolBar;
    }

    /**
     * 創建底部控制面板
     */
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(new Color(250, 250, 250));
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)));

        // 自動刷新選項
        autoRefreshCheckBox = new JCheckBox("自動刷新 (5秒)");
        autoRefreshCheckBox.setSelected(true);
        autoRefreshCheckBox.addActionListener(e -> {
            if (autoRefreshCheckBox.isSelected()) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });

        // 手動刷新按鈕
        JButton refreshButton = new JButton("立即刷新");
        refreshButton.setFocusPainted(false);
        refreshButton.addActionListener(e -> loadAllOrders());

        // 關閉按鈕
        JButton closeButton = new JButton("關閉");
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dispose());

        bottomPanel.add(autoRefreshCheckBox);
        bottomPanel.add(refreshButton);
        bottomPanel.add(closeButton);

        return bottomPanel;
    }

    /**
     * 交易者分析面板
     */
    private class TraderAnalysisPanel extends JPanel {

        private JTextArea analysisText;

        public TraderAnalysisPanel() {
            setLayout(new BorderLayout());
            setBackground(Color.WHITE);

            // 標題
            JLabel titleLabel = new JLabel("交易者行為分析", JLabel.CENTER);
            titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 18));
            titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
            add(titleLabel, BorderLayout.NORTH);

            // 分析文本區域
            analysisText = new JTextArea();
            analysisText.setEditable(false);
            analysisText.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
            analysisText.setMargin(new Insets(20, 20, 20, 20));

            JScrollPane scrollPane = new JScrollPane(analysisText);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            add(scrollPane, BorderLayout.CENTER);
        }

        public void updateAnalysis(String analysis) {
            analysisText.setText(analysis);
            analysisText.setCaretPosition(0);
        }
    }

    /**
     * 加載所有訂單
     */
    private void loadAllOrders() {
        // 更新所有分頁
        updateTableData(allOrdersModel, getAllOrders());
        updateTableData(buyOrdersModel, getBuyOrders());
        updateTableData(sellOrdersModel, getSellOrders());
        updateTableData(myOrdersModel, getMyOrders());

        // 更新統計信息
        updateStatistics();

        // 更新時間
        lastUpdateLabel.setText("最後更新: " + dateFormat.format(new java.util.Date()));
    }

    /**
     * 更新表格數據
     */
    private void updateTableData(DefaultTableModel model, List<Order> orders) {
        model.setRowCount(0);

        for (Order order : orders) {
            Object[] rowData = {
                order.getId(),
                getTraderDisplayName(order.getTrader().getTraderType()),
                order.getType().equalsIgnoreCase("buy") ? "買入" : "賣出",
                order.getVolume(),
                order.isMarketOrder() ? "市價" : String.format("%.2f", order.getPrice()),
                dateFormat.format(new java.util.Date(order.getTimestamp())),
                "待成交" // 狀態
            };
            model.addRow(rowData);
        }
    }

    /**
     * 獲取交易者顯示名稱
     */
    private String getTraderDisplayName(String traderType) {
        switch (traderType) {
            case "RETAIL_INVESTOR":
                return "散戶";
            case "MAIN_FORCE":
                return "主力";
            case "PERSONAL":
                return "個人戶";
            case "MarketBehavior":
                return "市場單";
            default:
                return traderType;
        }
    }

    /**
     * 獲取所有訂單
     */
    private List<Order> getAllOrders() {
        List<Order> allOrders = new ArrayList<>();
        allOrders.addAll(orderBook.getBuyOrders());
        allOrders.addAll(orderBook.getSellOrders());
        return allOrders;
    }

    /**
     * 獲取買入訂單
     */
    private List<Order> getBuyOrders() {
        return new ArrayList<>(orderBook.getBuyOrders());
    }

    /**
     * 獲取賣出訂單
     */
    private List<Order> getSellOrders() {
        return new ArrayList<>(orderBook.getSellOrders());
    }

    /**
     * 獲取我的訂單
     */
    private List<Order> getMyOrders() {
        List<Order> myOrders = new ArrayList<>();
        myOrders.addAll(orderBook.getBuyOrdersByTraderType("PERSONAL"));
        myOrders.addAll(orderBook.getSellOrdersByTraderType("PERSONAL"));
        return myOrders;
    }

    /**
     * 更新統計信息
     */
    private void updateStatistics() {
        int totalOrders = orderBook.getBuyOrders().size() + orderBook.getSellOrders().size();
        int buyOrders = orderBook.getBuyOrders().size();
        int sellOrders = orderBook.getSellOrders().size();

        totalOrdersLabel.setText("總訂單數: " + totalOrders);
        buyOrdersLabel.setText("買單: " + buyOrders);
        sellOrdersLabel.setText("賣單: " + sellOrders);
    }

    /**
     * 更新交易者分析
     */
    private void updateTraderAnalysis() {
        StringBuilder analysis = new StringBuilder();
        analysis.append("=== 交易者行為分析報告 ===\n\n");

        // 分析各類交易者的訂單分布
        analysis.append("【訂單分布分析】\n");
        analysis.append(analyzeOrderDistribution());
        analysis.append("\n");

        // 分析價格分布
        analysis.append("【價格分布分析】\n");
        analysis.append(analyzePriceDistribution());
        analysis.append("\n");

        // 分析交易活躍度
        analysis.append("【交易活躍度分析】\n");
        analysis.append(analyzeTradeActivity());

        traderAnalysisPanel.updateAnalysis(analysis.toString());
    }

    private String analyzeOrderDistribution() {
        // 這裡可以添加更詳細的分析邏輯
        return "• 散戶訂單集中在小額交易\n"
                + "• 主力訂單顯示明顯的價格操控意圖\n"
                + "• 個人戶交易呈現理性投資特徵\n";
    }

    private String analyzePriceDistribution() {
        return "• 買單主要集中在支撐位附近\n"
                + "• 賣單在壓力位形成密集區\n"
                + "• 市價單比例顯示市場情緒\n";
    }

    private String analyzeTradeActivity() {
        return "• 交易最活躍時段：開盤後30分鐘\n"
                + "• 主力在關鍵價位頻繁掛撤單\n"
                + "• 散戶追漲殺跌行為明顯\n";
    }

    /**
     * 開始自動刷新
     */
    private void startAutoRefresh() {
        if (refreshTimer == null) {
            refreshTimer = new Timer(5000, e -> loadAllOrders());
            refreshTimer.start();
        }
    }

    /**
     * 停止自動刷新
     */
    private void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }

    /**
     * OrderBookListener 接口實現
     */
    @Override
    public void onOrderBookUpdated() {
        SwingUtilities.invokeLater(this::loadAllOrders);
    }

    /**
     * 清理資源
     */
    @Override
    public void dispose() {
        stopAutoRefresh();
        orderBook.removeOrderBookListener(this);
        super.dispose();
    }

    /**
     * 導出表格數據
     */
    private void exportTableData(JTable table) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("導出訂單數據");
        fileChooser.setSelectedFile(new java.io.File("訂單數據_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            try {
                exportToCSV(table, file);
                JOptionPane.showMessageDialog(this,
                        "導出成功！\n檔案：" + file.getAbsolutePath(),
                        "導出完成",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "導出失敗：" + e.getMessage(),
                        "錯誤",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 導出到CSV檔案
     */
    private void exportToCSV(JTable table, java.io.File file) throws Exception {
        try ( java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(file), "UTF-8"))) {

            // 寫入BOM以支援Excel正確顯示中文
            writer.write('\ufeff');

            // 寫入標題行
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            for (int i = 0; i < model.getColumnCount(); i++) {
                if (i > 0) {
                    writer.print(",");
                }
                writer.print("\"" + model.getColumnName(i) + "\"");
            }
            writer.println();

            // 寫入數據行
            for (int row = 0; row < model.getRowCount(); row++) {
                for (int col = 0; col < model.getColumnCount(); col++) {
                    if (col > 0) {
                        writer.print(",");
                    }
                    Object value = model.getValueAt(row, col);
                    if (value != null) {
                        String text = value.toString();
                        // 如果包含逗號或引號，需要用引號包圍並轉義
                        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
                            text = "\"" + text.replace("\"", "\"\"") + "\"";
                        }
                        writer.print(text);
                    }
                }
                writer.println();
            }

            writer.flush();
        }
    }

    /**
     * 搜索表格
     */
    private void searchTable(JTable table, String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            table.setRowSorter(null);
            return;
        }

        DefaultTableModel model = (DefaultTableModel) table.getModel();
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        try {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText.trim()));
        } catch (Exception e) {
            // 忽略無效的正則表達式
        }
    }

    /**
     * 過濾表格
     */
    private void filterTable(JTable table, String filterType) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        if ("所有交易者".equals(filterType)) {
            sorter.setRowFilter(null);
        } else {
            String traderType = "";
            switch (filterType) {
                case "散戶":
                    traderType = "散戶";
                    break;
                case "主力":
                    traderType = "主力";
                    break;
                case "個人戶":
                    traderType = "個人戶";
                    break;
            }
            final String type = traderType;
            sorter.setRowFilter(new RowFilter<DefaultTableModel, Object>() {
                @Override
                public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                    return type.equals(entry.getStringValue(1)); // 交易者列
                }
            });
        }
    }
}
