package StockMainAction.view;

import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.Transaction;
import StockMainAction.util.logging.MarketLogger;
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
 * 完整的成交記錄視窗 - 支持限價單和市價單的詳細顯示
 */
public class TransactionHistoryViewer extends JFrame implements StockMarketModel.TransactionListener {

    // UI 組件
    private JTabbedPane tabbedPane;
    private JTable allTransactionsTable;
    private JTable buyTransactionsTable;
    private JTable sellTransactionsTable;
    private JTable myTransactionsTable;
    private JTable marketOrderTable;    // 市價單專用表格
    private JTable limitOrderTable;     // 限價單專用表格

    private DefaultTableModel allTransactionsModel;
    private DefaultTableModel buyTransactionsModel;
    private DefaultTableModel sellTransactionsModel;
    private DefaultTableModel myTransactionsModel;
    private DefaultTableModel marketOrderModel;    // 市價單模型
    private DefaultTableModel limitOrderModel;     // 限價單模型

    // 統計標籤
    private JLabel totalTransactionsLabel;
    private JLabel totalVolumeLabel;
    private JLabel totalAmountLabel;
    private JLabel avgPriceLabel;
    private JLabel lastUpdateLabel;
    private JLabel marketOrderStatsLabel;    // 市價單統計標籤
    private JLabel limitOrderStatsLabel;     // 限價單統計標籤

    // 圖表面板
    private TransactionChartPanel chartPanel;

    // 數據
    private List<Transaction> transactionHistory;
    private Timer refreshTimer;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private DecimalFormat priceFormat = new DecimalFormat("#,##0.00");
    private DecimalFormat volumeFormat = new DecimalFormat("#,##0");

    private StockMarketModel model;
    private JTextArea statsAnalysisTextArea;
    private DefaultTableModel traderAnalysisModel;
    private static final MarketLogger logger = MarketLogger.getInstance();

