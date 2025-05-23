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

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.xy.XYDataset;
import java.awt.event.*;
import org.jfree.chart.ChartPanel;
import java.util.ArrayList;
import javax.swing.event.ChangeListener;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.Range;

/**
 * 主視圖類別 - 負責顯示圖表和數據 作為MVC架構中的View組件
 */
public class MainView extends JFrame {

    // 圖表
    private JFreeChart priceChart;
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

    // 圖表數據
    private XYSeries priceSeries;
    private XYSeries smaSeries;
    private XYSeries volatilitySeries;
    private XYSeries rsiSeries;
    private XYSeries wapSeries;
    private DefaultCategoryDataset volumeDataset;
    private List<Color> colorList = new ArrayList<>();

    // UI組件
    private JLabel stockPriceLabel, retailCashLabel, retailStocksLabel, mainForceCashLabel, mainForceStocksLabel,
            targetPriceLabel, averageCostPriceLabel, fundsLabel, inventoryLabel, weightedAveragePriceLabel,
            chartValueLabel; //用於顯示光標位置的數值
    private JTextArea infoTextArea;
    private OrderBookView orderBookView;
    private JTabbedPane tabbedPane;

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
        tabbedPane.addTab("市場圖表", mainPanel);

        // 創建損益表分頁
        JPanel profitPanel = createProfitPanel();
        tabbedPane.addTab("損益表", profitPanel);

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
        // 創建股價圖
        XYSeriesCollection priceDataset = new XYSeriesCollection();
        priceDataset.addSeries(priceSeries);
        priceDataset.addSeries(smaSeries);
        priceChart = createXYChart("股價走勢", "時間", "價格", priceDataset);

        // 增強價格圖表顯示
        XYPlot pricePlot = priceChart.getXYPlot();

        // 自定義價格線渲染
        XYLineAndShapeRenderer priceRenderer = new XYLineAndShapeRenderer(true, false);
        priceRenderer.setSeriesPaint(0, new Color(0, 102, 204)); // 價格線顏色
        priceRenderer.setSeriesStroke(0, new BasicStroke(2.0f)); // 價格線粗細
        priceRenderer.setSeriesPaint(1, new Color(204, 0, 0));  // SMA 線顏色
        priceRenderer.setSeriesStroke(1, new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 0, new float[]{5.0f, 5.0f}, 0)); // SMA 虛線效果
        pricePlot.setRenderer(priceRenderer);

        // 設置背景色漸變
        pricePlot.setBackgroundPaint(new GradientPaint(
                0, 0, new Color(240, 240, 255),
                0, 1000, new Color(255, 255, 255)));

        // 網格線設置
        pricePlot.setDomainGridlinePaint(new Color(220, 220, 220));
        pricePlot.setRangeGridlinePaint(new Color(220, 220, 220));

        // 優化範圍軸，避免過度壓縮價格顯示
        NumberAxis rangeAxis = (NumberAxis) pricePlot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeStickyZero(false);

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
                // 獲取上一個價格點
                double previousPrice = 0;
                if (priceSeries.getItemCount() > 0) {
                    previousPrice = priceSeries.getY(priceSeries.getItemCount() - 1).doubleValue();
                }

                // 添加新價格點
                priceSeries.add(timeStep, price);
                keepSeriesWithinLimit(priceSeries, 100);

                // 以替代方式添加價格變動標記
                XYPlot plot = priceChart.getXYPlot();

                // 清除舊的標記 - 使用通用的方式
                plot.clearRangeMarkers();

                // 添加新的價格線標記
                float lineWidth = 1.0f;
                Color lineColor;
                String priceChangeText = "";

                // 決定顏色和標籤內容
                if (previousPrice > 0) {
                    double change = price - previousPrice;
                    double percentChange = change / previousPrice * 100;
                    priceChangeText = String.format(" (%.2f%%)", percentChange);

                    if (change > 0) {
                        lineColor = new Color(0, 150, 0);
                        priceChangeText = "↑" + priceChangeText;
                    } else if (change < 0) {
                        lineColor = new Color(200, 0, 0);
                        priceChangeText = "↓" + priceChangeText;
                    } else {
                        lineColor = new Color(100, 100, 100);
                    }
                } else {
                    lineColor = new Color(100, 100, 100);
                }

                // 創建價格線
                ValueMarker currentPriceMarker = new ValueMarker(price, lineColor, new BasicStroke(lineWidth));

                // 設置標籤（簡化方式，無需 RectangleInsets）
                String labelText = "價格: " + String.format("%.2f", price) + priceChangeText;
                currentPriceMarker.setLabel(labelText);
                currentPriceMarker.setLabelFont(new Font("SansSerif", Font.BOLD, 11));
                currentPriceMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
                currentPriceMarker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);

                // 添加標記
                plot.addRangeMarker(currentPriceMarker);
            }

            if (!Double.isNaN(sma)) {
                smaSeries.add(timeStep, sma);
                keepSeriesWithinLimit(smaSeries, 100);
            }
        });
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
        });
    }

    /**
     * 更新成交量圖
     */
    public void updateVolumeChart(int timeStep, int volume) {
        SwingUtilities.invokeLater(() -> {
            // 只在時間步長變化時添加新的數據點
            if (timeStep > lastTimeStep) {
                // 移除舊數據，保持最多30個數據點
                while (volumeDataset.getColumnCount() >= 30) {
                    String firstKey = (String) volumeDataset.getColumnKeys().get(0);
                    volumeDataset.removeColumn(firstKey);
                    if (!colorList.isEmpty()) {
                        colorList.remove(0);
                    }
                }

                // 添加新數據
                volumeDataset.addValue(volume, "Volume", String.valueOf(timeStep));

                // 獲取價格變化來決定顏色
                double currentPrice = 0;
                double previousPrice = 0;

                if (priceSeries.getItemCount() > 0) {
                    currentPrice = priceSeries.getY(priceSeries.getItemCount() - 1).doubleValue();
                }

                if (priceSeries.getItemCount() > 1) {
                    previousPrice = priceSeries.getY(priceSeries.getItemCount() - 2).doubleValue();
                }

                // 根據價格變動確定顏色
                Color color = Color.BLUE; // 預設為藍色
                if (currentPrice > previousPrice) {
                    color = Color.GREEN; // 價格上漲 → 綠色
                } else if (currentPrice < previousPrice) {
                    color = Color.RED; // 價格下跌 → 紅色
                }

                colorList.add(color);
                lastTimeStep = timeStep;
            } else {
                // 更新現有數據
                String columnKey = String.valueOf(timeStep);
                if (volumeDataset.getColumnKeys().contains(columnKey)) {
                    Number existingValue = volumeDataset.getValue("Volume", columnKey);
                    int newValue = existingValue != null ? existingValue.intValue() + volume : volume;
                    volumeDataset.setValue(newValue, "Volume", columnKey);
                }
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

        // 獲取或創建渲染器
        XYLineAndShapeRenderer renderer;
        if (plot.getRenderer() instanceof XYLineAndShapeRenderer) {
            renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        } else {
            renderer = new XYLineAndShapeRenderer();
            plot.setRenderer(renderer);
        }

        // 添加工具提示生成器
        renderer.setDefaultToolTipGenerator((dataset, series, item) -> {
            XYDataset xyDataset = (XYDataset) dataset;
            double x = xyDataset.getXValue(series, item);
            double y = xyDataset.getYValue(series, item);
            String seriesName = xyDataset.getSeriesKey(series).toString();

            return String.format("%s - 時間: %.0f, 值: %.2f", seriesName, x, y);
        });

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
