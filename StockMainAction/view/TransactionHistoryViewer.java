package StockMainAction.view;

import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.Transaction;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.util.Map;
import java.util.HashMap;

/**
 * 成交記錄視窗 - 詳細顯示所有交易記錄
 */
public class TransactionHistoryViewer extends JFrame implements StockMarketModel.TransactionListener {

    // UI 組件
    private JTabbedPane tabbedPane;
    private JTable allTransactionsTable;
    private JTable buyTransactionsTable;
    private JTable sellTransactionsTable;
    private JTable myTransactionsTable;
    private DefaultTableModel allTransactionsModel;
    private DefaultTableModel buyTransactionsModel;
    private DefaultTableModel sellTransactionsModel;
    private DefaultTableModel myTransactionsModel;

    // 統計標籤
    private JLabel totalTransactionsLabel;
    private JLabel totalVolumeLabel;
    private JLabel totalAmountLabel;
    private JLabel avgPriceLabel;
    private JLabel lastUpdateLabel;

    // 圖表面板
    private TransactionChartPanel chartPanel;

    // 數據
    private List<Transaction> transactionHistory;
    private Timer refreshTimer;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private DecimalFormat priceFormat = new DecimalFormat("#,##0.00");
    private DecimalFormat volumeFormat = new DecimalFormat("#,##0");

    private StockMarketModel model; // 添加 model 引用

    private JTextArea statsAnalysisTextArea;
    private DefaultTableModel traderAnalysisModel;

    // 修改建構函數
    public TransactionHistoryViewer(StockMarketModel model) {
        this.model = model;
        this.transactionHistory = new ArrayList<>();

        // 註冊為監聽器
        if (model != null) {
            model.addTransactionListener(this);
        }

        initializeUI();
        startAutoRefresh();
    }

    /**
     * 初始化用戶界面
     */
    private void initializeUI() {
        setTitle("成交記錄管理中心");
        setSize(1400, 900);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(245, 245, 245));

        // 添加頂部統計面板
        mainPanel.add(createTopPanel(), BorderLayout.NORTH);

        // 創建主要內容區域（左右分割）
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(900);
        splitPane.setOneTouchExpandable(true);

        // 左側：表格區域
        splitPane.setLeftComponent(createTableArea());

