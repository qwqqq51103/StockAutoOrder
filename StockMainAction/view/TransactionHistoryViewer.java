package StockMainAction.view;

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

/**
 * 成交記錄視窗 - 詳細顯示所有交易記錄
 */
public class TransactionHistoryViewer extends JFrame {
    
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
    
    public TransactionHistoryViewer() {
        this.transactionHistory = new ArrayList<>();
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
        topPanel.setPreferredSize(new Dimension(0, 100));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        // 標題
        JLabel titleLabel = new JLabel("成交記錄管理中心");
        titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        
        // 統計信息面板
        JPanel statsPanel = new JPanel(new GridLayout(2, 3, 30, 10));
        statsPanel.setOpaque(false);
        
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
        JLabel label = new JLabel("<html><div style='text-align: center;'>" + 
            "<span style='font-size:10px;color:#B0BEC5;'>" + title + "</span><br>" +
            "<span style='font-size:16px;color:#FFFFFF;font-weight:bold;'>" + value + "</span></div></html>");
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
                if (column != 9) setForeground(new Color(13, 71, 161));
            } else {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 250, 250));
                if (column != 9) setForeground(Color.BLACK);
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
        
        JTextArea statsText = new JTextArea();
        statsText.setEditable(false);
        statsText.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        statsText.setMargin(new Insets(10, 10, 10, 10));
        
        // 示例統計文本
        statsText.setText(generateStatisticsReport());
        
        JScrollPane scrollPane = new JScrollPane(statsText);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 創建交易者分析面板
     */
    private JPanel createTraderAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        
        // 創建交易者統計表格
        String[] columns = {"交易者類型", "成交筆數", "買入量", "賣出量", "淨買賣", "平均價格", "活躍度"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable traderTable = new JTable(model);
        traderTable.setRowHeight(30);
        
        JScrollPane scrollPane = new JScrollPane(traderTable);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
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
    
    /**
     * 圖表面板內部類
     */
    private class TransactionChartPanel extends JPanel {
        public TransactionChartPanel() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 繪製示例圖表
            g2.setColor(Color.BLACK);
            g2.drawString("價格走勢圖", 10, 20);
            
            // 這裡可以添加實際的圖表繪製邏輯
            // 例如使用 JFreeChart 或自定義繪製
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
            if ("PERSONAL".equals(trans.getBuyer().getTraderType()) || 
                "PERSONAL".equals(trans.getSeller().getTraderType())) {
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
        
        return new Object[] {
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
            case "RETAIL_INVESTOR": return "散戶";
            case "MAIN_FORCE": return "主力";
            case "PERSONAL": return "個人";
            default: return type;
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
            chartPanel.repaint();
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
        return transactionHistory.stream()
            .mapToDouble(Transaction::getPrice)
            .max()
            .map(priceFormat::format)
            .orElse("N/A");
    }
    
    private String getMinPrice() {
        return transactionHistory.stream()
            .mapToDouble(Transaction::getPrice)
            .min()
            .map(priceFormat::format)
            .orElse("N/A");
    }
    
    private String calculatePriceStdDev() {
        // 簡化計算
        return "N/A";
    }
    
    private String getMaxVolume() {
        return transactionHistory.stream()
            .mapToInt(Transaction::getVolume)
            .max()
            .map(volumeFormat::format)
            .orElse("N/A");
    }
    
    private String getMinVolume() {
        return transactionHistory.stream()
            .mapToInt(Transaction::getVolume)
            .min()
            .map(