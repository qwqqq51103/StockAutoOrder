package StockMainAction.view;

import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.StockMarketModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.Range;
import org.jfree.data.time.ohlc.OHLCItem;
import org.jfree.data.time.Second;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import javax.swing.event.ChangeListener;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 主視圖類別 - 負責顯示圖表和數據 作為MVC架構中的View組件
 */
public class MainView extends JFrame {

    // 圖表
    private JFreeChart priceChart;
    private JFreeChart candleChart;
    private JFreeChart volatilityChart;
    private JFreeChart rsiChart;
    private JFreeChart volumeChart;
    private JFreeChart wapChart;
    private JFreeChart retailProfitChart;
    private JFreeChart mainForceProfitChart;

    // 新增技術指標圖表
    private JFreeChart macdChart;
    private JFreeChart bollingerBandsChart;
    private JFreeChart kdjChart;

    // 新增技術指標數據系列
    private XYSeries macdLineSeries;
    private XYSeries macdSignalSeries;
    private XYSeries macdHistogramSeries;
    private XYSeries bollingerUpperSeries;
    private XYSeries bollingerMiddleSeries;
    private XYSeries bollingerLowerSeries;
    private XYSeries kSeries;
    private XYSeries dSeries;
    private XYSeries jSeries;
    // K線疊加指標系列
    private XYSeries sma5Series;
    private XYSeries sma10Series;
    private XYSeries sma20Series;
    private XYSeries ema12Series;
    private XYSeries ema26Series;
    private XYSeries bollUSeries;
    private XYSeries bollMSeries;
    private XYSeries bollLSeries;

    // 圖表數據
    private XYSeries priceSeries;
    private OHLCSeries ohlcSeries;
    private XYSeries smaSeries;
    private XYSeries volatilitySeries;
    private XYSeries rsiSeries;
    private XYSeries wapSeries;
    private DefaultCategoryDataset volumeDataset;
    private DefaultCategoryDataset volumeMADataset; // 成交量均線
    // K線多週期管理（新增 10秒、30秒 以秒為單位以 0.x 表示，內部會換算）
    private final int[] klineMinutes = new int[]{1, 5, 10, 30, 60};
    private final int[] klineSeconds = new int[]{10, 30};
    private final Map<Integer, OHLCSeries> minuteToSeries = new HashMap<>();
    private final Map<Integer, OHLCSeriesCollection> minuteToCollection = new HashMap<>();
    private int currentKlineMinutes = 1;
    private JComboBox<String> klineIntervalCombo;
    private JCheckBox cbSMA5, cbSMA10, cbSMA20, cbEMA12, cbEMA26, cbBOLL, cbSwapColor;
    private Color upColor = new Color(220, 20, 60);
    private Color downColor = new Color(34, 139, 34);
    private List<Color> colorList = new ArrayList<>();

    // UI組件
    private JLabel stockPriceLabel, retailCashLabel, retailStocksLabel, mainForceCashLabel, mainForceStocksLabel,
            targetPriceLabel, averageCostPriceLabel, fundsLabel, inventoryLabel, weightedAveragePriceLabel,
            chartValueLabel; //用於顯示光標位置的數值
    private JLabel mainForcePhaseLabel, recentTrendLabel; // 新增：顯示主力階段與近期趨勢
    private JTextArea infoTextArea;
    private OrderBookView orderBookView;
    private JTabbedPane tabbedPane;
    // 散戶資訊表
    private JTable retailInfoTable;
    private javax.swing.table.DefaultTableModel retailInfoTableModel;

    // 儲存最後一次更新的時間步長
    private int lastTimeStep = -1;
    private boolean isDarkTheme = false;  // 預設為明亮主題

    private final int maxDataPoints = 100; // 限制圖表數據點數量

    /**
     * 構造函數
     */
    public MainView() {
        initializeChartData();
        initializeUI();
    }

    /**
     * 初始化圖表數據
     */
    private void initializeChartData() {
        // 初始化數據系列
        priceSeries = new XYSeries("股價");
        smaSeries = new XYSeries("SMA");
        volatilitySeries = new XYSeries("波動性");
        rsiSeries = new XYSeries("RSI");
        wapSeries = new XYSeries("加權平均價格");

        // 初始化MACD數據系列
        macdLineSeries = new XYSeries("MACD線");
        macdSignalSeries = new XYSeries("信號線");
        macdHistogramSeries = new XYSeries("MACD柱狀圖");

        // 初始化布林帶數據系列
        bollingerUpperSeries = new XYSeries("上軌");
        bollingerMiddleSeries = new XYSeries("中軌");
        bollingerLowerSeries = new XYSeries("下軌");

        // 初始化KDJ數據系列
        kSeries = new XYSeries("K值");
        dSeries = new XYSeries("D值");
        jSeries = new XYSeries("J值");

        // 初始化成交量數據集
        volumeDataset = new DefaultCategoryDataset();
    }