        // 右側：圖表和分析區域
        splitPane.setRightComponent(createAnalysisArea());

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 添加底部控制面板
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    /**
     * 創建頂部統計面板
     */
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(48, 63, 159));
        topPanel.setPreferredSize(new Dimension(0, 120)); // 🔄 從 100 增加到 120
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // 標題
        JLabel titleLabel = new JLabel("成交記錄管理中心");
        titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);

        // 統計信息面板
        JPanel statsPanel = new JPanel(new GridLayout(2, 3, 30, 15)); // 🔄 增加垂直間距從 10 到 15
        statsPanel.setOpaque(false);
        statsPanel.setPreferredSize(new Dimension(600, 80)); // 🆕 設定固定寬度和高度

        totalTransactionsLabel = createStatsLabel("總成交筆數", "0");
        totalVolumeLabel = createStatsLabel("總成交量", "0");
        totalAmountLabel = createStatsLabel("總成交額", "0.00");
        avgPriceLabel = createStatsLabel("平均成交價", "0.00");
        lastUpdateLabel = createStatsLabel("最後更新", "--:--:--");

        statsPanel.add(totalTransactionsLabel);
        statsPanel.add(totalVolumeLabel);
        statsPanel.add(totalAmountLabel);
        statsPanel.add(avgPriceLabel);
        statsPanel.add(new JLabel()); // 空白
        statsPanel.add(lastUpdateLabel);

        topPanel.add(titleLabel, BorderLayout.WEST);
        topPanel.add(statsPanel, BorderLayout.EAST);

        return topPanel;
    }

    /**
     * 創建統計標籤
     */
    private JLabel createStatsLabel(String title, String value) {
        JLabel label = new JLabel("<html><div style='text-align: center;'>"
                + "<span style='font-size:10px;color:#B0BEC5;'>" + title + "</span><br>"
                + "<span style='font-size:16px;color:#FFFFFF;font-weight:bold;'>" + value + "</span></div></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    /**
     * 創建表格區域
     */
    private JComponent createTableArea() {
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));

        // 定義詳細的表格列
        String[] columnNames = {
            "成交編號", "成交時間", "買方", "賣方", "成交價",
            "成交量", "成交額", "買方剩餘", "賣方剩餘", "價格變動", "備註"
        };

        // 創建各個分頁
        allTransactionsModel = createDetailedTableModel(columnNames);
        allTransactionsTable = createDetailedTable(allTransactionsModel);
        tabbedPane.addTab("全部成交", createIcon(Color.GRAY),
                createTablePanel(allTransactionsTable, "所有成交記錄"));

        buyTransactionsModel = createDetailedTableModel(columnNames);
        buyTransactionsTable = createDetailedTable(buyTransactionsModel);
        tabbedPane.addTab("買入成交", createIcon(new Color(76, 175, 80)),
                createTablePanel(buyTransactionsTable, "買方主動成交"));

        sellTransactionsModel = createDetailedTableModel(columnNames);
        sellTransactionsTable = createDetailedTable(sellTransactionsModel);
        tabbedPane.addTab("賣出成交", createIcon(new Color(244, 67, 54)),
                createTablePanel(sellTransactionsTable, "賣方主動成交"));

        myTransactionsModel = createDetailedTableModel(columnNames);
        myTransactionsTable = createDetailedTable(myTransactionsModel);
        tabbedPane.addTab("我的成交", createIcon(new Color(33, 150, 243)),
                createTablePanel(myTransactionsTable, "個人交易記錄"));

        return tabbedPane;
    }

    /**
     * 創建詳細表格模型
     */
    private DefaultTableModel createDetailedTableModel(String[] columnNames) {
        return new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 4: // 成交價
                    case 6: // 成交額
                    case 9: // 價格變動
                        return Double.class;
                    case 5: // 成交量
                    case 7: // 買方剩餘
                    case 8: // 賣方剩餘
                        return Integer.class;
                    default:
                        return String.class;
                }
            }
        };
    }

    /**
     * 創建詳細表格
     */
    private JTable createDetailedTable(DefaultTableModel model) {
        JTable table = new JTable(model);

        // 基本設置
        table.setRowHeight(32);
        table.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        // 表頭樣式
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        header.setBackground(new Color(63, 81, 181));
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 35));

        // 設置列寬
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(80);   // 成交編號
        columnModel.getColumn(1).setPreferredWidth(130);  // 成交時間
        columnModel.getColumn(2).setPreferredWidth(80);   // 買方
        columnModel.getColumn(3).setPreferredWidth(80);   // 賣方
        columnModel.getColumn(4).setPreferredWidth(70);   // 成交價
        columnModel.getColumn(5).setPreferredWidth(70);   // 成交量
        columnModel.getColumn(6).setPreferredWidth(90);   // 成交額
        columnModel.getColumn(7).setPreferredWidth(70);   // 買方剩餘
        columnModel.getColumn(8).setPreferredWidth(70);   // 賣方剩餘
        columnModel.getColumn(9).setPreferredWidth(70);   // 價格變動
        columnModel.getColumn(10).setPreferredWidth(150); // 備註

        // 自定義渲染器
        table.setDefaultRenderer(Object.class, new TransactionTableCellRenderer());
        table.setDefaultRenderer(Double.class, new TransactionTableCellRenderer());
        table.setDefaultRenderer(Integer.class, new TransactionTableCellRenderer());

        return table;
    }

    /**
     * 成交記錄表格渲染器
     */
    private class TransactionTableCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            Component comp = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            // 設置對齊
            if (column >= 4 && column <= 9) {
                setHorizontalAlignment(CENTER);
            } else {
                setHorizontalAlignment(LEFT);
            }

            // 設置邊距
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

            // 格式化數值
            if (value != null) {
                if (column == 4 || column == 6) { // 價格列
                    setText(priceFormat.format(value));
                } else if (column == 5 || column == 7 || column == 8) { // 數量列
                    setText(volumeFormat.format(value));
                } else if (column == 9) { // 價格變動
                    double change = (Double) value;
                    setText(String.format("%+.2f%%", change));
                    if (!isSelected) {
                        if (change > 0) {
                            setForeground(new Color(76, 175, 80));
                        } else if (change < 0) {
                            setForeground(new Color(244, 67, 54));
                        } else {
                            setForeground(Color.GRAY);
                        }
                    }
                }
            }

            // 設置背景色
            if (isSelected) {
                setBackground(new Color(232, 240, 254));
                if (column != 9) {
                    setForeground(new Color(13, 71, 161));
                }
            } else {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 250, 250));
                if (column != 9) {
                    setForeground(Color.BLACK);
                }
            }

            return comp;
        }
    }

    /**
     * 創建表格面板
     */
    private JPanel createTablePanel(JTable table, String description) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        // 描述標籤
        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        descLabel.setForeground(Color.GRAY);
        descLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        panel.add(descLabel, BorderLayout.NORTH);

        // 表格滾動面板
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(224, 224, 224)));
        scrollPane.getViewport().setBackground(Color.WHITE);

        panel.add(scrollPane, BorderLayout.CENTER);

        // 添加右鍵菜單
        addTableContextMenu(table);

        return panel;
    }

    /**
     * 添加表格右鍵菜單
     */
    private void addTableContextMenu(JTable table) {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem viewDetailsItem = new JMenuItem("查看詳情");
        viewDetailsItem.addActionListener(e -> viewTransactionDetails(table));

        JMenuItem copyItem = new JMenuItem("複製行");
        copyItem.addActionListener(e -> copyTableRow(table));

        JMenuItem exportItem = new JMenuItem("導出選中");
        exportItem.addActionListener(e -> exportSelectedRows(table));

        popupMenu.add(viewDetailsItem);
        popupMenu.addSeparator();
        popupMenu.add(copyItem);
        popupMenu.add(exportItem);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() && table.getSelectedRow() != -1) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * 創建分析區域
     */
    private JComponent createAnalysisArea() {
        JPanel analysisPanel = new JPanel(new BorderLayout());
        analysisPanel.setBackground(Color.WHITE);
        analysisPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 創建分頁
        JTabbedPane analysisTabs = new JTabbedPane();

        // 圖表分頁
        chartPanel = new TransactionChartPanel();
        analysisTabs.addTab("成交圖表", chartPanel);

        // 統計分析分頁
        JPanel statsAnalysisPanel = createStatsAnalysisPanel();
        analysisTabs.addTab("統計分析", statsAnalysisPanel);

        // 交易者分析分頁
        JPanel traderAnalysisPanel = createTraderAnalysisPanel();
        analysisTabs.addTab("交易者分析", traderAnalysisPanel);

        analysisPanel.add(analysisTabs, BorderLayout.CENTER);

        return analysisPanel;
    }

    /**
     * 創建統計分析面板
     */
    private JPanel createStatsAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        statsAnalysisTextArea = new JTextArea(); // 設為成員變數
        statsAnalysisTextArea.setEditable(false);
        statsAnalysisTextArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        statsAnalysisTextArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(statsAnalysisTextArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        panel.add(scrollPane, BorderLayout.CENTER);

        // 更新統計文本
        updateStatisticsAnalysis();

        return panel;
    }