    /**
     * 構造函數
     */
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
        setTitle("成交記錄管理中心 - 增強版");
        setSize(1500, 900);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(245, 245, 245));

        // 添加頂部統計面板
        mainPanel.add(createEnhancedTopPanel(), BorderLayout.NORTH);

        // 創建主要內容區域（左右分割）
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(950);
        splitPane.setOneTouchExpandable(true);

        // 左側：表格區域
        splitPane.setLeftComponent(createEnhancedTableArea());

        // 右側：圖表和分析區域
        splitPane.setRightComponent(createEnhancedAnalysisArea());

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 添加底部控制面板
        mainPanel.add(createEnhancedBottomPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    /**
     * 創建增強版頂部統計面板
     */
    private JPanel createEnhancedTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(48, 63, 159));
        topPanel.setPreferredSize(new Dimension(0, 180)); // 增加到180像素高度
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // 標題
        JLabel titleLabel = new JLabel("成交記錄管理中心");
        titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);

        // 🔧 改為兩行布局：第一行5個，第二行4個
        JPanel statsContainer = new JPanel(new GridLayout(2, 1, 0, 10));
        statsContainer.setOpaque(false);

        // 第一行：5個主要統計
        JPanel firstRowPanel = new JPanel(new GridLayout(1, 5, 20, 0));
        firstRowPanel.setOpaque(false);

        totalTransactionsLabel = createStatsLabel("總成交筆數", "0");
        totalVolumeLabel = createStatsLabel("總成交量", "0");
        totalAmountLabel = createStatsLabel("總成交額", "0.00");
        avgPriceLabel = createStatsLabel("平均成交價", "0.00");
        lastUpdateLabel = createStatsLabel("最後更新", "--:--:--");

        firstRowPanel.add(totalTransactionsLabel);
        firstRowPanel.add(totalVolumeLabel);
        firstRowPanel.add(totalAmountLabel);
        firstRowPanel.add(avgPriceLabel);
        firstRowPanel.add(lastUpdateLabel);

        // 第二行：4個次要統計（置中顯示）
        JPanel secondRowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0));
        secondRowPanel.setOpaque(false);

        marketOrderStatsLabel = createStatsLabel("市價單數", "0");
        limitOrderStatsLabel = createStatsLabel("限價單數", "0");
        JLabel avgSlippageLabel = createStatsLabel("平均滑價", "0.00%");
        JLabel successRateLabel = createStatsLabel("成功率", "0.0%");

        secondRowPanel.add(marketOrderStatsLabel);
        secondRowPanel.add(limitOrderStatsLabel);
        secondRowPanel.add(avgSlippageLabel);
        secondRowPanel.add(successRateLabel);

        statsContainer.add(firstRowPanel);
        statsContainer.add(secondRowPanel);

        topPanel.add(titleLabel, BorderLayout.WEST);
        topPanel.add(statsContainer, BorderLayout.CENTER);

        return topPanel;
    }

    /**
     * 創建增強版表格區域
     */
    private JComponent createEnhancedTableArea() {
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));

        // 定義增強的表格列（支援市價單信息）
        String[] enhancedColumnNames = {
            "成交編號", "交易類型", "成交時間", "發起方", "對手方", "成交價",
            "成交量", "成交額", "請求量", "成交率", "滑價", "執行時間", "備註"
        };

        // 創建各個分頁
        allTransactionsModel = createDetailedTableModel(enhancedColumnNames);
        allTransactionsTable = createDetailedTable(allTransactionsModel);
        tabbedPane.addTab("全部成交", createIcon(Color.GRAY),
                createTablePanel(allTransactionsTable, "所有成交記錄（限價單+市價單）"));

        buyTransactionsModel = createDetailedTableModel(enhancedColumnNames);
        buyTransactionsTable = createDetailedTable(buyTransactionsModel);
        tabbedPane.addTab("買入成交", createIcon(new Color(76, 175, 80)),
                createTablePanel(buyTransactionsTable, "買方主動成交"));

        sellTransactionsModel = createDetailedTableModel(enhancedColumnNames);
        sellTransactionsTable = createDetailedTable(sellTransactionsModel);
        tabbedPane.addTab("賣出成交", createIcon(new Color(244, 67, 54)),
                createTablePanel(sellTransactionsTable, "賣方主動成交"));

        myTransactionsModel = createDetailedTableModel(enhancedColumnNames);
        myTransactionsTable = createDetailedTable(myTransactionsModel);
        tabbedPane.addTab("我的成交", createIcon(new Color(33, 150, 243)),
                createTablePanel(myTransactionsTable, "個人交易記錄"));

        // 新增：市價單專用分頁
        marketOrderModel = createDetailedTableModel(enhancedColumnNames);
        marketOrderTable = createDetailedTable(marketOrderModel);
        tabbedPane.addTab("市價單", createIcon(new Color(255, 152, 0)),
                createTablePanel(marketOrderTable, "市價單成交記錄（包含滑價分析）"));

        // 新增：限價單專用分頁
        limitOrderModel = createDetailedTableModel(enhancedColumnNames);
        limitOrderTable = createDetailedTable(limitOrderModel);
        tabbedPane.addTab("限價單", createIcon(new Color(156, 39, 176)),
                createTablePanel(limitOrderTable, "限價單成交記錄"));

        return tabbedPane;
    }

    /**
     * 創建詳細表格模型 - 增強版
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
                    case 5: // 成交價
                    case 7: // 成交額
                    case 9: // 成交率
                    case 10: // 滑價
                        return Double.class;
                    case 6: // 成交量
                    case 8: // 請求量
                        return Integer.class;
                    case 11: // 執行時間
                        return String.class;
                    default:
                        return String.class;
                }
            }
        };
    }

    /**
     * 創建詳細表格 - 增強版
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

        // 設置列寬 - 針對增強版列進行優化
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(90);   // 成交編號
        columnModel.getColumn(1).setPreferredWidth(70);   // 交易類型
        columnModel.getColumn(2).setPreferredWidth(130);  // 成交時間
        columnModel.getColumn(3).setPreferredWidth(70);   // 發起方
        columnModel.getColumn(4).setPreferredWidth(70);   // 對手方
        columnModel.getColumn(5).setPreferredWidth(70);   // 成交價
        columnModel.getColumn(6).setPreferredWidth(70);   // 成交量
        columnModel.getColumn(7).setPreferredWidth(90);   // 成交額
        columnModel.getColumn(8).setPreferredWidth(70);   // 請求量
        columnModel.getColumn(9).setPreferredWidth(60);   // 成交率
        columnModel.getColumn(10).setPreferredWidth(70);  // 滑價
        columnModel.getColumn(11).setPreferredWidth(80);  // 執行時間
        columnModel.getColumn(12).setPreferredWidth(120); // 備註

        // 自定義渲染器 - 增強版
        table.setDefaultRenderer(Object.class, new EnhancedTransactionTableCellRenderer());
        table.setDefaultRenderer(Double.class, new EnhancedTransactionTableCellRenderer());
        table.setDefaultRenderer(Integer.class, new EnhancedTransactionTableCellRenderer());

        return table;
    }

    /**
     * 增強版成交記錄表格渲染器
     */
    private class EnhancedTransactionTableCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            Component comp = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            // 設置對齊
            if (column >= 5 && column <= 11) {
                setHorizontalAlignment(CENTER);
            } else {
                setHorizontalAlignment(LEFT);
            }

            // 設置邊距
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

            // 格式化數值
            if (value != null) {
                switch (column) {
                    case 5: // 成交價
                    case 7: // 成交額
                        setText(priceFormat.format(value));
                        break;
                    case 6: // 成交量
                    case 8: // 請求量
                        setText(volumeFormat.format(value));
                        break;
                    case 9: // 成交率
                        if (value instanceof Double) {
                            double rate = (Double) value;
                            setText(String.format("%.1f%%", rate));
                            // 根據成交率設置顏色
                            if (!isSelected) {
                                if (rate >= 100.0) {
                                    setForeground(new Color(76, 175, 80)); // 綠色：完全成交
                                } else if (rate >= 80.0) {
                                    setForeground(new Color(255, 193, 7));  // 黃色：大部分成交
                                } else {
                                    setForeground(new Color(244, 67, 54));  // 紅色：部分成交
                                }
                            }
                        }
                        break;
                    case 10: // 滑價
                        if ("N/A".equals(value.toString())) {
                            setText("N/A");
                            if (!isSelected) {
                                setForeground(Color.GRAY);
                            }
                        } else if (value instanceof String && value.toString().contains("%")) {
                            setText(value.toString());
                            // 根據滑價設置顏色
                            if (!isSelected) {
                                try {
                                    double slippage = Double.parseDouble(value.toString().replace("%", ""));
                                    if (Math.abs(slippage) > 2.0) {
                                        setForeground(new Color(244, 67, 54)); // 紅色：高滑價
                                    } else if (Math.abs(slippage) > 0.5) {
                                        setForeground(new Color(255, 152, 0)); // 橙色：中等滑價
                                    } else {
                                        setForeground(new Color(76, 175, 80)); // 綠色：低滑價
                                    }
                                } catch (NumberFormatException e) {
                                    setForeground(Color.BLACK);
                                }
                            }
                        }
                        break;
                    case 1: // 交易類型
                        // 根據交易類型設置顏色
                        if (!isSelected) {
                            String type = value.toString();
                            if (type.contains("市價")) {
                                setForeground(new Color(255, 152, 0)); // 橙色：市價單
                            } else if (type.contains("限價")) {
                                setForeground(new Color(156, 39, 176)); // 紫色：限價單
                            } else if (type.contains("FOK")) {
                                setForeground(new Color(33, 150, 243)); // 藍色：FOK單
                            }
                        }
                        break;
                }
            }

            // 設置背景色
            if (isSelected) {
                setBackground(new Color(232, 240, 254));
                // 選中時保持文字顏色可見性
                if (column != 9 && column != 10 && column != 1) {
                    setForeground(new Color(13, 71, 161));
                }
            } else {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 250, 250));
                // 非選中時的默認顏色在上面的switch中已設置
                if (column != 9 && column != 10 && column != 1) {
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
        addEnhancedTableContextMenu(table);

        return panel;
    }

    /**
     * 添加增強版表格右鍵菜單
     */
    private void addEnhancedTableContextMenu(JTable table) {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem viewDetailsItem = new JMenuItem("查看詳情");
        viewDetailsItem.addActionListener(e -> viewEnhancedTransactionDetails(table));

        JMenuItem copyItem = new JMenuItem("複製行");
        copyItem.addActionListener(e -> copyTableRow(table));

        JMenuItem exportItem = new JMenuItem("導出選中");
        exportItem.addActionListener(e -> exportSelectedRows(table));

        // 新增菜單項
        JMenuItem analyzeSlippageItem = new JMenuItem("分析滑價");
        analyzeSlippageItem.addActionListener(e -> analyzeSlippage(table));

        JMenuItem compareFillsItem = new JMenuItem("比較填單");
        compareFillsItem.addActionListener(e -> compareFills(table));

        popupMenu.add(viewDetailsItem);
        popupMenu.addSeparator();
        popupMenu.add(analyzeSlippageItem);
        popupMenu.add(compareFillsItem);
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
     * 創建增強版分析區域
     */
    private JComponent createEnhancedAnalysisArea() {
        JPanel analysisPanel = new JPanel(new BorderLayout());
        analysisPanel.setBackground(Color.WHITE);
        analysisPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 創建分頁
        JTabbedPane analysisTabs = new JTabbedPane();

        // 圖表分頁
        chartPanel = new TransactionChartPanel();
        analysisTabs.addTab("成交圖表", chartPanel);

        // 統計分析分頁
        JPanel statsAnalysisPanel = createEnhancedStatsAnalysisPanel();
        analysisTabs.addTab("統計分析", statsAnalysisPanel);

        // 交易者分析分頁
        JPanel traderAnalysisPanel = createTraderAnalysisPanel();
        analysisTabs.addTab("交易者分析", traderAnalysisPanel);

        // 新增：市價單分析分頁
        JPanel marketOrderAnalysisPanel = createMarketOrderAnalysisPanel();
        analysisTabs.addTab("市價單分析", marketOrderAnalysisPanel);

        // 新增：滑價分析分頁
        JPanel slippageAnalysisPanel = createSlippageAnalysisPanel();
        analysisTabs.addTab("滑價分析", slippageAnalysisPanel);

        analysisPanel.add(analysisTabs, BorderLayout.CENTER);

        return analysisPanel;
    }

    /**
     * 創建市價單分析面板
     */
    private JPanel createMarketOrderAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        JTextArea marketOrderAnalysisText = new JTextArea();
        marketOrderAnalysisText.setEditable(false);
        marketOrderAnalysisText.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        marketOrderAnalysisText.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(marketOrderAnalysisText);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        panel.add(scrollPane, BorderLayout.CENTER);

        // 設置初始內容
        marketOrderAnalysisText.setText("市價單分析數據載入中...");

        return panel;
    }

    /**
     * 創建滑價分析面板
     */
    private JPanel createSlippageAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        // 創建滑價統計表格
        String[] slippageColumns = {"滑價範圍", "交易筆數", "平均滑價", "最大滑價", "影響因素"};
        DefaultTableModel slippageModel = new DefaultTableModel(slippageColumns, 0);
        JTable slippageTable = new JTable(slippageModel);
        slippageTable.setRowHeight(30);

        JScrollPane scrollPane = new JScrollPane(slippageTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 創建增強版統計分析面板
     */
    private JPanel createEnhancedStatsAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        statsAnalysisTextArea = new JTextArea();
        statsAnalysisTextArea.setEditable(false);
        statsAnalysisTextArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        statsAnalysisTextArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(statsAnalysisTextArea);
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
        traderAnalysisModel = new DefaultTableModel(columns, 0);
        JTable traderTable = new JTable(traderAnalysisModel);
        traderTable.setRowHeight(30);

        JScrollPane scrollPane = new JScrollPane(traderTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 交易圖表面板
     */
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

            if (priceRange == 0) {
                priceRange = 1; // 避免除零
            }

            // 繪製座標軸
            g2.setColor(Color.BLACK);
            g2.drawLine(padding, padding, padding, getHeight() - padding); // Y軸
            g2.drawLine(padding, getHeight() - padding, getWidth() - padding, getHeight() - padding); // X軸

            // 繪製標題
            g2.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
            g2.drawString("成交價格走勢圖（增強版）", getWidth() / 2 - 80, 20);

            // 繪製圖例
            g2.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
            g2.setColor(new Color(33, 150, 243));
            g2.fillOval(10, 30, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawString("限價單", 25, 40);

            g2.setColor(new Color(255, 152, 0));
            g2.fillOval(80, 30, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawString("市價單", 95, 40);

            // 繪製價格線和點
            for (int i = 0; i < chartData.size(); i++) {
                Transaction trans = chartData.get(i);

                int x = padding + i * width / Math.max(1, chartData.size() - 1);
                int y = padding + height - (int) ((trans.getPrice() - minPrice) / priceRange * height);

                // 根據交易類型選擇顏色（簡化判斷）
                boolean isMarketOrder = isTransactionMarketOrder(trans);
                if (isMarketOrder) {
                    g2.setColor(new Color(255, 152, 0)); // 橙色：市價單
                } else {
                    g2.setColor(new Color(33, 150, 243)); // 藍色：限價單
                }

                // 繪製連線
                if (i > 0) {
                    Transaction prevTrans = chartData.get(i - 1);
                    int prevX = padding + (i - 1) * width / Math.max(1, chartData.size() - 1);
                    int prevY = padding + height - (int) ((prevTrans.getPrice() - minPrice) / priceRange * height);

                    g2.setStroke(new BasicStroke(1));
                    g2.drawLine(prevX, prevY, x, y);
                }

                // 繪製數據點
                int pointSize = isMarketOrder ? 8 : 6; // 市價單用較大的點
                g2.fillOval(x - pointSize / 2, y - pointSize / 2, pointSize, pointSize);
            }

            // 繪製價格標籤
            g2.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 10));
            g2.setColor(Color.BLACK);
            g2.drawString(String.format("%.2f", maxPrice), 5, padding);
            g2.drawString(String.format("%.2f", minPrice), 5, getHeight() - padding);
        }
    }

    /**
     * 創建增強版底部控制面板
     */
    private JPanel createEnhancedBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(new Color(250, 250, 250));
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)));

        // 時間範圍選擇
        JComboBox<String> timeRangeCombo = new JComboBox<>(
                new String[]{"全部", "今日", "最近1小時", "最近30分鐘", "最近10分鐘"}
        );

        // 交易類型篩選
        JComboBox<String> typeFilterCombo = new JComboBox<>(
                new String[]{"全部類型", "僅市價單", "僅限價單", "僅FOK單"}
        );

        // 自動刷新
        JCheckBox autoRefreshCheck = new JCheckBox("自動刷新");
        autoRefreshCheck.setSelected(true);

        // 顯示詳細信息
        JCheckBox showDetailCheck = new JCheckBox("顯示詳細信息");
        showDetailCheck.setSelected(true);

        // 導出按鈕
        JButton exportAllButton = new JButton("導出全部");
        exportAllButton.addActionListener(e -> exportAllTransactions());

        // 分析按鈕
        JButton analyzeButton = new JButton("深度分析");
        analyzeButton.addActionListener(e -> performDeepAnalysis());

        // 刷新按鈕
        JButton refreshButton = new JButton("立即刷新");
        refreshButton.addActionListener(e -> refreshData());

        // 關閉按鈕
        JButton closeButton = new JButton("關閉");
        closeButton.addActionListener(e -> dispose());

        bottomPanel.add(new JLabel("時間範圍:"));
        bottomPanel.add(timeRangeCombo);
        bottomPanel.add(Box.createHorizontalStrut(10));
        bottomPanel.add(new JLabel("類型篩選:"));
        bottomPanel.add(typeFilterCombo);
        bottomPanel.add(Box.createHorizontalStrut(20));
        bottomPanel.add(autoRefreshCheck);
        bottomPanel.add(showDetailCheck);
        bottomPanel.add(refreshButton);
        bottomPanel.add(analyzeButton);
        bottomPanel.add(exportAllButton);
        bottomPanel.add(closeButton);

        return bottomPanel;
    }

    /**
     * 創建統計標籤
     */
    private JLabel createStatsLabel(String title, String value) {
        JLabel label = new JLabel("<html><div style='text-align: center; padding: 5px;'>"
                + "<span style='font-size:11px;color:#B0BEC5;'>" + title + "</span><br>"
                + "<span style='font-size:16px;color:#FFFFFF;font-weight:bold;'>" + value + "</span></div></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);

        // 🔧 設置最小尺寸，防止標籤被壓縮
        label.setMinimumSize(new Dimension(120, 40));
        label.setPreferredSize(new Dimension(140, 45));

        // 🔧 添加邊框，增加視覺分隔
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 30), 1, true), // 淡白色圓角邊框
                BorderFactory.createEmptyBorder(5, 8, 5, 8) // 內邊距
        ));

        return label;
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
     * 更新所有表格 - 增強版，支持市價單和限價單分類
     */
    private void updateAllTables() {
        // 清空所有表格
        allTransactionsModel.setRowCount(0);
        buyTransactionsModel.setRowCount(0);
        sellTransactionsModel.setRowCount(0);
        myTransactionsModel.setRowCount(0);
        marketOrderModel.setRowCount(0);
        limitOrderModel.setRowCount(0);

        // 填充數據
        for (Transaction trans : transactionHistory) {
            Object[] rowData = createEnhancedRowData(trans);

            // 添加到全部成交表
            allTransactionsModel.addRow(rowData);

            // 根據交易類型分類
            boolean isMarketOrder = isTransactionMarketOrder(trans);
            if (isMarketOrder) {
                marketOrderModel.addRow(rowData);
            } else {
                limitOrderModel.addRow(rowData);
            }

            // 根據買賣方向分類
            if (trans.isBuyerInitiated()) {
                buyTransactionsModel.addRow(rowData);
            } else {
                sellTransactionsModel.addRow(rowData);
            }

            // 如果是個人交易，添加到我的成交表
            if (isPersonalTransaction(trans)) {
                myTransactionsModel.addRow(rowData);
            }
        }
    }

    /**
     * 創建增強版表格行數據 - 修復空指針版本
     */
    private Object[] createEnhancedRowData(Transaction trans) {
        if (trans == null) {
            return new Object[]{
                "N/A", "未知", "--", "未知", "未知",
                0.0, 0, 0.0, 0, 0.0, "N/A", "N/A", "無效成交"
            };
        }

        try {
            // 成交類型判斷（基於實際成交數據）
            String transactionType = determineTransactionType(trans);

            // 發起方和對手方（基於實際成交）
            String initiator = getTransactionInitiator(trans);
            String counterparty = getTransactionCounterparty(trans);

            // 成交相關計算
            double fillRate = calculateActualFillRate(trans);
            String slippage = calculateActualSlippage(trans);
            String executionTime = getActualExecutionTime(trans);
            String remark = generateTransactionRemark(trans);

            return new Object[]{
                trans.getId(),
                transactionType,
                dateFormat.format(new Date(trans.getTimestamp())),
                initiator,
                counterparty,
                trans.getPrice(),
                trans.getVolume(),
                trans.getPrice() * trans.getVolume(), // 實際成交金額
                trans.getVolume(), // 對於已成交記錄，請求量=成交量
                fillRate,
                slippage,
                executionTime,
                remark
            };
        } catch (Exception e) {
            logger.error("創建成交記錄數據時發生錯誤: " + e.getMessage(), "TRANSACTION_VIEWER");
            return new Object[]{
                "ERROR", "錯誤", "--", "錯誤", "錯誤",
                0.0, 0, 0.0, 0, 0.0, "N/A", "N/A", "數據錯誤: " + e.getMessage()
            };
        }
    }

    /**
     * 確定成交類型（基於實際成交記錄）
     */
    private String determineTransactionType(Transaction trans) {
        String id = trans.getId();

        // 市價單標識
        if (id.startsWith("MKT_")) {
            return trans.isBuyerInitiated() ? "市價買" : "市價賣";
        }

        // FOK單標識
        if (id.contains("FOK")) {
            return "FOK單";
        }

        // 檢查是否有滑價（可能是市價單）
        if (trans instanceof Transaction && hasSlippage(trans)) {
            return trans.isBuyerInitiated() ? "市價買" : "市價賣";
        }

        // 默認為限價單
        return "限價單";
    }

    /**
     * 檢查成交是否有滑價
     */
    private boolean hasSlippage(Transaction trans) {
        // 如果Transaction有多個填單記錄，可能存在滑價
        try {
            if (trans.getFillRecords() != null && trans.getFillRecords().size() > 1) {
                return true;
            }

            // 其他滑價判斷邏輯...
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 計算實際成交率（對於成交記錄，通常是100%）
     */
    private double calculateActualFillRate(Transaction trans) {
        // 對於已完成的成交記錄，成交率通常是100%
        // 除非是部分成交的市價單
        if (trans.getId().startsWith("MKT_")) {
            try {
                int requested = trans.getRequestedVolume();
                int actual = trans.getActualVolume();
                if (requested > 0) {
                    return (double) actual / requested * 100.0;
                }
            } catch (Exception e) {
                // 如果無法獲取請求數量，假設完全成交
            }
        }
        return 100.0;
    }

    /**
     * 計算實際滑價（基於成交記錄）
     */
    private String calculateActualSlippage(Transaction trans) {
        try {
            // 對於市價單，可能有滑價信息
            if (trans.getId().startsWith("MKT_")) {
                double slippagePercent = trans.getSlippagePercentage();
                return String.format("%.2f%%", slippagePercent);
            }

            // 限價單沒有滑價
            return "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * 獲取實際執行時間
     */
    private String getActualExecutionTime(Transaction trans) {
        try {
            if (trans.getId().startsWith("MKT_")) {
                long executionTime = trans.getExecutionTimeMs();
                return executionTime + "ms";
            }
            return "即時";
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 獲取成交發起方
     */
    private String getTransactionInitiator(Transaction trans) {
        try {
            if (trans.isBuyerInitiated() && trans.getBuyer() != null) {
                return getTraderDisplay(trans.getBuyer().getTraderType());
            } else if (!trans.isBuyerInitiated() && trans.getSeller() != null) {
                return getTraderDisplay(trans.getSeller().getTraderType());
            }
        } catch (Exception e) {
            logger.debug("獲取發起方時發生錯誤: " + e.getMessage(), "TRANSACTION_VIEWER");
        }
        return "未知";
    }

    /**
     * 獲取成交對手方
     */
    private String getTransactionCounterparty(Transaction trans) {
        try {
            if (trans.isBuyerInitiated() && trans.getSeller() != null) {
                return getTraderDisplay(trans.getSeller().getTraderType());
            } else if (!trans.isBuyerInitiated() && trans.getBuyer() != null) {
                return getTraderDisplay(trans.getBuyer().getTraderType());
            }
        } catch (Exception e) {
            logger.debug("獲取對手方時發生錯誤: " + e.getMessage(), "TRANSACTION_VIEWER");
        }
        return "未知";
    }

    /**
     * 生成成交備註
     */
    private String generateTransactionRemark(Transaction trans) {
        StringBuilder remark = new StringBuilder();

        try {
            // 大額成交標記
            if (trans.getPrice() * trans.getVolume() > 100000) {
                remark.append("大額成交");
            }

            // 市價單特殊處理
            if (trans.getId().startsWith("MKT_")) {
                if (remark.length() > 0) {
                    remark.append(", ");
                }
                remark.append("市價執行");

                // 添加填單層數信息
//                try {
//                    int depthLevels = trans.getDepthLevels();
//                    if (depthLevels > 1) {
//                        remark.append(String.format(", %d層深度", depthLevels));
//                    }
//                } catch (Exception e) {
//                    // 忽略
//                }
            }

            // 撮合模式信息
            String matchingMode = trans.getMatchingMode();
            if (matchingMode != null && !matchingMode.isEmpty() && !"STANDARD".equals(matchingMode)) {
                if (remark.length() > 0) {
                    remark.append(", ");
                }
                remark.append("模式: ").append(matchingMode);
            }

        } catch (Exception e) {
            return "成交記錄 - " + trans.getId();
        }

        return remark.toString();
    }

    /**
     * 輔助方法：判斷是否為市價單交易
     */
    private boolean isTransactionMarketOrder(Transaction trans) {
        if (trans == null || trans.getId() == null) {
            return false;
        }

        // 基於ID前綴判斷
        if (trans.getId().startsWith("MKT_")) {
            return true;
        }

        // 基於Transaction的isMarketOrder方法（如果有的話）
        try {
            return trans.isMarketOrder();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 輔助方法：判斷是否為個人交易 - 修復空指針版本
     */
    private boolean isPersonalTransaction(Transaction trans) {
        if (trans == null) {
            return false;
        }

        // 檢查買方
        try {
            if (trans.getBuyer() != null
                    && trans.getBuyer().getTraderType() != null
                    && "PERSONAL".equals(trans.getBuyer().getTraderType())) {
                return true;
            }
        } catch (Exception e) {
            // 忽略異常，繼續檢查賣方
        }

        // 檢查賣方
        try {
            if (trans.getSeller() != null
                    && trans.getSeller().getTraderType() != null
                    && "PERSONAL".equals(trans.getSeller().getTraderType())) {
                return true;
            }
        } catch (Exception e) {
            // 忽略異常
        }

        return false;
    }

    /**
     * 獲取交易者顯示名稱
     */
    private String getTraderDisplay(String traderType) {
        if (traderType == null) {
            return "未知";
        }

        switch (traderType) {
            case "RETAIL_INVESTOR":
                return "散戶";
            case "MAIN_FORCE":
                return "主力";
            case "PERSONAL":
                return "個人";
            case "MarketBehavior":
                return "市場";
            default:
                return traderType;
        }
    }

    /**
     * 刷新數據
     */
    private void refreshData() {
        SwingUtilities.invokeLater(() -> {
            updateAllTables();
            updateStatistics();
            updateCharts();
            updateStatisticsAnalysis();
            updateTraderAnalysis();
            lastUpdateLabel.setText(createStatsLabel("最後更新", timeFormat.format(new Date())).getText());
        });
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
        int marketOrderCount = 0;
        int limitOrderCount = 0;

        for (Transaction trans : transactionHistory) {
            totalVolume += trans.getVolume();
            totalAmount += trans.getPrice() * trans.getVolume();
            sumPrice += trans.getPrice();

            if (isTransactionMarketOrder(trans)) {
                marketOrderCount++;
            } else {
                limitOrderCount++;
            }
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
        marketOrderStatsLabel.setText(createStatsLabel("市價單數",
                String.valueOf(marketOrderCount)).getText());
        limitOrderStatsLabel.setText(createStatsLabel("限價單數",
                String.valueOf(limitOrderCount)).getText());
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
     * 更新統計分析
     */
    private void updateStatisticsAnalysis() {
        String report = generateStatisticsReport();
        if (statsAnalysisTextArea != null) {
            statsAnalysisTextArea.setText(report);
            statsAnalysisTextArea.setCaretPosition(0);
        }
    }

    /**
     * 輔助類：交易者統計
     */
    private static class TraderStats {

        int buyCount = 0;
        int sellCount = 0;
        int buyVolume = 0;
        int sellVolume = 0;
        double totalAmount = 0;
        int totalVolume = 0;
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
        report.append("• 賣方主動成交: ").append(countSellerInitiated()).append(" 筆\n");

        // 市價單 vs 限價單統計
        long marketOrderCount = transactionHistory.stream()
                .filter(this::isTransactionMarketOrder)
                .count();
        report.append("• 市價單成交: ").append(marketOrderCount).append(" 筆\n");
        report.append("• 限價單成交: ").append(transactionHistory.size() - marketOrderCount).append(" 筆\n\n");

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
        return "09:30-10:00"; // 簡化實現
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
     * 新增功能方法
     */
    private void viewEnhancedTransactionDetails(JTable table) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }

        DefaultTableModel model = (DefaultTableModel) table.getModel();
        String transactionId = (String) model.getValueAt(selectedRow, 0);

        // 創建詳情對話框
        JDialog detailDialog = new JDialog(this, "成交詳情 - " + transactionId, true);
        detailDialog.setSize(600, 500);
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

        // 添加關閉按鈕
        JButton closeButton = new JButton("關閉");
        closeButton.addActionListener(e -> detailDialog.dispose());
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        detailPanel.add(closeButton, gbc);

        JScrollPane scrollPane = new JScrollPane(detailPanel);
        detailDialog.add(scrollPane);
        detailDialog.setVisible(true);
    }

    private void analyzeSlippage(JTable table) {
        JOptionPane.showMessageDialog(this, "滑價分析功能開發中...", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void compareFills(JTable table) {
        JOptionPane.showMessageDialog(this, "填單比較功能開發中...", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void performDeepAnalysis() {
        JOptionPane.showMessageDialog(this, "深度分析功能開發中...", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

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

    private void exportSelectedRows(JTable table) {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "請先選擇要導出的行", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 簡化實現
        JOptionPane.showMessageDialog(this, "導出功能開發中...", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportAllTransactions() {
        JOptionPane.showMessageDialog(this, "導出功能開發中...", "提示", JOptionPane.INFORMATION_MESSAGE);
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
    @Override
    public void onTransactionAdded(Transaction transaction) {
        // 驗證這是真正的成交記錄
        if (!isValidTransaction(transaction)) {
            logger.warn("收到無效的成交記錄，忽略: " + transaction.getId(), "TRANSACTION_VIEWER");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            // 添加到成交歷史
            transactionHistory.add(transaction);

            // 更新UI顯示
            addTransactionToTables(transaction);
            updateStatistics();
            updateCharts();

            // 記錄日誌
            logger.info(String.format(
                    "新增成交記錄：ID=%s, 價格=%.2f, 數量=%d, 買方=%s, 賣方=%s",
                    transaction.getId(),
                    transaction.getPrice(),
                    transaction.getVolume(),
                    transaction.getBuyer() != null ? transaction.getBuyer().getTraderType() : "未知",
                    transaction.getSeller() != null ? transaction.getSeller().getTraderType() : "未知"
            ), "TRANSACTION_VIEWER");

            // 更新最後更新時間
            lastUpdateLabel.setText(createStatsLabel("最後更新",
                    timeFormat.format(new Date())).getText());
        });
    }

    /**
     * 驗證Transaction是否為有效的成交記錄
     */
    private boolean isValidTransaction(Transaction transaction) {
        if (transaction == null) {
            return false;
        }

        // 檢查基本屬性
        if (transaction.getPrice() <= 0 || transaction.getVolume() <= 0) {
            return false;
        }

        // 檢查ID格式（成交記錄應該有特定格式）
        String id = transaction.getId();
        if (id == null || id.trim().isEmpty()) {
            return false;
        }

        // 成交記錄應該有買賣雙方（除非是市價單的特殊情況）
        boolean hasValidParties = (transaction.getBuyer() != null && transaction.getSeller() != null)
                || transaction.getId().startsWith("MKT_"); // 市價單例外

        return hasValidParties;
    }

    /**
     * 新增方法：只添加單筆交易到表格
     *
     */
    private void addTransactionToTables(Transaction trans) {
        Object[] rowData = createEnhancedRowData(trans);

        // 添加到全部成交表
        allTransactionsModel.addRow(rowData);

        // 根據交易類型分類
        boolean isMarketOrder = isTransactionMarketOrder(trans);
        if (isMarketOrder) {
            marketOrderModel.addRow(rowData);
        } else {
            limitOrderModel.addRow(rowData);
        }

        // 根據買賣方向分類
        if (trans.isBuyerInitiated()) {
            buyTransactionsModel.addRow(rowData);
        } else {
            sellTransactionsModel.addRow(rowData);
        }

        // 如果是個人交易，添加到我的成交表
        if (isPersonalTransaction(trans)) {
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

    /**
     * 批量添加成交記錄
     */
    public void addTransactions(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        transactionHistory.addAll(transactions);
        refreshData();
    }

    /**
     * 添加單筆成交記錄
     */
    public void addTransaction(Transaction transaction) {
        if (transaction == null) {
            return;
        }

        transactionHistory.add(transaction);

        // 即時更新UI（不等待定時刷新）
        SwingUtilities.invokeLater(() -> {
            addTransactionToTables(transaction);
            updateStatistics();
            updateCharts();
        });
    }

    /**
     * 獲取所有成交記錄
     */
    public List<Transaction> getTransactionHistory() {
        return new ArrayList<>(transactionHistory);
    }

    /**
     * 清空所有成交記錄
     */
    public void clearTransactionHistory() {
        transactionHistory.clear();

        // 清空所有表格
        SwingUtilities.invokeLater(() -> {
            allTransactionsModel.setRowCount(0);
            buyTransactionsModel.setRowCount(0);
            sellTransactionsModel.setRowCount(0);
            myTransactionsModel.setRowCount(0);
            if (marketOrderModel != null) {
                marketOrderModel.setRowCount(0);
            }
            if (limitOrderModel != null) {
                limitOrderModel.setRowCount(0);
            }

            // 重置統計
            updateStatistics();
            updateCharts();
        });
    }

    /**
     * 更新交易者分析
     */
    private void updateTraderAnalysis() {
        if (traderAnalysisModel == null) {
            return;
        }

        traderAnalysisModel.setRowCount(0);

        // 統計各交易者類型的數據
        Map<String, TraderStats> statsMap = new HashMap<>();

        for (Transaction trans : transactionHistory) {
            try {
                // 統計買方
                if (trans.getBuyer() != null && trans.getBuyer().getTraderType() != null) {
                    String buyerType = trans.getBuyer().getTraderType();
                    TraderStats buyerStats = statsMap.computeIfAbsent(buyerType, k -> new TraderStats());
                    buyerStats.buyCount++;
                    buyerStats.buyVolume += trans.getVolume();
                    buyerStats.totalAmount += trans.getPrice() * trans.getVolume();
                    buyerStats.totalVolume += trans.getVolume();
                }

                // 統計賣方
                if (trans.getSeller() != null && trans.getSeller().getTraderType() != null) {
                    String sellerType = trans.getSeller().getTraderType();
                    TraderStats sellerStats = statsMap.computeIfAbsent(sellerType, k -> new TraderStats());
                    sellerStats.sellCount++;
                    sellerStats.sellVolume += trans.getVolume();
                    sellerStats.totalAmount += trans.getPrice() * trans.getVolume();
                    sellerStats.totalVolume += trans.getVolume();
                }
            } catch (Exception e) {
                // 忽略單筆記錄的錯誤，繼續處理其他記錄
                System.err.println("處理交易記錄時發生錯誤: " + e.getMessage());
            }
        }

        // 添加到表格
        for (Map.Entry<String, TraderStats> entry : statsMap.entrySet()) {
            String traderType = entry.getKey();
            TraderStats stats = entry.getValue();

            try {
                Object[] rowData = {
                    getTraderDisplay(traderType),
                    stats.buyCount + stats.sellCount,
                    volumeFormat.format(stats.buyVolume),
                    volumeFormat.format(stats.sellVolume),
                    volumeFormat.format(stats.buyVolume - stats.sellVolume),
                    stats.totalVolume > 0 ? priceFormat.format(stats.totalAmount / stats.totalVolume) : "0.00",
                    transactionHistory.size() > 0
                    ? String.format("%.1f%%", (double) (stats.buyCount + stats.sellCount) / transactionHistory.size() * 100) : "0.0%"
                };

                traderAnalysisModel.addRow(rowData);
            } catch (Exception e) {
                // 如果添加行時發生錯誤，記錄但繼續
                System.err.println("添加交易者統計行時發生錯誤: " + e.getMessage());
            }
        }
    }
}