    /**
     * 初始化UI組件
     */
    private void initializeUI() {
        // 設置視窗基本屬性
        setTitle("股票市場模擬 (MVC版)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);

        // 創建選單欄
        JMenuBar menuBar = new JMenuBar();

        // 創建主分頁面板
        tabbedPane = new JTabbedPane();

        // 創建圖表
        createCharts();

        // 創建主分頁
        JPanel mainPanel = createMainPanel();
        // 在市場圖表分頁加入 K線週期切換下拉
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(new JLabel("K線週期:"));
        klineIntervalCombo = new JComboBox<>(new String[]{"10秒","30秒","1分","5分","10分","30分","60分"});
        klineIntervalCombo.setSelectedIndex(0);
        klineIntervalCombo.addActionListener(e -> switchKlineInterval());
        topBar.add(klineIntervalCombo);

        // 指標勾選
        cbSMA5 = new JCheckBox("SMA5", true);
        cbSMA10 = new JCheckBox("SMA10", false);
        cbSMA20 = new JCheckBox("SMA20", false);
        cbEMA12 = new JCheckBox("EMA12", false);
        cbEMA26 = new JCheckBox("EMA26", false);
        cbBOLL = new JCheckBox("BOLL", false);
        cbSwapColor = new JCheckBox("漲跌顏色互換", false);
        ActionListener toggleIndicators = e -> refreshOverlayIndicators();
        cbSMA5.addActionListener(toggleIndicators);
        cbSMA10.addActionListener(toggleIndicators);
        cbSMA20.addActionListener(toggleIndicators);
        cbEMA12.addActionListener(toggleIndicators);
        cbEMA26.addActionListener(toggleIndicators);
        cbBOLL.addActionListener(toggleIndicators);
        cbSwapColor.addActionListener(ev -> swapUpDownColors());

        topBar.add(cbSMA5);
        topBar.add(cbSMA10);
        topBar.add(cbSMA20);
        topBar.add(cbEMA12);
        topBar.add(cbEMA26);
        topBar.add(cbBOLL);
        topBar.add(cbSwapColor);
        mainPanel.add(topBar, BorderLayout.NORTH);
        tabbedPane.addTab("市場圖表", mainPanel);

        // 創建損益表分頁
        JPanel profitPanel = createProfitPanel();
        tabbedPane.addTab("損益表", profitPanel);

        // 新增：散戶資訊分頁
        JPanel retailPanel = createRetailInfoPanel();
        tabbedPane.addTab("散戶資訊", retailPanel);

        // 創建技術指標分頁
        JPanel indicatorsPanel = createIndicatorsPanel();
        tabbedPane.addTab("技術指標", indicatorsPanel);

        // 添加分頁面板到視窗
        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        // 設置全局快捷鍵
        setupKeyboardShortcuts();

        // 創建視圖選單
        JMenu viewMenu = new JMenu("視圖");

        // 添加主題選單項
        JMenuItem darkThemeItem = new JMenuItem("切換暗黑模式");
        darkThemeItem.addActionListener(e -> toggleTheme());
        viewMenu.add(darkThemeItem);

        // 添加其他視圖選項
        viewMenu.addSeparator();
        JCheckBoxMenuItem showGridItem = new JCheckBoxMenuItem("顯示網格線", true);
        showGridItem.addActionListener(e -> toggleGridLines(showGridItem.isSelected()));
        viewMenu.add(showGridItem);

        // 創建工具選單
        JMenu toolsMenu = new JMenu("工具");
        JMenuItem resetChartsItem = new JMenuItem("重置所有圖表");
        resetChartsItem.addActionListener(e -> resetAllCharts((JPanel) tabbedPane.getSelectedComponent()));
        toolsMenu.add(resetChartsItem);

        // 創建幫助選單
        JMenu helpMenu = new JMenu("幫助");
        JMenuItem keyboardShortcutsItem = new JMenuItem("鍵盤快捷鍵");
        keyboardShortcutsItem.addActionListener(e -> showHelpDialog());
        helpMenu.add(keyboardShortcutsItem);
        JMenuItem aboutItem = new JMenuItem("關於");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);

        // 添加所有選單到選單欄
        menuBar.add(viewMenu);
        menuBar.add(toolsMenu);
        menuBar.add(helpMenu);

        // 設置選單欄到窗口
        setJMenuBar(menuBar);

        // 設置關閉事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                // 如果你想保存設置，可以在這裡調用 saveSettings();
                System.exit(0);
            }
        });

        // 如果你想載入設置，可以在這裡調用 loadSettings();
    }

    /**
     * 創建主分頁面板
     */
    private JPanel createMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 創建圖表面板
        JPanel chartPanel = new JPanel(new GridLayout(2, 2));

        // 創建各個圖表面板
        ChartPanel priceChartPanel = new ChartPanel(priceChart);
        ChartPanel volatilityChartPanel = new ChartPanel(volatilityChart);
        ChartPanel rsiChartPanel = new ChartPanel(rsiChart);
        ChartPanel volumeChartPanel = new ChartPanel(volumeChart);

        // 設置圖表交互性
        setupChartInteraction(priceChartPanel, "股價");
        setupChartInteraction(volatilityChartPanel, "波動性");
        setupChartInteraction(rsiChartPanel, "RSI");
        setupChartInteraction(volumeChartPanel, "成交量");

        // 添加到圖表面板 - 只添加一次
        chartPanel.add(priceChartPanel);
        chartPanel.add(volatilityChartPanel);
        chartPanel.add(rsiChartPanel);
        chartPanel.add(volumeChartPanel);

        // 創建標籤面板
        JPanel labelPanel = new JPanel();
        initializeLabels(labelPanel);

        // 創建訂單簿視圖
        orderBookView = new OrderBookView();

        // 信息面板
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("市場信息"));

        // 創建信息文本區域
        infoTextArea = new JTextArea(8, 30);
        infoTextArea.setEditable(false);
        infoTextArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        infoTextArea.setBackground(new Color(250, 250, 250));

        // 創建自定義滾動面板
        JScrollPane infoScrollPane = new JScrollPane(infoTextArea);
        infoScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        infoScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // 添加信息區域工具欄
        JToolBar infoToolBar = new JToolBar();
        infoToolBar.setFloatable(false);
        infoToolBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        // 添加清除按鈕
        JButton clearButton = new JButton("清除");
        clearButton.addActionListener(e -> infoTextArea.setText(""));
        infoToolBar.add(clearButton);
        infoToolBar.addSeparator();

        // 添加搜索欄位
        JTextField searchField = new JTextField(10);
        JButton searchButton = new JButton("搜索");
        searchButton.addActionListener(e -> searchInInfoText(searchField.getText()));

        infoToolBar.add(new JLabel("搜索: "));
        infoToolBar.add(searchField);
        infoToolBar.add(searchButton);

        // 將工具欄和滾動面板添加到信息面板
        infoPanel.add(infoToolBar, BorderLayout.NORTH);
        infoPanel.add(infoScrollPane, BorderLayout.CENTER);

        // 將訂單簿和信息區域組合
        JSplitPane infoSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                orderBookView.getScrollPane(),
                infoPanel);
        infoSplitPane.setResizeWeight(0.7);

        // 將圖表和標籤區域組合
        JSplitPane chartLabelSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                chartPanel,
                labelPanel);
        chartLabelSplitPane.setResizeWeight(0.8);

        // 將左右兩部分組合
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                chartLabelSplitPane,
                infoSplitPane);
        mainSplitPane.setResizeWeight(0.7);

        // 添加分割面板持續位置記憶功能
        mainSplitPane.setContinuousLayout(true);
        infoSplitPane.setContinuousLayout(true);
        chartLabelSplitPane.setContinuousLayout(true);

        mainPanel.add(mainSplitPane, BorderLayout.CENTER);

        // 添加狀態欄
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());

        // 創建並添加圖表數值標籤
        chartValueLabel = new JLabel("移動鼠標至圖表查看數值");
        statusBar.add(chartValueLabel, BorderLayout.WEST);

        // 添加主題切換按鈕到狀態欄右側
        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JToggleButton themeToggleButton = new JToggleButton("暗黑模式");
        themeToggleButton.addActionListener(e -> toggleTheme());
        rightStatusPanel.add(themeToggleButton);

        statusBar.add(rightStatusPanel, BorderLayout.EAST);

        // 添加狀態欄到主面板
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        return mainPanel;
    }

    /**
     * 搜索信息文本區域
     */
    private void searchInInfoText(String searchText) {
        if (searchText == null || searchText.isEmpty() || infoTextArea.getText().isEmpty()) {
            return;
        }

        // 從當前光標位置開始搜索
        int caretPos = infoTextArea.getCaretPosition();
        int foundPos = infoTextArea.getText().indexOf(searchText, caretPos);

        // 如果沒找到，從頭開始搜索
        if (foundPos == -1) {
            foundPos = infoTextArea.getText().indexOf(searchText, 0);
        }

        // 找到後選中文本
        if (foundPos != -1) {
            infoTextArea.setCaretPosition(foundPos);
            infoTextArea.select(foundPos, foundPos + searchText.length());
            infoTextArea.requestFocus();
        } else {
            JOptionPane.showMessageDialog(this, "未找到: " + searchText,
                    "搜索結果", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * 創建損益表分頁
     */
    private JPanel createProfitPanel() {
        JPanel profitPanel = new JPanel(new GridLayout(2, 1));

        retailProfitChart = createProfitChart("散戶損益", "散戶", 1);
        mainForceProfitChart = createProfitChart("主力損益", "主力", 1);

        ChartPanel retailChartPanel = new ChartPanel(retailProfitChart);
        ChartPanel mainForceChartPanel = new ChartPanel(mainForceProfitChart);

        // 為柱狀圖添加適合的交互功能
        setupBarChartInteraction(retailChartPanel, "散戶損益");
        setupBarChartInteraction(mainForceChartPanel, "主力損益");

        profitPanel.add(retailChartPanel);
        profitPanel.add(mainForceChartPanel);

        return profitPanel;
    }

    // 新增：散戶資訊分頁（表格）
    private JPanel createRetailInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] columns = new String[]{"ID", "現金", "持股", "損益"};
        retailInfoTableModel = new javax.swing.table.DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 1:
                        return Double.class;
                    case 2:
                        return Integer.class;
                    case 3:
                        return Double.class;
                    default:
                        return String.class;
                }
            }
        };
        retailInfoTable = new JTable(retailInfoTableModel);
        retailInfoTable.setRowHeight(28);
        retailInfoTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane sp = new JScrollPane(retailInfoTable);
        panel.add(sp, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 為柱狀圖設置交互功能
     */
    private void setupBarChartInteraction(ChartPanel chartPanel, String title) {
        // 啟用縮放
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setMouseWheelEnabled(true);

        // 添加工具提示
        CategoryPlot plot = (CategoryPlot) chartPanel.getChart().getPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();

        renderer.setDefaultToolTipGenerator((dataset, row, column) -> {
            CategoryDataset categoryDataset = (CategoryDataset) dataset;
            String rowKey = categoryDataset.getRowKey(row).toString();
            String columnKey = categoryDataset.getColumnKey(column).toString();
            Number value = categoryDataset.getValue(row, column);

            return String.format("%s - %s: %.2f", columnKey, rowKey, value);
        });
    }

    /**
     * 創建技術指標分頁（優化版）
     */
    private JPanel createIndicatorsPanel() {
        // 創建一個使用CardLayout的面板來切換不同的指標組
        JPanel indicatorsPanel = new JPanel(new BorderLayout());

        // 創建頂部控制面板，包含指標選擇和顯示選項
        JPanel controlPanel = new JPanel(new BorderLayout());

        // === 創建指標切換區 ===
        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        // 創建指標選擇按鈕和面板
        ButtonGroup indicatorGroup = new ButtonGroup();
        JToggleButton basicButton = new JToggleButton("基本指標");
        JToggleButton macdButton = new JToggleButton("MACD");
        JToggleButton bollingerButton = new JToggleButton("布林帶");
        JToggleButton kdjButton = new JToggleButton("KDJ");

        // 添加到按鈕組
        indicatorGroup.add(basicButton);
        indicatorGroup.add(macdButton);
        indicatorGroup.add(bollingerButton);
        indicatorGroup.add(kdjButton);

        // 添加到面板
        selectorPanel.add(basicButton);
        selectorPanel.add(macdButton);
        selectorPanel.add(bollingerButton);
        selectorPanel.add(kdjButton);

        // === 創建顯示選項面板 ===
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox syncZoomCheckBox = new JCheckBox("同步縮放", true);
        JButton resetZoomButton = new JButton("重置縮放");
        JComboBox<String> periodComboBox = new JComboBox<>(new String[]{"全部", "最近50筆", "最近30筆", "最近10筆"});

        optionsPanel.add(new JLabel("顯示範圍:"));
        optionsPanel.add(periodComboBox);
        optionsPanel.add(syncZoomCheckBox);
        optionsPanel.add(resetZoomButton);

        // 組合頂部控制面板
        controlPanel.add(selectorPanel, BorderLayout.NORTH);
        controlPanel.add(optionsPanel, BorderLayout.SOUTH);

        // === 創建CardLayout面板保存不同的指標圖表 ===
        final JPanel cardPanel = new JPanel(new CardLayout());

        // 創建基本指標面板
        JPanel basicPanel = createBasicIndicatorsPanel();
        cardPanel.add(basicPanel, "基本指標");

        // 創建MACD面板
        JPanel macdPanel = createMACDPanel();
        cardPanel.add(macdPanel, "MACD");

        // 創建布林帶面板
        JPanel bollingerPanel = createBollingerBandsPanel();
        cardPanel.add(bollingerPanel, "布林帶");

        // 創建KDJ面板
        JPanel kdjPanel = createKDJPanel();
        cardPanel.add(kdjPanel, "KDJ");

        // 設置指標切換監聽器
        basicButton.addActionListener(e -> {
            CardLayout cl = (CardLayout) cardPanel.getLayout();
            cl.show(cardPanel, "基本指標");
        });

        macdButton.addActionListener(e -> {
            CardLayout cl = (CardLayout) cardPanel.getLayout();
            cl.show(cardPanel, "MACD");
        });

        bollingerButton.addActionListener(e -> {
            CardLayout cl = (CardLayout) cardPanel.getLayout();
            cl.show(cardPanel, "布林帶");
        });

        kdjButton.addActionListener(e -> {
            CardLayout cl = (CardLayout) cardPanel.getLayout();
            cl.show(cardPanel, "KDJ");
        });

        // 默認選中基本指標
        basicButton.setSelected(true);

        // 添加重置縮放功能
        resetZoomButton.addActionListener(e -> {
            resetAllCharts(cardPanel);
        });

        // 添加顯示範圍選擇功能
        periodComboBox.addActionListener(e -> {
            String selectedPeriod = (String) periodComboBox.getSelectedItem();
            int dataPoints;

            switch (selectedPeriod) {
                case "最近50筆":
                    dataPoints = 50;
                    break;
                case "最近30筆":
                    dataPoints = 30;
                    break;
                case "最近10筆":
                    dataPoints = 10;
                    break;
                default:
                    dataPoints = Integer.MAX_VALUE; // 全部
            }

            // 設置數據範圍限制
            limitAllDataPoints(dataPoints);
        });

        // 添加同步縮放功能
        syncZoomCheckBox.addActionListener(e -> {
            boolean sync = syncZoomCheckBox.isSelected();
            if (sync) {
                try {
                    // 嘗試同步當前可見的指標面板中的圖表
//                    syncChartsInVisiblePanel(cardPanel);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "同步縮放功能需要更新的 JFreeChart 版本",
                            "功能不可用",
                            JOptionPane.WARNING_MESSAGE);
                    syncZoomCheckBox.setSelected(false);
                }
            } else {
                // 移除同步
                //unlinkAllCharts(cardPanel);
            }
        });

        // 組裝最終面板
        indicatorsPanel.add(controlPanel, BorderLayout.NORTH);
        indicatorsPanel.add(cardPanel, BorderLayout.CENTER);

        return indicatorsPanel;
    }

    /**
     * 創建基本指標面板（原來的指標）
     */
    private JPanel createBasicIndicatorsPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1));

        // 創建圖表面板
        ChartPanel volatilityChartPanel = new ChartPanel(volatilityChart);
        ChartPanel rsiChartPanel = new ChartPanel(rsiChart);
        ChartPanel wapChartPanel = new ChartPanel(wapChart);

        // 設置圖表面板的首選大小
        Dimension chartSize = new Dimension(800, 200);
        volatilityChartPanel.setPreferredSize(chartSize);
        rsiChartPanel.setPreferredSize(chartSize);
        wapChartPanel.setPreferredSize(chartSize);

        // 啟用圖表交互功能
        enableChartInteraction(volatilityChartPanel);
        enableChartInteraction(rsiChartPanel);
        enableChartInteraction(wapChartPanel);

        // 為每個圖表添加交互功能
        setupChartInteraction(volatilityChartPanel, "波動性");
        setupChartInteraction(rsiChartPanel, "RSI");
        setupChartInteraction(wapChartPanel, "加權平均價格");

        // 添加圖表到面板
        panel.add(volatilityChartPanel);
        panel.add(rsiChartPanel);
        panel.add(wapChartPanel);

        return panel;
    }

    /**
     * 創建MACD指標面板（事件驅動版本）
     */
    private JPanel createMACDPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 檢查是否已創建MACD圖表
        if (macdChart == null) {
            // 初始化MACD數據系列
            macdLineSeries = new XYSeries("MACD線");
            macdSignalSeries = new XYSeries("信號線");
            macdHistogramSeries = new XYSeries("柱狀圖");

            // 創建MACD圖表
            XYSeriesCollection macdDataset = new XYSeriesCollection();
            macdDataset.addSeries(macdLineSeries);
            macdDataset.addSeries(macdSignalSeries);
            macdChart = createXYChart("MACD指標", "時間", "MACD值", macdDataset);

            // 設置柱狀圖為第二個數據集
            XYPlot macdPlot = macdChart.getXYPlot();
            XYBarRenderer barRenderer = new XYBarRenderer(0.2);
            barRenderer.setShadowVisible(false);

            XYSeriesCollection histogramDataset = new XYSeriesCollection();
            histogramDataset.addSeries(macdHistogramSeries);

            macdPlot.setDataset(1, histogramDataset);
            macdPlot.setRenderer(1, barRenderer);

            // 設置顏色
            XYLineAndShapeRenderer lineRenderer = (XYLineAndShapeRenderer) macdPlot.getRenderer(0);
            lineRenderer.setSeriesPaint(0, Color.BLUE);  // MACD線
            lineRenderer.setSeriesPaint(1, Color.RED);   // 信號線
            barRenderer.setSeriesPaint(0, new Color(0, 150, 0, 150));  // 柱狀圖
        }

        // 創建MACD圖表面板
        ChartPanel macdChartPanel = new ChartPanel(macdChart);
        macdChartPanel.setPreferredSize(new Dimension(800, 400));

        // 啟用圖表交互
        enableChartInteraction(macdChartPanel);
        setupChartInteraction(macdChartPanel, "MACD");

        // 創建參數面板（僅顯示當前參數，實際計算由Model負責）
        JPanel paramPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel shortPeriodLabel = new JLabel("短期EMA:");
        JLabel shortPeriodValue = new JLabel("12");

        JLabel longPeriodLabel = new JLabel("長期EMA:");
        JLabel longPeriodValue = new JLabel("26");

        JLabel signalPeriodLabel = new JLabel("信號線:");
        JLabel signalPeriodValue = new JLabel("9");

        JLabel statusLabel = new JLabel("● 自動更新中");
        statusLabel.setForeground(new Color(0, 150, 0));

        // 組裝參數面板（純顯示用）
        paramPanel.add(shortPeriodLabel);
        paramPanel.add(shortPeriodValue);
        paramPanel.add(Box.createHorizontalStrut(10));
        paramPanel.add(longPeriodLabel);
        paramPanel.add(longPeriodValue);
        paramPanel.add(Box.createHorizontalStrut(10));
        paramPanel.add(signalPeriodLabel);
        paramPanel.add(signalPeriodValue);
        paramPanel.add(Box.createHorizontalStrut(20));
        paramPanel.add(statusLabel);

        // 組合面板
        panel.add(paramPanel, BorderLayout.NORTH);
        panel.add(macdChartPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 創建布林帶指標面板（事件驅動版本）
     */
    private JPanel createBollingerBandsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 檢查是否已創建布林帶圖表
        if (bollingerBandsChart == null) {
            // 初始化布林帶數據系列
            bollingerUpperSeries = new XYSeries("上軌");
            bollingerMiddleSeries = new XYSeries("中軌");
            bollingerLowerSeries = new XYSeries("下軌");

            // 創建價格系列（將通過事件更新）
            XYSeries priceCopy = new XYSeries("價格");

            // 創建布林帶圖表
            XYSeriesCollection bollingerDataset = new XYSeriesCollection();
            bollingerDataset.addSeries(priceCopy);
            bollingerDataset.addSeries(bollingerUpperSeries);
            bollingerDataset.addSeries(bollingerMiddleSeries);
            bollingerDataset.addSeries(bollingerLowerSeries);

            bollingerBandsChart = createXYChart("布林帶", "時間", "價格", bollingerDataset);

            // 設置線條顏色
            XYPlot plot = bollingerBandsChart.getXYPlot();
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, Color.BLACK);  // 價格線
            renderer.setSeriesPaint(1, Color.RED);    // 上軌
            renderer.setSeriesPaint(2, Color.BLUE);   // 中軌
            renderer.setSeriesPaint(3, Color.RED);    // 下軌
        }

        // 創建布林帶圖表面板
        ChartPanel bollingerChartPanel = new ChartPanel(bollingerBandsChart);
        bollingerChartPanel.setPreferredSize(new Dimension(800, 400));

        // 啟用圖表交互
        enableChartInteraction(bollingerChartPanel);
        setupChartInteraction(bollingerChartPanel, "布林帶");

        // 創建參數面板（僅顯示當前參數，實際計算由Model負責）
        JPanel paramPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel periodLabel = new JLabel("SMA週期:");
        JLabel periodValue = new JLabel("20");

        JLabel stdDevLabel = new JLabel("標準差倍數:");
        JLabel stdDevValue = new JLabel("2.0");

        JLabel statusLabel = new JLabel("● 自動更新中");
        statusLabel.setForeground(new Color(0, 150, 0));

        // 組裝參數面板（純顯示用）
        paramPanel.add(periodLabel);
        paramPanel.add(periodValue);
        paramPanel.add(Box.createHorizontalStrut(10));
        paramPanel.add(stdDevLabel);
        paramPanel.add(stdDevValue);
        paramPanel.add(Box.createHorizontalStrut(20));
        paramPanel.add(statusLabel);

        // 組合面板
        panel.add(paramPanel, BorderLayout.NORTH);
        panel.add(bollingerChartPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 創建KDJ指標面板（事件驅動版本）
     */
    private JPanel createKDJPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 檢查是否已創建KDJ圖表
        if (kdjChart == null) {
            // 初始化KDJ數據系列
            kSeries = new XYSeries("K值");
            dSeries = new XYSeries("D值");
            jSeries = new XYSeries("J值");

            // 創建KDJ圖表
            XYSeriesCollection kdjDataset = new XYSeriesCollection();
            kdjDataset.addSeries(kSeries);
            kdjDataset.addSeries(dSeries);
            kdjDataset.addSeries(jSeries);

            kdjChart = createXYChart("KDJ指標", "時間", "KDJ值", kdjDataset);

            // 設置線條顏色
            XYPlot plot = kdjChart.getXYPlot();
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, Color.BLACK);  // K值
            renderer.setSeriesPaint(1, Color.BLUE);   // D值
            renderer.setSeriesPaint(2, Color.MAGENTA); // J值

            // 添加參考線
            plot.addRangeMarker(new ValueMarker(80.0, Color.RED, new BasicStroke(1.0f)));
            plot.addRangeMarker(new ValueMarker(20.0, Color.GREEN, new BasicStroke(1.0f)));
        }

        // 創建KDJ圖表面板
        ChartPanel kdjChartPanel = new ChartPanel(kdjChart);
        kdjChartPanel.setPreferredSize(new Dimension(800, 400));

        // 啟用圖表交互
        enableChartInteraction(kdjChartPanel);
        setupChartInteraction(kdjChartPanel, "KDJ");

        // 創建參數面板（僅顯示當前參數，實際計算由Model負責）
        JPanel paramPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel nPeriodLabel = new JLabel("N週期:");
        JLabel nPeriodValue = new JLabel("9");

        JLabel kPeriodLabel = new JLabel("K週期:");
        JLabel kPeriodValue = new JLabel("3");

        JLabel dPeriodLabel = new JLabel("D週期:");
        JLabel dPeriodValue = new JLabel("3");

        JLabel statusLabel = new JLabel("● 自動更新中");
        statusLabel.setForeground(new Color(0, 150, 0));

        // 組裝參數面板（純顯示用）
        paramPanel.add(nPeriodLabel);
        paramPanel.add(nPeriodValue);
        paramPanel.add(Box.createHorizontalStrut(10));
        paramPanel.add(kPeriodLabel);
        paramPanel.add(kPeriodValue);
        paramPanel.add(Box.createHorizontalStrut(10));
        paramPanel.add(dPeriodLabel);
        paramPanel.add(dPeriodValue);
        paramPanel.add(Box.createHorizontalStrut(20));
        paramPanel.add(statusLabel);

        // 組合面板
        panel.add(paramPanel, BorderLayout.NORTH);
        panel.add(kdjChartPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 更新MACD指標數據
     */
    public void updateMACDIndicator(int timeStep, double macdLine, double signalLine, double histogram) {
        if (macdLineSeries != null && macdSignalSeries != null && macdHistogramSeries != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    macdLineSeries.add(timeStep, macdLine);
                    macdSignalSeries.add(timeStep, signalLine);
                    macdHistogramSeries.add(timeStep, histogram);

                    // 限制數據點數量，保持性能
                    limitSeriesDataPoints(macdLineSeries, maxDataPoints);
                    limitSeriesDataPoints(macdSignalSeries, maxDataPoints);
                    limitSeriesDataPoints(macdHistogramSeries, maxDataPoints);
                } catch (Exception e) {
                    System.err.println("更新MACD指標時發生錯誤: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 更新布林帶指標數據
     */
    public void updateBollingerBandsIndicator(int timeStep, double upperBand, double middleBand, double lowerBand) {
        if (bollingerUpperSeries != null && bollingerMiddleSeries != null && bollingerLowerSeries != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    bollingerUpperSeries.add(timeStep, upperBand);
                    bollingerMiddleSeries.add(timeStep, middleBand);
                    bollingerLowerSeries.add(timeStep, lowerBand);

                    // 同時更新布林帶圖表中的價格線（第一個系列）
                    if (bollingerBandsChart != null) {
                        XYPlot plot = bollingerBandsChart.getXYPlot();
                        XYSeriesCollection dataset = (XYSeriesCollection) plot.getDataset();
                        if (dataset.getSeriesCount() > 0) {
                            XYSeries priceSeries = dataset.getSeries(0);
                            // 從主價格序列複製當前價格點
                            if (this.priceSeries != null && this.priceSeries.getItemCount() > 0) {
                                int lastIndex = this.priceSeries.getItemCount() - 1;
                                double currentPrice = this.priceSeries.getY(lastIndex).doubleValue();
                                priceSeries.add(timeStep, currentPrice);
                                limitSeriesDataPoints(priceSeries, maxDataPoints);
                            }
                        }
                    }

                    // 限制數據點數量，保持性能
                    limitSeriesDataPoints(bollingerUpperSeries, maxDataPoints);
                    limitSeriesDataPoints(bollingerMiddleSeries, maxDataPoints);
                    limitSeriesDataPoints(bollingerLowerSeries, maxDataPoints);
                } catch (Exception e) {
                    System.err.println("更新布林帶指標時發生錯誤: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 更新KDJ指標數據
     */
    public void updateKDJIndicator(int timeStep, double kValue, double dValue, double jValue) {
        if (kSeries != null && dSeries != null && jSeries != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    kSeries.add(timeStep, kValue);
                    dSeries.add(timeStep, dValue);
                    jSeries.add(timeStep, jValue);

                    // 限制數據點數量，保持性能
                    limitSeriesDataPoints(kSeries, maxDataPoints);
                    limitSeriesDataPoints(dSeries, maxDataPoints);
                    limitSeriesDataPoints(jSeries, maxDataPoints);
                } catch (Exception e) {
                    System.err.println("更新KDJ指標時發生錯誤: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 限制數據系列的數據點數量
     */
    private void limitSeriesDataPoints(XYSeries series, int maxPoints) {
        if (series.getItemCount() > maxPoints) {
            series.remove(0);
        }
    }

    /**
     * 啟用圖表交互功能
     */
    private void enableChartInteraction(ChartPanel chartPanel) {
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setMouseWheelEnabled(true);
    }

    /**
     * 限制所有圖表的數據點數量
     */
    private void limitAllDataPoints(int maxPoints) {
        try {
            // 限制XY圖表（價格和技術指標）
            if (priceChart != null) {
                limitDataPoints(priceChart, maxPoints);
            }

            if (volatilityChart != null) {
                limitDataPoints(volatilityChart, maxPoints);
            }

            if (rsiChart != null) {
                limitDataPoints(rsiChart, maxPoints);
            }

            if (wapChart != null) {
                limitDataPoints(wapChart, maxPoints);
            }

            // 限制新的技術指標圖表
            if (macdChart != null) {
                limitDataPoints(macdChart, maxPoints);
            }

            if (bollingerBandsChart != null) {
                limitDataPoints(bollingerBandsChart, maxPoints);
            }

            if (kdjChart != null) {
                limitDataPoints(kdjChart, maxPoints);
            }

            // 限制利潤圖表
            if (retailProfitChart != null) {
                limitDataPoints(retailProfitChart, maxPoints);
            }

            if (mainForceProfitChart != null) {
                limitDataPoints(mainForceProfitChart, maxPoints);
            }

            // 限制分類圖表（成交量）
            if (volumeChart != null) {
                limitDataPoints(volumeChart, maxPoints);
            }

        } catch (Exception e) {
            System.err.println("限制所有數據點時發生錯誤: " + e.getMessage());
            // 顯示用戶友好的錯誤訊息
            JOptionPane.showMessageDialog(this,
                    "調整顯示範圍時發生錯誤，請稍後再試",
                    "顯示錯誤",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * 限制指定圖表的數據點數量
     */
    private void limitDataPoints(JFreeChart chart, int maxPoints) {
        if (chart == null) {
            return;
        }

        try {
            Plot plot = chart.getPlot();

            if (plot instanceof XYPlot) {
                // 處理XY圖表（價格、技術指標等）
                limitXYPlotDataPoints((XYPlot) plot, maxPoints);
            } else if (plot instanceof CategoryPlot) {
                // 處理分類圖表（成交量等）
                limitCategoryPlotDataPoints((CategoryPlot) plot, maxPoints);
            }

        } catch (Exception e) {
            System.err.println("限制數據點時發生錯誤: " + e.getMessage());
            // 不重新拋出異常，避免程序崩潰
        }
    }

    /**
     * 限制XY圖表的數據點
     */
    private void limitXYPlotDataPoints(XYPlot plot, int maxPoints) {
        try {
            // 處理所有數據集
            for (int datasetIndex = 0; datasetIndex < plot.getDatasetCount(); datasetIndex++) {
                XYDataset dataset = plot.getDataset(datasetIndex);
                if (dataset instanceof XYSeriesCollection) {
                    XYSeriesCollection collection = (XYSeriesCollection) dataset;

                    // 限制每個系列的數據點
                    for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
                        XYSeries series = collection.getSeries(seriesIndex);

                        // 移除多餘的數據點
                        while (series.getItemCount() > maxPoints) {
                            series.remove(0);
                        }
                    }
                }
            }

            // 安全地調整Y軸範圍
            adjustYAxisRangeSafely(plot);

        } catch (Exception e) {
            System.err.println("限制XY圖表數據點時發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 限制分類圖表的數據點（如成交量圖表）
     */
    private void limitCategoryPlotDataPoints(CategoryPlot plot, int maxPoints) {
        try {
            CategoryDataset dataset = plot.getDataset();
            if (dataset instanceof DefaultCategoryDataset) {
                DefaultCategoryDataset categoryDataset = (DefaultCategoryDataset) dataset;

                // 獲取所有列（時間點）
                @SuppressWarnings("unchecked")
                List<Comparable> columnKeys = categoryDataset.getColumnKeys();

                // 如果數據點超過限制，移除最舊的數據
                while (columnKeys.size() > maxPoints) {
                    Comparable oldestKey = columnKeys.get(0);

                    // 移除所有系列中的這個時間點數據
                    @SuppressWarnings("unchecked")
                    List<Comparable> rowKeys = categoryDataset.getRowKeys();
                    for (Comparable rowKey : rowKeys) {
                        categoryDataset.removeValue(rowKey, oldestKey);
                    }

                    // 重新獲取列鍵列表
                    columnKeys = categoryDataset.getColumnKeys();
                }
            }

        } catch (Exception e) {
            System.err.println("限制分類圖表數據點時發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 安全地調整Y軸範圍，避免相同值範圍錯誤
     */
    private void adjustYAxisRangeSafely(XYPlot plot) {
        try {
            ValueAxis yAxis = plot.getRangeAxis();
            if (yAxis != null) {
                // 獲取數據範圍
                Range dataRange = plot.getDataRange(yAxis);

                if (dataRange != null) {
                    double lower = dataRange.getLowerBound();
                    double upper = dataRange.getUpperBound();

                    // 檢查是否為相同值的範圍
                    if (Math.abs(upper - lower) < 1e-10) {
                        // 如果範圍太小，則擴展範圍
                        double center = (upper + lower) / 2;
                        double expansion = Math.max(Math.abs(center) * 0.1, 1.0); // 擴展10%或至少1

                        yAxis.setRange(center - expansion, center + expansion);
                    } else {
                        // 正常設置範圍，添加一些邊距
                        double margin = (upper - lower) * 0.05; // 5%邊距
                        yAxis.setRange(lower - margin, upper + margin);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("調整Y軸範圍時發生錯誤: " + e.getMessage());
            // 靜默處理，避免程序崩潰
        }
    }

    /**
     * 連結兩個圖表的域軸，使它們同步縮放
     */
    private void linkDomainAxes(ChartPanel source, ChartPanel target) {
        // 這個方法在部分 JFreeChart 版本中可能不可用
        // 如果遇到問題，可以嘗試使用監聽器實現
        try {
            XYPlot sourcePlot = source.getChart().getXYPlot();
            XYPlot targetPlot = target.getChart().getXYPlot();

            sourcePlot.getDomainAxis().addChangeListener(event -> {
                ValueAxis sourceAxis = (ValueAxis) event.getSource();
                targetPlot.getDomainAxis().setRange(sourceAxis.getRange());
            });
        } catch (Exception e) {
            System.err.println("連結域軸失敗: " + e.getMessage());
        }
    }

    /**
     * 初始化標籤並進行排列優化，使用 BoxLayout 實現三列並排
     */
    private void initializeLabels(JPanel panel) {
        // 初始化所有標籤
        stockPriceLabel = new JLabel("股票價格: 0.00");
        retailCashLabel = new JLabel("散戶平均現金: 0.00");
        retailStocksLabel = new JLabel("散戶平均持股: 0");
        mainForceCashLabel = new JLabel("主力現金: 0.00");
        mainForceStocksLabel = new JLabel("主力持有籌碼: 0");
        targetPriceLabel = new JLabel("主力目標價位: 0.00");
        averageCostPriceLabel = new JLabel("主力平均成本: 0.00");
        mainForcePhaseLabel = new JLabel("主力階段: IDLE");
        recentTrendLabel = new JLabel("近期趨勢: 0.0000");
        fundsLabel = new JLabel("市場可用資金: 0.00");
        inventoryLabel = new JLabel("市場庫存: 0");
        weightedAveragePriceLabel = new JLabel("加權平均價格: 0.00");

        // 設置標籤字體
        Font labelFont = new Font("Microsoft JhengHei", Font.PLAIN, 14);
        Font titleFont = new Font("Microsoft JhengHei", Font.BOLD, 15);

        stockPriceLabel.setFont(labelFont);
        retailCashLabel.setFont(labelFont);
        retailStocksLabel.setFont(labelFont);
        mainForceCashLabel.setFont(labelFont);
        mainForceStocksLabel.setFont(labelFont);
        targetPriceLabel.setFont(labelFont);
        averageCostPriceLabel.setFont(labelFont);
        fundsLabel.setFont(labelFont);
        inventoryLabel.setFont(labelFont);
        weightedAveragePriceLabel.setFont(labelFont);

        // 創建主面板，使用 BoxLayout 縱向排列
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // === 創建三列標籤容器 ===
        JPanel threeColumnPanel = new JPanel();
        threeColumnPanel.setLayout(new BoxLayout(threeColumnPanel, BoxLayout.X_AXIS));

        // === 第一列：市場數據 ===
        JPanel marketPanel = new JPanel();
        marketPanel.setLayout(new BoxLayout(marketPanel, BoxLayout.Y_AXIS));

        // 標題
        JLabel marketTitle = new JLabel("市場數據");
        marketTitle.setFont(titleFont);
        marketTitle.setForeground(new Color(0, 102, 204));
        marketTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 添加標題和標籤
        marketPanel.add(marketTitle);
        marketPanel.add(Box.createVerticalStrut(5));
        addAlignedLabel(marketPanel, stockPriceLabel);
        addAlignedLabel(marketPanel, weightedAveragePriceLabel);
        addAlignedLabel(marketPanel, fundsLabel);
        addAlignedLabel(marketPanel, inventoryLabel);

        // === 第二列：散戶數據 ===
        JPanel retailPanel = new JPanel();
        retailPanel.setLayout(new BoxLayout(retailPanel, BoxLayout.Y_AXIS));

        // 標題
        JLabel retailTitle = new JLabel("散戶資訊");
        retailTitle.setFont(titleFont);
        retailTitle.setForeground(new Color(0, 102, 204));
        retailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 添加標題和標籤
        retailPanel.add(retailTitle);
        retailPanel.add(Box.createVerticalStrut(5));
        addAlignedLabel(retailPanel, retailCashLabel);
        addAlignedLabel(retailPanel, retailStocksLabel);
        retailPanel.add(Box.createVerticalGlue()); // 填充剩餘空間

        // === 第三列：主力數據 ===
        JPanel mainForcePanel = new JPanel();
        mainForcePanel.setLayout(new BoxLayout(mainForcePanel, BoxLayout.Y_AXIS));

        // 標題
        JLabel mainForceTitle = new JLabel("主力資訊");
        mainForceTitle.setFont(titleFont);
        mainForceTitle.setForeground(new Color(0, 102, 204));
        mainForceTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 添加標題和標籤
        mainForcePanel.add(mainForceTitle);
        mainForcePanel.add(Box.createVerticalStrut(5));
        addAlignedLabel(mainForcePanel, mainForceCashLabel);
        addAlignedLabel(mainForcePanel, mainForceStocksLabel);
        addAlignedLabel(mainForcePanel, targetPriceLabel);
        addAlignedLabel(mainForcePanel, averageCostPriceLabel);
        addAlignedLabel(mainForcePanel, mainForcePhaseLabel);
        addAlignedLabel(mainForcePanel, recentTrendLabel);

        // 添加等量間隔，確保三列均分寬度
        threeColumnPanel.add(marketPanel);
        threeColumnPanel.add(Box.createHorizontalStrut(20));
        threeColumnPanel.add(retailPanel);
        threeColumnPanel.add(Box.createHorizontalStrut(20));
        threeColumnPanel.add(mainForcePanel);

        // 將三列面板添加到主面板
        panel.add(threeColumnPanel);

        // 設置邊框
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    /**
     * 添加左對齊的標籤
     */
    private void addAlignedLabel(JPanel panel, JLabel label) {
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(3)); // 標籤間垂直間隔
    }

    /**
     * 創建所有圖表
     */
    private void createCharts() {
        // 建立多週期 K 線系列與集合（秒級 + 分級）
        for (int s : klineSeconds) {
            OHLCSeries srs = new OHLCSeries("K線(" + s + "秒)");
            minuteToSeries.put(-s, srs); // 以負值 key 表示秒
            OHLCSeriesCollection c = new OHLCSeriesCollection();
            c.addSeries(srs);
            minuteToCollection.put(-s, c);
        }
        for (int m : klineMinutes) {
            OHLCSeries s = new OHLCSeries("K線(" + m + "分)");
            minuteToSeries.put(m, s);
            OHLCSeriesCollection c = new OHLCSeriesCollection();
            c.addSeries(s);
            minuteToCollection.put(m, c);
        }
        // 預設使用 10 秒 K 線
        currentKlineMinutes = -10;
        ohlcSeries = minuteToSeries.get(currentKlineMinutes);
        OHLCSeriesCollection ohlcCollection = minuteToCollection.get(currentKlineMinutes);
        candleChart = ChartFactory.createCandlestickChart("K線走勢", "時間", "價格", ohlcCollection, true);

        XYPlot candlePlot = candleChart.getXYPlot();
        CandlestickRenderer candleRenderer = new CandlestickRenderer();
        candleRenderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
        candleRenderer.setUpPaint(new Color(220, 20, 60));   // 上漲
        candleRenderer.setDownPaint(new Color(34, 139, 34)); // 下跌
        candlePlot.setRenderer(candleRenderer);

        DateAxis dateAxis = new DateAxis("時間");
        dateAxis.setDateFormatOverride(new java.text.SimpleDateFormat("HH:mm:ss"));
        candlePlot.setDomainAxis(dateAxis);

        // 疊加 SMA 折線
        XYSeriesCollection smaDataset = new XYSeriesCollection();
        smaDataset.addSeries(smaSeries);
        candlePlot.setDataset(1, smaDataset);
        XYLineAndShapeRenderer smaRenderer = new XYLineAndShapeRenderer(true, false);
        smaRenderer.setSeriesPaint(0, new Color(204, 0, 0));
        smaRenderer.setSeriesStroke(0, new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 0, new float[]{5.0f, 5.0f}, 0));
        candlePlot.setRenderer(1, smaRenderer);

        // 準備其他疊加系列容器（先建立空系列，按勾選顯示/隱藏）
        sma5Series = new XYSeries("SMA5");
        sma10Series = new XYSeries("SMA10");
        sma20Series = new XYSeries("SMA20");
        ema12Series = new XYSeries("EMA12");
        ema26Series = new XYSeries("EMA26");
        bollUSeries = new XYSeries("BOLL_U");
        bollMSeries = new XYSeries("BOLL_M");
        bollLSeries = new XYSeries("BOLL_L");

        // Dataset 索引：2..N 由 refreshOverlayIndicators 動態填入

        // 背景與網格
        candlePlot.setBackgroundPaint(new GradientPaint(0, 0, new Color(240, 240, 255), 0, 1000, Color.WHITE));
        candlePlot.setDomainGridlinePaint(new Color(220, 220, 220));
        candlePlot.setRangeGridlinePaint(new Color(220, 220, 220));

        NumberAxis rangeAxis = (NumberAxis) candlePlot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeStickyZero(false);

        // 與既有流程相容：把價格圖參考指向 K 線圖
        priceChart = candleChart;

        // 與既有流程相容：把價格圖參考指向 K 線圖
        // 保持 priceChart 指向折線圖
        // 創建MACD圖表
        XYSeriesCollection macdDataset = new XYSeriesCollection();
        macdDataset.addSeries(macdLineSeries);
        macdDataset.addSeries(macdSignalSeries);
        macdChart = createXYChart("MACD指標", "時間", "MACD值", macdDataset);

        // 為MACD添加柱狀圖
        XYBarRenderer macdBarRenderer = new XYBarRenderer(0.2);
        NumberAxis macdRangeAxis = (NumberAxis) macdChart.getXYPlot().getRangeAxis();
        XYPlot macdPlot = macdChart.getXYPlot();

        // 創建另一個數據集並添加柱狀圖系列
        XYSeriesCollection macdHistogramDataset = new XYSeriesCollection();
        macdHistogramDataset.addSeries(macdHistogramSeries);

        // 在同一圖表中設置第二個渲染器
        macdPlot.setDataset(1, macdHistogramDataset);
        macdPlot.setRenderer(1, macdBarRenderer);

        // 創建布林帶圖表
        XYSeriesCollection bollingerDataset = new XYSeriesCollection();
        bollingerDataset.addSeries(bollingerUpperSeries);
        bollingerDataset.addSeries(bollingerMiddleSeries);
        bollingerDataset.addSeries(bollingerLowerSeries);
        bollingerBandsChart = createXYChart("布林帶", "時間", "價格", bollingerDataset);

        // 設置線條顏色和樣式
        XYPlot bollingerPlot = bollingerBandsChart.getXYPlot();
        XYLineAndShapeRenderer bollingerRenderer = (XYLineAndShapeRenderer) bollingerPlot.getRenderer();
        bollingerRenderer.setSeriesPaint(0, Color.RED);
        bollingerRenderer.setSeriesPaint(1, Color.BLUE);
        bollingerRenderer.setSeriesPaint(2, Color.RED);

        // 創建KDJ圖表
        XYSeriesCollection kdjDataset = new XYSeriesCollection();
        kdjDataset.addSeries(kSeries);
        kdjDataset.addSeries(dSeries);
        kdjDataset.addSeries(jSeries);
        kdjChart = createXYChart("KDJ指標", "時間", "KDJ值", kdjDataset);

        // 設置KDJ線條顏色
        XYPlot kdjPlot = kdjChart.getXYPlot();
        XYLineAndShapeRenderer kdjRenderer = (XYLineAndShapeRenderer) kdjPlot.getRenderer();
        kdjRenderer.setSeriesPaint(0, Color.BLACK);  // K線為黑色
        kdjRenderer.setSeriesPaint(1, Color.BLUE);   // D線為藍色
        kdjRenderer.setSeriesPaint(2, Color.MAGENTA); // J線為洋紅色

        // 設置圖表字體
        setChartFont(macdChart);
        setChartFont(bollingerBandsChart);
        setChartFont(kdjChart);

        // 創建波動性圖
        XYSeriesCollection volatilityDataset = new XYSeriesCollection();
        volatilityDataset.addSeries(volatilitySeries);
        volatilityChart = createXYChart("市場波動性", "時間", "波動性", volatilityDataset);

        // 創建RSI圖
        XYSeriesCollection rsiDataset = new XYSeriesCollection();
        rsiDataset.addSeries(rsiSeries);
        rsiChart = createXYChart("相對強弱指數 (RSI)", "時間", "RSI", rsiDataset);

        // 設置RSI圖的額外屬性
        XYPlot rsiPlot = rsiChart.getXYPlot();
        NumberAxis rsiRangeAxis = (NumberAxis) rsiPlot.getRangeAxis();
        rsiRangeAxis.setRange(0.0, 100.0);
        rsiPlot.addRangeMarker(new ValueMarker(70.0, Color.RED, new BasicStroke(1.0f)));
        rsiPlot.addRangeMarker(new ValueMarker(30.0, Color.GREEN, new BasicStroke(1.0f)));

        // 創建加權平均價格圖
        XYSeriesCollection wapDataset = new XYSeriesCollection();
        wapDataset.addSeries(wapSeries);
        wapChart = createXYChart("加權平均價格 (WAP)", "時間", "WAP", wapDataset);

        // 創建成交量圖 - 增強版
        volumeChart = ChartFactory.createBarChart(
                "成交量", "時間", "成交量", volumeDataset,
                PlotOrientation.VERTICAL, false, true, false
        );

        // 設置成交量圖的渲染器
        CategoryPlot volumePlot = volumeChart.getCategoryPlot();
        BarRenderer volumeRenderer = new BarRenderer() {
            @Override
            public Paint getItemPaint(int row, int column) {
                if (column < colorList.size()) {
                    Color color = colorList.get(column);

                    // 創建漸變效果，從頂部到底部顏色漸深
                    return new GradientPaint(
                            0, 0,
                            new Color(color.getRed(), color.getGreen(), color.getBlue(), 150),
                            0, 1000,
                            new Color(color.getRed(), color.getGreen(), color.getBlue(), 220)
                    );
                }
                return Color.BLUE;
            }

            public GradientPaint getGradientPaint(int row, int column) {
                if (column < colorList.size()) {
                    Color color = colorList.get(column);
                    // 添加漸變效果，顏色從頂部到底部加深
                    Color startColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 150);
                    Color endColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 220);
                    return new GradientPaint(0, 0, startColor, 0, 0, endColor);
                }
                return null;
            }
        };

        // 使用自定義的漸變柱狀
        volumeRenderer.setBarPainter(new StandardBarPainter());
        volumeRenderer.setShadowVisible(false);
        volumeRenderer.setDrawBarOutline(false);
        volumeRenderer.setItemMargin(0.1); // 正確的方法：設置柱狀圖之間的間距

        volumePlot.setRenderer(volumeRenderer);
        volumePlot.setBackgroundPaint(new Color(250, 250, 250));

        // 設置類別軸，限制標籤顯示，避免擁擠
        CategoryAxis domainAxis = volumePlot.getDomainAxis();
        domainAxis.setCategoryMargin(0.05);
        domainAxis.setLowerMargin(0.01);
        domainAxis.setUpperMargin(0.01);
        domainAxis.setMaximumCategoryLabelWidthRatio(0.3f);

        volumeRenderer.setBarPainter(new StandardBarPainter());
        volumePlot.setRenderer(volumeRenderer);

        // 設置圖表字體
        setChartFont(volumeChart);
        setChartFont(priceChart);
        setChartFont(volatilityChart);
        setChartFont(rsiChart);
        setChartFont(wapChart);
    }

    /**
     * 創建XY折線圖
     */
    private JFreeChart createXYChart(String title, String xAxisLabel, String yAxisLabel, XYSeriesCollection dataset) {
        JFreeChart chart = ChartFactory.createXYLineChart(
                title, xAxisLabel, yAxisLabel, dataset,
                PlotOrientation.VERTICAL, true, true, false
        );

        // 設置圖表字體和樣式
        setChartFont(chart);

        return chart;
    }

    /**
     * 創建損益柱狀圖
     */
    private JFreeChart createProfitChart(String title, String categoryPrefix, int count) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        // 初始化數據
        for (int i = 1; i <= count; i++) {
            dataset.addValue(0, "現金", categoryPrefix + i);
            dataset.addValue(0, "持股", categoryPrefix + i);
            dataset.addValue(0, "損益", categoryPrefix + i);
        }

        JFreeChart chart = ChartFactory.createBarChart(
                title, "分類", "數值", dataset,
                PlotOrientation.HORIZONTAL, true, true, false
        );

        // 設置字體
        setChartFont(chart);

        // 設置渲染器顏色
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);    // 現金
        renderer.setSeriesPaint(1, Color.GREEN);   // 持股
        renderer.setSeriesPaint(2, Color.ORANGE);  // 損益

        return chart;
    }

    // 新增：更新散戶資訊表
    public void updateRetailInfoTable(java.util.List<StockMainAction.model.RetailInvestorAI> investors, double stockPrice) {
        SwingUtilities.invokeLater(() -> {
            // 清空
            while (retailInfoTableModel.getRowCount() > 0) {
                retailInfoTableModel.removeRow(0);
            }

            for (StockMainAction.model.RetailInvestorAI inv : investors) {
                String id = inv.getTraderID();
                double cash = inv.getCash();
                int stocks = inv.getAccumulatedStocks();
                double profit = cash + stocks * stockPrice - inv.getInitialCash();
                retailInfoTableModel.addRow(new Object[]{id, cash, stocks, profit});
            }
        });
    }

    // 新增：更新散戶損益圖（將所有散戶匯入到同一個分類圖）
    public void updateRetailProfitChart(java.util.List<StockMainAction.model.RetailInvestorAI> investors, double stockPrice, double defaultInitial) {
        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset retailDataset = (DefaultCategoryDataset) retailProfitChart.getCategoryPlot().getDataset();
            // 先清理舊的列鍵
            @SuppressWarnings("unchecked")
            java.util.List<Comparable> colKeys = new java.util.ArrayList<>(retailDataset.getColumnKeys());
            for (Comparable k : colKeys) {
                retailDataset.removeColumn(k);
            }

            // 逐一加入散戶
            for (int i = 0; i < investors.size(); i++) {
                StockMainAction.model.RetailInvestorAI inv = investors.get(i);
                String col = inv.getTraderID();
                double cash = inv.getCash();
                int stocks = inv.getAccumulatedStocks();
                double init = inv.getInitialCash() > 0 ? inv.getInitialCash() : defaultInitial;
                double profit = cash + stocks * stockPrice - init;
                retailDataset.addValue(cash, "現金", col);
                retailDataset.addValue(stocks, "持股", col);
                retailDataset.addValue(profit, "損益", col);
            }
        });
    }

    /**
     * 設置圖表字體
     */
    private void setChartFont(JFreeChart chart) {
        Font titleFont = new Font("Microsoft JhengHei", Font.BOLD, 18);
        Font axisFont = new Font("Microsoft JhengHei", Font.PLAIN, 12);
        chart.getTitle().setFont(titleFont);

        // 設置坐標軸字體
        if (chart.getPlot() instanceof XYPlot) {
            XYPlot plot = (XYPlot) chart.getPlot();
            plot.getDomainAxis().setLabelFont(axisFont);
            plot.getDomainAxis().setTickLabelFont(axisFont);
            plot.getRangeAxis().setLabelFont(axisFont);
            plot.getRangeAxis().setTickLabelFont(axisFont);
        } else if (chart.getPlot() instanceof CategoryPlot) {
            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            plot.getDomainAxis().setLabelFont(axisFont);
            plot.getDomainAxis().setTickLabelFont(axisFont);
            plot.getRangeAxis().setLabelFont(axisFont);
            plot.getRangeAxis().setTickLabelFont(axisFont);

            // 設置圖例字體
            if (chart.getLegend() != null) {
                chart.getLegend().setItemFont(axisFont);
            }
        }
    }

    /**
     * 更新價格圖表
     */
    public void updatePriceChart(int timeStep, double price, double sma) {
        SwingUtilities.invokeLater(() -> {
            if (!Double.isNaN(price)) {
                // 更新折線供其他功能使用
                priceSeries.add(timeStep, price);
                keepSeriesWithinLimit(priceSeries, 100);

                // 以所選秒/分鐘窗聚合 K 線（對齊到桶）
                long now = System.currentTimeMillis();
                long aligned;
                RegularTimePeriod period;
                if (currentKlineMinutes < 0) {
                    int s = -currentKlineMinutes; // 秒
                    long bucketMs = 1000L * s;
                    aligned = now - (now % bucketMs);
                    period = new Second(new java.util.Date(aligned));
                } else {
                    int m = currentKlineMinutes;  // 分
                    long bucketMs = 60_000L * m;
                    aligned = now - (now % bucketMs);
                    period = new Minute(new java.util.Date(aligned));
                }
                try {
                    OHLCSeries series = minuteToSeries.get(currentKlineMinutes);
                    if (series.getItemCount() == 0) {
                        series.add(period, price, price, price, price);
                    } else {
                        int last = series.getItemCount() - 1;
                        // 先取出最後一根
                        OHLCItem lastItem = (OHLCItem) series.getDataItem(last);
                        // 用 item 取得 O/H/L
                        double open = lastItem.getOpenValue();
                        double high = Math.max(lastItem.getHighValue(), price);
                        double low = Math.min(lastItem.getLowValue(), price);
                        // 判斷是否同一桶
                        if (lastItem.getPeriod().equals(period)) {
                            series.remove(lastItem.getPeriod());
                            series.add(period, open, high, low, price);
                        } else {
                            double prevClose = lastItem.getCloseValue();
                            double newOpen = prevClose;
                            double newHigh = Math.max(newOpen, price);
                            double newLow = Math.min(newOpen, price);
                            series.add(period, newOpen, newHigh, newLow, price);
                        }
                    }
                } catch (Exception ignore) {
                }
            }

            // 依最新 K 線即時重算覆蓋指標，確保完全對齊目前時間窗
            recomputeOverlayFromOHLC();

            if (!Double.isNaN(sma)) {
                // 與 K 線共用 DateAxis：使用 epoch 毫秒作為 X
                long now = System.currentTimeMillis();
                smaSeries.add((double) now, sma);
                keepSeriesWithinLimit(smaSeries, 100);
            }
        });
    }

    // 切換 K 線週期
    private void switchKlineInterval() {
        int idx = klineIntervalCombo.getSelectedIndex();
        // 對應下拉鍵：10秒、30秒、1分、5分、10分、30分、60分
        int[] opts = new int[]{-10,-30,1,5,10,30,60};
        currentKlineMinutes = opts[idx];
        // 切換 dataset 到對應集合
        XYPlot candlePlot = candleChart.getXYPlot();
        candlePlot.setDataset(0, minuteToCollection.get(currentKlineMinutes));
        candlePlot.datasetChanged(null);
        // 以當前 K 線序列重算覆蓋指標，確保時間座標完全對齊
        recomputeOverlayFromOHLC();
        refreshOverlayIndicators();
    }

    // 重新整理疊加指標的顯示與資料集配置
    private void refreshOverlayIndicators() {
        try {
            XYPlot candlePlot = candleChart.getXYPlot();

            int datasetIndex = 1; // 0 是 K 線，1 之後留給疊加

            // 先清空 1 之後的 dataset（保留 index 1 的 SMA 主線）
            while (candlePlot.getDatasetCount() > 1) {
                candlePlot.setDataset(candlePlot.getDatasetCount() - 1, null);
            }

            // 重新掛上 SMA 主線（保持 index 1）
            XYSeriesCollection smaDs = new XYSeriesCollection();
            smaDs.addSeries(smaSeries);
            candlePlot.setDataset(datasetIndex, smaDs);
            XYLineAndShapeRenderer smaR = new XYLineAndShapeRenderer(true, false);
            smaR.setSeriesPaint(0, new Color(204, 0, 0));
            candlePlot.setRenderer(datasetIndex, smaR);

            // 勾選額外 SMA
            if (cbSMA5.isSelected()) {
                datasetIndex++;
                XYSeriesCollection ds = new XYSeriesCollection();
                ds.addSeries(sma5Series);
                candlePlot.setDataset(datasetIndex, ds);
                XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
                r.setSeriesPaint(0, new Color(255, 165, 0));
                candlePlot.setRenderer(datasetIndex, r);
            }
            if (cbSMA10.isSelected()) {
                datasetIndex++;
                XYSeriesCollection ds = new XYSeriesCollection();
                ds.addSeries(sma10Series);
                candlePlot.setDataset(datasetIndex, ds);
                XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
                r.setSeriesPaint(0, new Color(0, 128, 255));
                candlePlot.setRenderer(datasetIndex, r);
            }
            if (cbSMA20.isSelected()) {
                datasetIndex++;
                XYSeriesCollection ds = new XYSeriesCollection();
                ds.addSeries(sma20Series);
                candlePlot.setDataset(datasetIndex, ds);
                XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
                r.setSeriesPaint(0, new Color(128, 0, 128));
                candlePlot.setRenderer(datasetIndex, r);
            }
            if (cbEMA12.isSelected()) {
                datasetIndex++;
                XYSeriesCollection ds = new XYSeriesCollection();
                ds.addSeries(ema12Series);
                candlePlot.setDataset(datasetIndex, ds);
                XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
                r.setSeriesPaint(0, new Color(0, 180, 180));
                candlePlot.setRenderer(datasetIndex, r);
            }
            if (cbEMA26.isSelected()) {
                datasetIndex++;
                XYSeriesCollection ds = new XYSeriesCollection();
                ds.addSeries(ema26Series);
                candlePlot.setDataset(datasetIndex, ds);
                XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
                r.setSeriesPaint(0, new Color(180, 120, 0));
                candlePlot.setRenderer(datasetIndex, r);
            }
            if (cbBOLL.isSelected()) {
                datasetIndex++;
                XYSeriesCollection ds = new XYSeriesCollection();
                ds.addSeries(bollUSeries);
                ds.addSeries(bollMSeries);
                ds.addSeries(bollLSeries);
                candlePlot.setDataset(datasetIndex, ds);
                XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
                r.setSeriesPaint(0, Color.GRAY);
                r.setSeriesPaint(1, Color.DARK_GRAY);
                r.setSeriesPaint(2, Color.GRAY);
                candlePlot.setRenderer(datasetIndex, r);
            }

            candlePlot.datasetChanged(null);
        } catch (Exception ignore) {}
    }

    // 基於當前 K 線序列（OHLCSeries）的 close 值重算 SMA/EMA/BOLL 並以 K 線 period 時間作為 X 軸
    private void recomputeOverlayFromOHLC() {
        try {
            OHLCSeries series = minuteToSeries.get(currentKlineMinutes);
            if (series == null) return;

            // 先清空既有資料
            sma5Series.clear();
            sma10Series.clear();
            sma20Series.clear();
            ema12Series.clear();
            ema26Series.clear();
            bollUSeries.clear();
            bollMSeries.clear();
            bollLSeries.clear();

            int n = series.getItemCount();
            if (n == 0) return;

            // 取 close 與對應時間（毫秒）
            java.util.List<Double> closes = new java.util.ArrayList<>(n);
            java.util.List<Long> times = new java.util.ArrayList<>(n);
            java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
            for (int i = 0; i < n; i++) {
                org.jfree.data.time.ohlc.OHLCItem it = (org.jfree.data.time.ohlc.OHLCItem) series.getDataItem(i);
                closes.add(it.getCloseValue());
                long t = it.getPeriod().getMiddleMillisecond(cal);
                times.add(t);
            }

            // 工具：移動平均
            java.util.function.BiConsumer<Integer, org.jfree.data.xy.XYSeries> smaFill = (period, out) -> {
                if (n < period) return;
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    sum += closes.get(i);
                    if (i >= period) sum -= closes.get(i - period);
                    if (i >= period - 1) {
                        out.add(times.get(i).doubleValue(), sum / period);
                    }
                }
            };

            // 工具：EMA
            java.util.function.BiConsumer<Integer, org.jfree.data.xy.XYSeries> emaFill = (period, out) -> {
                if (n == 0) return;
                double k = 2.0 / (period + 1);
                double ema = closes.get(0);
                for (int i = 0; i < n; i++) {
                    if (i == 0) {
                        ema = closes.get(0);
                    } else {
                        ema = (closes.get(i) - ema) * k + ema;
                    }
                    if (i >= period - 1) out.add(times.get(i).doubleValue(), ema);
                }
            };

            // 工具：BOLL(period=20)
            java.util.function.Consumer<Integer> bollFill = (Integer period) -> {
                if (n < period) return;
                double sum = 0, sumSq = 0;
                for (int i = 0; i < n; i++) {
                    double c = closes.get(i);
                    sum += c; sumSq += c * c;
                    if (i >= period) {
                        double old = closes.get(i - period);
                        sum -= old; sumSq -= old * old;
                    }
                    if (i >= period - 1) {
                        double mean = sum / period;
                        double var = (sumSq / period) - (mean * mean);
                        if (var < 0) var = 0;
                        double sd = Math.sqrt(var);
                        bollMSeries.add(times.get(i).doubleValue(), mean);
                        bollUSeries.add(times.get(i).doubleValue(), mean + 2 * sd);
                        bollLSeries.add(times.get(i).doubleValue(), mean - 2 * sd);
                    }
                }
            };

            // 實際填入
            smaFill.accept(5, sma5Series);
            smaFill.accept(10, sma10Series);
            smaFill.accept(20, sma20Series);
            emaFill.accept(12, ema12Series);
            emaFill.accept(26, ema26Series);
            bollFill.accept(20);

        } catch (Exception ignore) {}
    }

    // 交換上漲/下跌顏色，並套用到成交量與 K 線
    private void swapUpDownColors() {
        Color tmp = upColor;
        upColor = downColor;
        downColor = tmp;
    }

    /**
     * 更新技術指標
     */
    public void updateTechnicalIndicators(int timeStep, double volatility, double rsi, double wap) {
        SwingUtilities.invokeLater(() -> {
            if (!Double.isNaN(volatility)) {
                volatilitySeries.add(timeStep, volatility);
                keepSeriesWithinLimit(volatilitySeries, 100);
            }

            if (!Double.isNaN(rsi)) {
                rsiSeries.add(timeStep, rsi);
                keepSeriesWithinLimit(rsiSeries, 100);
            }

            if (!Double.isNaN(wap)) {
                wapSeries.add(timeStep, wap);
                keepSeriesWithinLimit(wapSeries, 100);
            }
            // 同步更新量能均線等可在此處理（若有對應數據）
        });
    }

    /**
     * 更新成交量圖
     */
    public void updateVolumeChart(int timeStep, int volume) {
        SwingUtilities.invokeLater(() -> {
            // 以 K 線相同的桶（秒/分）聚合成交量，並以時間字串為列鍵
            long now = System.currentTimeMillis();
            long aligned;
            RegularTimePeriod period;
            String key;
            if (currentKlineMinutes < 0) {
                int s = -currentKlineMinutes; // 秒
                long bucketMs = 1000L * s;
                aligned = now - (now % bucketMs);
                period = new Second(new java.util.Date(aligned));
                key = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(aligned));
            } else {
                int m = currentKlineMinutes;  // 分
                long bucketMs = 60_000L * m;
                aligned = now - (now % bucketMs);
                period = new Minute(new java.util.Date(aligned));
                key = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(aligned));
            }

            // 若已存在該時間桶則累加，否則新增（限制最多 30 根）
            @SuppressWarnings("unchecked")
            java.util.List<Comparable> keys = volumeDataset.getColumnKeys();
            if (!keys.contains(key)) {
                while (volumeDataset.getColumnCount() >= 30) {
                    String firstKey = (String) volumeDataset.getColumnKeys().get(0);
                    volumeDataset.removeColumn(firstKey);
                    if (!colorList.isEmpty()) colorList.remove(0);
                }
                volumeDataset.addValue(volume, "Volume", key);

                // 以對應 K 線方向決定顏色
                Color color = upColor;
                try {
                    OHLCSeries series = minuteToSeries.get(currentKlineMinutes);
                    if (series != null) {
                        int idx = series.indexOf(period);
                        if (idx >= 0) {
                            OHLCItem ki = (OHLCItem) series.getDataItem(idx);
                            color = (ki.getCloseValue() >= ki.getOpenValue()) ? upColor : downColor;
                        }
                    }
                } catch (Exception ignore) {}
                colorList.add(color);
            } else {
                Number existingValue = volumeDataset.getValue("Volume", key);
                int newValue = (existingValue != null ? existingValue.intValue() : 0) + volume;
                volumeDataset.setValue(newValue, "Volume", key);
            }
        });
    }

    /**
     * 更新損益表
     */
    public void updateProfitChart(double retailCash, int retailStocks, double stockPrice, double initialRetailCash,
            double mainForceCash, int mainForceStocks, double initialMainForceCash) {
        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset retailDataset = (DefaultCategoryDataset) retailProfitChart.getCategoryPlot().getDataset();
            DefaultCategoryDataset mainForceDataset = (DefaultCategoryDataset) mainForceProfitChart.getCategoryPlot().getDataset();

            // 更新散戶數據
            String retailCategory = "散戶1";
            retailDataset.setValue(retailCash, "現金", retailCategory);
            retailDataset.setValue(retailStocks, "持股", retailCategory);
            double retailProfit = (retailStocks * stockPrice) + retailCash - initialRetailCash;
            retailDataset.setValue(retailProfit, "損益", retailCategory);

            // 更新主力數據
            String mainCategory = "主力1";
            mainForceDataset.setValue(mainForceCash, "現金", mainCategory);
            mainForceDataset.setValue(mainForceStocks, "持股", mainCategory);
            double mainForceProfit = (mainForceStocks * stockPrice) + mainForceCash - initialMainForceCash;
            mainForceDataset.setValue(mainForceProfit, "損益", mainCategory);
        });
    }

    /**
     * 更新市場狀態標籤
     */
    public void updateMarketStateLabels(double price, double retailCash, int retailStocks,
            double mainForceCash, int mainForceStocks,
            double targetPrice, double avgCostPrice,
            double funds, int inventory,
            double wap) {
        SwingUtilities.invokeLater(() -> {
            stockPriceLabel.setText("股票價格: " + String.format("%.2f", price));
            retailCashLabel.setText("散戶平均現金: " + String.format("%.2f", retailCash));
            retailStocksLabel.setText("散戶平均持股: " + retailStocks);
            mainForceCashLabel.setText("主力現金: " + String.format("%.2f", mainForceCash));
            mainForceStocksLabel.setText("主力持有籌碼: " + mainForceStocks);
            targetPriceLabel.setText("主力目標價位: " + String.format("%.2f", targetPrice));
            averageCostPriceLabel.setText("主力平均成本: " + String.format("%.2f", avgCostPrice));
            fundsLabel.setText("市場可用資金: " + String.format("%.2f", funds));
            inventoryLabel.setText("市場庫存: " + inventory);
            weightedAveragePriceLabel.setText("加權平均價格: " + String.format("%.2f", wap));
        });
    }

    // 新增：更新主力狀態與趨勢
    public void updateMainForceStatus(String phase, double recentTrend) {
        SwingUtilities.invokeLater(() -> {
            if (mainForcePhaseLabel != null) {
                mainForcePhaseLabel.setText("主力階段: " + phase);
            }
            if (recentTrendLabel != null) {
                recentTrendLabel.setText("近期趨勢: " + String.format("%.4f", recentTrend));
            }
        });
    }

    /**
     * 更新訂單簿顯示
     */
    public void updateOrderBookDisplay(OrderBook orderBook) {
        SwingUtilities.invokeLater(() -> {
            orderBookView.updateOrderBookDisplay(orderBook);
        });
    }

    /**
     * 添加信息到文本區域（增強版）
     */
    public void appendToInfoArea(String message) {
        appendToInfoArea(message, InfoType.NORMAL);
    }

    /**
     * 顯示輸入對話框
     */
    public String showInputDialog(String message, String title, int messageType) {
        return JOptionPane.showInputDialog(this, message, title, messageType);
    }

    /**
     * 顯示確認對話框
     */
    public int showConfirmDialog(String message, String title, int optionType) {
        return JOptionPane.showConfirmDialog(this, message, title, optionType);
    }

    /**
     * 顯示錯誤消息
     */
    public void showErrorMessage(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * 顯示信息消息
     */
    public void showInfoMessage(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 限制數據點數量，只保留最新的一定數量的數據
     */
    private void keepSeriesWithinLimit(XYSeries series, int maxPoints) {
        while (series.getItemCount() > maxPoints) {
            series.remove(0);
        }
    }

    /**
     * 消息類型枚舉
     */
    public enum InfoType {
        NORMAL,
        TRANSACTION,
        SYSTEM,
        WARNING,
        ERROR,
        MARKET
    }

    /**
     * 添加帶類型的信息到文本區域
     */
    public void appendToInfoArea(String message, InfoType type) {
        SwingUtilities.invokeLater(() -> {
            // 獲取當前時間
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

            // 根據消息類型設置前綴
            String prefix;
            switch (type) {
                case TRANSACTION:
                    prefix = "[交易] ";
                    break;
                case SYSTEM:
                    prefix = "[系統] ";
                    break;
                case WARNING:
                    prefix = "[警告] ";
                    break;
                case ERROR:
                    prefix = "[錯誤] ";
                    break;
                case MARKET:
                    prefix = "[市場] ";
                    break;
                default:
                    prefix = "";
            }

            // 格式化輸出
            String formattedMessage = String.format("%s %s%s\n", timestamp, prefix, message);

            // 添加到文本區域
            infoTextArea.append(formattedMessage);

            // 自動滾動到最新內容
            infoTextArea.setCaretPosition(infoTextArea.getDocument().getLength());
        });
    }

    /**
     * 設置圖表交互功能（更新版本，支持 XYPlot 和 CategoryPlot）
     *
     * @param chartPanel 要增強的圖表面板
     * @param title 圖表標題，用於識別不同圖表
     */
    private void setupChartInteraction(ChartPanel chartPanel, String title) {
        // 啟用縮放和滾輪
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setMouseWheelEnabled(true);

        // 獲取圖表
        JFreeChart chart = chartPanel.getChart();
        Plot plot = chart.getPlot();

        // 根據圖表類型進行不同處理
        if (plot instanceof XYPlot) {
            // XY 圖表（折線圖等）
            setupXYPlotInteraction((XYPlot) plot, chartPanel, title);
        } else if (plot instanceof CategoryPlot) {
            // 類別圖表（柱狀圖等）
            setupCategoryPlotInteraction((CategoryPlot) plot, chartPanel, title);
        }
    }

    /**
     * 設置 XYPlot 類型圖表的交互功能
     */
    private void setupXYPlotInteraction(XYPlot plot, ChartPanel chartPanel, String title) {
        // 調整繪圖區外觀
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setRangeCrosshairLockedOnData(false);
        plot.setDomainCrosshairPaint(new Color(0, 0, 0, 80));
        plot.setRangeCrosshairPaint(new Color(0, 0, 0, 80));

        // 針對不同渲染器類型設置互動：
        // 1) 若是 K 線（CandlestickRenderer），避免把渲染器改成折線
        if (plot.getRenderer() instanceof org.jfree.chart.renderer.xy.CandlestickRenderer) {
            // 可選：設定提示（顯示 O/H/L/C）
            // 保持現有渲染器以確保蠟燭為直立形態
        } else {
            // 2) 其他 XY 圖（折線等）設定提示
            if (!(plot.getRenderer() instanceof XYLineAndShapeRenderer)) {
                plot.setRenderer(new XYLineAndShapeRenderer());
        }
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setDefaultToolTipGenerator((dataset, series, item) -> {
            XYDataset xyDataset = (XYDataset) dataset;
            double x = xyDataset.getXValue(series, item);
            double y = xyDataset.getYValue(series, item);
            String seriesName = xyDataset.getSeriesKey(series).toString();
            return String.format("%s - 時間: %.0f, 值: %.2f", seriesName, x, y);
        });
        }

        // 添加鼠標監聽器
        chartPanel.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
                // 點擊事件可以實現標記功能
                // 這裡可以預留未來擴展
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                // 將鼠標位置轉換為數據坐標
                Point2D p = chartPanel.translateScreenToJava2D(
                        event.getTrigger().getPoint());
                Rectangle2D plotArea = chartPanel.getScreenDataArea();

                if (plotArea != null && plotArea.contains(p)) {
                    try {
                        // 將Java2D座標轉換為數據值
                        double chartX = plot.getDomainAxis().java2DToValue(
                                p.getX(), plotArea, plot.getDomainAxisEdge());
                        double chartY = plot.getRangeAxis().java2DToValue(
                                p.getY(), plotArea, plot.getRangeAxisEdge());

                        // 顯示十字線
                        plot.setDomainCrosshairValue(chartX);
                        plot.setRangeCrosshairValue(chartY);

                        // 更新狀態欄或信息區域
                        String valueText = String.format("%s - 時間: %.0f, 值: %.2f",
                                title, chartX, chartY);

                        // 如果存在狀態欄標籤，更新它
                        if (chartValueLabel != null) {
                            chartValueLabel.setText(valueText);
                        }
                    } catch (Exception e) {
                        // 忽略任何坐標轉換錯誤
                    }
                }
            }
        });
    }

    /**
     * 設置 CategoryPlot 類型圖表的交互功能
     */
    private void setupCategoryPlotInteraction(CategoryPlot plot, ChartPanel chartPanel, String title) {
        // 類別圖表的交叉線設置（柱狀圖等）
        plot.setRangeCrosshairVisible(true);
        plot.setRangeCrosshairPaint(new Color(0, 0, 0, 80));

        // 獲取渲染器
        CategoryItemRenderer renderer = plot.getRenderer();

        // 為柱狀圖添加工具提示
        if (renderer instanceof BarRenderer) {
            ((BarRenderer) renderer).setDefaultToolTipGenerator((dataset, row, column) -> {
                CategoryDataset categoryDataset = dataset;
                Number value = categoryDataset.getValue(row, column);
                String rowKey = categoryDataset.getRowKey(row).toString();
                String columnKey = categoryDataset.getColumnKey(column).toString();

                return String.format("%s - %s: %s", title, rowKey, value);
            });
        }

        // 添加鼠標監聽器
        chartPanel.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
                // 點擊事件
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                // 類別圖表的鼠標移動處理
                Point2D p = chartPanel.translateScreenToJava2D(
                        event.getTrigger().getPoint());
                Rectangle2D plotArea = chartPanel.getScreenDataArea();

                if (plotArea != null && plotArea.contains(p)) {
                    try {
                        // 轉換為數據值 - 類別圖表只能獲取到值軸(Y軸)的精確值
                        double chartY = plot.getRangeAxis().java2DToValue(
                                p.getY(), plotArea, plot.getRangeAxisEdge());

                        // 更新狀態欄或信息區域
                        String valueText = String.format("%s - 值: %.2f", title, chartY);

                        // 如果存在狀態欄標籤，更新它
                        if (chartValueLabel != null) {
                            chartValueLabel.setText(valueText);
                        }
                    } catch (Exception e) {
                        // 忽略任何坐標轉換錯誤
                    }
                }
            }
        });
    }

    /**
     * 設置鍵盤快捷鍵
     */
    private void setupKeyboardShortcuts() {
        // 獲取根面板的輸入和動作映射
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        // === 添加功能快捷鍵 ===
        // F5: 切換到市場圖表頁面
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "showMarketTab");
        actionMap.put("showMarketTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tabbedPane.setSelectedIndex(0);
            }
        });

        // F6: 切換到損益表頁面
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), "showProfitTab");
        actionMap.put("showProfitTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tabbedPane.setSelectedIndex(1);
            }
        });

        // F7: 切換到技術指標頁面
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "showIndicatorsTab");
        actionMap.put("showIndicatorsTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tabbedPane.setSelectedIndex(2);
            }
        });

        // === 信息區域快捷鍵 ===
        // Ctrl+L: 清除信息區域
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), "clearInfo");
        actionMap.put("clearInfo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (infoTextArea != null) {
                    infoTextArea.setText("");
                }
            }
        });

        // Ctrl+F: 顯示搜索對話框
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "findText");
        actionMap.put("findText", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String query = JOptionPane.showInputDialog(
                        MainView.this,
                        "請輸入要搜索的文字:",
                        "搜索信息",
                        JOptionPane.QUESTION_MESSAGE);

                if (query != null && !query.isEmpty()) {
                    searchInInfoText(query);
                }
            }
        });

        // === 圖表操作快捷鍵 ===
        // Ctrl+0: 重置所有圖表縮放
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "resetChartZoom");
        actionMap.put("resetChartZoom", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 重置當前選中的分頁中的所有圖表
                Component selectedComponent = tabbedPane.getSelectedComponent();
                if (selectedComponent instanceof JPanel) {
                    resetAllCharts((JPanel) selectedComponent);
                }
            }
        });

        // === 系統操作快捷鍵 ===
        // F1: 顯示幫助信息
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "showHelp");
        actionMap.put("showHelp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHelpDialog();
            }
        });

        // Esc: 退出全屏（如果在全屏模式）
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitFullScreen");
        actionMap.put("exitFullScreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 如果擴展功能中有全屏模式，則可以在此退出全屏
                // 例如：if (isFullScreen) { toggleFullScreen(); }
            }
        });
    }

    /**
     * 重置所有圖表的縮放
     */
    private void resetAllCharts(JPanel cardPanel) {
        try {
            // 重置XY圖表
            resetChartZoom(priceChart);
            resetChartZoom(volatilityChart);
            resetChartZoom(rsiChart);
            resetChartZoom(wapChart);
            resetChartZoom(macdChart);
            resetChartZoom(bollingerBandsChart);
            resetChartZoom(kdjChart);
            resetChartZoom(retailProfitChart);
            resetChartZoom(mainForceProfitChart);

            // 重置分類圖表（成交量）
            resetCategoryChartZoom(volumeChart);

        } catch (Exception e) {
            System.err.println("重置圖表縮放時發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 重置單個XY圖表的縮放
     */
    private void resetChartZoom(JFreeChart chart) {
        if (chart != null) {
            try {
                Plot plot = chart.getPlot();
                if (plot instanceof XYPlot) {
                    XYPlot xyPlot = (XYPlot) plot;
                    ValueAxis domainAxis = xyPlot.getDomainAxis();
                    ValueAxis rangeAxis = xyPlot.getRangeAxis();

                    if (domainAxis != null) {
                        domainAxis.setAutoRange(true);
                    }

                    if (rangeAxis != null) {
                        rangeAxis.setAutoRange(true);
                    }
                }
            } catch (Exception e) {
                System.err.println("重置XY圖表 " + chart.getTitle().getText() + " 時發生錯誤: " + e.getMessage());
            }
        }
    }

    /**
     * 重置分類圖表的縮放
     */
    private void resetCategoryChartZoom(JFreeChart chart) {
        if (chart != null) {
            try {
                Plot plot = chart.getPlot();
                if (plot instanceof CategoryPlot) {
                    CategoryPlot categoryPlot = (CategoryPlot) plot;
                    CategoryAxis domainAxis = categoryPlot.getDomainAxis();
                    ValueAxis rangeAxis = categoryPlot.getRangeAxis();

                    // CategoryAxis 沒有 setAutoRange 方法，需要使用其他方式重置
                    if (domainAxis != null) {
                        // 重置分類軸的縮放和平移
                        domainAxis.setLowerMargin(0.05); // 設置預設邊距
                        domainAxis.setUpperMargin(0.05);
                        domainAxis.setCategoryMargin(0.1);
                    }

                    if (rangeAxis != null) {
                        rangeAxis.setAutoRange(true);
                    }
                }
            } catch (Exception e) {
                System.err.println("重置分類圖表 " + chart.getTitle().getText() + " 時發生錯誤: " + e.getMessage());
            }
        }
    }

    /**
     * 顯示幫助對話框
     */
    private void showHelpDialog() {
        StringBuilder helpText = new StringBuilder();
        helpText.append("股票市場模擬系統快捷鍵指南\n\n");
        helpText.append("--- 頁面切換 ---\n");
        helpText.append("F5: 顯示市場圖表頁面\n");
        helpText.append("F6: 顯示損益表頁面\n");
        helpText.append("F7: 顯示技術指標頁面\n\n");

        helpText.append("--- 信息操作 ---\n");
        helpText.append("Ctrl+L: 清除信息區域\n");
        helpText.append("Ctrl+F: 搜索信息\n\n");

        helpText.append("--- 圖表操作 ---\n");
        helpText.append("Ctrl+0: 重置所有圖表縮放\n");
        helpText.append("滑鼠滾輪: 縮放圖表\n");
        helpText.append("拖動: 平移圖表\n\n");

        helpText.append("--- 系統操作 ---\n");
        helpText.append("F1: 顯示此幫助\n");
        helpText.append("Esc: 退出全屏模式\n");

        JTextArea textArea = new JTextArea(helpText.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));

        JOptionPane.showMessageDialog(
                this,
                scrollPane,
                "快捷鍵幫助",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * 切換明亮/暗黑主題
     */
    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;  // 切換主題狀態

        if (isDarkTheme) {
            applyDarkTheme();
        } else {
            applyLightTheme();
        }

        // 更新所有組件
        SwingUtilities.updateComponentTreeUI(this);

        // 更新圖表顏色
        updateChartsTheme(isDarkTheme);
    }

    /**
     * 應用暗黑主題
     */
    private void applyDarkTheme() {
        try {
            // 設置基本 UI 顏色
            UIManager.put("Panel.background", new Color(50, 50, 50));
            UIManager.put("Panel.foreground", new Color(220, 220, 220));
            UIManager.put("Label.foreground", new Color(220, 220, 220));
            UIManager.put("Button.background", new Color(70, 70, 70));
            UIManager.put("Button.foreground", new Color(220, 220, 220));
            UIManager.put("TextField.background", new Color(60, 60, 60));
            UIManager.put("TextField.foreground", new Color(220, 220, 220));
            UIManager.put("TextArea.background", new Color(60, 60, 60));
            UIManager.put("TextArea.foreground", new Color(220, 220, 220));
            UIManager.put("ComboBox.background", new Color(70, 70, 70));
            UIManager.put("ComboBox.foreground", new Color(220, 220, 220));
            UIManager.put("TabbedPane.background", new Color(50, 50, 50));
            UIManager.put("TabbedPane.foreground", new Color(220, 220, 220));
            UIManager.put("TabbedPane.selected", new Color(80, 80, 80));
            UIManager.put("ScrollPane.background", new Color(50, 50, 50));
            UIManager.put("Table.background", new Color(60, 60, 60));
            UIManager.put("Table.foreground", new Color(220, 220, 220));
            UIManager.put("Table.selectionBackground", new Color(100, 100, 100));
            UIManager.put("Table.selectionForeground", new Color(255, 255, 255));
            UIManager.put("Table.gridColor", new Color(90, 90, 90));

            // 設置文本區域
            infoTextArea.setBackground(new Color(60, 60, 60));
            infoTextArea.setForeground(new Color(220, 220, 220));

            // 設置狀態欄
            chartValueLabel.setForeground(new Color(220, 220, 220));

        } catch (Exception e) {
            System.err.println("應用暗黑主題失敗: " + e.getMessage());
        }
    }

    /**
     * 應用明亮主題
     */
    private void applyLightTheme() {
        try {
            // 重置為默認顏色
            UIManager.put("Panel.background", UIManager.getDefaults().getColor("Panel.background"));
            UIManager.put("Panel.foreground", UIManager.getDefaults().getColor("Panel.foreground"));
            UIManager.put("Label.foreground", UIManager.getDefaults().getColor("Label.foreground"));
            UIManager.put("Button.background", UIManager.getDefaults().getColor("Button.background"));
            UIManager.put("Button.foreground", UIManager.getDefaults().getColor("Button.foreground"));
            UIManager.put("TextField.background", UIManager.getDefaults().getColor("TextField.background"));
            UIManager.put("TextField.foreground", UIManager.getDefaults().getColor("TextField.foreground"));
            UIManager.put("TextArea.background", UIManager.getDefaults().getColor("TextArea.background"));
            UIManager.put("TextArea.foreground", UIManager.getDefaults().getColor("TextArea.foreground"));
            UIManager.put("ComboBox.background", UIManager.getDefaults().getColor("ComboBox.background"));
            UIManager.put("ComboBox.foreground", UIManager.getDefaults().getColor("ComboBox.foreground"));
            UIManager.put("TabbedPane.background", UIManager.getDefaults().getColor("TabbedPane.background"));
            UIManager.put("TabbedPane.foreground", UIManager.getDefaults().getColor("TabbedPane.foreground"));
            UIManager.put("TabbedPane.selected", UIManager.getDefaults().getColor("TabbedPane.selected"));
            UIManager.put("ScrollPane.background", UIManager.getDefaults().getColor("ScrollPane.background"));
            UIManager.put("Table.background", UIManager.getDefaults().getColor("Table.background"));
            UIManager.put("Table.foreground", UIManager.getDefaults().getColor("Table.foreground"));
            UIManager.put("Table.selectionBackground", UIManager.getDefaults().getColor("Table.selectionBackground"));
            UIManager.put("Table.selectionForeground", UIManager.getDefaults().getColor("Table.selectionForeground"));
            UIManager.put("Table.gridColor", UIManager.getDefaults().getColor("Table.gridColor"));

            // 設置文本區域
            infoTextArea.setBackground(new Color(250, 250, 250));
            infoTextArea.setForeground(Color.BLACK);

            // 設置狀態欄
            chartValueLabel.setForeground(Color.BLACK);

        } catch (Exception e) {
            System.err.println("應用明亮主題失敗: " + e.getMessage());
        }
    }

    /**
     * 更新圖表主題
     */
    private void updateChartsTheme(boolean isDark) {
        // 更新股價圖
        updateChartTheme(priceChart, isDark);

        // 更新波動性圖
        updateChartTheme(volatilityChart, isDark);

        // 更新RSI圖
        updateChartTheme(rsiChart, isDark);

        // 更新成交量圖
        updateChartTheme(volumeChart, isDark);

        // 更新WAP圖
        updateChartTheme(wapChart, isDark);

        // 更新損益圖
        updateChartTheme(retailProfitChart, isDark);
        updateChartTheme(mainForceProfitChart, isDark);
    }

    /**
     * 更新單個圖表的主題
     */
    private void updateChartTheme(JFreeChart chart, boolean isDark) {
        if (chart == null) {
            return;
        }

        // 設置圖表背景
        Color bgColor = isDark ? new Color(50, 50, 50) : Color.WHITE;
        Color fgColor = isDark ? new Color(220, 220, 220) : Color.BLACK;
        Color gridColor = isDark ? new Color(80, 80, 80) : new Color(220, 220, 220);

        chart.setBackgroundPaint(bgColor);

        // 設置標題
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(fgColor);
        }

        // 設置圖例
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(bgColor);
            chart.getLegend().setItemPaint(fgColor);
        }

        // 根據圖表類型設置繪圖區
        if (chart.getPlot() instanceof XYPlot) {
            XYPlot plot = (XYPlot) chart.getPlot();
            plot.setBackgroundPaint(bgColor);
            plot.setDomainGridlinePaint(gridColor);
            plot.setRangeGridlinePaint(gridColor);
            plot.getDomainAxis().setLabelPaint(fgColor);
            plot.getRangeAxis().setLabelPaint(fgColor);
            plot.getDomainAxis().setTickLabelPaint(fgColor);
            plot.getRangeAxis().setTickLabelPaint(fgColor);
        } else if (chart.getPlot() instanceof CategoryPlot) {
            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            plot.setBackgroundPaint(bgColor);
            plot.setDomainGridlinePaint(gridColor);
            plot.setRangeGridlinePaint(gridColor);
            plot.getDomainAxis().setLabelPaint(fgColor);
            plot.getRangeAxis().setLabelPaint(fgColor);
            plot.getDomainAxis().setTickLabelPaint(fgColor);
            plot.getRangeAxis().setTickLabelPaint(fgColor);
        }

        // 發送圖表改變事件，強制重繪
        chart.fireChartChanged();
    }

    /**
     * 切換圖表網格線
     */
    private void toggleGridLines(boolean show) {
        if (priceChart != null && priceChart.getPlot() instanceof XYPlot) {
            XYPlot plot = (XYPlot) priceChart.getPlot();
            plot.setDomainGridlinesVisible(show);
            plot.setRangeGridlinesVisible(show);
        }

        // 同樣處理其他圖表...
        for (JFreeChart chart : new JFreeChart[]{volatilityChart, rsiChart, wapChart}) {
            if (chart != null && chart.getPlot() instanceof XYPlot) {
                XYPlot plot = (XYPlot) chart.getPlot();
                plot.setDomainGridlinesVisible(show);
                plot.setRangeGridlinesVisible(show);
            }
        }

        // 處理柱狀圖
        if (volumeChart != null && volumeChart.getPlot() instanceof CategoryPlot) {
            CategoryPlot plot = (CategoryPlot) volumeChart.getPlot();
            plot.setDomainGridlinesVisible(show);
            plot.setRangeGridlinesVisible(show);
        }
    }

    /**
     * 顯示關於對話框
     */
    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "股票市場模擬系統 v1.0\n"
                + "© 2025 Tara \n\n"
                + "使用 JFreeChart 和 Java Swing 開發\n"
                + "一個專業的股票市場模擬和交易練習系統",
                "關於",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