// 新增方法：更新統計分析
    private void updateStatisticsAnalysis() {
        String report = generateStatisticsReport();
        if (statsAnalysisTextArea != null) {
            statsAnalysisTextArea.setText(report);
            statsAnalysisTextArea.setCaretPosition(0);
        }
    }

    /**
     * 創建交易者分析面板
     */
    private JPanel createTraderAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        // 創建交易者統計表格
        String[] columns = {"交易者類型", "成交筆數", "買入量", "賣出量", "淨買賣", "平均價格", "活躍度"};
        traderAnalysisModel = new DefaultTableModel(columns, 0); // 設為成員變數
        JTable traderTable = new JTable(traderAnalysisModel);
        traderTable.setRowHeight(30);

        JScrollPane scrollPane = new JScrollPane(traderTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 更新交易者分析
        updateTraderAnalysis();

        return panel;
    }

// 新增方法：更新交易者分析
    private void updateTraderAnalysis() {
        if (traderAnalysisModel == null) {
            return;
        }

        traderAnalysisModel.setRowCount(0);

        // 統計各交易者類型的數據
        Map<String, TraderStats> statsMap = new HashMap<>();

        for (Transaction trans : transactionHistory) {
            // 統計買方
            String buyerType = trans.getBuyer().getTraderType();
            TraderStats buyerStats = statsMap.computeIfAbsent(buyerType, k -> new TraderStats());
            buyerStats.buyCount++;
            buyerStats.buyVolume += trans.getVolume();
            buyerStats.totalAmount += trans.getPrice() * trans.getVolume();
            buyerStats.totalVolume += trans.getVolume();

            // 統計賣方
            String sellerType = trans.getSeller().getTraderType();
            TraderStats sellerStats = statsMap.computeIfAbsent(sellerType, k -> new TraderStats());
            sellerStats.sellCount++;
            sellerStats.sellVolume += trans.getVolume();
            sellerStats.totalAmount += trans.getPrice() * trans.getVolume();
            sellerStats.totalVolume += trans.getVolume();
        }

        // 添加到表格
        for (Map.Entry<String, TraderStats> entry : statsMap.entrySet()) {
            String traderType = entry.getKey();
            TraderStats stats = entry.getValue();

            Object[] rowData = {
                getTraderDisplay(traderType),
                stats.buyCount + stats.sellCount,
                volumeFormat.format(stats.buyVolume),
                volumeFormat.format(stats.sellVolume),
                volumeFormat.format(stats.buyVolume - stats.sellVolume),
                priceFormat.format(stats.totalAmount / stats.totalVolume),
                String.format("%.1f%%", (double) (stats.buyCount + stats.sellCount) / transactionHistory.size() * 100)
            };

            traderAnalysisModel.addRow(rowData);
        }
    }

// 輔助類：交易者統計
    private static class TraderStats {

        int buyCount = 0;
        int sellCount = 0;
        int buyVolume = 0;
        int sellVolume = 0;
        double totalAmount = 0;
        int totalVolume = 0;
    }

// 獲取交易者顯示名稱
    private String getTraderDisplay(String traderType) {
        switch (traderType) {
            case "RETAIL_INVESTOR":
                return "散戶";
            case "MAIN_FORCE":
                return "主力";
            case "PERSONAL":
                return "個人";
            case "MARKET":
                return "市場";
            default:
                return traderType;
        }
    }

    /**
     * 創建底部控制面板
     */
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(new Color(250, 250, 250));
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)));

        // 時間範圍選擇
        JComboBox<String> timeRangeCombo = new JComboBox<>(
                new String[]{"全部", "今日", "最近1小時", "最近30分鐘", "最近10分鐘"}
        );

        // 自動刷新
        JCheckBox autoRefreshCheck = new JCheckBox("自動刷新");
        autoRefreshCheck.setSelected(true);

        // 導出按鈕
        JButton exportAllButton = new JButton("導出全部");
        exportAllButton.addActionListener(e -> exportAllTransactions());

        // 刷新按鈕
        JButton refreshButton = new JButton("立即刷新");
        refreshButton.addActionListener(e -> refreshData());

        // 關閉按鈕
        JButton closeButton = new JButton("關閉");
        closeButton.addActionListener(e -> dispose());

        bottomPanel.add(new JLabel("時間範圍:"));
        bottomPanel.add(timeRangeCombo);
        bottomPanel.add(Box.createHorizontalStrut(20));
        bottomPanel.add(autoRefreshCheck);
        bottomPanel.add(refreshButton);
        bottomPanel.add(exportAllButton);
        bottomPanel.add(closeButton);

        return bottomPanel;
    }

    // 修改 TransactionChartPanel 內部類
    private class TransactionChartPanel extends JPanel {

        private List<Transaction> chartData = new ArrayList<>();

        public TransactionChartPanel() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            setPreferredSize(new Dimension(400, 300));
        }

        public void updateData(List<Transaction> transactions) {
            this.chartData = new ArrayList<>(transactions);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (chartData.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.drawString("暫無成交數據", getWidth() / 2 - 40, getHeight() / 2);
                return;
            }

            // 繪製價格走勢圖
            int padding = 40;
            int width = getWidth() - 2 * padding;
            int height = getHeight() - 2 * padding;

            // 找出價格範圍
            double minPrice = chartData.stream().mapToDouble(Transaction::getPrice).min().orElse(0);
            double maxPrice = chartData.stream().mapToDouble(Transaction::getPrice).max().orElse(0);
            double priceRange = maxPrice - minPrice;

            // 繪製座標軸
            g2.setColor(Color.BLACK);
            g2.drawLine(padding, padding, padding, getHeight() - padding); // Y軸
            g2.drawLine(padding, getHeight() - padding, getWidth() - padding, getHeight() - padding); // X軸

            // 繪製標題
            g2.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
            g2.drawString("成交價格走勢圖", getWidth() / 2 - 50, 20);

            // 繪製價格線
            g2.setColor(new Color(33, 150, 243));
            g2.setStroke(new BasicStroke(2));

            for (int i = 1; i < chartData.size(); i++) {
                Transaction prev = chartData.get(i - 1);
                Transaction curr = chartData.get(i);

                int x1 = padding + (i - 1) * width / (chartData.size() - 1);
                int y1 = padding + height - (int) ((prev.getPrice() - minPrice) / priceRange * height);

                int x2 = padding + i * width / (chartData.size() - 1);
                int y2 = padding + height - (int) ((curr.getPrice() - minPrice) / priceRange * height);

                g2.drawLine(x1, y1, x2, y2);

                // 繪製數據點
                g2.fillOval(x2 - 3, y2 - 3, 6, 6);
            }

            // 繪製價格標籤
            g2.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 10));
            g2.setColor(Color.BLACK);
            g2.drawString(String.format("%.2f", maxPrice), 5, padding);
            g2.drawString(String.format("%.2f", minPrice), 5, getHeight() - padding);
        }
    }

    /**
     * 創建圖標
     */
    private Icon createIcon(Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x, y, 14, 14);
            }

            @Override
            public int getIconWidth() {
                return 14;
            }

            @Override
            public int getIconHeight() {
                return 14;
            }
        };
    }

    /**
     * 添加成交記錄
     */
    public void addTransaction(Transaction transaction) {
        transactionHistory.add(transaction);
        refreshData();
    }

    /**
     * 批量添加成交記錄
     */
    public void addTransactions(List<Transaction> transactions) {
        transactionHistory.addAll(transactions);
        refreshData();
    }

    /**
     * 刷新數據
     */
    private void refreshData() {
        SwingUtilities.invokeLater(() -> {
            updateAllTables();
            updateStatistics();
            updateCharts();
            updateStatisticsAnalysis();  // 🆕 更新統計分析
            updateTraderAnalysis();      // 🆕 更新交易者分析
            lastUpdateLabel.setText(createStatsLabel("最後更新", timeFormat.format(new Date())).getText());
        });
    }

    /**
     * 更新所有表格
     */
    private void updateAllTables() {
        // 清空所有表格
        allTransactionsModel.setRowCount(0);
        buyTransactionsModel.setRowCount(0);
        sellTransactionsModel.setRowCount(0);
        myTransactionsModel.setRowCount(0);

        // 填充數據
        for (Transaction trans : transactionHistory) {
            Object[] rowData = createRowData(trans);

            // 添加到全部成交表
            allTransactionsModel.addRow(rowData);

            // 根據類型添加到相應表格
            if (trans.isBuyerInitiated()) {
                buyTransactionsModel.addRow(rowData);
            } else {
                sellTransactionsModel.addRow(rowData);
            }

            // 如果是個人交易，添加到我的成交表
            if ("PERSONAL".equals(trans.getBuyer().getTraderType())
                    || "PERSONAL".equals(trans.getSeller().getTraderType())) {
                myTransactionsModel.addRow(rowData);
            }
        }
    }

    /**
     * 創建表格行數據
     */
    private Object[] createRowData(Transaction trans) {
        double priceChange = calculatePriceChange(trans);
        String remark = generateRemark(trans);

        return new Object[]{
            trans.getId(),
            dateFormat.format(new Date(trans.getTimestamp())),
            getTraderDisplay(trans.getBuyer()),
            getTraderDisplay(trans.getSeller()),
            trans.getPrice(),
            trans.getVolume(),
            trans.getPrice() * trans.getVolume(),
            trans.getBuyOrderRemainingVolume(),
            trans.getSellOrderRemainingVolume(),
            priceChange,
            remark
        };
    }

    /**
     * 獲取交易者顯示名稱
     */
    private String getTraderDisplay(StockMainAction.model.core.Trader trader) {
        String type = trader.getTraderType();
        switch (type) {
            case "RETAIL_INVESTOR":
                return "散戶";
            case "MAIN_FORCE":
                return "主力";
            case "PERSONAL":
                return "個人";
            case "MarketBehavior":
                return "市場";
            default:
                return type;
        }
    }

    /**
     * 計算價格變動百分比
     */
    private double calculatePriceChange(Transaction trans) {
        // 這裡需要根據前一筆成交價計算
        // 暫時返回隨機值作為示例
        return (Math.random() - 0.5) * 5;
    }

    /**
     * 生成備註信息
     */
    private String generateRemark(Transaction trans) {
        if (trans.getVolume() > 1000) {
            return "大單成交";
        } else if (trans.isBuyerInitiated() && trans.getBuyOrderRemainingVolume() == 0) {
            return "買單完全成交";
        } else if (!trans.isBuyerInitiated() && trans.getSellOrderRemainingVolume() == 0) {
            return "賣單完全成交";
        }
        return "";
    }

    /**
     * 更新統計信息
     */
    private void updateStatistics() {
        if (transactionHistory.isEmpty()) {
            return;
        }

        int totalCount = transactionHistory.size();
        long totalVolume = 0;
        double totalAmount = 0;
        double sumPrice = 0;

        for (Transaction trans : transactionHistory) {
            totalVolume += trans.getVolume();
            totalAmount += trans.getPrice() * trans.getVolume();
            sumPrice += trans.getPrice();
        }

        double avgPrice = sumPrice / totalCount;

        totalTransactionsLabel.setText(createStatsLabel("總成交筆數",
                String.valueOf(totalCount)).getText());
        totalVolumeLabel.setText(createStatsLabel("總成交量",
                volumeFormat.format(totalVolume)).getText());
        totalAmountLabel.setText(createStatsLabel("總成交額",
                priceFormat.format(totalAmount)).getText());
        avgPriceLabel.setText(createStatsLabel("平均成交價",
                priceFormat.format(avgPrice)).getText());
    }

    /**
     * 更新圖表
     */
    private void updateCharts() {
        if (chartPanel != null) {
            chartPanel.updateData(transactionHistory);
        }
    }

    /**
     * 生成統計報告
     */
    private String generateStatisticsReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 成交統計分析報告 ===\n\n");
        report.append("生成時間: ").append(dateFormat.format(new Date())).append("\n\n");

        report.append("【成交概況】\n");
        report.append("• 總成交筆數: ").append(transactionHistory.size()).append(" 筆\n");
        report.append("• 買方主動成交: ").append(countBuyerInitiated()).append(" 筆\n");
        report.append("• 賣方主動成交: ").append(countSellerInitiated()).append(" 筆\n\n");

        report.append("【價格分析】\n");
        report.append("• 最高成交價: ").append(getMaxPrice()).append("\n");
        report.append("• 最低成交價: ").append(getMinPrice()).append("\n");
        report.append("• 價格標準差: ").append(calculatePriceStdDev()).append("\n\n");

        report.append("【成交量分析】\n");
        report.append("• 單筆最大成交量: ").append(getMaxVolume()).append("\n");
        report.append("• 單筆最小成交量: ").append(getMinVolume()).append("\n");
        report.append("• 平均成交量: ").append(getAvgVolume()).append("\n\n");

        report.append("【時間分布】\n");
        report.append("• 最活躍時段: ").append(getMostActiveTimeRange()).append("\n");
        report.append("• 平均成交間隔: ").append(getAvgTransactionInterval()).append(" 秒\n");

        return report.toString();
    }

    // 統計輔助方法
    private long countBuyerInitiated() {
        return transactionHistory.stream().filter(Transaction::isBuyerInitiated).count();
    }

    private long countSellerInitiated() {
        return transactionHistory.stream().filter(t -> !t.isBuyerInitiated()).count();
    }

    private String getMaxPrice() {
        OptionalDouble max = transactionHistory.stream()
                .mapToDouble(Transaction::getPrice)
                .max();
        return max.isPresent() ? priceFormat.format(max.getAsDouble()) : "N/A";
    }

    private String getMinPrice() {
        OptionalDouble min = transactionHistory.stream()
                .mapToDouble(Transaction::getPrice)
                .min();
        return min.isPresent() ? priceFormat.format(min.getAsDouble()) : "N/A";
    }

    private String calculatePriceStdDev() {
        if (transactionHistory.size() < 2) {
            return "N/A";
        }

        double avg = transactionHistory.stream()
                .mapToDouble(Transaction::getPrice)
                .average()
                .orElse(0.0);

        double variance = transactionHistory.stream()
                .mapToDouble(Transaction::getPrice)
                .map(price -> Math.pow(price - avg, 2))
                .average()
                .orElse(0.0);

        return priceFormat.format(Math.sqrt(variance));
    }

    private String getMaxVolume() {
        OptionalInt max = transactionHistory.stream()
                .mapToInt(Transaction::getVolume)
                .max();
        return max.isPresent() ? volumeFormat.format(max.getAsInt()) : "N/A";
    }

    private String getMinVolume() {
        OptionalInt min = transactionHistory.stream()
                .mapToInt(Transaction::getVolume)
                .min();
        return min.isPresent() ? volumeFormat.format(min.getAsInt()) : "N/A";
    }

    private String getAvgVolume() {
        OptionalDouble avg = transactionHistory.stream()
                .mapToInt(Transaction::getVolume)
                .average();
        return avg.isPresent() ? volumeFormat.format((int) avg.getAsDouble()) : "N/A";
    }

    private String getMostActiveTimeRange() {
        // 簡化實現
        return "09:30-10:00";
    }

    private String getAvgTransactionInterval() {
        if (transactionHistory.size() < 2) {
            return "N/A";
        }

        long totalInterval = 0;
        for (int i = 1; i < transactionHistory.size(); i++) {
            totalInterval += transactionHistory.get(i).getTimestamp()
                    - transactionHistory.get(i - 1).getTimestamp();
        }

        return String.valueOf(totalInterval / 1000 / (transactionHistory.size() - 1));
    }

    /**
     * 查看成交詳情
     */
    private void viewTransactionDetails(JTable table) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }

        DefaultTableModel model = (DefaultTableModel) table.getModel();

        // 創建詳情對話框
        JDialog detailDialog = new JDialog(this, "成交詳情", true);
        detailDialog.setSize(500, 600);
        detailDialog.setLocationRelativeTo(this);

        JPanel detailPanel = new JPanel(new GridBagLayout());
        detailPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // 添加詳情信息
        int row = 0;
        for (int col = 0; col < model.getColumnCount(); col++) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.3;
            JLabel label = new JLabel(model.getColumnName(col) + ":");
            label.setFont(new Font("Microsoft JhengHei", Font.BOLD, 12));
            detailPanel.add(label, gbc);

            gbc.gridx = 1;
            gbc.weightx = 0.7;
            Object value = model.getValueAt(selectedRow, col);
            JLabel valueLabel = new JLabel(value != null ? value.toString() : "");
            valueLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
            detailPanel.add(valueLabel, gbc);

            row++;
        }

        // 添加額外信息
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        detailPanel.add(new JSeparator(), gbc);

        gbc.gridy = row++;
        JLabel additionalInfo = new JLabel("額外信息");
        additionalInfo.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        detailPanel.add(additionalInfo, gbc);

        // 添加關閉按鈕
        JButton closeButton = new JButton("關閉");
        closeButton.addActionListener(e -> detailDialog.dispose());
        gbc.gridy = row++;
        gbc.anchor = GridBagConstraints.CENTER;
        detailPanel.add(closeButton, gbc);

        JScrollPane scrollPane = new JScrollPane(detailPanel);
        detailDialog.add(scrollPane);
        detailDialog.setVisible(true);
    }

    /**
     * 複製表格行
     */
    private void copyTableRow(JTable table) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }

        DefaultTableModel model = (DefaultTableModel) table.getModel();
        StringBuilder sb = new StringBuilder();

        for (int col = 0; col < model.getColumnCount(); col++) {
            if (col > 0) {
                sb.append("\t");
            }
            Object value = model.getValueAt(selectedRow, col);
            sb.append(value != null ? value.toString() : "");
        }

        // 複製到剪貼板
        java.awt.datatransfer.StringSelection stringSelection
                = new java.awt.datatransfer.StringSelection(sb.toString());
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(stringSelection, null);

        JOptionPane.showMessageDialog(this, "已複製到剪貼板", "複製成功",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 導出選中的行
     */
    private void exportSelectedRows(JTable table) {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "請先選擇要導出的行", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 實現導出邏輯
        exportTableData(table, selectedRows);
    }

    /**
     * 導出所有成交記錄
     */
    private void exportAllTransactions() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("導出成交記錄");
        fileChooser.setSelectedFile(new java.io.File("成交記錄_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            try {
                exportToCSV(file);
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
     * 導出表格數據
     */
    private void exportTableData(JTable table, int[] rows) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("導出選中記錄");
        fileChooser.setSelectedFile(new java.io.File("選中成交記錄_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            try {
                exportSelectedToCSV(table, rows, file);
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
    private void exportToCSV(java.io.File file) throws Exception {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(file), "UTF-8"))) {

            // 寫入BOM
            writer.write('\ufeff');

            // 寫入標題行
            DefaultTableModel model = allTransactionsModel;
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
     * 導出選中行到CSV
     */
    private void exportSelectedToCSV(JTable table, int[] rows, java.io.File file) throws Exception {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(file), "UTF-8"))) {

            // 寫入BOM
            writer.write('\ufeff');

            DefaultTableModel model = (DefaultTableModel) table.getModel();

            // 寫入標題行
            for (int i = 0; i < model.getColumnCount(); i++) {
                if (i > 0) {
                    writer.print(",");
                }
                writer.print("\"" + model.getColumnName(i) + "\"");
            }
            writer.println();

            // 寫入選中的數據行
            for (int row : rows) {
                for (int col = 0; col < model.getColumnCount(); col++) {
                    if (col > 0) {
                        writer.print(",");
                    }
                    Object value = model.getValueAt(row, col);
                    if (value != null) {
                        String text = value.toString();
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
     * 開始自動刷新
     */
    private void startAutoRefresh() {
        if (refreshTimer == null) {
            refreshTimer = new Timer(3000, e -> refreshData()); // 每3秒刷新
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
     * 清理資源
     */
    @Override
    public void dispose() {
        stopAutoRefresh();

        // 取消註冊監聽器
        if (model != null) {
            model.removeTransactionListener(this);
        }

        super.dispose();
    }

    // 實現監聽器方法
    public void onTransactionAdded(Transaction transaction) {
        // 在 Swing 線程中更新
        SwingUtilities.invokeLater(() -> {
            transactionHistory.add(transaction);

            // 更新所有表格
            addTransactionToTables(transaction);

            // 更新統計信息
            updateStatistics();

            // 更新最後更新時間
            lastUpdateLabel.setText(createStatsLabel("最後更新",
                    timeFormat.format(new Date())).getText());
        });
    }

    // 新增方法：只添加單筆交易到表格
    private void addTransactionToTables(Transaction trans) {
        Object[] rowData = createRowData(trans);

        // 添加到全部成交表
        allTransactionsModel.addRow(rowData);

        // 根據類型添加到相應表格
        if (trans.isBuyerInitiated()) {
            buyTransactionsModel.addRow(rowData);
        } else {
            sellTransactionsModel.addRow(rowData);
        }

        // 如果是個人交易，添加到我的成交表
        if ("PERSONAL".equals(trans.getBuyer().getTraderType())
                || "PERSONAL".equals(trans.getSeller().getTraderType())) {
            myTransactionsModel.addRow(rowData);
        }

        // 自動滾動到最新記錄
        scrollToLastRow();
    }

    // 自動滾動到最後一行
    private void scrollToLastRow() {
        int selectedTab = tabbedPane.getSelectedIndex();
        JTable currentTable = null;

        switch (selectedTab) {
            case 0:
                currentTable = allTransactionsTable;
                break;
            case 1:
                currentTable = buyTransactionsTable;
                break;
            case 2:
                currentTable = sellTransactionsTable;
                break;
            case 3:
                currentTable = myTransactionsTable;
                break;
        }

        if (currentTable != null && currentTable.getRowCount() > 0) {
            int lastRow = currentTable.getRowCount() - 1;
            currentTable.scrollRectToVisible(
                    currentTable.getCellRect(lastRow, 0, true));
        }
    }
}
