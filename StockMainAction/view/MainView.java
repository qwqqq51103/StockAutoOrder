package StockMainAction.view;

// import StockMainAction.model.core.MatchingMode; // 已停用舊撮合模式 UI
// import StockMainAction.model.core.Order;        // 目前此檔案未使用
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Transaction;
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
import java.text.DecimalFormat;
import java.util.Date;
// [UI] 內外盤分析大圖
import javax.swing.border.EmptyBorder;

/**
 * 主視圖類別 - 負責顯示圖表和數據 作為MVC架構中的View組件
 */
public class MainView extends JFrame {

    // [PERF] 圖表合併重繪排程參數
    private static volatile int chartFlushIntervalMs = 120; // 節能/平衡/效能 = 200/120/60
    private static volatile boolean chartDirty = false;
    private static javax.swing.Timer chartFlushTimer;
    private static final java.util.List<JFreeChart> registeredCharts = new java.util.ArrayList<>();
    // [UI] 全域字級縮放
    private static volatile float globalFontSizePt = 13f;

    // 參照模型（供工具列與事件模式參數下發）
    private StockMarketModel model;
    private javax.swing.Timer marketStatsTimer;
    private JLabel marketStatsLabel;

    // 圖表
    private JFreeChart priceChart;
    private JFreeChart candleChart;
    private JFreeChart combinedChart; // [TradingView] K線+成交量組合圖
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
    // [CHART] VWAP 與帶
    private XYSeries vwapSeries;
    private XYSeries vwapUpperSeries;
    private XYSeries vwapLowerSeries;
    private XYSeriesCollection dsVWAP;
    private XYLineAndShapeRenderer rVWAP;
    // VWAP 累計
    private long vwapCumulativeVolume = 0L;
    private double vwapCumulativePV = 0.0; // price*volume
    private long vwapSamples = 0L; // 用於方差估計
    private double vwapMean = 0.0;
    private double vwapM2 = 0.0;
    // [CHART] 觸發點（多空）
    private XYSeries bullSignals = new XYSeries("BullSignal");
    private XYSeries bearSignals = new XYSeries("BearSignal");
    private XYSeriesCollection dsSignals;
    private XYLineAndShapeRenderer rSignals;
    // [CHART] 大單標記
    private XYSeries bigBuySeries = new XYSeries("BigBuy");
    private XYSeries bigSellSeries = new XYSeries("BigSell");
    private XYSeries tickImbBuySeries = new XYSeries("TickBuyImb");
    private XYSeries tickImbSellSeries = new XYSeries("TickSellImb");
    private XYSeriesCollection dsBig;
    private XYLineAndShapeRenderer rBig;
    private int bigOrderThreshold = 500; // 可調
    // [CHART] 多週期均線容器（與 minuteToSeries 對應）
    private final Map<Integer, XYSeries> periodToSMA5 = new HashMap<>();
    private final Map<Integer, XYSeries> periodToSMA10 = new HashMap<>();
    private final Map<Integer, XYSeries> periodToEMA12 = new HashMap<>();
    // [CHART] Anchored VWAP
    private XYSeries avwapSeries = new XYSeries("AVWAP");
    private XYSeriesCollection dsAVWAP;
    private XYLineAndShapeRenderer rAVWAP;
    private long avwapAnchorMs = -1L;
    private double avwapCumPV = 0.0; private long avwapCount = 0L;
    // [UI] 指標開關
    private boolean showSMA5 = true, showSMA10 = true, showEMA12 = true, showVWAP = true, showAVWAP = false;
    // [UI] TradingView 風格：OHLC 信息面板
    private JLabel ohlcInfoLabel;
    // [UI] 信號指示器面板
    private SignalIndicatorPanel signalPanel;
    // [CHART] Volume Profile（可見區間）
    private final java.util.List<org.jfree.chart.plot.IntervalMarker> profileMarkers = new java.util.ArrayList<>();
    private long lastProfileUpdateMs = 0L;
    // [CHART] 均線參數
    private int sma5Period = 5;
    private int sma10Period = 10;
    private int ema12Period = 12;
    private float smaLineWidth = 1.4f;
    private float emaLineWidth = 1.4f;
    // [CHART] 關鍵價位標註
    private Double openPrice = null;
    private double dayHigh = Double.NEGATIVE_INFINITY;
    private double dayLow = Double.POSITIVE_INFINITY;
    private org.jfree.chart.plot.ValueMarker openMarker;
    private org.jfree.chart.plot.ValueMarker highMarker;
    private org.jfree.chart.plot.ValueMarker lowMarker;
    // [UX] 量尺（十字線差值）
    private Double anchorXMs = null;
    private Double anchorPrice = null;
    private boolean measuring = false;
    // [ANALYTICS] 內外盤參數同步與連續窗判定
    private volatile int lastInPctFromOB = -1;
    private volatile long lastInVolFromOB = 0, lastOutVolFromOB = 0;
    private int consecutiveRequired = 2;
    private int effectiveThreshold = 65;
    private int consecIn = 0, consecOut = 0;
    private final java.util.Deque<Double> priceWindow = new java.util.ArrayDeque<>();
    private int priceWindowSize = 30;
    // K線疊加指標系列
    private XYSeries sma5Series;
    private XYSeries sma10Series;
    private XYSeries sma20Series;
    private XYSeries ema12Series;
    private XYSeries ema26Series;
    private XYSeries bollUSeries;
    private XYSeries bollMSeries;
    private XYSeries bollLSeries;
    // 主圖成交量覆蓋
    private XYSeries volumeOverlaySeries;
    private NumberAxis volumeAxis;
    // 疊加資料集與渲染器（常駐，僅切換可見性以減少閃爍）
    private XYSeriesCollection dsSMA5, dsSMA10, dsSMA20, dsEMA12, dsEMA26, dsBOLL, dsVOL, dsMACD;
    private XYLineAndShapeRenderer rSMA5, rSMA10, rSMA20, rEMA12, rEMA26, rBOLL, rMACD;
    private XYBarRenderer rVOL;
    // 自適應效能控制與指標點數上限
    private volatile long kOverlayLastRecomputeMs = 0L;
    private volatile int kOverlayMinIntervalMs = 120; // 50~300ms 動態調整
    // [PERF] K線/指標/UI 節流狀態
    private volatile long overlayLastXMs = Long.MIN_VALUE;          // 最近一次處理的K線時間（ms）
    private volatile long domainLastUpdateMs = 0L;                  // 最近一次更新域軸時間
    private volatile long domainLastXMs = Long.MIN_VALUE;           // 最近一次域軸所對應的K線時間
    private volatile long ohlcInfoLastUpdateMs = 0L;                // OHLC info label 節流
    private volatile long ohlcInfoLastXMs = Long.MIN_VALUE;
    private volatile double ema12PrevForCurrent = Double.NaN;       // 當前K線的 EMA 計算所用的「前一根」EMA
    private int indicatorMaxPoints = 600;
    // 副軸（同一張 K 線圖上顯示 MACD/KDJ）
    private NumberAxis macdAxis;
    private NumberAxis kdjAxis;
    private JComboBox<String> perfModeCombo;

    // 圖表數據
    private XYSeries priceSeries;
    private OHLCSeries ohlcSeries;
    private XYSeries smaSeries;
    private XYSeries volatilitySeries;
    private XYSeries rsiSeries;
    private XYSeries wapSeries;
    private DefaultCategoryDataset volumeDataset;
    private DefaultCategoryDataset volumeMADataset; // 成交量均線
    // [TradingView] 成交量XY數據（用於組合圖）
    private XYSeries volumeXYSeries;
    private XYSeries volumeMA5Series;  // 成交量MA5
    private XYSeries volumeMA10Series; // 成交量MA10
    // K線多週期管理（支持1秒到60分鐘）
    private final int[] klineMinutes = new int[]{1, 5, 10, 30, 60};
    private final int[] klineSeconds = new int[]{1, 10, 30, 60};  // 新增1秒週期
    private final Map<Integer, OHLCSeries> minuteToSeries = new HashMap<>();
    private final Map<Integer, OHLCSeriesCollection> minuteToCollection = new HashMap<>();
    // [限制式週期切換] 每個週期獨立的成交量數據
    private final Map<Integer, XYSeries> periodToVolume = new HashMap<>();
    private final Map<Integer, XYSeries> periodToVolumeMA5 = new HashMap<>();
    private final Map<Integer, XYSeries> periodToVolumeMA10 = new HashMap<>();
    private int currentKlineMinutes = -1;  // 固定為1秒K線
    
    // [限制式週期切換] 週期切換鏈（已停用，保留代碼以防未來需要）
    // 注意：週期已固定為1秒，不再支持切換
    private final int[] periodChain = new int[]{-1, -10, -30, -60, 5, 10, 30, 60};
    private final String[] periodNames = new String[]{"1秒", "10秒", "30秒", "1分", "5分", "10分", "30分", "60分"};
    private int currentPeriodIndex = 0;  // 固定在1秒（索引0）
    
    // [K線自動跟隨] 控制K線圖是否自動跟隨最新數據
    private boolean autoFollowLatest = true;  // 預設啟用自動跟隨
    private volatile int visibleCandles = 20;  // 預設顯示最近20根K線（避免長時間變成一條線）
    
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
    // [UI] 市場資訊分頁 + 內外盤分析分頁
    private JTabbedPane infoTabs; // [UI]
    private InOutAnalyticsPanel inOutPanel; // [UI]
    private TapePanel tapePanel; // [UI] Tape
    // 散戶資訊表
    private JTable retailInfoTable;
    private javax.swing.table.DefaultTableModel retailInfoTableModel;
    // 市場參與者資訊表（主力/做市/噪音/散戶/個人）
    private JTable traderInfoTable;
    private javax.swing.table.DefaultTableModel traderInfoTableModel;

    // 儲存最後一次更新的時間步長
    private int lastTimeStep = -1;
    private boolean isDarkTheme = false;  // 預設為明亮主題

    private final int maxDataPoints = 100; // 限制圖表數據點數量
    // 新增：K線與成交量最大保留數，避免無限增長
    private int maxKlineBars = 600;
    private int maxVolumeColumns = 60;
    // （移除）視窗控制與效能優化欄位

    /**
     * 構造函數
     */
    public MainView() {
        // [UI] FlatLaf / Fallback + 全域字型
        setupLookAndFeelAndFonts();
        initializeChartData();
        initializeUI();
        applyCandleDomainWindow();
        // 啟動自動效能偵測（3秒內估算最佳參數）
        autoTunePerformance();
        // 啟動市場指標摘要刷新（每秒）
        try {
            marketStatsTimer = new javax.swing.Timer(1000, e -> refreshMarketStats());
            marketStatsTimer.start();
        } catch (Exception ignore) {}
    }

    // [UI] 設定 LAF 與全域字型（JhengHei UI 13pt），FlatLaf 若不可用則使用系統 LAF
    private void setupLookAndFeelAndFonts() {
        try {
            try {
                Class<?> laf = Class.forName("com.formdev.flatlaf.FlatLightLaf");
                laf.getMethod("setup").invoke(null);
            } catch (Throwable noFlat) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Throwable ignore) {}
        try {
            applyGlobalUIFont(new Font("Microsoft JhengHei UI", Font.PLAIN, (int) globalFontSizePt)); // [UI]
        } catch (Throwable ignore) {}
    }

    // [UI] 套用全域字型
    private void applyGlobalUIFont(Font font) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object val = UIManager.get(key);
            if (val instanceof Font) {
                UIManager.put(key, font);
            }
        }
        SwingUtilities.updateComponentTreeUI(this);
    }

    /**
     * 初始化圖表數據
     */
    private void initializeChartData() {
        // 初始化數據系列
        priceSeries = new XYSeries("股價"); priceSeries.setMaximumItemCount(2000);
        smaSeries = new XYSeries("SMA"); smaSeries.setMaximumItemCount(2000);
        volatilitySeries = new XYSeries("波動性"); volatilitySeries.setMaximumItemCount(2000);
        rsiSeries = new XYSeries("RSI"); rsiSeries.setMaximumItemCount(2000);
        wapSeries = new XYSeries("加權平均價格"); wapSeries.setMaximumItemCount(2000);

        // 初始化MACD數據系列
        macdLineSeries = new XYSeries("MACD線"); macdLineSeries.setMaximumItemCount(2000);
        macdSignalSeries = new XYSeries("信號線"); macdSignalSeries.setMaximumItemCount(2000);
        macdHistogramSeries = new XYSeries("MACD柱狀圖"); macdHistogramSeries.setMaximumItemCount(2000);

        // 初始化布林帶數據系列
        bollingerUpperSeries = new XYSeries("上軌"); bollingerUpperSeries.setMaximumItemCount(2000);
        bollingerMiddleSeries = new XYSeries("中軌"); bollingerMiddleSeries.setMaximumItemCount(2000);
        bollingerLowerSeries = new XYSeries("下軌"); bollingerLowerSeries.setMaximumItemCount(2000);

        // 初始化KDJ數據系列
        kSeries = new XYSeries("K值"); kSeries.setMaximumItemCount(2000);
        dSeries = new XYSeries("D值"); dSeries.setMaximumItemCount(2000);
        jSeries = new XYSeries("J值"); jSeries.setMaximumItemCount(2000);

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
        // [UI] 工具列：主題切換 / 字級縮放 / 效能模式
        JToolBar toolBar = createTopToolBar(); // [UI]
        JPanel withToolbar = new JPanel(new BorderLayout());
        withToolbar.add(toolBar, BorderLayout.NORTH);
        withToolbar.add(mainPanel, BorderLayout.CENTER);
        tabbedPane.addTab("市場圖表", withToolbar);

        // 創建損益表分頁
        JPanel profitPanel = createProfitPanel();
        tabbedPane.addTab("損益表", profitPanel);

        // 新增：散戶資訊分頁
        JPanel retailPanel = createRetailInfoPanel();
        tabbedPane.addTab("散戶資訊", retailPanel);

        // 新增：市場參與者分頁（包含主力/做市商/噪音/散戶/個人）
        JPanel traderPanel = createTraderInfoPanel();
        tabbedPane.addTab("市場參與者", traderPanel);

        // 精簡：移除技術指標分頁

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

        // （移除）視窗範圍與顯示控制

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

    // [UI] 建立頂部工具列
    private JToolBar createTopToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        // 主題切換
        JButton themeBtn = new JButton("主題"); // [UI]
        themeBtn.addActionListener(e -> toggleTheme());
        bar.add(themeBtn);
        bar.addSeparator();

        // 字級縮放
        JButton fontMinus = new JButton("A-");
        JButton fontPlus = new JButton("A+");
        JButton fontReset = new JButton("A");
        fontMinus.addActionListener(e -> {
            globalFontSizePt = Math.max(10f, globalFontSizePt - 1f);
            applyGlobalUIFont(new Font("Microsoft JhengHei UI", Font.PLAIN, (int) globalFontSizePt));
        });
        fontPlus.addActionListener(e -> {
            globalFontSizePt = Math.min(20f, globalFontSizePt + 1f);
            applyGlobalUIFont(new Font("Microsoft JhengHei UI", Font.PLAIN, (int) globalFontSizePt));
        });
        fontReset.addActionListener(e -> {
            globalFontSizePt = 13f;
            applyGlobalUIFont(new Font("Microsoft JhengHei UI", Font.PLAIN, (int) globalFontSizePt));
        });
        bar.add(fontMinus);
        bar.add(fontPlus);
        bar.add(fontReset);
        bar.addSeparator();

        // 效能模式
        JComboBox<String> perfCombo = new JComboBox<>(new String[]{"節能", "平衡", "效能"}); // [PERF]
        perfCombo.setSelectedIndex(1);
        perfCombo.addActionListener(e -> {
            String mode = (String) perfCombo.getSelectedItem();
            applyPerfMode(mode); // [PERF]
            applyPerfModeOverlay(mode); // [PERF]
        });
        bar.add(new JLabel("效能:"));
        bar.add(perfCombo);

        // 指令面板 (Ctrl+K)
        JButton cmdBtn = new JButton("命令(Ctrl+K)"); // [UX]
        cmdBtn.addActionListener(e -> showCommandPalette());
        bar.addSeparator();
        bar.add(cmdBtn);

        // [UX] 一鍵重置視窗
        JButton resetBtn = new JButton("重置視窗");
        resetBtn.addActionListener(e -> {
            try {
                resetAllCharts((JPanel) tabbedPane.getSelectedComponent());
                scheduleChartFlush();
            } catch (Exception ignore) {}
        });
        bar.add(resetBtn);

        bar.addSeparator();
        // 市價滑價保護帶（0-50%）
        bar.add(new JLabel("滑價上限%:"));
        JSpinner spSlip = new JSpinner(new SpinnerNumberModel(10, 0, 50, 1));
        JButton btnSlipApply = new JButton("套用滑價");
        btnSlipApply.addActionListener(e -> {
            try {
                int v = (Integer) spSlip.getValue();
                if (model != null && model.getOrderBook() != null) {
                    model.getOrderBook().setMaxMarketSlippageRatio(v / 100.0);
                    appendToInfoArea("已更新市價單滑價上限為 " + v + "%", InfoType.SYSTEM);
                }
            } catch (Exception ignore) {}
        });
        bar.add(spSlip);
        bar.add(btnSlipApply);

        // Replace Interval（主力撤換間隔 ticks）
        bar.add(new JLabel("撤換間隔:"));
        JSpinner spRepl = new JSpinner(new SpinnerNumberModel(10, 1, 200, 1));
        JButton btnRepl = new JButton("套用撤換");
        btnRepl.addActionListener(e -> {
            try {
                int v = (Integer) spRepl.getValue();
                if (model != null && model.getMainForce() != null) {
                    model.getMainForce().setReplaceIntervalTicks(v);
                    appendToInfoArea("已更新主力撤換間隔為 " + v + " ticks", InfoType.SYSTEM);
                }
            } catch (Exception ignore) {}
        });
        bar.add(spRepl);
        bar.add(btnRepl);
        bar.addSeparator();
        // [UI] 事件模式/門檻同步（全域）
        bar.add(new JLabel("事件:"));
        JComboBox<String> evMode = new JComboBox<>(new String[]{"一般","新聞","財報"});
        JSpinner spWin = new JSpinner(new SpinnerNumberModel(60, 10, 600, 10));
        JSpinner spCon = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        JSpinner spTh = new JSpinner(new SpinnerNumberModel(65, 30, 95, 1));
        JButton applyEv = new JButton("套用");
        bar.add(evMode); bar.add(new JLabel("窗")); bar.add(spWin); bar.add(new JLabel("連")); bar.add(spCon); bar.add(new JLabel("%")); bar.add(spTh); bar.add(applyEv);
        applyEv.addActionListener(e -> {
            try {
                int w = (Integer) spWin.getValue();
                int c = (Integer) spCon.getValue();
                int t = (Integer) spTh.getValue();
                String m = (String) evMode.getSelectedItem();
                if (orderBookView != null) orderBookView.applyParams(w, c, t, m);
                // 下發到模型：事件模式影響（門檻提升與倉位係數）
                if (model != null) {
                    model.setEventParams(w, c, t, m);
                }
                if (inOutPanel != null) {
                    int eff = t; if ("新聞".equals(m)) eff = Math.min(95, t+5); if ("財報".equals(m)) eff = Math.min(95, t+10);
                    inOutPanel.setParams(w, c, t, m, eff);
                }
            } catch (Exception ignore) {}
        });

        bar.addSeparator();
        // [UI] K線週期已固定為1秒，週期切換UI已移除
        
        // [K線自動跟隨] 自動跟隨/顯示全部 切換按鈕
        bar.add(new JLabel("K線視圖:"));
        JButton followBtn = new JButton(autoFollowLatest ? "🎯 自動跟隨" : "📊 顯示全部");
        // [UX] 可調顯示根數（追隨模式）
        JSpinner spVisible = new JSpinner(new SpinnerNumberModel(20, 10, 200, 5));
        spVisible.setToolTipText("追隨模式：只顯示最近 N 根K線（可調）");
        spVisible.setEnabled(autoFollowLatest);
        followBtn.setToolTipText(autoFollowLatest ? ("當前自動跟隨最近" + visibleCandles + "根K線，點擊切換到顯示全部") : "當前顯示全部K線，點擊切換到自動跟隨");
        spVisible.addChangeListener(e -> {
            try {
                visibleCandles = Math.max(5, Math.min(500, (Integer) spVisible.getValue()));
                if (autoFollowLatest) applyCandleDomainWindow();
                scheduleChartFlush();
            } catch (Exception ignore) {}
        });
        followBtn.addActionListener(e -> {
            autoFollowLatest = !autoFollowLatest;
            followBtn.setText(autoFollowLatest ? "🎯 自動跟隨" : "📊 顯示全部");
            spVisible.setEnabled(autoFollowLatest);
            followBtn.setToolTipText(autoFollowLatest ? ("當前自動跟隨最近" + visibleCandles + "根K線，點擊切換到顯示全部") : "當前顯示全部K線，點擊切換到自動跟隨");
            
            if (!autoFollowLatest) {
                // 切換到顯示全部：重置域軸範圍
                resetCandleDomainToAll();
            } else {
                // 切換到自動跟隨：應用最近N根的域窗口
                applyCandleDomainWindow();
            }
            
            appendToInfoArea("已切換到" + (autoFollowLatest ? "自動跟隨模式" : "顯示全部模式"), InfoType.SYSTEM);
        });
        bar.add(followBtn);
        bar.add(new JLabel("最近"));
        bar.add(spVisible);
        bar.add(new JLabel("根"));
        
        bar.addSeparator();
        // [UI] 均線設定面板
        JButton maBtn = new JButton("指標設定");
        maBtn.addActionListener(e -> showMaSettingsDialog());
        bar.add(maBtn);

        return bar;
    }

    // 供控制器/外部注入模型引用
    public void setModel(StockMarketModel model) {
        this.model = model;
    }
    
    // [K線自動跟隨] 應用域窗口：只顯示最近N根K線
    private void applyCandleDomainWindow() {
        try {
            OHLCSeries series = minuteToSeries.get(currentKlineMinutes);
            if (series == null || series.getItemCount() == 0) return;
            
            int count = series.getItemCount();
            int nVisible = Math.max(5, Math.min(500, visibleCandles));
            if (count <= nVisible) {
                // 如果K線數量不足，顯示全部
                resetCandleDomainToAll();
                return;
            }
            
            // 取最後N根K線的時間範圍
            OHLCItem firstVisible = (OHLCItem) series.getDataItem(count - nVisible);
            OHLCItem lastVisible = (OHLCItem) series.getDataItem(count - 1);
            
            long startMs = firstVisible.getPeriod().getFirstMillisecond();
            long endMs = lastVisible.getPeriod().getLastMillisecond();
            
            // 設置域軸範圍
            if (combinedChart != null && combinedChart.getPlot() instanceof org.jfree.chart.plot.CombinedDomainXYPlot) {
                org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
                    (org.jfree.chart.plot.CombinedDomainXYPlot) combinedChart.getPlot();
                
                // domainAxis 實際可能是 DateAxis（不是 NumberAxis），用 ValueAxis 才能通用
                org.jfree.chart.axis.ValueAxis domainAxis = (org.jfree.chart.axis.ValueAxis) combinedPlot.getDomainAxis();
                if (domainAxis != null) {
                    domainAxis.setRange(startMs, endMs);
                    domainAxis.setAutoRange(false);
                }
            }
            
        } catch (Exception e) {
            // 忽略錯誤
        }
    }
    
    // [K線自動跟隨] 重置域軸：顯示全部K線
    private void resetCandleDomainToAll() {
        try {
            if (combinedChart != null && combinedChart.getPlot() instanceof org.jfree.chart.plot.CombinedDomainXYPlot) {
                org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
                    (org.jfree.chart.plot.CombinedDomainXYPlot) combinedChart.getPlot();
                
                org.jfree.chart.axis.ValueAxis domainAxis = (org.jfree.chart.axis.ValueAxis) combinedPlot.getDomainAxis();
                if (domainAxis != null) {
                    domainAxis.setAutoRange(true);
                }
            }
        } catch (Exception e) {
            // 忽略錯誤
        }
    }
    
    // [修復VWAP] 重置VWAP累積變量
    private void resetVWAPAccumulators() {
        try {
            vwapCumulativeVolume = 0L;
            vwapCumulativePV = 0.0;
            vwapSamples = 0L;
            vwapMean = 0.0;
            vwapM2 = 0.0;
            
            // 清空VWAP系列數據，準備從新週期重新計算
            if (vwapSeries != null) {
                vwapSeries.clear();
            }
            if (vwapUpperSeries != null) {
                vwapUpperSeries.clear();
            }
            if (vwapLowerSeries != null) {
                vwapLowerSeries.clear();
            }
            
            appendToInfoArea("已重置VWAP累積變量", InfoType.SYSTEM);
            
        } catch (Exception e) {
            appendToInfoArea("重置VWAP失敗: " + e.getMessage(), InfoType.ERROR);
        }
    }
    
    // [修復域軸壓縮] 切換週期時重置域軸範圍
    private void resetDomainAxisForPeriod(int period) {
        try {
            if (combinedChart == null || 
                !(combinedChart.getPlot() instanceof org.jfree.chart.plot.CombinedDomainXYPlot)) {
                return;
            }
            
            org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
                (org.jfree.chart.plot.CombinedDomainXYPlot) combinedChart.getPlot();
            
            org.jfree.chart.axis.ValueAxis domainAxis = (org.jfree.chart.axis.ValueAxis) combinedPlot.getDomainAxis();
            if (domainAxis == null) return;
            
            // 獲取當前週期的K線數據
            OHLCSeries series = minuteToSeries.get(period);
            if (series == null || series.getItemCount() == 0) {
                // 沒有數據，使用自動範圍
                domainAxis.setAutoRange(true);
                appendToInfoArea("域軸已重置為自動範圍（無數據）", InfoType.SYSTEM);
                return;
            }
            
            int count = series.getItemCount();
            
            // 如果數據量少，顯示全部
            int nVisible = Math.max(5, Math.min(500, visibleCandles));
            if (count <= nVisible) {
                OHLCItem first = (OHLCItem) series.getDataItem(0);
                OHLCItem last = (OHLCItem) series.getDataItem(count - 1);
                
                long startMs = first.getPeriod().getFirstMillisecond();
                long endMs = last.getPeriod().getLastMillisecond();
                
                // 計算週期的毫秒數，添加一些邊距
                int periodSeconds = period < 0 ? -period : period * 60;
                long periodMs = periodSeconds * 1000L;
                long margin = periodMs * 2;  // 左右各加2個週期的邊距
                
                domainAxis.setRange(startMs - margin, endMs + margin);
                domainAxis.setAutoRange(false);
                
                appendToInfoArea(String.format("域軸已重置（顯示全部 %d 根K線）", count), InfoType.SYSTEM);
            } else {
                // 數據量多，先設置顯示全部，後續再根據模式調整
                OHLCItem first = (OHLCItem) series.getDataItem(0);
                OHLCItem last = (OHLCItem) series.getDataItem(count - 1);
                
                long startMs = first.getPeriod().getFirstMillisecond();
                long endMs = last.getPeriod().getLastMillisecond();
                
                // 計算週期的毫秒數，添加邊距
                int periodSeconds = period < 0 ? -period : period * 60;
                long periodMs = periodSeconds * 1000L;
                long margin = periodMs * 2;
                
                domainAxis.setRange(startMs - margin, endMs + margin);
                domainAxis.setAutoRange(false);
                
                if (autoFollowLatest) {
                    appendToInfoArea(String.format("域軸已重置（顯示全部 %d 根K線，將自動跟隨最近%d根）", count, nVisible), InfoType.SYSTEM);
                } else {
                    appendToInfoArea(String.format("域軸已重置（顯示全部 %d 根K線）", count), InfoType.SYSTEM);
                }
            }
            
        } catch (Exception e) {
            appendToInfoArea("重置域軸失敗: " + e.getMessage(), InfoType.ERROR);
        }
    }

    // [UI] 均線設定對話框
    private void showMaSettingsDialog(){
        JCheckBox cbSma5 = new JCheckBox("SMA5", showSMA5);
        JCheckBox cbSma10 = new JCheckBox("SMA10", showSMA10);
        JCheckBox cbEma12 = new JCheckBox("EMA12", showEMA12);
        JCheckBox cbVW = new JCheckBox("VWAP", showVWAP);
        JCheckBox cbAVW = new JCheckBox("Anchored VWAP", showAVWAP);
        JSpinner spSma5 = new JSpinner(new SpinnerNumberModel(sma5Period, 2, 200, 1));
        JSpinner spSma10 = new JSpinner(new SpinnerNumberModel(sma10Period, 2, 300, 1));
        JSpinner spEma12 = new JSpinner(new SpinnerNumberModel(ema12Period, 2, 300, 1));
        JSpinner spSmaW = new JSpinner(new SpinnerNumberModel((double) smaLineWidth, 0.5, 5.0, 0.1));
        JSpinner spEmaW = new JSpinner(new SpinnerNumberModel((double) emaLineWidth, 0.5, 5.0, 0.1));
        JButton btnAnchor = new JButton("重設 AVWAP 起點(使用目前視窗起點)");
        btnAnchor.addActionListener(e -> {
            try {
                if (priceChart != null && priceChart.getPlot() instanceof XYPlot){
                    XYPlot p = (XYPlot) priceChart.getPlot();
                    Range r = p.getDomainAxis().getRange();
                    avwapAnchorMs = (long) r.getLowerBound();
                    avwapSeries.clear(); avwapCumPV = 0.0; avwapCount = 0L;
                }
            } catch (Exception ignore) {}
        });
        JPanel p = new JPanel(new GridLayout(0,2,6,6));
        p.add(cbSma5); p.add(spSma5);
        p.add(cbSma10); p.add(spSma10);
        p.add(cbEma12); p.add(spEma12);
        p.add(cbVW); p.add(new JLabel("(跟隨主圖)"));
        p.add(cbAVW); p.add(btnAnchor);
        p.add(new JLabel("SMA 線寬")); p.add(spSmaW);
        p.add(new JLabel("EMA 線寬")); p.add(spEmaW);
        int ok = JOptionPane.showConfirmDialog(this, p, "指標設定", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION){
            showSMA5 = cbSma5.isSelected(); showSMA10 = cbSma10.isSelected(); showEMA12 = cbEma12.isSelected(); showVWAP = cbVW.isSelected(); showAVWAP = cbAVW.isSelected();
            sma5Period = (Integer) spSma5.getValue();
            sma10Period = (Integer) spSma10.getValue();
            ema12Period = (Integer) spEma12.getValue();
            smaLineWidth = ((Double) spSmaW.getValue()).floatValue();
            emaLineWidth = ((Double) spEmaW.getValue()).floatValue();
            try { rSMA5.setSeriesStroke(0, new BasicStroke(smaLineWidth)); } catch (Exception ignore) {}
            try { rSMA10.setSeriesStroke(0, new BasicStroke(smaLineWidth)); } catch (Exception ignore) {}
            try { rEMA12.setSeriesStroke(0, new BasicStroke(emaLineWidth)); } catch (Exception ignore) {}
            applyIndicatorVisibility();
        }
    }

    private void applyIndicatorVisibility(){
        try {
            XYPlot p = (XYPlot) candleChart.getPlot();
            p.getRenderer(3).setSeriesVisible(0, showSMA5);
            p.getRenderer(4).setSeriesVisible(0, showSMA10);
            p.getRenderer(5).setSeriesVisible(0, showEMA12);
            p.getRenderer(1).setSeriesVisible(0, showVWAP);
            p.getRenderer(1).setSeriesVisible(1, showVWAP);
            p.getRenderer(1).setSeriesVisible(2, showVWAP);
            if (showAVWAP) {
                if (dsAVWAP == null){
                    dsAVWAP = new XYSeriesCollection();
                    dsAVWAP.addSeries(avwapSeries);
                    rAVWAP = new XYLineAndShapeRenderer(true,false);
                    rAVWAP.setSeriesPaint(0, new Color(255,87,34));
                    p.setDataset(7, dsAVWAP);
                    p.setRenderer(7, rAVWAP);
                }
            }
        } catch (Exception ignore) {}
    }

    // [CHART] 套用圖表預設（抗鋸齒、字型）
    private void applyChartDefaults(JFreeChart chart) {
        if (chart == null) return;
        try {
            chart.setAntiAlias(true);
            setChartFont(chart);
        } catch (Exception ignore) {}
    }

    // [CHART] 註冊圖表，以便集中觸發重繪
    private void registerChart(JFreeChart chart) {
        if (chart == null) return;
        synchronized (registeredCharts) {
            if (!registeredCharts.contains(chart)) {
                registeredCharts.add(chart);
            }
        }
    }

    // [CHART] 合併重繪（預設 120ms；節能/平衡/效能=200/120/60）
    public static void scheduleChartFlush() {
        chartDirty = true;
        if (chartFlushTimer == null) {
            chartFlushTimer = new javax.swing.Timer(chartFlushIntervalMs, e -> {
                if (!chartDirty) return;
                chartDirty = false;
                java.util.List<JFreeChart> copy;
                synchronized (registeredCharts) { copy = new java.util.ArrayList<>(registeredCharts); }
                for (JFreeChart c : copy) {
                    try { c.fireChartChanged(); } catch (Exception ignore) {}
                }
            });
            chartFlushTimer.setRepeats(false);
        }
        chartFlushTimer.setDelay(chartFlushIntervalMs);
        chartFlushTimer.restart();
    }

    // [PERF] 提供外部（ControlView）切換效能模式介面
    public static void applyPerfMode(String mode) {
        int ms;
        if ("節能".equals(mode)) ms = 200; else if ("效能".equals(mode)) ms = 60; else ms = 120;
        chartFlushIntervalMs = ms;
        if (chartFlushTimer != null) chartFlushTimer.setDelay(chartFlushIntervalMs);
    }

    /**
     * 創建主分頁面板
     */
    private JPanel createMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // === [TradingView] 創建組合圖表面板（K線+成交量） ===
        JPanel chartPanel = new JPanel(new BorderLayout());

        // 創建組合圖表面板（K線在上，成交量在下，已整合）
        ChartPanel combinedChartPanel = new ChartPanel(combinedChart);
        combinedChartPanel.setPreferredSize(new Dimension(800, 600));
        
        // === TradingView 風格：添加 OHLC 信息面板（疊加在圖表上） ===
        // 創建左上角的OHLC信息面板
        ohlcInfoLabel = new JLabel(" ");
        ohlcInfoLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        ohlcInfoLabel.setForeground(new Color(60, 60, 60));
        ohlcInfoLabel.setOpaque(true);
        ohlcInfoLabel.setBackground(new Color(255, 255, 255, 230));
        ohlcInfoLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        
        // 使用 JLayeredPane 實現疊加效果
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(800, 600));
        
        // 添加圖表到底層
        combinedChartPanel.setBounds(0, 0, 800, 600);
        layeredPane.add(combinedChartPanel, JLayeredPane.DEFAULT_LAYER);
        
        // 添加 OHLC 面板到頂層
        ohlcInfoLabel.setBounds(10, 10, 400, 80);
        layeredPane.add(ohlcInfoLabel, JLayeredPane.PALETTE_LAYER);
        
        // 添加 ComponentListener 來處理大小調整
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                Dimension size = layeredPane.getSize();
                combinedChartPanel.setBounds(0, 0, size.width, size.height);
                ohlcInfoLabel.setBounds(10, 10, 400, 80);
            }
        });

        // 設置圖表交互性
        setupChartInteraction(combinedChartPanel, "K線與成交量");

        // 直接添加組合圖表（使用 LayeredPane）
        chartPanel.add(layeredPane, BorderLayout.CENTER);
        
        // === [TradingView] 創建信號指示器面板（顯示在圖表下方） ===
        signalPanel = new SignalIndicatorPanel();
        chartPanel.add(signalPanel, BorderLayout.SOUTH);

        // 創建標籤面板
        JPanel labelPanel = new JPanel();
        initializeLabels(labelPanel);

        // 創建訂單簿視圖
        orderBookView = new OrderBookView();

        // 信息面板（置入分頁：市場信息 + 內外盤分析）
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        infoTextArea = new JTextArea(8, 30);
        infoTextArea.setEditable(false);
        infoTextArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        infoTextArea.setBackground(new Color(250, 250, 250));
        // 控制訊息區長度上限，避免無限增長
        infoTextArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            private void trim(){
                try{
                    int max = 10000; // 最大字元數
                    int len = infoTextArea.getDocument().getLength();
                    if (len > max){
                        infoTextArea.getDocument().remove(0, len - max);
                    }
                } catch (Exception ignore) {}
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e){ trim(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e){}
            public void changedUpdate(javax.swing.event.DocumentEvent e){ trim(); }
        });
        JScrollPane infoScrollPane = new JScrollPane(infoTextArea);
        infoScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        infoScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        JToolBar infoToolBar = new JToolBar();
        infoToolBar.setFloatable(false);
        infoToolBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        JButton clearButton = new JButton("清除");
        clearButton.addActionListener(e -> infoTextArea.setText(""));
        infoToolBar.add(clearButton);
        infoToolBar.addSeparator();
        JTextField searchField = new JTextField(10);
        JButton searchButton = new JButton("搜索");
        searchButton.addActionListener(e -> searchInInfoText(searchField.getText()));
        infoToolBar.add(new JLabel("搜索: "));
        infoToolBar.add(searchField);
        infoToolBar.add(searchButton);
        infoToolBar.addSeparator();
        // 即時市場指標摘要（In/Out、Δ、失衡、TPS、VPS）
        marketStatsLabel = new JLabel("指標: In/Out --/--  Δ --  失衡 --  TPS -- VPS --");
        marketStatsLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        infoToolBar.add(marketStatsLabel);
        infoPanel.add(infoToolBar, BorderLayout.NORTH);
        infoPanel.add(infoScrollPane, BorderLayout.CENTER);

        // 內外盤分析面板（大圖）
        inOutPanel = new InOutAnalyticsPanel();
        JPanel inOutTab = new JPanel(new BorderLayout());
        inOutTab.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        inOutTab.add(inOutPanel, BorderLayout.CENTER);

        // Tape（逐筆成交）：Aggressor/價/量/滑價/速度
        tapePanel = new TapePanel();
        JPanel tapeTab = new JPanel(new BorderLayout());
        tapeTab.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        tapeTab.add(tapePanel, BorderLayout.CENTER);

        // 分頁容器
        infoTabs = new JTabbedPane(JTabbedPane.TOP);
        infoTabs.addTab("市場信息", infoPanel);
        infoTabs.addTab("內外盤分析", inOutTab);
        infoTabs.addTab("逐筆Tape", tapeTab);

        // （移除）多週期聯動分頁

        // 將訂單簿和信息區域組合
        JSplitPane infoSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                orderBookView.getScrollPane(),
                infoTabs);
        infoSplitPane.setResizeWeight(0.7);
        infoSplitPane.setOneTouchExpandable(true);
        infoSplitPane.setDividerSize(6);

        // 將圖表和標籤區域組合
        JSplitPane chartLabelSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                chartPanel,
                labelPanel);
        chartLabelSplitPane.setResizeWeight(0.8);
        chartLabelSplitPane.setOneTouchExpandable(true);
        chartLabelSplitPane.setDividerSize(6);

        // 註冊內外盤回呼 -> 更新分析分頁
        try {
            orderBookView.setInOutListener((inVol, outVol, inPct) -> {
                if (inOutPanel != null) inOutPanel.setData(inVol, outVol, inPct);
                lastInPctFromOB = inPct; lastInVolFromOB = inVol; lastOutVolFromOB = outVol;
            });
            orderBookView.setParamListener((window, consecutive, threshold, mode, effTh) -> {
                consecutiveRequired = Math.max(1, consecutive);
                effectiveThreshold = Math.max(1, Math.min(99, effTh));
                if (inOutPanel != null) inOutPanel.setParams(window, consecutive, threshold, mode, effTh);
            });
            orderBookView.setTapeListener((buyerInitiated, price, volume, bestBid, bestAsk) -> {
                pushTapeTrade(buyerInitiated, price, volume, bestBid, bestAsk);
            });
        } catch (Exception ignore) {}

        // 將左右兩部分組合
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                chartLabelSplitPane,
                infoSplitPane);
        mainSplitPane.setResizeWeight(0.7);
        mainSplitPane.setOneTouchExpandable(true);
        mainSplitPane.setDividerSize(6);

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

    // [UX] Ctrl+K 指令面板（簡化版）
    private void showCommandPalette() {
        String[] cmds = new String[]{
                "切換主題",
                "重置圖表縮放",
                "效能: 節能",
                "效能: 平衡",
                "效能: 效能",
                "搜索資訊..."
        };
        String sel = (String) JOptionPane.showInputDialog(
                this, "指令:", "Command Palette", JOptionPane.PLAIN_MESSAGE,
                null, cmds, cmds[1]);
        if (sel == null) return;
        switch (sel) {
            case "切換主題":
                toggleTheme();
                break;
            case "重置圖表縮放":
                Component selectedComponent = tabbedPane.getSelectedComponent();
                if (selectedComponent instanceof JPanel) {
                    resetAllCharts((JPanel) selectedComponent);
                    scheduleChartFlush(); // [CHART]
                }
                break;
            case "效能: 節能": applyPerfMode("節能"); applyPerfModeOverlay("節能"); break;
            case "效能: 平衡": applyPerfMode("平衡"); applyPerfModeOverlay("平衡"); break;
            case "效能: 效能": applyPerfMode("效能"); applyPerfModeOverlay("效能"); break;
            case "搜索資訊...":
                String q = JOptionPane.showInputDialog(this, "輸入關鍵字:");
                if (q != null && !q.isEmpty()) searchInInfoText(q);
                break;
        }
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

    // 新增：市場參與者分頁（表格）
    private JPanel createTraderInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] columns = new String[]{"身分", "類別", "可用現金", "凍結現金", "可用持股", "凍結持股", "總資產", "備註"};
        traderInfoTableModel = new javax.swing.table.DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 2:
                    case 3:
                    case 6:
                        return Double.class;
                    case 4:
                    case 5:
                        return Integer.class;
                    default:
                        return String.class;
                }
            }
        };

        traderInfoTable = new JTable(traderInfoTableModel);
        traderInfoTable.setRowHeight(28);
        traderInfoTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane sp = new JScrollPane(traderInfoTable);
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
    public void updateMACDIndicator(int timeStep, double macdLine, double signalLine, double histogram) { }

    /**
     * 更新布林帶指標數據
     */
    public void updateBollingerBandsIndicator(int timeStep, double upperBand, double middleBand, double lowerBand) { }

    /**
     * 更新KDJ指標數據
     */
    public void updateKDJIndicator(int timeStep, double kValue, double dValue, double jValue) { }

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
                        // 批次更新關閉通知，減少重繪
                        boolean prev = series.getNotify();
                        series.setNotify(false);

                        // 移除多餘的數據點
                        while (series.getItemCount() > maxPoints) {
                            series.remove(0);
                        }

                        series.setNotify(prev);
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
            // 根據週期設置不同的最大保留數量
            // 1秒: 300根(5分鐘) | 10秒: 180根(30分鐘) | 30秒: 120根(1小時) | 60秒: 60根(1小時)
            int maxItems = (s == 1) ? 300 : (s == 10) ? 180 : (s == 30) ? 120 : 60;
            try { srs.setMaximumItemCount(maxItems); } catch (Exception ignore) {}
            minuteToSeries.put(-s, srs); // 以負值 key 表示秒
            OHLCSeriesCollection c = new OHLCSeriesCollection();
            c.addSeries(srs);
            minuteToCollection.put(-s, c);
        }
        for (int m : klineMinutes) {
            OHLCSeries s = new OHLCSeries("K線(" + m + "分)");
            try { s.setMaximumItemCount(30); } catch (Exception ignore) {}
            minuteToSeries.put(m, s);
            OHLCSeriesCollection c = new OHLCSeriesCollection();
            c.addSeries(s);
            minuteToCollection.put(m, c);
        }
        // 預設使用 1 秒 K 線（固定週期）
        // currentKlineMinutes 已在類變量聲明時設置為 -1，不需要再次設置
        ohlcSeries = minuteToSeries.get(currentKlineMinutes);
        OHLCSeriesCollection ohlcCollection = minuteToCollection.get(currentKlineMinutes);
        candleChart = ChartFactory.createCandlestickChart("K線走勢", "時間", "價格", ohlcCollection, true);
        applyChartDefaults(candleChart); // [CHART]
        registerChart(candleChart); // [CHART]

        XYPlot candlePlot = candleChart.getXYPlot();
        
        // === TradingView 風格的蠟燭渲染器 ===
        CandlestickRenderer candleRenderer = new CandlestickRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                // 根據漲跌顯示顏色
                return super.getItemPaint(series, item);
            }
        };
        
        // 蠟燭寬度和間距設定（TradingView風格）
        try {
            candleRenderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_AVERAGE);
            candleRenderer.setAutoWidthGap(0.15); // 稍微緊湊一些
            candleRenderer.setCandleWidth(5.0); // 固定寬度
        } catch (Exception ignore) {}
        
        // TradingView 配色：紅漲綠跌（中國習慣）
        candleRenderer.setUpPaint(new Color(239, 83, 80));       // 紅色上漲
        candleRenderer.setDownPaint(new Color(38, 166, 154));    // 綠色下跌
        candleRenderer.setUseOutlinePaint(true);
        candleRenderer.setDrawVolume(false);
        
        candlePlot.setRenderer(candleRenderer);

        // === TradingView 風格的時間軸 ===
        DateAxis dateAxis = new DateAxis("時間");
        dateAxis.setDateFormatOverride(new java.text.SimpleDateFormat("HH:mm:ss"));
        dateAxis.setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 11));
        dateAxis.setLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        candlePlot.setDomainAxis(dateAxis);

        // 精簡：移除 K 線圖上的 SMA 虛線疊加

        // 準備其他疊加系列容器（先建立空系列，按勾選顯示/隱藏）
        sma5Series = new XYSeries("SMA5");
        sma10Series = new XYSeries("SMA10");
        sma20Series = new XYSeries("SMA20");
        ema12Series = new XYSeries("EMA12");
        ema26Series = new XYSeries("EMA26");
        bollUSeries = new XYSeries("BOLL_U");
        bollMSeries = new XYSeries("BOLL_M");
        bollLSeries = new XYSeries("BOLL_L");
        volumeOverlaySeries = new XYSeries("VOL");
        // [CHART] VWAP 與帶
        vwapSeries = new XYSeries("VWAP"); vwapSeries.setMaximumItemCount(2000);
        vwapUpperSeries = new XYSeries("VWAP_U"); vwapUpperSeries.setMaximumItemCount(2000);
        vwapLowerSeries = new XYSeries("VWAP_L"); vwapLowerSeries.setMaximumItemCount(2000);

        // 預先建立常駐 datasets/renderers，之後僅切換可見性，避免重附加造成閃爍
        dsSMA5 = new XYSeriesCollection(); dsSMA5.addSeries(sma5Series);
        dsSMA10 = new XYSeriesCollection(); dsSMA10.addSeries(sma10Series);
        dsSMA20 = new XYSeriesCollection(); dsSMA20.addSeries(sma20Series);
        dsEMA12 = new XYSeriesCollection(); dsEMA12.addSeries(ema12Series);
        dsEMA26 = new XYSeriesCollection(); dsEMA26.addSeries(ema26Series);
        dsBOLL = new XYSeriesCollection(); dsBOLL.addSeries(bollUSeries); dsBOLL.addSeries(bollMSeries); dsBOLL.addSeries(bollLSeries);
        dsVOL = new XYSeriesCollection(); dsVOL.addSeries(volumeOverlaySeries);
        dsMACD = new XYSeriesCollection(); dsMACD.addSeries(macdLineSeries); dsMACD.addSeries(macdSignalSeries);
        dsVWAP = new XYSeriesCollection(); dsVWAP.addSeries(vwapSeries); dsVWAP.addSeries(vwapUpperSeries); dsVWAP.addSeries(vwapLowerSeries);

        rSMA5 = new XYLineAndShapeRenderer(true, false); rSMA5.setSeriesPaint(0, new Color(255, 165, 0)); rSMA5.setDefaultSeriesVisibleInLegend(false);
        rSMA10 = new XYLineAndShapeRenderer(true, false); rSMA10.setSeriesPaint(0, new Color(0, 128, 255)); rSMA10.setDefaultSeriesVisibleInLegend(false);
        rSMA20 = new XYLineAndShapeRenderer(true, false); rSMA20.setSeriesPaint(0, new Color(128, 0, 128)); rSMA20.setDefaultSeriesVisibleInLegend(false);
        rEMA12 = new XYLineAndShapeRenderer(true, false); rEMA12.setSeriesPaint(0, new Color(0, 180, 180)); rEMA12.setDefaultSeriesVisibleInLegend(false);
        rEMA26 = new XYLineAndShapeRenderer(true, false); rEMA26.setSeriesPaint(0, new Color(180, 120, 0)); rEMA26.setDefaultSeriesVisibleInLegend(false);
        rBOLL = new XYLineAndShapeRenderer(true, false); rBOLL.setSeriesPaint(0, Color.GRAY); rBOLL.setSeriesPaint(1, Color.DARK_GRAY); rBOLL.setSeriesPaint(2, Color.GRAY); rBOLL.setDefaultSeriesVisibleInLegend(false);
        rVOL = new XYBarRenderer(1.0);
        rMACD = new XYLineAndShapeRenderer(true, false); rMACD.setSeriesPaint(0, new Color(200, 80, 0)); rMACD.setSeriesPaint(1, new Color(0, 180, 0));
        rVWAP = new XYLineAndShapeRenderer(true, false);
        rVWAP.setSeriesPaint(0, new Color(33,150,243));
        rVWAP.setSeriesPaint(1, new Color(33,150,243,120));
        rVWAP.setSeriesPaint(2, new Color(33,150,243,120));
        // 觸發點渲染（只畫點）（紅漲綠跌）
        dsSignals = new XYSeriesCollection(); dsSignals.addSeries(bullSignals); dsSignals.addSeries(bearSignals); dsSignals.addSeries(tickImbBuySeries); dsSignals.addSeries(tickImbSellSeries);
        rSignals = new XYLineAndShapeRenderer(false, true);
        rSignals.setSeriesPaint(0, new Color(239, 83, 80));    // 多頭信號：紅色（上漲）
        rSignals.setSeriesPaint(1, new Color(38, 166, 154));   // 空頭信號：綠色（下跌）
        rSignals.setSeriesPaint(2, new Color(255, 152, 0));    // Tick買盤失衡：橙色
        rSignals.setSeriesPaint(3, new Color(156, 39, 176));   // Tick賣盤失衡：紫色
        
        // 多頭信號：正三角形（指向上）▲
        java.awt.Polygon upTriangle = new java.awt.Polygon();
        upTriangle.addPoint(0, -5);   // 頂點
        upTriangle.addPoint(-4, 3);   // 左下
        upTriangle.addPoint(4, 3);    // 右下
        
        // 空頭信號：倒三角形（指向下）▼
        java.awt.Polygon downTriangle = new java.awt.Polygon();
        downTriangle.addPoint(0, 5);    // 底部頂點
        downTriangle.addPoint(-4, -3);  // 左上
        downTriangle.addPoint(4, -3);   // 右上
        
        rSignals.setSeriesShape(0, upTriangle);     // 多頭：紅色正三角形
        rSignals.setSeriesShape(1, downTriangle);   // 空頭：綠色倒三角形

        // === TradingView 風格的背景與網格 ===
        candlePlot.setBackgroundPaint(new Color(255, 255, 255));  // 純白背景
        candlePlot.setDomainGridlinePaint(new Color(240, 243, 250));  // 非常淡的藍灰色網格
        candlePlot.setRangeGridlinePaint(new Color(240, 243, 250));
        candlePlot.setDomainGridlinesVisible(true);
        candlePlot.setRangeGridlinesVisible(true);
        
        // 設定網格線樣式（虛線）
        candlePlot.setDomainGridlineStroke(new BasicStroke(
            1.0f, 
            BasicStroke.CAP_BUTT, 
            BasicStroke.JOIN_MITER, 
            10.0f, 
            new float[]{2.0f, 2.0f}, 
            0.0f
        ));
        candlePlot.setRangeGridlineStroke(new BasicStroke(
            1.0f, 
            BasicStroke.CAP_BUTT, 
            BasicStroke.JOIN_MITER, 
            10.0f, 
            new float[]{2.0f, 2.0f}, 
            0.0f
        ));

        // === TradingView 風格的價格軸 ===
        NumberAxis rangeAxis = (NumberAxis) candlePlot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeStickyZero(false);
        rangeAxis.setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 11));
        rangeAxis.setLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        rangeAxis.setNumberFormatOverride(new DecimalFormat("0.00"));  // 保留兩位小數

        // [CHART] 關鍵價位標註（開/高/低）
        try {
            openMarker = new org.jfree.chart.plot.ValueMarker(0, new Color(96,125,139), new BasicStroke(0.8f));
            openMarker.setLabel("開"); openMarker.setLabelPaint(new Color(96,125,139)); openMarker.setLabelAnchor(org.jfree.chart.ui.RectangleAnchor.TOP_LEFT); openMarker.setLabelTextAnchor(org.jfree.chart.ui.TextAnchor.TOP_LEFT);
            highMarker = new org.jfree.chart.plot.ValueMarker(0, new Color(198,40,40), new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, new float[]{4f,4f}, 0f));
            highMarker.setLabel("高"); highMarker.setLabelPaint(new Color(198,40,40)); highMarker.setLabelAnchor(org.jfree.chart.ui.RectangleAnchor.BOTTOM_LEFT); highMarker.setLabelTextAnchor(org.jfree.chart.ui.TextAnchor.BOTTOM_LEFT);
            lowMarker = new org.jfree.chart.plot.ValueMarker(0, new Color(56,142,60), new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, new float[]{4f,4f}, 0f));
            lowMarker.setLabel("低"); lowMarker.setLabelPaint(new Color(56,142,60)); lowMarker.setLabelAnchor(org.jfree.chart.ui.RectangleAnchor.TOP_LEFT); lowMarker.setLabelTextAnchor(org.jfree.chart.ui.TextAnchor.TOP_LEFT);
            candlePlot.addRangeMarker(openMarker);
            candlePlot.addRangeMarker(highMarker);
            candlePlot.addRangeMarker(lowMarker);
        } catch (Exception ignore) {}

        // [CHART] 在 dataset 1 位置加入 VWAP 與上下帶
        candlePlot.setDataset(1, dsVWAP);
        candlePlot.setRenderer(1, rVWAP);
        // [CHART] 在 dataset 2 位置加入觸發點
        candlePlot.setDataset(2, dsSignals);
        candlePlot.setRenderer(2, rSignals);
        // [CHART] 在 dataset 3/4/5 位置加入 SMA5、SMA10、EMA12
        candlePlot.setDataset(3, dsSMA5);
        candlePlot.setRenderer(3, rSMA5);
        candlePlot.setDataset(4, dsSMA10);
        candlePlot.setRenderer(4, rSMA10);
        candlePlot.setDataset(5, dsEMA12);
        candlePlot.setRenderer(5, rEMA12);
        // [CHART] 在 dataset 6 加入大單標記（紅漲綠跌）
        dsBig = new XYSeriesCollection(); dsBig.addSeries(bigBuySeries); dsBig.addSeries(bigSellSeries);
        rBig = new XYLineAndShapeRenderer(false, true);
        java.awt.Shape bigShape = new java.awt.geom.Ellipse2D.Double(-4,-4,8,8);
        rBig.setSeriesShape(0, bigShape); rBig.setSeriesPaint(0, new Color(239, 83, 80));    // 大買單：紅色（上漲）
        rBig.setSeriesShape(1, bigShape); rBig.setSeriesPaint(1, new Color(38, 166, 154));  // 大賣單：綠色（下跌）
        candlePlot.setDataset(6, dsBig);
        candlePlot.setRenderer(6, rBig);

        // === [TradingView] 創建成交量XY圖表（使用XYBarRenderer） ===
        // [限制式週期切換] 為每個週期創建獨立的成交量系列
        for (int s : klineSeconds) {
            int key = -s;
            XYSeries volSeries = new XYSeries("成交量(" + s + "秒)");
            volSeries.setMaximumItemCount(300);
            periodToVolume.put(key, volSeries);
            
            XYSeries ma5 = new XYSeries("成交量MA5");
            XYSeries ma10 = new XYSeries("成交量MA10");
            periodToVolumeMA5.put(key, ma5);
            periodToVolumeMA10.put(key, ma10);
        }
        for (int m : klineMinutes) {
            XYSeries volSeries = new XYSeries("成交量(" + m + "分)");
            volSeries.setMaximumItemCount(300);
            periodToVolume.put(m, volSeries);
            
            XYSeries ma5 = new XYSeries("成交量MA5");
            XYSeries ma10 = new XYSeries("成交量MA10");
            periodToVolumeMA5.put(m, ma5);
            periodToVolumeMA10.put(m, ma10);
        }
        
        // 設置當前週期的成交量系列
        volumeXYSeries = periodToVolume.get(currentKlineMinutes);
        volumeMA5Series = periodToVolumeMA5.get(currentKlineMinutes);
        volumeMA10Series = periodToVolumeMA10.get(currentKlineMinutes);
        
        XYSeriesCollection volumeXYDataset = new XYSeriesCollection(volumeXYSeries);
        
        // 創建成交量Plot
        NumberAxis volumeAxis = new NumberAxis("成交量");
        volumeAxis.setAutoRangeIncludesZero(true);
        volumeAxis.setTickLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 10));
        volumeAxis.setLabelFont(new Font("Microsoft JhengHei", Font.PLAIN, 11));
        
        XYPlot volumePlot = new XYPlot(volumeXYDataset, null, volumeAxis, new XYBarRenderer(0.2));
        volumePlot.setBackgroundPaint(new Color(255, 255, 255));
        volumePlot.setDomainGridlinePaint(new Color(240, 243, 250));
        volumePlot.setRangeGridlinePaint(new Color(240, 243, 250));
        
        // 成交量柱顏色渲染器（根據漲跌：紅漲綠跌）
        final OHLCSeries finalOhlcSeries = minuteToSeries.get(currentKlineMinutes);
        XYBarRenderer volumeBarRenderer = new XYBarRenderer(0.2) {
            @Override
            public Paint getItemPaint(int series, int item) {
                // 根據對應K線的漲跌決定成交量柱顏色
                try {
                    if (finalOhlcSeries != null && item < finalOhlcSeries.getItemCount()) {
                        OHLCItem ohlcItem = (OHLCItem) finalOhlcSeries.getDataItem(item);
                        if (ohlcItem != null) {
                            double close = ohlcItem.getCloseValue();
                            double open = ohlcItem.getOpenValue();
                            if (close >= open) {
                                return new Color(239, 83, 80, 180);  // 紅色上漲
                            } else {
                                return new Color(38, 166, 154, 180); // 綠色下跌
                            }
                        }
                    }
                } catch (Exception ignore) {}
                return new Color(100, 181, 246, 180); // 默認藍色
            }
        };
        volumeBarRenderer.setShadowVisible(false);
        volumeBarRenderer.setDrawBarOutline(false);
        volumeBarRenderer.setBarPainter(new org.jfree.chart.renderer.xy.StandardXYBarPainter());
        volumePlot.setRenderer(0, volumeBarRenderer);
        
        // 添加成交量MA5和MA10
        XYSeriesCollection volumeMA5Dataset = new XYSeriesCollection(volumeMA5Series);
        XYSeriesCollection volumeMA10Dataset = new XYSeriesCollection(volumeMA10Series);
        
        XYLineAndShapeRenderer volumeMA5Renderer = new XYLineAndShapeRenderer(true, false);
        volumeMA5Renderer.setSeriesPaint(0, new Color(255, 165, 0));  // 橙色
        volumeMA5Renderer.setSeriesStroke(0, new BasicStroke(1.2f));
        
        XYLineAndShapeRenderer volumeMA10Renderer = new XYLineAndShapeRenderer(true, false);
        volumeMA10Renderer.setSeriesPaint(0, new Color(138, 43, 226)); // 紫色
        volumeMA10Renderer.setSeriesStroke(0, new BasicStroke(1.2f));
        
        volumePlot.setDataset(1, volumeMA5Dataset);
        volumePlot.setRenderer(1, volumeMA5Renderer);
        volumePlot.setDataset(2, volumeMA10Dataset);
        volumePlot.setRenderer(2, volumeMA10Renderer);
        
        // === [TradingView] 使用 CombinedDomainXYPlot 組合K線和成交量 ===
        org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
            new org.jfree.chart.plot.CombinedDomainXYPlot(candlePlot.getDomainAxis());
        
        // 添加K線圖（權重7，佔70%）
        combinedPlot.add(candlePlot, 7);
        
        // 添加成交量圖（權重3，佔30%）
        combinedPlot.add(volumePlot, 3);
        
        // 設置整體間距
        combinedPlot.setGap(10.0);
        
        // 創建組合圖表
        combinedChart = new JFreeChart("K線與成交量", JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, false);
        combinedChart.setBackgroundPaint(Color.WHITE);
        applyChartDefaults(combinedChart);
        registerChart(combinedChart);
        
        // 與既有流程相容：把價格圖參考指向組合圖
        priceChart = combinedChart;

        // 初始就填一次覆蓋資料，避免啟動時只有 K 線
        try { recomputeOverlayFromOHLC(); refreshOverlayIndicators(); } catch (Exception ignore) {}
        // 創建MACD圖表
        XYSeriesCollection macdDataset = new XYSeriesCollection();
        macdDataset.addSeries(macdLineSeries);
        macdDataset.addSeries(macdSignalSeries);
        macdChart = createXYChart("MACD指標", "時間", "MACD值", macdDataset);
        applyChartDefaults(macdChart); // [CHART]
        registerChart(macdChart); // [CHART]

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
        applyChartDefaults(bollingerBandsChart); // [CHART]
        registerChart(bollingerBandsChart); // [CHART]

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
        applyChartDefaults(kdjChart); // [CHART]
        registerChart(kdjChart); // [CHART]

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
        applyChartDefaults(volatilityChart); // [CHART]
        registerChart(volatilityChart); // [CHART]

        // 創建RSI圖
        XYSeriesCollection rsiDataset = new XYSeriesCollection();
        rsiDataset.addSeries(rsiSeries);
        rsiChart = createXYChart("相對強弱指數 (RSI)", "時間", "RSI", rsiDataset);
        applyChartDefaults(rsiChart); // [CHART]
        registerChart(rsiChart); // [CHART]

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
        applyChartDefaults(wapChart); // [CHART]
        registerChart(wapChart); // [CHART]

        // 創建成交量圖 - 增強版
        volumeChart = ChartFactory.createBarChart(
                "成交量", "時間", "成交量", volumeDataset,
                PlotOrientation.VERTICAL, false, true, false
        );
        applyChartDefaults(volumeChart); // [CHART]
        registerChart(volumeChart); // [CHART]

        // 設置成交量圖的渲染器
        CategoryPlot volumeCategoryPlot = volumeChart.getCategoryPlot();
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

        volumeCategoryPlot.setRenderer(volumeRenderer);
        volumeCategoryPlot.setBackgroundPaint(new Color(250, 250, 250));

        // 設置類別軸，限制標籤顯示，避免擁擠
        CategoryAxis domainAxis = volumeCategoryPlot.getDomainAxis();
        domainAxis.setCategoryMargin(0.05);
        domainAxis.setLowerMargin(0.01);
        domainAxis.setUpperMargin(0.01);
        domainAxis.setMaximumCategoryLabelWidthRatio(0.3f);

        volumeRenderer.setBarPainter(new StandardBarPainter());
        volumeCategoryPlot.setRenderer(volumeRenderer);

        // 設置圖表字體與渲染器優化
        setChartFont(volumeChart);
        setChartFont(priceChart);

        // 統一渲染器：關閉 shapes，長線條使用 path 模式以提速
        try {
            if (candlePlot.getRenderer() instanceof XYLineAndShapeRenderer) {
                XYLineAndShapeRenderer rr = (XYLineAndShapeRenderer) candlePlot.getRenderer();
                rr.setDefaultShapesVisible(false);
                rr.setDrawSeriesLineAsPath(true);
            }
        } catch (Exception ignore) {}
    }

    // [CHART] 從現有 K 線資料建立指定週期的只讀副本圖（簡版）
    private JFreeChart copyChartForPeriod(int minutes){
        try {
            OHLCSeriesCollection col = minuteToCollection.get(minutes);
            if (col == null) {
                // 若尚未存在，臨時建立空集合避免 NPE
                OHLCSeries s = new OHLCSeries("K線("+minutes+(minutes<0?"秒":"分")+")");
                try { s.setMaximumItemCount(10); } catch (Exception ignore) {}
                col = new OHLCSeriesCollection(); col.addSeries(s);
            }
            JFreeChart cc = ChartFactory.createCandlestickChart("K("+(minutes<0?(-minutes+"秒"):(minutes+"分"))+")","時間","價格", col, false);
            applyChartDefaults(cc);
            // 設定紅漲綠跌的蠟燭顏色
            XYPlot p = cc.getXYPlot();
            org.jfree.chart.renderer.xy.CandlestickRenderer r = (org.jfree.chart.renderer.xy.CandlestickRenderer) p.getRenderer();
            r.setUpPaint(new Color(220, 20, 60));   // 上漲=紅
            r.setDownPaint(new Color(34, 139, 34)); // 下跌=綠
            // 於每張分圖加入簡版均線（SMA5/SMA10/EMA12），各自 dataset 層
            XYSeries s5 = periodToSMA5.computeIfAbsent(minutes, k -> new XYSeries("SMA5"));
            XYSeries s10 = periodToSMA10.computeIfAbsent(minutes, k -> new XYSeries("SMA10"));
            XYSeries e12 = periodToEMA12.computeIfAbsent(minutes, k -> new XYSeries("EMA12"));
            XYSeriesCollection d5 = new XYSeriesCollection(); d5.addSeries(s5);
            XYSeriesCollection d10 = new XYSeriesCollection(); d10.addSeries(s10);
            XYSeriesCollection d12 = new XYSeriesCollection(); d12.addSeries(e12);
            XYLineAndShapeRenderer rr5 = new XYLineAndShapeRenderer(true,false); rr5.setSeriesPaint(0, new Color(255,165,0)); rr5.setSeriesStroke(0, new BasicStroke(smaLineWidth));
            XYLineAndShapeRenderer rr10 = new XYLineAndShapeRenderer(true,false); rr10.setSeriesPaint(0, new Color(0,128,255)); rr10.setSeriesStroke(0, new BasicStroke(smaLineWidth));
            XYLineAndShapeRenderer rr12 = new XYLineAndShapeRenderer(true,false); rr12.setSeriesPaint(0, new Color(0,180,180)); rr12.setSeriesStroke(0, new BasicStroke(emaLineWidth));
            p.setDataset(1, d5); p.setRenderer(1, rr5);
            p.setDataset(2, d10); p.setRenderer(2, rr10);
            p.setDataset(3, d12); p.setRenderer(3, rr12);
            return cc;
        } catch (Exception e){
            return ChartFactory.createCandlestickChart("K","時間","價格", new OHLCSeriesCollection(), false);
        }
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

    // 新增：更新市場參與者資訊表
    public void updateTraderInfoTable(java.util.List<StockMainAction.model.StockMarketModel.TraderSnapshot> snapshots) {
        SwingUtilities.invokeLater(() -> {
            if (traderInfoTableModel == null) return;
            // 清空
            while (traderInfoTableModel.getRowCount() > 0) {
                traderInfoTableModel.removeRow(0);
            }
            if (snapshots == null) return;

            for (StockMainAction.model.StockMarketModel.TraderSnapshot s : snapshots) {
                if (s == null) continue;
                traderInfoTableModel.addRow(new Object[]{
                    s.traderType,
                    s.role,
                    s.availableFunds,
                    s.frozenFunds,
                    s.availableStocks,
                    s.frozenStocks,
                    s.totalAssets,
                    s.extra == null ? "" : s.extra
                });
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
        if (chart == null) return;
        
        Font titleFont = new Font("Microsoft JhengHei", Font.BOLD, 18);
        Font axisFont = new Font("Microsoft JhengHei", Font.PLAIN, 12);
        
        // 設置標題字體
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(titleFont);
        }

        // 設置坐標軸字體
        Plot plot = chart.getPlot();
        if (plot instanceof org.jfree.chart.plot.CombinedDomainXYPlot) {
            // [TradingView] 處理組合圖表
            org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
                (org.jfree.chart.plot.CombinedDomainXYPlot) plot;
            
            // 設置共享的域軸（時間軸）
            if (combinedPlot.getDomainAxis() != null) {
                combinedPlot.getDomainAxis().setLabelFont(axisFont);
                combinedPlot.getDomainAxis().setTickLabelFont(axisFont);
            }
            
            // 為每個子圖設置值軸字體
            @SuppressWarnings("unchecked")
            java.util.List<XYPlot> subplots = combinedPlot.getSubplots();
            if (subplots != null) {
                for (XYPlot subplot : subplots) {
                    if (subplot.getRangeAxis() != null) {
                        subplot.getRangeAxis().setLabelFont(axisFont);
                        subplot.getRangeAxis().setTickLabelFont(axisFont);
                    }
                }
            }
        } else if (plot instanceof XYPlot) {
            XYPlot xyPlot = (XYPlot) plot;
            if (xyPlot.getDomainAxis() != null) {
                xyPlot.getDomainAxis().setLabelFont(axisFont);
                xyPlot.getDomainAxis().setTickLabelFont(axisFont);
            }
            if (xyPlot.getRangeAxis() != null) {
                xyPlot.getRangeAxis().setLabelFont(axisFont);
                xyPlot.getRangeAxis().setTickLabelFont(axisFont);
            }
        } else if (plot instanceof CategoryPlot) {
            CategoryPlot categoryPlot = (CategoryPlot) plot;
            if (categoryPlot.getDomainAxis() != null) {
                categoryPlot.getDomainAxis().setLabelFont(axisFont);
                categoryPlot.getDomainAxis().setTickLabelFont(axisFont);
            }
            if (categoryPlot.getRangeAxis() != null) {
                categoryPlot.getRangeAxis().setLabelFont(axisFont);
                categoryPlot.getRangeAxis().setTickLabelFont(axisFont);
            }

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
                    if (series == null) return;
                    boolean prevNotify = true;
                    boolean trimmed = false;
                    try {
                        prevNotify = series.getNotify();
                        series.setNotify(false);
                    // 控制最大保留K線根數，避免無限增長
                        while (series.getItemCount() > maxKlineBars) { series.remove(0); trimmed = true; }
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
                    } finally {
                        try { series.setNotify(prevNotify); } catch (Exception ignore) {}
                    }
                    // [PERF] 若K線被裁切，對應的標誌符號（signals/big/tick imbalance）也要同步裁切，避免前面殘留幽靈點
                    if (trimmed) {
                        try { trimSignalMarkersToOhlcWindow(series); } catch (Exception ignore) {}
                    }
                } catch (Exception ignore) {
                }

                // [CHART] 另外更新多週期資料（30秒、60秒、10分、30分）
                int[] extraKeys = new int[]{-30, -60, 10, 30};
                for (int key : extraKeys) {
                    if (key == currentKlineMinutes) continue;
                    try { updateOhlcForKey(price, now, key); } catch (Exception ignore) {}
                }

                // [PERF] K線疊加指標（SMA/EMA）改為增量更新 + 節流（避免每 tick 全量 clear/add）
                try {
                    OHLCSeries s = minuteToSeries.get(currentKlineMinutes);
                    if (s != null && s.getItemCount() > 0) {
                        updateKOverlayIncremental(s);
                    }
                } catch (Exception ignore) {}

                // [CHART] 即時計算 VWAP 與上下帶（以當前窗累積）
                try {
                    vwapCumulativeVolume += 1; // 以 tick 當作單位量，若有真實量請改為 volume
                    vwapCumulativePV += price; // 以 tick 價格代替 price*volume
                    vwapSamples++;
                    double vwap = vwapCumulativePV / Math.max(1.0, vwapCumulativeVolume);
                    // Welford 單通道方差，用於動態帶寬
                    double delta = price - vwapMean; vwapMean += delta / vwapSamples; double delta2 = price - vwapMean; vwapM2 += delta * delta2;
                    double variance = vwapSamples>1 ? vwapM2 / (vwapSamples - 1) : 0.0;
                    double stdev = Math.sqrt(Math.max(0.0, variance));
                    double upper = vwap + 2 * stdev;
                    double lower = vwap - 2 * stdev;
                    long xMs;
                    if (currentKlineMinutes < 0) xMs = ((Second) period).getFirstMillisecond(); else xMs = ((Minute) period).getFirstMillisecond();
                    int idx = vwapSeries.indexOf(xMs);
                    if (idx >= 0) vwapSeries.updateByIndex(idx, vwap); else vwapSeries.add(xMs, vwap);
                    idx = vwapUpperSeries.indexOf(xMs);
                    if (idx >= 0) vwapUpperSeries.updateByIndex(idx, upper); else vwapUpperSeries.add(xMs, upper);
                    idx = vwapLowerSeries.indexOf(xMs);
                    if (idx >= 0) vwapLowerSeries.updateByIndex(idx, lower); else vwapLowerSeries.add(xMs, lower);
                    keepSeriesWithinLimit(vwapSeries, indicatorMaxPoints);
                    keepSeriesWithinLimit(vwapUpperSeries, indicatorMaxPoints);
                    keepSeriesWithinLimit(vwapLowerSeries, indicatorMaxPoints);
                } catch (Exception ignore) {}

                // [CHART] 連續窗 + 價格創新高/低訊號
                try {
                    if (!Double.isNaN(price)) {
                        priceWindow.addLast(price);
                        while (priceWindow.size() > priceWindowSize) priceWindow.removeFirst();
                        double max = Double.NEGATIVE_INFINITY, min = Double.POSITIVE_INFINITY;
                        for (Double v : priceWindow) { if (v>max) max=v; if (v<min) min=v; }
                        boolean newHigh = price >= max;
                        boolean newLow = price <= min;
                        // 以 OrderBookView 推送的 inPct 連續值作判定
                        if (lastInPctFromOB >= 0) {
                            int inPct = lastInPctFromOB; int outPct = 100 - inPct;
                            if (outPct >= effectiveThreshold) { consecOut++; consecIn = 0; } else if (inPct >= effectiveThreshold) { consecIn++; consecOut = 0; } else { consecIn = consecOut = 0; }
                            long xMs;
                            if (currentKlineMinutes < 0) xMs = ((Second) period).getFirstMillisecond(); else xMs = ((Minute) period).getFirstMillisecond();
                            if (consecOut >= consecutiveRequired && newHigh) {
                                int idx = bullSignals.indexOf(xMs);
                                if (idx >= 0) bullSignals.updateByIndex(idx, price); else bullSignals.add(xMs, price);
                                consecOut = 0;
                            }
                            if (consecIn >= consecutiveRequired && newLow) {
                                int idx = bearSignals.indexOf(xMs);
                                if (idx >= 0) bearSignals.updateByIndex(idx, price); else bearSignals.add(xMs, price);
                                consecIn = 0;
                            }
                            keepSeriesWithinLimit(bullSignals, 200);
                            keepSeriesWithinLimit(bearSignals, 200);
                        }

                    }
                } catch (Exception ignore) {}
                
                // [CHART] 買賣盤失衡檢測（Tick Imbalance）
                try {
                    if (model != null) {
                        // 取得最近 60 筆交易的買賣盤失衡度
                        double tickImb = model.getRecentTickImbalance(60);
                        
                        long xMs;
                        if (currentKlineMinutes < 0) 
                            xMs = ((Second) period).getFirstMillisecond(); 
                        else 
                            xMs = ((Minute) period).getFirstMillisecond();
                        
                        // 買盤失衡：失衡度 > 0.25（買方主動筆數遠多於賣方）
                        if (tickImb > 0.25) {
                            int idx = tickImbBuySeries.indexOf(xMs);
                            if (idx >= 0) {
                                tickImbBuySeries.updateByIndex(idx, price);
                            } else {
                                tickImbBuySeries.add(xMs, price);
                            }
                            keepSeriesWithinLimit(tickImbBuySeries, 100);
                        }
                        
                        // 賣盤失衡：失衡度 < -0.25（賣方主動筆數遠多於買方）
                        else if (tickImb < -0.25) {
                            int idx = tickImbSellSeries.indexOf(xMs);
                            if (idx >= 0) {
                                tickImbSellSeries.updateByIndex(idx, price);
                            } else {
                                tickImbSellSeries.add(xMs, price);
                            }
                            keepSeriesWithinLimit(tickImbSellSeries, 100);
                        }
                    }
                } catch (Exception ignore) {}
            }

            // [K線自動跟隨] 節流域軸更新：只在新K線或間隔到期時調整，避免長時間 setRange 造成卡頓
            if (autoFollowLatest) {
                try { maybeApplyCandleDomainWindow(); } catch (Exception ignore) {}
            }
            
            // === TradingView 風格：更新 OHLC 信息面板（顯示最新K線） ===
            try { maybeUpdateOhlcInfoLabel(); } catch (Exception ignore) {}
            
            // === [TradingView] 更新信號指示器面板 ===
            try {
                if (signalPanel != null) {
                    int bullCount = bullSignals != null ? bullSignals.getItemCount() : 0;
                    int bearCount = bearSignals != null ? bearSignals.getItemCount() : 0;
                    int bigBuyCount = bigBuySeries != null ? bigBuySeries.getItemCount() : 0;
                    int bigSellCount = bigSellSeries != null ? bigSellSeries.getItemCount() : 0;
                    int tickBuyCount = tickImbBuySeries != null ? tickImbBuySeries.getItemCount() : 0;
                    int tickSellCount = tickImbSellSeries != null ? tickImbSellSeries.getItemCount() : 0;
                    
                    signalPanel.updateAllSignals(bullCount, bearCount, bigBuyCount, bigSellCount, tickBuyCount, tickSellCount);
                }
            } catch (Exception ignore) {}
            
            scheduleChartFlush(); // [CHART]

                // 精簡：不再維護 SMA 折線資料
        });
    }

    // [CHART] 將即時價格聚合到指定 period 的 OHLCSeries（key<0=秒，>0=分）
    private void updateOhlcForKey(double price, long nowMs, int key){
        OHLCSeries series = minuteToSeries.get(key);
        if (series == null) return;
        boolean prevNotify = true;
        try {
            prevNotify = series.getNotify();
            series.setNotify(false);
        RegularTimePeriod p;
        if (key < 0) {
            int s = -key;
            long bucket = 1000L * s;
            long aligned = nowMs - (nowMs % bucket);
            p = new Second(new java.util.Date(aligned));
        } else {
            int m = key;
            long bucket = 60_000L * m;
            long aligned = nowMs - (nowMs % bucket);
            p = new Minute(new java.util.Date(aligned));
        }
        if (series.getItemCount() == 0) {
            series.add(p, price, price, price, price);
            return;
        }
        OHLCItem last = (OHLCItem) series.getDataItem(series.getItemCount()-1);
        if (last.getPeriod().equals(p)) {
            double open = last.getOpenValue();
            double high = Math.max(last.getHighValue(), price);
            double low = Math.min(last.getLowValue(), price);
            series.remove(last.getPeriod());
            series.add(p, open, high, low, price);
        } else {
            double prevClose = last.getCloseValue();
            double newOpen = prevClose;
            double newHigh = Math.max(newOpen, price);
            double newLow = Math.min(newOpen, price);
            series.add(p, newOpen, newHigh, newLow, price);
        }
        } finally {
            try { series.setNotify(prevNotify); } catch (Exception ignore) {}
        }
    }

    // [PERF] 將 marker 系列裁切到目前 OHLCSeries 的可用時間窗（避免K線前面殘留符號）
    private void trimSignalMarkersToOhlcWindow(OHLCSeries ohlc) {
        if (ohlc == null || ohlc.getItemCount() == 0) return;
        long minX = ohlcXMs((OHLCItem) ohlc.getDataItem(0));
        // 這些 series 對應到圖上的「標誌符號」（點/三角形/大單/失衡）而不是連線指標
        trimXYSeriesBeforeX(bullSignals, minX);
        trimXYSeriesBeforeX(bearSignals, minX);
        trimXYSeriesBeforeX(bigBuySeries, minX);
        trimXYSeriesBeforeX(bigSellSeries, minX);
        trimXYSeriesBeforeX(tickImbBuySeries, minX);
        trimXYSeriesBeforeX(tickImbSellSeries, minX);
    }

    private void trimXYSeriesBeforeX(XYSeries s, long minXInclusive) {
        if (s == null) return;
        try {
            // 由於資料是按時間遞增加入，直接從頭 while remove(0) 即可
            while (s.getItemCount() > 0) {
                Number x0 = s.getX(0);
                if (x0 == null) { s.remove(0); continue; }
                if (x0.longValue() < minXInclusive) s.remove(0);
                else break;
            }
        } catch (Exception ignore) {}
    }

    // [PERF] 取得 OHLCItem 的 X（毫秒）
    private long ohlcXMs(OHLCItem item) {
        try { return item.getPeriod().getFirstMillisecond(); } catch (Exception e) { return System.currentTimeMillis(); }
    }

    // [PERF] 計算某一根 K 線（以 idx 結尾）的 SMA（period<=60 的小窗）
    private double computeSMAAt(OHLCSeries s, int period, int idxInclusive) {
        if (s == null) return Double.NaN;
        int n = s.getItemCount();
        if (n <= 0) return Double.NaN;
        int end = Math.min(n - 1, Math.max(0, idxInclusive));
        int p = Math.max(1, period);
        int start = Math.max(0, end - (p - 1));
        double sum = 0.0;
        int cnt = 0;
        for (int i = start; i <= end; i++) {
            try {
                OHLCItem it = (OHLCItem) s.getDataItem(i);
                sum += it.getCloseValue();
                cnt++;
            } catch (Exception ignore) {}
        }
        if (cnt <= 0) {
            try { return ((OHLCItem) s.getDataItem(end)).getCloseValue(); } catch (Exception e) { return Double.NaN; }
        }
        return sum / cnt;
    }

    // [PERF] XYSeries：若最後一筆 X 相同則更新，否則追加（不觸發過多通知）
    private void updateOrAddXY(XYSeries series, long x, double y) {
        if (series == null || Double.isNaN(y) || Double.isInfinite(y)) return;
        try {
            int c = series.getItemCount();
            if (c > 0) {
                Number lastX = series.getX(c - 1);
                if (lastX != null && lastX.longValue() == x) {
                    series.updateByIndex(c - 1, y);
                    return;
                }
            }
            series.add(x, y, false);
        } catch (Exception ignore) {}
    }

    // [PERF] 增量更新 SMA/EMA（僅更新最後一根，且在新K線時回填上一根的最終值）
    private void updateKOverlayIncremental(OHLCSeries s) {
        if (s == null) return;
        int n = s.getItemCount();
        if (n <= 0) return;

        long nowMs = System.currentTimeMillis();
        int lastIdx = n - 1;
        OHLCItem lastItem = (OHLCItem) s.getDataItem(lastIdx);
        long xMs = ohlcXMs(lastItem);
        double close = lastItem.getCloseValue();

        boolean isNewCandle = (xMs != overlayLastXMs);
        // 只在新K線或間隔到期才更新當前K線的指標點，避免每 tick 觸發多個 dataset 事件
        boolean allowUpdateCurrent = isNewCandle || (nowMs - kOverlayLastRecomputeMs >= Math.max(50, kOverlayMinIntervalMs));

        // 批次關閉 notify，避免同一輪更新觸發多次重繪
        try { toggleOverlayNotify(false); } catch (Exception ignore) {}
        try {
            // 新K線：回填「上一根」的最終值（避免節流導致上一根指標停留在舊 close）
            if (isNewCandle && n >= 2) {
                int prevIdx = n - 2;
                OHLCItem prevItem = (OHLCItem) s.getDataItem(prevIdx);
                long prevX = ohlcXMs(prevItem);
                double prevClose = prevItem.getCloseValue();

                // SMA 回填（以 prevIdx 結尾）
                updateOrAddXY(sma5Series, prevX, computeSMAAt(s, sma5Period, prevIdx));
                updateOrAddXY(sma10Series, prevX, computeSMAAt(s, sma10Period, prevIdx));

                // EMA 回填：用「前前根」EMA + prevClose 重算 prevEMA，再更新上一根點
                double k = 2.0 / (Math.max(1, ema12Period) + 1.0);
                double emaPrevPrev = Double.NaN;
                try {
                    int ec = ema12Series.getItemCount();
                    if (ec >= 2) {
                        emaPrevPrev = ema12Series.getY(ec - 2).doubleValue();
                    } else if (ec == 1) {
                        emaPrevPrev = ema12Series.getY(0).doubleValue();
                    }
                } catch (Exception ignore) {}
                if (Double.isNaN(emaPrevPrev)) emaPrevPrev = prevClose;
                double prevEma = prevClose * k + emaPrevPrev * (1.0 - k);
                updateOrAddXY(ema12Series, prevX, prevEma);

                // 同步多週期 overlay（若存在）
                try {
                    XYSeries s5 = periodToSMA5.get(currentKlineMinutes);
                    XYSeries s10 = periodToSMA10.get(currentKlineMinutes);
                    XYSeries e12 = periodToEMA12.get(currentKlineMinutes);
                    if (s5 != null) updateOrAddXY(s5, prevX, computeSMAAt(s, sma5Period, prevIdx));
                    if (s10 != null) updateOrAddXY(s10, prevX, computeSMAAt(s, sma10Period, prevIdx));
                    if (e12 != null) updateOrAddXY(e12, prevX, prevEma);
                } catch (Exception ignore) {}

                // 設定當前K線要使用的「前一根EMA」
                ema12PrevForCurrent = prevEma;
            }

            if (isNewCandle) {
                // 新K線但沒有 prevIdx（n==1）時，初始化 prev EMA
                if (n == 1 || Double.isNaN(ema12PrevForCurrent)) {
                    // 以當前 close 作為起始
                    ema12PrevForCurrent = close;
                }
                overlayLastXMs = xMs;
            }

            if (allowUpdateCurrent) {
                // SMA（以 lastIdx 結尾）
                updateOrAddXY(sma5Series, xMs, computeSMAAt(s, sma5Period, lastIdx));
                updateOrAddXY(sma10Series, xMs, computeSMAAt(s, sma10Period, lastIdx));

                // EMA（以「前一根EMA」+ 當前 close 計算，對同一根K線可反覆更新）
                double k = 2.0 / (Math.max(1, ema12Period) + 1.0);
                double ema = close * k + ema12PrevForCurrent * (1.0 - k);
                updateOrAddXY(ema12Series, xMs, ema);

                // 同步多週期 overlay（若存在）
                try {
                    XYSeries s5 = periodToSMA5.get(currentKlineMinutes);
                    XYSeries s10 = periodToSMA10.get(currentKlineMinutes);
                    XYSeries e12 = periodToEMA12.get(currentKlineMinutes);
                    if (s5 != null) updateOrAddXY(s5, xMs, computeSMAAt(s, sma5Period, lastIdx));
                    if (s10 != null) updateOrAddXY(s10, xMs, computeSMAAt(s, sma10Period, lastIdx));
                    if (e12 != null) updateOrAddXY(e12, xMs, ema);
                } catch (Exception ignore) {}

                keepSeriesWithinLimit(sma5Series, indicatorMaxPoints);
                keepSeriesWithinLimit(sma10Series, indicatorMaxPoints);
                keepSeriesWithinLimit(ema12Series, indicatorMaxPoints);
                kOverlayLastRecomputeMs = nowMs;
            }
        } finally {
            try { toggleOverlayNotify(true); } catch (Exception ignore) {}
        }
    }

    // [PERF] 節流域軸更新（避免長時間 setRange 重複執行）
    private void maybeApplyCandleDomainWindow() {
        long now = System.currentTimeMillis();
        // 先讀出最新K線的 X（若沒資料就跳過）
        try {
            OHLCSeries s = minuteToSeries.get(currentKlineMinutes);
            if (s == null || s.getItemCount() == 0) return;
            OHLCItem last = (OHLCItem) s.getDataItem(s.getItemCount() - 1);
            long xMs = ohlcXMs(last);

            // 新K線一定更新；否則最多每 250ms 更新一次
            if (xMs != domainLastXMs || (now - domainLastUpdateMs) >= 250) {
                domainLastXMs = xMs;
                domainLastUpdateMs = now;
                applyCandleDomainWindow();
            }
        } catch (Exception ignore) {}
    }

    // [PERF] 節流 OHLC info label 更新（HTML setText 很吃 GC）
    private void maybeUpdateOhlcInfoLabel() {
        if (ohlcInfoLabel == null) return;
        long now = System.currentTimeMillis();
        try {
            OHLCSeries series = minuteToSeries.get(currentKlineMinutes);
            if (series == null || series.getItemCount() == 0) return;
            int lastIndex = series.getItemCount() - 1;
            OHLCItem item = (OHLCItem) series.getDataItem(lastIndex);
            long xMs = ohlcXMs(item);

            // 新K線必更新；否則每 200ms 更新一次
            if (xMs == ohlcInfoLastXMs && (now - ohlcInfoLastUpdateMs) < 200) return;
            ohlcInfoLastXMs = xMs;
            ohlcInfoLastUpdateMs = now;

            double open = item.getOpenValue();
            double high = item.getHighValue();
            double low = item.getLowValue();
            double close = item.getCloseValue();
            double change = close - open;
            double changePct = (open != 0) ? (change / open * 100.0) : 0.0;

            String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(xMs));
            String color = (close >= open) ? "#26a69a" : "#ef5350";
            String changeStr = String.format("%+.2f (%+.2f%%)", change, changePct);

            ohlcInfoLabel.setText(String.format(
                "<html><div style='font-family: Monospaced; font-size: 11px;'>" +
                "<b>%s</b>  <span style='color: %s;'>%s</span><br/>" +
                "O: %.2f  H: %.2f  L: %.2f  C: <span style='color: %s; font-weight: bold;'>%.2f</span>" +
                "</div></html>",
                timeStr, color, changeStr,
                open, high, low, color, close
            ));
        } catch (Exception ignore) {}
    }

    // [限制式週期切換] 切換到指定週期索引
    private void switchToPeriod(int newIndex, JLabel periodLabel) {
        if (newIndex < 0 || newIndex >= periodChain.length) {
            appendToInfoArea("無效的週期索引", InfoType.ERROR);
            return;
        }
        
        int oldPeriod = periodChain[currentPeriodIndex];
        int newPeriod = periodChain[newIndex];
        String oldName = periodNames[currentPeriodIndex];
        String newName = periodNames[newIndex];
        
        appendToInfoArea(String.format("正在從 %s 切換到 %s...", oldName, newName), InfoType.SYSTEM);
        
        SwingUtilities.invokeLater(() -> {
            try {
                // 判斷是放大還是縮小
                boolean isZoomOut = newIndex > currentPeriodIndex;  // 切換到更大週期
                
                if (isZoomOut) {
                    // 放大：從小週期聚合到大週期
                    aggregatePeriodData(oldPeriod, newPeriod);
                } else {
                    // 縮小：切換到更小週期（使用已有數據）
                    // 不需要特殊處理，直接切換即可
                }
                
                // 更新當前週期
                currentPeriodIndex = newIndex;
                currentKlineMinutes = newPeriod;
                
                // 更新UI標籤
                periodLabel.setText(newName);
                
                // 切換圖表數據集
                updateChartDataset(newPeriod);
                
                // 重新對齊信號標記
                realignSignalMarkers(newPeriod);
                
                // [修復VWAP] 重置VWAP累積變量，避免使用舊週期的累積值
                resetVWAPAccumulators();
                
                // [修復域軸壓縮] 強制重置域軸範圍，避免從大週期切回小週期時K線被壓縮
                resetDomainAxisForPeriod(newPeriod);
                
                // [K線自動跟隨] 應用域窗口
                if (autoFollowLatest) {
                    applyCandleDomainWindow();
                }
                
                // 觸發圖表重繪
                scheduleChartFlush();
                
                appendToInfoArea(String.format("✓ 已切換到 %s 週期", newName), InfoType.SYSTEM);
                
            } catch (Exception e) {
                appendToInfoArea("切換週期失敗: " + e.getMessage(), InfoType.ERROR);
                e.printStackTrace();
            }
        });
    }
    
    // [限制式週期切換] 從小週期聚合到大週期
    private void aggregatePeriodData(int sourcePeriod, int targetPeriod) {
        try {
            OHLCSeries sourceSeries = minuteToSeries.get(sourcePeriod);
            OHLCSeries targetSeries = minuteToSeries.get(targetPeriod);
            
            if (sourceSeries == null || targetSeries == null) {
                appendToInfoArea("數據系列不存在，無法聚合", InfoType.WARNING);
                return;
            }
            
            if (sourceSeries.getItemCount() == 0) {
                appendToInfoArea("來源週期無數據，無法聚合", InfoType.WARNING);
                return;
            }
            
            // 計算倍數關係
            int sourceSeconds = sourcePeriod < 0 ? -sourcePeriod : sourcePeriod * 60;
            int targetSeconds = targetPeriod < 0 ? -targetPeriod : targetPeriod * 60;
            int multiplier = targetSeconds / sourceSeconds;
            
            if (targetSeconds % sourceSeconds != 0) {
                appendToInfoArea(String.format("週期不是整數倍關係（%d秒 -> %d秒），無法聚合", sourceSeconds, targetSeconds), InfoType.ERROR);
                return;
            }
            
            // 清空目標系列
            targetSeries.clear();
            
            // 聚合K線數據
            int sourceCount = sourceSeries.getItemCount();
            for (int i = 0; i < sourceCount; i += multiplier) {
                double open = 0, high = Double.NEGATIVE_INFINITY, low = Double.POSITIVE_INFINITY, close = 0;
                RegularTimePeriod targetPeriodObj = null;
                int aggregatedBars = 0;
                
                // 聚合 multiplier 根小週期K線成1根大週期K線
                for (int j = 0; j < multiplier && (i + j) < sourceCount; j++) {
                    OHLCItem sourceItem = (OHLCItem) sourceSeries.getDataItem(i + j);
                    if (sourceItem == null) continue;
                    
                    if (aggregatedBars == 0) {
                        // 第一根：使用其開盤價和時間
                        open = sourceItem.getOpenValue();
                        
                        // 計算目標週期的時間桶
                        long sourceMs = sourceItem.getPeriod().getFirstMillisecond();
                        long targetBucket = targetSeconds * 1000L;
                        long alignedMs = sourceMs - (sourceMs % targetBucket);
                        
                        if (targetPeriod < 0) {
                            targetPeriodObj = new Second(new java.util.Date(alignedMs));
                        } else {
                            targetPeriodObj = new Minute(new java.util.Date(alignedMs));
                        }
                    }
                    
                    // 更新最高價、最低價
                    high = Math.max(high, sourceItem.getHighValue());
                    low = Math.min(low, sourceItem.getLowValue());
                    
                    // 最後一根：使用其收盤價
                    close = sourceItem.getCloseValue();
                    
                    aggregatedBars++;
                }
                
                // 添加聚合後的K線
                if (aggregatedBars > 0 && targetPeriodObj != null) {
                    targetSeries.add(targetPeriodObj, open, high, low, close);
                }
            }
            
            appendToInfoArea(String.format("已聚合 %d 根小週期K線 -> %d 根大週期K線", sourceCount, targetSeries.getItemCount()), InfoType.SYSTEM);
            
            // [限制式週期切換] 同時聚合成交量數據
            aggregateVolumeData(sourcePeriod, targetPeriod, multiplier);
            
        } catch (Exception e) {
            appendToInfoArea("聚合數據時發生錯誤: " + e.getMessage(), InfoType.ERROR);
            e.printStackTrace();
        }
    }
    
    // [限制式週期切換] 聚合成交量數據
    private void aggregateVolumeData(int sourcePeriod, int targetPeriod, int multiplier) {
        try {
            XYSeries sourceVolume = periodToVolume.get(sourcePeriod);
            XYSeries targetVolume = periodToVolume.get(targetPeriod);
            
            if (sourceVolume == null || targetVolume == null) {
                return;
            }
            
            if (sourceVolume.getItemCount() == 0) {
                return;
            }
            
            // 清空目標成交量系列
            targetVolume.clear();
            
            // 聚合成交量：將multiplier根小週期的成交量相加
            int sourceCount = sourceVolume.getItemCount();
            for (int i = 0; i < sourceCount; i += multiplier) {
                double totalVolume = 0;
                long alignedMs = 0;
                int aggregatedBars = 0;
                
                for (int j = 0; j < multiplier && (i + j) < sourceCount; j++) {
                    org.jfree.data.xy.XYDataItem item = sourceVolume.getDataItem(i + j);
                    if (item == null) continue;
                    
                    if (aggregatedBars == 0) {
                        // 計算目標週期的時間桶
                        long sourceMs = item.getX().longValue();
                        int targetSeconds = targetPeriod < 0 ? -targetPeriod : targetPeriod * 60;
                        long targetBucket = targetSeconds * 1000L;
                        alignedMs = sourceMs - (sourceMs % targetBucket);
                    }
                    
                    totalVolume += item.getY().doubleValue();
                    aggregatedBars++;
                }
                
                if (aggregatedBars > 0) {
                    targetVolume.add(alignedMs, totalVolume, false);
                }
            }
            targetVolume.fireSeriesChanged();
            
            // 重新計算成交量MA
            recalculateVolumeMA(targetPeriod);
            
            appendToInfoArea(String.format("已聚合成交量：%d 根 -> %d 根", sourceCount, targetVolume.getItemCount()), InfoType.SYSTEM);
            
        } catch (Exception e) {
            // 忽略成交量聚合錯誤
        }
    }
    
    // [限制式週期切換] 重新計算指定週期的成交量MA
    private void recalculateVolumeMA(int period) {
        try {
            XYSeries volumeSeries = periodToVolume.get(period);
            XYSeries ma5 = periodToVolumeMA5.get(period);
            XYSeries ma10 = periodToVolumeMA10.get(period);
            
            if (volumeSeries == null || ma5 == null || ma10 == null) {
                return;
            }
            
            ma5.clear();
            ma10.clear();
            
            int count = volumeSeries.getItemCount();
            if (count == 0) return;
            
            // 計算MA5
            for (int i = 0; i < count; i++) {
                double sum = 0;
                int cnt = 0;
                for (int j = Math.max(0, i - 4); j <= i; j++) {
                    sum += volumeSeries.getDataItem(j).getY().doubleValue();
                    cnt++;
                }
                double ma = sum / cnt;
                long x = volumeSeries.getDataItem(i).getX().longValue();
                ma5.add(x, ma, false);
            }
            
            // 計算MA10
            for (int i = 0; i < count; i++) {
                double sum = 0;
                int cnt = 0;
                for (int j = Math.max(0, i - 9); j <= i; j++) {
                    sum += volumeSeries.getDataItem(j).getY().doubleValue();
                    cnt++;
                }
                double ma = sum / cnt;
                long x = volumeSeries.getDataItem(i).getX().longValue();
                ma10.add(x, ma, false);
            }
            
            ma5.fireSeriesChanged();
            ma10.fireSeriesChanged();
            
        } catch (Exception e) {
            // 忽略MA計算錯誤
        }
    }
    
    // [限制式週期切換] 更新圖表數據集
    private void updateChartDataset(int period) {
        try {
            // 更新當前週期的成交量系列引用
            volumeXYSeries = periodToVolume.get(period);
            volumeMA5Series = periodToVolumeMA5.get(period);
            volumeMA10Series = periodToVolumeMA10.get(period);
            
            if (combinedChart != null && combinedChart.getPlot() instanceof org.jfree.chart.plot.CombinedDomainXYPlot) {
                org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
                    (org.jfree.chart.plot.CombinedDomainXYPlot) combinedChart.getPlot();
                
                // 更新K線圖（第一個subplot）
                if (combinedPlot.getSubplots().size() > 0) {
                    XYPlot candlePlot = (XYPlot) combinedPlot.getSubplots().get(0);
                    candlePlot.setNotify(false);
                    try {
                        candlePlot.setDataset(0, minuteToCollection.get(period));
                        
                        // [修復Y軸跳動] 暫時固定Y軸範圍，避免自動縮放造成跳動
                        OHLCSeries ohlcSeries = minuteToSeries.get(period);
                        if (ohlcSeries != null && ohlcSeries.getItemCount() > 0) {
                            double minPrice = Double.MAX_VALUE;
                            double maxPrice = Double.MIN_VALUE;
                            for (int i = 0; i < ohlcSeries.getItemCount(); i++) {
                                OHLCItem item = (OHLCItem) ohlcSeries.getDataItem(i);
                                minPrice = Math.min(minPrice, item.getLowValue());
                                maxPrice = Math.max(maxPrice, item.getHighValue());
                            }
                            double range = maxPrice - minPrice;
                            double padding = range * 0.1;  // 10%留白
                            candlePlot.getRangeAxis().setRange(minPrice - padding, maxPrice + padding);
                            candlePlot.getRangeAxis().setAutoRange(false);  // 暫時關閉自動範圍
                        }
                        
                        recomputeOverlayFromOHLC();
                        refreshOverlayIndicators();
                        // [修復域軸壓縮] 移除這裡的applyCandleDomainWindow調用
                        // 讓switchToPeriod統一管理域軸設置，避免調用順序問題
                    } finally {
                        candlePlot.setNotify(true);
                    }
                }
                
                // 更新成交量圖（第二個subplot）
                if (combinedPlot.getSubplots().size() > 1) {
                    XYPlot volumePlot = (XYPlot) combinedPlot.getSubplots().get(1);
                    volumePlot.setNotify(false);
                    try {
                        // 更新成交量數據集
                        XYSeriesCollection volumeDataset = new XYSeriesCollection(volumeXYSeries);
                        volumePlot.setDataset(0, volumeDataset);
                        
                        // 更新成交量MA數據集
                        XYSeriesCollection maDataset = new XYSeriesCollection();
                        maDataset.addSeries(volumeMA5Series);
                        maDataset.addSeries(volumeMA10Series);
                        volumePlot.setDataset(1, maDataset);
                        
                        // [修復Y軸跳動] 固定成交量Y軸範圍
                        if (volumeXYSeries != null && volumeXYSeries.getItemCount() > 0) {
                            double maxVol = 0;
                            for (int i = 0; i < volumeXYSeries.getItemCount(); i++) {
                                maxVol = Math.max(maxVol, volumeXYSeries.getDataItem(i).getY().doubleValue());
                            }
                            volumePlot.getRangeAxis().setRange(0, maxVol * 1.2);  // 20%留白
                            volumePlot.getRangeAxis().setAutoRange(false);  // 暫時關閉自動範圍
                        }
                        
                    } finally {
                        volumePlot.setNotify(true);
                    }
                }
                
                // [修復Y軸跳動] 延遲恢復自動範圍，避免頻繁跳動
                javax.swing.Timer autoRangeTimer = new javax.swing.Timer(2000, e -> {
                    try {
                        if (combinedPlot.getSubplots().size() > 0) {
                            XYPlot candlePlot = (XYPlot) combinedPlot.getSubplots().get(0);
                            candlePlot.getRangeAxis().setAutoRange(true);
                        }
                        if (combinedPlot.getSubplots().size() > 1) {
                            XYPlot volumePlot = (XYPlot) combinedPlot.getSubplots().get(1);
                            volumePlot.getRangeAxis().setAutoRange(true);
                        }
                    } catch (Exception ignore) {}
                });
                autoRangeTimer.setRepeats(false);
                autoRangeTimer.start();
            }
            
            if (candleChart != null) {
                XYPlot candlePlot = candleChart.getXYPlot();
                candlePlot.setNotify(false);
                try {
                    candlePlot.setDataset(0, minuteToCollection.get(period));
                } finally {
                    candlePlot.setNotify(true);
                }
            }
        } catch (Exception e) {
            appendToInfoArea("更新圖表數據集失敗: " + e.getMessage(), InfoType.ERROR);
            e.printStackTrace();
        }
    }
    
    // [限制式週期切換] 重新對齊信號標記到新週期的時間桶
    private void realignSignalMarkers(int period) {
        try {
            long bucketMs;
            if (period < 0) {
                bucketMs = (-period) * 1000L;  // 秒級
            } else {
                bucketMs = period * 60_000L;   // 分鐘級
            }
            
            // 重新對齊所有信號系列（保留標記點，只調整時間戳）
            int totalSignals = bullSignals.getItemCount() + bearSignals.getItemCount() + 
                             bigBuySeries.getItemCount() + bigSellSeries.getItemCount() +
                             tickImbBuySeries.getItemCount() + tickImbSellSeries.getItemCount();
            
            realignSeries(bullSignals, bucketMs, period);
            realignSeries(bearSignals, bucketMs, period);
            realignSeries(bigBuySeries, bucketMs, period);
            realignSeries(bigSellSeries, bucketMs, period);
            realignSeries(tickImbBuySeries, bucketMs, period);
            realignSeries(tickImbSellSeries, bucketMs, period);
            
            int newTotalSignals = bullSignals.getItemCount() + bearSignals.getItemCount() + 
                                bigBuySeries.getItemCount() + bigSellSeries.getItemCount() +
                                tickImbBuySeries.getItemCount() + tickImbSellSeries.getItemCount();
            
            if (totalSignals > 0) {
                appendToInfoArea(String.format("標記點對齊完成：%d 個 -> %d 個（去重後）", 
                    totalSignals, newTotalSignals), InfoType.SYSTEM);
            }
            
        } catch (Exception e) {
            appendToInfoArea("重新對齊信號標記失敗: " + e.getMessage(), InfoType.ERROR);
        }
    }
    
    // [限制式週期切換] 重新對齊單個信號系列
    private void realignSeries(XYSeries series, long bucketMs, int period) {
        if (series == null || series.getItemCount() == 0) return;
        
        try {
            // 創建臨時列表儲存重新對齊後的數據點
            java.util.List<org.jfree.data.xy.XYDataItem> newItems = new java.util.ArrayList<>();
            
            for (int i = 0; i < series.getItemCount(); i++) {
                org.jfree.data.xy.XYDataItem item = series.getDataItem(i);
                long originalMs = item.getX().longValue();
                double price = item.getY().doubleValue();
                
                // 對齊到時間桶
                long alignedMs = originalMs - (originalMs % bucketMs);
                
                // 檢查是否已有該時間點的標記（避免重複）
                boolean exists = false;
                for (org.jfree.data.xy.XYDataItem newItem : newItems) {
                    if (newItem.getX().longValue() == alignedMs) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    newItems.add(new org.jfree.data.xy.XYDataItem(alignedMs, price));
                }
            }
            
            // 清空並重新添加
            series.clear();
            for (org.jfree.data.xy.XYDataItem item : newItems) {
                series.add(item.getX(), item.getY(), false);  // 不通知，最後統一通知
            }
            series.fireSeriesChanged();
            
        } catch (Exception e) {
            // 忽略單個系列的錯誤
        }
    }
    
    // 切換 K 線週期（舊方法，保留以供兼容）
    private void switchKlineInterval() {
        int idx = klineIntervalCombo.getSelectedIndex();
        // 對應下拉鍵：10秒、30秒、60秒、1分、5分、10分、30分、60分
        int[] opts = new int[]{-10, -30, -60, 1, 5, 10, 30, 60};
        currentKlineMinutes = opts[idx];
        
        // 切換 dataset 到對應集合
        SwingUtilities.invokeLater(() -> {
            try {
                // 更新組合圖中的K線數據集
                if (combinedChart != null && combinedChart.getPlot() instanceof org.jfree.chart.plot.CombinedDomainXYPlot) {
                    org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
                        (org.jfree.chart.plot.CombinedDomainXYPlot) combinedChart.getPlot();
                    
                    // 取得K線子圖（第一個subplot）
                    if (combinedPlot.getSubplots().size() > 0) {
                        XYPlot candlePlot = (XYPlot) combinedPlot.getSubplots().get(0);
                        candlePlot.setNotify(false);
                        try {
                            candlePlot.setDataset(0, minuteToCollection.get(currentKlineMinutes));
                            
                            // 以當前 K 線序列重算覆蓋指標，確保時間座標完全對齊
                            recomputeOverlayFromOHLC();
                            refreshOverlayIndicators();
                            applyCandleDomainWindow();
                        } finally {
                            candlePlot.setNotify(true);
                        }
                    }
                }
                
                // 也更新獨立的 candleChart（以防使用）
                if (candleChart != null) {
                    XYPlot candlePlot = candleChart.getXYPlot();
                    candlePlot.setNotify(false);
                    try {
                        candlePlot.setDataset(0, minuteToCollection.get(currentKlineMinutes));
                    } finally {
                        candlePlot.setNotify(true);
                    }
                }
                
                // 觸發圖表重繪
                scheduleChartFlush();
                
                // 顯示切換訊息
                String periodName = klineIntervalCombo.getItemAt(idx);
                appendToInfoArea("已切換K線週期至: " + periodName, InfoType.SYSTEM);
                
            } catch (Exception e) {
                appendToInfoArea("切換K線週期失敗: " + e.getMessage(), InfoType.ERROR);
            }
        });
    }

    // 重新整理疊加指標的顯示與資料集配置
    private void refreshOverlayIndicators() {
        // 精簡：已移除疊加指標刷新
    }

    // 基於當前 K 線序列（OHLCSeries）的 close 值重算 SMA/EMA/BOLL 並以 K 線 period 時間作為 X 軸
    private void recomputeOverlayFromOHLC() {
        // 精簡：已移除疊加指標重算
    }

    // 僅重算成本估計，不寫入任何 UI（供啟動自動效能偵測使用）
    private void recomputeOverlayCostOnly() {
        try {
            OHLCSeries series = minuteToSeries.get(currentKlineMinutes);
            if (series == null) return;
            int n = series.getItemCount();
            if (n == 0) return;

            java.util.List<Double> closes = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                org.jfree.data.time.ohlc.OHLCItem it = (org.jfree.data.time.ohlc.OHLCItem) series.getDataItem(i);
                closes.add(it.getCloseValue());
            }

            // 輕量級計算（EMA + MACD 估算），不觸發任何圖表寫入
            final int macdShort = 12, macdLong = 26, macdSignal = 9;
            double[] emaShort = new double[n];
            double[] emaLong = new double[n];
            double multShort = 2.0 / (macdShort + 1);
            double multLong = 2.0 / (macdLong + 1);
            emaShort[0] = closes.get(0);
            emaLong[0] = closes.get(0);
            for (int i = 1; i < n; i++) {
                double c = closes.get(i);
                emaShort[i] = (c - emaShort[i-1]) * multShort + emaShort[i-1];
                emaLong[i]  = (c - emaLong[i-1])  * multLong  + emaLong[i-1];
            }
            double[] macdLine = new double[n];
            for (int i = 0; i < n; i++) macdLine[i] = emaShort[i] - emaLong[i];
            double[] macdSignalArr = new double[n];
            double multSig = 2.0 / (macdSignal + 1);
            macdSignalArr[0] = macdLine[0];
            for (int i = 1; i < n; i++) macdSignalArr[i] = (macdLine[i] - macdSignalArr[i-1]) * multSig + macdSignalArr[i-1];
        } catch (Exception ignore) {}
    }

    // 暫停/恢復所有覆蓋序列的通知，減少重繪閃爍
    private void toggleOverlayNotify(boolean on) {
        try {
            smaSeries.setNotify(on);
            sma5Series.setNotify(on); sma10Series.setNotify(on); sma20Series.setNotify(on);
            ema12Series.setNotify(on); ema26Series.setNotify(on);
            bollUSeries.setNotify(on); bollMSeries.setNotify(on); bollLSeries.setNotify(on);
            volumeOverlaySeries.setNotify(on);
            macdLineSeries.setNotify(on); macdSignalSeries.setNotify(on);
            if (macdHistogramSeries != null) macdHistogramSeries.setNotify(on);
            if (kSeries != null) kSeries.setNotify(on);
            if (dSeries != null) dSeries.setNotify(on);
            if (jSeries != null) jSeries.setNotify(on);
        } catch (Exception ignore) {}
    }

    // 將來源 series 的資料離線複製到目標 series（整批替換，避免逐筆觸發重繪）
    private void swapSeriesData(XYSeries target, XYSeries source) {
        if (target == null || source == null) return;
        try {
            target.setNotify(false);
            target.clear();
            for (int i = 0; i < source.getItemCount(); i++) {
                target.add(source.getX(i), source.getY(i), false);
            }
        } catch (Exception ignore) {
        } finally {
            target.setNotify(true);
        }
    }

    // 根據模式套用自適應參數
    private void applyPerfModeOverlay(String mode) {
        if (mode == null) return;
        switch (mode) {
            case "節能":
                kOverlayMinIntervalMs = 220;
                indicatorMaxPoints = 300;
                break;
            case "效能":
                kOverlayMinIntervalMs = 80;
                indicatorMaxPoints = 800;
                break;
            default: // 一般
                kOverlayMinIntervalMs = 120;
                indicatorMaxPoints = 600;
        }
    }

    // 啟動後前幾秒自動偵測重算耗時並調整參數
    private void autoTunePerformance() {
        new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                int samples = 0;
                long sum = 0;
                while (System.currentTimeMillis() - start < 3000) { // 3 秒內抽樣
                    long t0 = System.nanoTime();
                    // 嘗試只做一次輕量重算（不進 EDT 寫入）
                    try { recomputeOverlayCostOnly(); } catch (Exception ignore) {}
                    long t1 = System.nanoTime();
                    long costMs = (t1 - t0) / 1_000_000;
                    sum += Math.max(1, costMs);
                    samples++;
                    Thread.sleep(60);
                }
                if (samples > 0) {
                    long avg = sum / samples;
                    // 根據平均耗時設定 min interval 和點數上限
                    if (avg <= 12) { // 很快
                        kOverlayMinIntervalMs = 80;
                        indicatorMaxPoints = 900;
                    } else if (avg <= 25) {
                        kOverlayMinIntervalMs = 120;
                        indicatorMaxPoints = 700;
                    } else if (avg <= 40) {
                        kOverlayMinIntervalMs = 160;
                        indicatorMaxPoints = 600;
                    } else {
                        kOverlayMinIntervalMs = 220;
                        indicatorMaxPoints = 400;
                    }
                }
            } catch (InterruptedException ignore) {}
        }, "AutoTune-Indicators").start();
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
    public void updateTechnicalIndicators(int timeStep, double volatility, double rsi, double wap) { }

    // [限制式週期切換] 同時更新所有週期的成交量
    private void updateAllPeriodVolumes(int volume, long now) {
        // 更新所有秒級週期
        for (int s : klineSeconds) {
            int key = -s;
            XYSeries volSeries = periodToVolume.get(key);
            if (volSeries != null) {
                long bucketMs = s * 1000L;
                long aligned = now - (now % bucketMs);
                
                try {
                    int existingIndex = volSeries.indexOf(aligned);
                    if (existingIndex >= 0) {
                        Number existingVolume = volSeries.getY(existingIndex);
                        int newVolume = (existingVolume != null ? existingVolume.intValue() : 0) + volume;
                        volSeries.updateByIndex(existingIndex, newVolume);
                    } else {
                        volSeries.add(aligned, volume, false);
                        while (volSeries.getItemCount() > 300) {
                            volSeries.remove(0);
                        }
                    }
                } catch (Exception ignore) {}
            }
        }
        
        // 更新所有分鐘級週期
        for (int m : klineMinutes) {
            XYSeries volSeries = periodToVolume.get(m);
            if (volSeries != null) {
                long bucketMs = m * 60_000L;
                long aligned = now - (now % bucketMs);
                
                try {
                    int existingIndex = volSeries.indexOf(aligned);
                    if (existingIndex >= 0) {
                        Number existingVolume = volSeries.getY(existingIndex);
                        int newVolume = (existingVolume != null ? existingVolume.intValue() : 0) + volume;
                        volSeries.updateByIndex(existingIndex, newVolume);
                    } else {
                        volSeries.add(aligned, volume, false);
                        while (volSeries.getItemCount() > 300) {
                            volSeries.remove(0);
                        }
                    }
                } catch (Exception ignore) {}
            }
        }
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

            // === [TradingView] 更新XY系列成交量（用於組合圖） ===
            // [限制式週期切換] 同時更新所有週期的成交量數據
            updateAllPeriodVolumes(volume, now);
            
            // 更新當前週期的成交量引用（確保最新）
            if (volumeXYSeries != null) {
                try {
                    int existingIndex = volumeXYSeries.indexOf(aligned);
                    if (existingIndex >= 0) {
                        // 累加到現有數據點
                        Number existingVolume = volumeXYSeries.getY(existingIndex);
                        int newVolume = (existingVolume != null ? existingVolume.intValue() : 0) + volume;
                        volumeXYSeries.updateByIndex(existingIndex, newVolume);
                    } else {
                        // 新增數據點
                        volumeXYSeries.add(aligned, volume);
                        
                        // 限制數據點數量
                        while (volumeXYSeries.getItemCount() > 600) {
                            volumeXYSeries.remove(0);
                        }
                    }
                } catch (Exception ignore) {}
            }

            // 保留原有的 Category 數據集更新（用於獨立的成交量圖表）
            @SuppressWarnings("unchecked")
            java.util.List<Comparable> keys = volumeDataset.getColumnKeys();
            if (!keys.contains(key)) {
                while (volumeDataset.getColumnCount() >= maxVolumeColumns) {
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
            
            // === [TradingView] 計算成交量MA5和MA10 ===
            try {
                if (volumeXYSeries != null && volumeXYSeries.getItemCount() > 0) {
                    // 清空MA系列
                    volumeMA5Series.clear();
                    volumeMA10Series.clear();
                    
                    int count = volumeXYSeries.getItemCount();
                    
                    // 計算MA5
                    for (int i = 0; i < count; i++) {
                        double sum = 0;
                        int n = 0;
                        for (int j = Math.max(0, i - 4); j <= i; j++) {
                            sum += volumeXYSeries.getY(j).doubleValue();
                            n++;
                        }
                        double ma5 = sum / n;
                        volumeMA5Series.add(volumeXYSeries.getX(i), ma5);
                    }
                    
                    // 計算MA10
                    for (int i = 0; i < count; i++) {
                        double sum = 0;
                        int n = 0;
                        for (int j = Math.max(0, i - 9); j <= i; j++) {
                            sum += volumeXYSeries.getY(j).doubleValue();
                            n++;
                        }
                        double ma10 = sum / n;
                        volumeMA10Series.add(volumeXYSeries.getX(i), ma10);
                    }
                    
                    // 限制MA系列數據點
                    while (volumeMA5Series.getItemCount() > 600) {
                        volumeMA5Series.remove(0);
                    }
                    while (volumeMA10Series.getItemCount() > 600) {
                        volumeMA10Series.remove(0);
                    }
                }
            } catch (Exception ignore) {}
            
            scheduleChartFlush(); // [CHART]
        });
    }

    // === Tape 逐筆面板（簡版） ===
    private static class TapePanel extends JPanel {
        private static class Trade { long ts; boolean buy; double price; int vol; double slipAbs; }
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final JList<String> list = new JList<>(model);
        private final JLabel ratioLabel = new JLabel("買/賣比: -- / --");
        private final JLabel rateLabel = new JLabel("近10s: 0.0 筆/秒  0 量/秒");
        private final JLabel slipLabel = new JLabel("均滑價: --");
        private final JLabel avgVolLabel = new JLabel("均量: --");
        private final JLabel streakLabel = new JLabel("最大連續: --");
        private final java.util.Deque<Trade> q = new java.util.ArrayDeque<>();
        private final long WINDOW_MS = 10_000L;
        public TapePanel(){
            setLayout(new BorderLayout(6,6));
            list.setFont(new Font("Monospaced", Font.PLAIN, 12));
            add(new JScrollPane(list), BorderLayout.CENTER);
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
            top.add(ratioLabel); top.add(rateLabel); top.add(slipLabel); top.add(avgVolLabel); top.add(streakLabel);
            add(top, BorderLayout.NORTH);
        }
        public void pushTrade(boolean buyerInitiated, double price, int volume, double bestBid, double bestAsk){
            String side = buyerInitiated ? "買" : "賣";
            double slipAbs = Math.abs(buyerInitiated ? (price - bestAsk) : (bestBid - price));
            String t = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            model.add(0, String.format("%s  %s  價:%.2f  量:%d  滑價:%.2f", t, side, price, volume, slipAbs));
            while(model.size()>300) model.removeElementAt(model.size()-1);
            Trade tr = new Trade(); tr.ts = System.currentTimeMillis(); tr.buy = buyerInitiated; tr.price = price; tr.vol = volume; tr.slipAbs = slipAbs;
            q.addLast(tr); prune(); recompute();
            // 大單標記：若超門檻，於主圖加一點
            if (volume >= ((MainView)SwingUtilities.getWindowAncestor(this)).bigOrderThreshold) {
                try {
                    long nowMs = tr.ts;
                    if (buyerInitiated) ((MainView)SwingUtilities.getWindowAncestor(this)).bigBuySeries.add(nowMs, price);
                    else ((MainView)SwingUtilities.getWindowAncestor(this)).bigSellSeries.add(nowMs, price);
                } catch (Exception ignore) {}
            }
        }
        private void prune(){ long now = System.currentTimeMillis(); while(!q.isEmpty() && now - q.peekFirst().ts > WINDOW_MS) q.removeFirst(); }
        private void recompute(){
            if (q.isEmpty()){ ratioLabel.setText("買/賣比: -- / --"); rateLabel.setText("近10s: 0.0 筆/秒  0 量/秒"); slipLabel.setText("均滑價: --"); avgVolLabel.setText("均量: --"); streakLabel.setText("最大連續: --"); return; }
            int trades=0; long vol=0, buyVol=0, sellVol=0; double slipSum=0; int slipN=0; int maxBuy=0, maxSell=0, cur=0; Boolean curSide=null;
            for(Trade tr: q){
                trades++; vol += tr.vol; if (tr.buy) buyVol += tr.vol; else sellVol += tr.vol; slipSum += tr.slipAbs; slipN++;
                if (curSide==null || curSide!=tr.buy){ curSide = tr.buy; cur = 1; }
                else { cur++; }
                if (tr.buy) maxBuy = Math.max(maxBuy, cur); else maxSell = Math.max(maxSell, cur);
            }
            double secs = Math.max(1.0, WINDOW_MS/1000.0);
            double tps = trades / secs; double vps = vol / secs;
            double buyPct = (buyVol + sellVol) > 0 ? (buyVol*100.0/(buyVol+sellVol)) : 0.0;
            double sellPct = 100.0 - buyPct;
            double avgSlip = slipN>0 ? (slipSum/slipN) : 0.0;
            double avgVol = trades>0 ? (vol*1.0/trades) : 0.0;
            ratioLabel.setText(String.format("買/賣比: %.1f%% / %.1f%%", buyPct, sellPct));
            rateLabel.setText(String.format("近10s: %.2f 筆/秒  %,d 量/秒", tps, Math.round(vps)));
            slipLabel.setText(String.format("均滑價: %.2f", avgSlip));
            avgVolLabel.setText(String.format("均量: %.1f", avgVol));
            streakLabel.setText(String.format("最大連續: 買%d / 賣%d", maxBuy, maxSell));
        }
    }

    // 對外公開：控制器可直接推送逐筆到 Tape
    public void pushTapeTrade(boolean buyerInitiated, double price, int volume, double bestBid, double bestAsk){
        if (tapePanel != null) {
            SwingUtilities.invokeLater(() -> tapePanel.pushTrade(buyerInitiated, price, volume, bestBid, bestAsk));
        }
    }
    
    // 對外公開：手動更新信號指示器面板
    public void updateSignalIndicators() {
        SwingUtilities.invokeLater(() -> {
            try {
                if (signalPanel != null) {
                    int bullCount = bullSignals != null ? bullSignals.getItemCount() : 0;
                    int bearCount = bearSignals != null ? bearSignals.getItemCount() : 0;
                    int bigBuyCount = bigBuySeries != null ? bigBuySeries.getItemCount() : 0;
                    int bigSellCount = bigSellSeries != null ? bigSellSeries.getItemCount() : 0;
                    int tickBuyCount = tickImbBuySeries != null ? tickImbBuySeries.getItemCount() : 0;
                    int tickSellCount = tickImbSellSeries != null ? tickImbSellSeries.getItemCount() : 0;
                    
                    signalPanel.updateAllSignals(bullCount, bearCount, bigBuyCount, bigSellCount, tickBuyCount, tickSellCount);
                }
            } catch (Exception ignore) {}
        });
    }

    // 刷新底部指標摘要（每秒）
    private void refreshMarketStats(){
        try {
            if (marketStatsLabel == null || model == null) return;
            java.util.List<Transaction> recent = model.getRecentTransactions(60);
            if (recent.isEmpty()) {
                marketStatsLabel.setText("指標: In/Out --/--  Δ --  失衡 --  TPS -- VPS --");
                return;
            }
            long inVol=0,outVol=0,vol=0;
            int buyTicks=0,sellTicks=0;
            for (Transaction t: recent){
                vol += t.getVolume();
                if (t.isBuyerInitiated()) { outVol += t.getVolume(); buyTicks++; } else { inVol += t.getVolume(); sellTicks++; }
            }
            int tot = (int)Math.max(1, inVol + outVol);
            int inPct = (int)Math.round(inVol * 100.0 / tot);
            int outPct = 100 - inPct;
            long delta = outVol - inVol;
            double tps = model.getRecentTPS(60);
            double vps = model.getRecentVPS(60);
            double imb = model.getRecentTickImbalance(60);
            marketStatsLabel.setText(String.format("指標: In/Out %d%%/%d%%  Δ %,d  失衡 %.2f  TPS %.2f  VPS %,d", inPct, outPct, delta, imb, tps, Math.round(vps)));
        } catch (Exception ignore) {}
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
            scheduleChartFlush(); // [CHART]
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
            DecimalFormat df2 = new DecimalFormat("#,##0.00");
            stockPriceLabel.setText("股票價格: " + df2.format(price));
            retailCashLabel.setText("散戶平均現金: " + df2.format(retailCash));
            retailStocksLabel.setText("散戶平均持股: " + retailStocks);
            mainForceCashLabel.setText("主力現金: " + df2.format(mainForceCash));
            mainForceStocksLabel.setText("主力持有籌碼: " + mainForceStocks);
            targetPriceLabel.setText("主力目標價位: " + df2.format(targetPrice));
            averageCostPriceLabel.setText("主力平均成本: " + df2.format(avgCostPrice));
            fundsLabel.setText("市場可用資金: " + df2.format(funds));
            inventoryLabel.setText("市場庫存: " + inventory);
            weightedAveragePriceLabel.setText("加權平均價格: " + df2.format(wap));
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
        // [TradingView] 處理組合圖表的特殊情況
        if (plot instanceof org.jfree.chart.plot.CombinedDomainXYPlot) {
            setupCombinedPlotInteraction((org.jfree.chart.plot.CombinedDomainXYPlot) plot, chartPanel, title);
            return;
        }
        // === TradingView 風格的十字光標 ===
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setRangeCrosshairLockedOnData(false);
        
        // 細線十字光標（TradingView風格）
        plot.setDomainCrosshairPaint(new Color(100, 100, 100, 180));
        plot.setRangeCrosshairPaint(new Color(100, 100, 100, 180));
        plot.setDomainCrosshairStroke(new BasicStroke(1.0f));
        plot.setRangeCrosshairStroke(new BasicStroke(1.0f));

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
                // [UX] 左鍵點擊開/關量尺；右鍵清除
                if (!(plot instanceof XYPlot)) return;
                if (SwingUtilities.isLeftMouseButton(event.getTrigger())) {
                    XYPlot xp = (XYPlot) plot;
                    Point2D p = chartPanel.translateScreenToJava2D(event.getTrigger().getPoint());
                    Rectangle2D area = chartPanel.getScreenDataArea();
                    if (area != null && area.contains(p)) {
                        double x = xp.getDomainAxis().java2DToValue(p.getX(), area, xp.getDomainAxisEdge());
                        double y = xp.getRangeAxis().java2DToValue(p.getY(), area, xp.getRangeAxisEdge());
                        measuring = !measuring;
                        if (measuring) { anchorXMs = x; anchorPrice = y; }
                        else { anchorXMs = null; anchorPrice = null; chartValueLabel.setText(""); }
                    }
                } else if (SwingUtilities.isRightMouseButton(event.getTrigger())) {
                    measuring = false; anchorXMs = null; anchorPrice = null; chartValueLabel.setText("");
                }
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

                        // === TradingView 風格：更新 OHLC 信息面板 ===
                        if (plot.getRenderer() instanceof org.jfree.chart.renderer.xy.CandlestickRenderer && ohlcInfoLabel != null) {
                            try {
                                // 找到最接近鼠標位置的K線數據
                                OHLCSeries series = minuteToSeries.get(currentKlineMinutes);
                                if (series != null && series.getItemCount() > 0) {
                                    // 找到最接近的K線
                                    int closestIndex = -1;
                                    double minDistance = Double.MAX_VALUE;
                                    for (int i = 0; i < series.getItemCount(); i++) {
                                        OHLCItem item = (OHLCItem) series.getDataItem(i);
                                        long itemTime = item.getPeriod().getFirstMillisecond();
                                        double distance = Math.abs(itemTime - chartX);
                                        if (distance < minDistance) {
                                            minDistance = distance;
                                            closestIndex = i;
                                        }
                                    }
                                    
                                    if (closestIndex >= 0) {
                                        OHLCItem item = (OHLCItem) series.getDataItem(closestIndex);
                                        double open = item.getOpenValue();
                                        double high = item.getHighValue();
                                        double low = item.getLowValue();
                                        double close = item.getCloseValue();
                                        double change = close - open;
                                        double changePct = (open != 0) ? (change / open * 100.0) : 0.0;
                                        
                                        // 格式化時間
                                        String timeStr = new SimpleDateFormat("HH:mm:ss").format(
                                            new Date(item.getPeriod().getFirstMillisecond())
                                        );
                                        
                                        // 使用HTML格式化顯示，根據漲跌顯示顏色
                                        String color = (close >= open) ? "#26a69a" : "#ef5350";
                                        String changeStr = String.format("%+.2f (%+.2f%%)", change, changePct);
                                        
                                        ohlcInfoLabel.setText(String.format(
                                            "<html><div style='font-family: Monospaced; font-size: 11px;'>" +
                                            "<b>%s</b>  <span style='color: %s;'>%s</span><br/>" +
                                            "O: %.2f  H: %.2f  L: %.2f  C: <span style='color: %s; font-weight: bold;'>%.2f</span>" +
                                            "</div></html>",
                                            timeStr, color, changeStr,
                                            open, high, low, color, close
                                        ));
                                    }
                                }
                            } catch (Exception ignore) {}
                        }

                        // 更新狀態欄或信息區域
                        String valueText = String.format("%s  價: %.2f",
                                title, chartY);
                        if (measuring && anchorXMs != null && anchorPrice != null) {
                            double dx = Math.abs(chartX - anchorXMs);
                            double dy = chartY - anchorPrice;
                            double pct = (anchorPrice != 0) ? (dy / anchorPrice * 100.0) : 0.0;
                            valueText += String.format("  Δt: %.0fms  Δ價: %.2f (%.2f%%)", dx, dy, pct);
                        }

                        // 如果存在狀態欄標籤，更新它
                        if (chartValueLabel != null) {
                            chartValueLabel.setText(valueText);
                        }

                        // [UX] 在圖上顯示量尺文字（更明顯）
                        if (plot instanceof XYPlot) {
                            XYPlot xp = (XYPlot) plot;
                            // 用 plot 的 annotations 管理文字
                            // 清除上一個臨時標註（簡化：保留至多一個）
                            if (!measuring || anchorXMs == null || anchorPrice == null) {
                                try { xp.clearAnnotations(); } catch (Exception ignore) {}
                            } else {
                                String ann = String.format("Δt: %.0fms\nΔ價: %.2f (%.2f%%)", Math.abs(chartX-anchorXMs), (chartY-anchorPrice), anchorPrice!=0?((chartY-anchorPrice)/anchorPrice*100.0):0.0);
                                org.jfree.chart.annotations.XYTextAnnotation txt = new org.jfree.chart.annotations.XYTextAnnotation(ann, chartX, chartY);
                                txt.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 11));
                                txt.setPaint(new Color(33,33,33));
                                txt.setTextAnchor(org.jfree.chart.ui.TextAnchor.TOP_LEFT);
                                try { xp.clearAnnotations(); } catch (Exception ignore) {}
                                xp.addAnnotation(txt);
                            }
                        }
                    } catch (Exception e) {
                        // 忽略任何坐標轉換錯誤
                    }
                }
            }
        });
    }

    /**
     * [TradingView] 設置組合圖表的交互功能
     */
    private void setupCombinedPlotInteraction(org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot, 
                                             ChartPanel chartPanel, String title) {
        // 為組合圖中的第一個子圖（K線圖）設置交互
        @SuppressWarnings("unchecked")
        java.util.List<XYPlot> subplots = combinedPlot.getSubplots();
        if (subplots != null && !subplots.isEmpty()) {
            XYPlot candlePlot = subplots.get(0);  // K線圖
            
            // 設置十字光標
            candlePlot.setDomainCrosshairVisible(true);
            candlePlot.setRangeCrosshairVisible(true);
            candlePlot.setDomainCrosshairLockedOnData(false);
            candlePlot.setRangeCrosshairLockedOnData(false);
            candlePlot.setDomainCrosshairPaint(new Color(100, 100, 100, 180));
            candlePlot.setRangeCrosshairPaint(new Color(100, 100, 100, 180));
            candlePlot.setDomainCrosshairStroke(new BasicStroke(1.0f));
            candlePlot.setRangeCrosshairStroke(new BasicStroke(1.0f));
            
            // 為成交量圖也設置十字光標（可選）
            if (subplots.size() > 1) {
                XYPlot volumePlot = subplots.get(1);
                volumePlot.setDomainCrosshairVisible(true);
                volumePlot.setRangeCrosshairVisible(false);  // 成交量不顯示水平線
                volumePlot.setDomainCrosshairPaint(new Color(100, 100, 100, 180));
                volumePlot.setDomainCrosshairStroke(new BasicStroke(1.0f));
            }
        }
        
        // 添加鼠標監聽器
        chartPanel.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event.getTrigger())) {
                    measuring = !measuring;
                    if (measuring) {
                        Point2D p = chartPanel.translateScreenToJava2D(event.getTrigger().getPoint());
                        Rectangle2D area = chartPanel.getScreenDataArea();
                        if (area != null && area.contains(p) && !subplots.isEmpty()) {
                            XYPlot candlePlot = subplots.get(0);
                            double x = candlePlot.getDomainAxis().java2DToValue(p.getX(), area, candlePlot.getDomainAxisEdge());
                            double y = candlePlot.getRangeAxis().java2DToValue(p.getY(), area, candlePlot.getRangeAxisEdge());
                            anchorXMs = x;
                            anchorPrice = y;
                        }
                    } else {
                        anchorXMs = null;
                        anchorPrice = null;
                        chartValueLabel.setText("");
                    }
                } else if (SwingUtilities.isRightMouseButton(event.getTrigger())) {
                    measuring = false;
                    anchorXMs = null;
                    anchorPrice = null;
                    chartValueLabel.setText("");
                }
            }
            
            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                Point2D p = chartPanel.translateScreenToJava2D(event.getTrigger().getPoint());
                Rectangle2D plotArea = chartPanel.getScreenDataArea();
                
                if (plotArea != null && plotArea.contains(p) && !subplots.isEmpty()) {
                    try {
                        XYPlot candlePlot = subplots.get(0);
                        double chartX = candlePlot.getDomainAxis().java2DToValue(
                                p.getX(), plotArea, candlePlot.getDomainAxisEdge());
                        double chartY = candlePlot.getRangeAxis().java2DToValue(
                                p.getY(), plotArea, candlePlot.getRangeAxisEdge());
                        
                        // 更新十字光標
                        candlePlot.setDomainCrosshairValue(chartX);
                        candlePlot.setRangeCrosshairValue(chartY);
                        
                        // 同步成交量圖的垂直線
                        if (subplots.size() > 1) {
                            subplots.get(1).setDomainCrosshairValue(chartX);
                        }
                        
                        // 更新 OHLC 信息面板
                        if (ohlcInfoLabel != null) {
                            try {
                                OHLCSeries series = minuteToSeries.get(currentKlineMinutes);
                                if (series != null && series.getItemCount() > 0) {
                                    int closestIndex = -1;
                                    double minDistance = Double.MAX_VALUE;
                                    for (int i = 0; i < series.getItemCount(); i++) {
                                        OHLCItem item = (OHLCItem) series.getDataItem(i);
                                        long itemTime = item.getPeriod().getFirstMillisecond();
                                        double distance = Math.abs(itemTime - chartX);
                                        if (distance < minDistance) {
                                            minDistance = distance;
                                            closestIndex = i;
                                        }
                                    }
                                    
                                    if (closestIndex >= 0) {
                                        OHLCItem item = (OHLCItem) series.getDataItem(closestIndex);
                                        double open = item.getOpenValue();
                                        double high = item.getHighValue();
                                        double low = item.getLowValue();
                                        double close = item.getCloseValue();
                                        double change = close - open;
                                        double changePct = (open != 0) ? (change / open * 100.0) : 0.0;
                                        
                                        String timeStr = new SimpleDateFormat("HH:mm:ss").format(
                                            new Date(item.getPeriod().getFirstMillisecond())
                                        );
                                        
                                        String color = (close >= open) ? "#26a69a" : "#ef5350";
                                        String changeStr = String.format("%+.2f (%+.2f%%)", change, changePct);
                                        
                                        ohlcInfoLabel.setText(String.format(
                                            "<html><div style='font-family: Monospaced; font-size: 11px;'>" +
                                            "<b>%s</b>  <span style='color: %s;'>%s</span><br/>" +
                                            "O: %.2f  H: %.2f  L: %.2f  C: <span style='color: %s; font-weight: bold;'>%.2f</span>" +
                                            "</div></html>",
                                            timeStr, color, changeStr,
                                            open, high, low, color, close
                                        ));
                                    }
                                }
                            } catch (Exception ignore) {}
                        }
                        
                        // 更新狀態欄
                        String valueText = String.format("%s  價: %.2f", title, chartY);
                        if (measuring && anchorXMs != null && anchorPrice != null) {
                            double dx = Math.abs(chartX - anchorXMs);
                            double dy = chartY - anchorPrice;
                            double pct = (anchorPrice != 0) ? (dy / anchorPrice * 100.0) : 0.0;
                            valueText += String.format("  Δt: %.0fms  Δ價: %.2f (%.2f%%)", dx, dy, pct);
                        }
                        
                        if (chartValueLabel != null) {
                            chartValueLabel.setText(valueText);
                        }
                    } catch (Exception e) {
                        // 忽略錯誤
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

        // 精簡：移除技術指標頁面快捷鍵

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
                if (plot instanceof org.jfree.chart.plot.CombinedDomainXYPlot) {
                    // [TradingView] 處理組合圖表
                    org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
                        (org.jfree.chart.plot.CombinedDomainXYPlot) plot;
                    
                    // 重置共享的域軸
                    if (combinedPlot.getDomainAxis() != null) {
                        combinedPlot.getDomainAxis().setAutoRange(true);
                    }
                    
                    // 重置每個子圖的值軸
                    @SuppressWarnings("unchecked")
                    java.util.List<XYPlot> subplots = combinedPlot.getSubplots();
                    if (subplots != null) {
                        for (XYPlot subplot : subplots) {
                            if (subplot.getRangeAxis() != null) {
                                subplot.getRangeAxis().setAutoRange(true);
                            }
                        }
                    }
                } else if (plot instanceof XYPlot) {
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
                System.err.println("重置XY圖表 " + (chart.getTitle() != null ? chart.getTitle().getText() : "未知") + " 時發生錯誤: " + e.getMessage());
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
        // 精簡：刪除技術指標頁面說明

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
        Plot plot = chart.getPlot();
        if (plot instanceof org.jfree.chart.plot.CombinedDomainXYPlot) {
            // [TradingView] 處理組合圖表
            org.jfree.chart.plot.CombinedDomainXYPlot combinedPlot = 
                (org.jfree.chart.plot.CombinedDomainXYPlot) plot;
            
            // 設置共享的域軸
            if (combinedPlot.getDomainAxis() != null) {
                combinedPlot.getDomainAxis().setLabelPaint(fgColor);
                combinedPlot.getDomainAxis().setTickLabelPaint(fgColor);
            }
            
            // 為每個子圖設置主題
            @SuppressWarnings("unchecked")
            java.util.List<XYPlot> subplots = combinedPlot.getSubplots();
            if (subplots != null) {
                for (XYPlot subplot : subplots) {
                    subplot.setBackgroundPaint(bgColor);
                    subplot.setDomainGridlinePaint(gridColor);
                    subplot.setRangeGridlinePaint(gridColor);
                    if (subplot.getRangeAxis() != null) {
                        subplot.getRangeAxis().setLabelPaint(fgColor);
                        subplot.getRangeAxis().setTickLabelPaint(fgColor);
                    }
                }
            }
        } else if (plot instanceof XYPlot) {
            XYPlot xyPlot = (XYPlot) plot;
            xyPlot.setBackgroundPaint(bgColor);
            xyPlot.setDomainGridlinePaint(gridColor);
            xyPlot.setRangeGridlinePaint(gridColor);
            if (xyPlot.getDomainAxis() != null) {
                xyPlot.getDomainAxis().setLabelPaint(fgColor);
                xyPlot.getDomainAxis().setTickLabelPaint(fgColor);
            }
            if (xyPlot.getRangeAxis() != null) {
                xyPlot.getRangeAxis().setLabelPaint(fgColor);
                xyPlot.getRangeAxis().setTickLabelPaint(fgColor);
            }
        } else if (plot instanceof CategoryPlot) {
            CategoryPlot categoryPlot = (CategoryPlot) plot;
            categoryPlot.setBackgroundPaint(bgColor);
            categoryPlot.setDomainGridlinePaint(gridColor);
            categoryPlot.setRangeGridlinePaint(gridColor);
            if (categoryPlot.getDomainAxis() != null) {
                categoryPlot.getDomainAxis().setLabelPaint(fgColor);
                categoryPlot.getDomainAxis().setTickLabelPaint(fgColor);
            }
            if (categoryPlot.getRangeAxis() != null) {
                categoryPlot.getRangeAxis().setLabelPaint(fgColor);
                categoryPlot.getRangeAxis().setTickLabelPaint(fgColor);
            }
        }

        // [CHART] 合併重繪
        scheduleChartFlush();
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

// === 內外盤分析大圖面板（方案B：於資訊分頁顯示） ===
class InOutAnalyticsPanel extends JPanel {
    private final RatioBar ratioBar = new RatioBar();
    private final SparkPanel spark = new SparkPanel();
    private final DeltaPanel delta = new DeltaPanel();
    private final JLabel desc = new JLabel();
    private final DefaultListModel<String> signalModel = new DefaultListModel<>();
    private final JList<String> signalList = new JList<>(signalModel);
    private int lastInPct = -1;
    private int curWindow = 120, curConsecutive = 2, curThreshold = 65, curEff = 65; private String curMode = "一般";

    public InOutAnalyticsPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        ratioBar.setPreferredSize(new Dimension(600, 40));
        add(ratioBar, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(8,0));
        JPanel charts = new JPanel(new GridLayout(1, 2, 10, 0));
        charts.add(spark);
        charts.add(delta);
        center.add(charts, BorderLayout.CENTER);
        // 訊號清單
        signalList.setVisibleRowCount(5);
        signalList.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        JScrollPane sigScroll = new JScrollPane(signalList);
        sigScroll.setPreferredSize(new Dimension(220, 120));
        sigScroll.setBorder(BorderFactory.createTitledBorder("連續窗訊號"));
        center.add(sigScroll, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        // 說明區（HTML，可動態更新當前值）
        desc.setBorder(new EmptyBorder(6,4,0,4));
        desc.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        desc.setText("<html>"
                + "<b>解釋：</b> 內盤=成交價≤買一或賣方主動；外盤=成交價≥賣一或買方主動。"
                + " 建議：連續外盤比＞65%且價格創短線新高→偏多；內盤比＞65%且價格創新低→偏空。"
                + " 事件模式（新聞/財報）建議將門檻上調5%/10%。"
                + "</html>");
        add(desc, BorderLayout.SOUTH);
    }

    // 外部可呼叫：更新內外盤數據
    public void setData(long inVol, long outVol, int inPct) {
        ratioBar.setData(inVol, outVol);
        spark.pushRatio(inPct);
        delta.pushDelta((int) (outVol - inVol));
        int outPct = 100 - Math.max(0, Math.min(100, inPct));
        desc.setText(String.format("<html>"
                + "<b>解釋：</b> 內盤=成交價≤買一或賣方主動；外盤=成交價≥賣一或買方主動。"
                + " <b>目前：</b> 內盤 <span style='color:#2E7D32'>%,d</span> ( %d%% )，外盤 <span style='color:#C62828'>%,d</span> ( %d%% )。"
                + " 建議：連續外盤比＞65%% 並且價格創短線新高⇒偏多；連續內盤比＞65%% 並且價格創短線新低⇒偏空。"
                + "</html>", inVol, inPct, outVol, outPct));
        // 簡單 SMA/EMA 顯示與穿越偵測
        double sma = spark.computeSMA(20);
        double ema = spark.computeEMA(20);
        String head = String.format("SMA20=%.1f%%  EMA20=%.1f%%", sma, ema);
        signalList.setBorder(BorderFactory.createTitledBorder(head));
        if (lastInPct >= 0) {
            int th = 65; // 可與事件模式連動
            if (lastInPct <= th && inPct > th) addSignal("內盤上穿 " + th + "% → 偏空");
            if (lastInPct >= (100-th) && inPct < (100-th)) addSignal("外盤上穿 " + th + "% → 偏多");
        }
        lastInPct = inPct;
        revalidate();
        repaint();
    }

    // 外部可呼叫：同步顯示參數（來自五檔控制面板）
    public void setParams(int window, int consecutive, int threshold, String mode, int effTh){
        curWindow = window; curConsecutive = consecutive; curThreshold = threshold; curMode = mode; curEff = effTh;
        desc.setText(String.format("<html>"
                + "<b>解釋：</b> 內盤=成交價≤買一或賣方主動；外盤=成交價≥賣一或買方主動。"
                + " 建議：連續外盤比＞%d%%且價格創短線新高→偏多；內盤比＞%d%%且價格創新低→偏空。"
                + " 事件模式：%s（門檻生效=%d%%），連續窗=%d，觀察窗口=%d。"
                + "</html>", curEff, curEff, curMode, curEff, curConsecutive, curWindow));
    }
    private void addSignal(String s){
        String t = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        signalModel.add(0, t+"  "+s);
        while (signalModel.size()>50) signalModel.removeElementAt(signalModel.size()-1);
    }

    // 左綠右紅長條
    private static class RatioBar extends JPanel {
        private long inVol, outVol;
        private final Color green = new Color(67, 160, 71);
        private final Color red = new Color(198, 40, 40);
        private final Color border = new Color(180, 180, 180);
        public void setData(long inVol, long outVol) { this.inVol = Math.max(0, inVol); this.outVol = Math.max(0, outVol); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(); int pad = 6; int barH = h - pad * 2; int x = pad, y = pad;
            g2.setColor(new Color(245,245,245)); g2.fillRoundRect(x, y, w - pad * 2, barH, 10, 10);
            g2.setColor(border); g2.drawRoundRect(x, y, w - pad * 2, barH, 10, 10);
            long total = Math.max(1, inVol + outVol); int inW = (int) Math.round((w - pad * 2) * (inVol / (double) total));
            g2.setColor(green); g2.fillRoundRect(x, y, Math.max(0, inW), barH, 10, 10);
            g2.setColor(red); g2.fillRoundRect(x + inW, y, Math.max(0, (w - pad * 2) - inW), barH, 10, 10);
            int inPct = (int) Math.round(inVol * 100.0 / total); int outPct = 100 - inPct;
            g2.setColor(Color.WHITE); g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.drawString("內 " + inPct + "%", x + 8, y + barH - 6);
            String rs = "外 " + outPct + "%"; int sw = g2.getFontMetrics().stringWidth(rs);
            g2.drawString(rs, x + (w - pad * 2) - sw - 8, y + barH - 6);
            g2.dispose();
        }
    }

    // 內盤百分比歷史（最近60點）
    private static class SparkPanel extends JPanel {
        private final java.util.Deque<Integer> hist = new java.util.ArrayDeque<>();
        private int window = 120;
        public void pushRatio(int v){ hist.addLast(Math.max(0, Math.min(100, v))); while(hist.size() > window) hist.removeFirst(); repaint(); }
        public double computeSMA(int n){ if (hist.isEmpty()) return 0; int c=0,sum=0; for(Integer v:hist){sum+=v; c++;} return sum*1.0/Math.max(1, Math.min(c,n)); }
        public double computeEMA(int n){ if (hist.isEmpty()) return 0; double k=2.0/(n+1); double ema=hist.peekFirst(); for(Integer v:hist){ ema = v*k + ema*(1-k);} return ema; }
        @Override protected void paintComponent(Graphics g){ super.paintComponent(g); Graphics2D g2=(Graphics2D)g.create(); int w=getWidth(),h=getHeight(); g2.setColor(new Color(250,250,250)); g2.fillRect(0,0,w,h); g2.setColor(new Color(220,220,220)); g2.drawRect(0,0,w-1,h-1); if(hist.isEmpty()){g2.dispose();return;} int i=0,px=0,py=h-(hist.peekFirst()*h/100); for(Integer v:hist){ int x=i*(w-1)/Math.max(1,window-1); int y=h-(v*h/100); if(i>0){ g2.setColor(new Color(33,150,243)); g2.drawLine(px,py,x,y);} px=x; py=y; i++; } g2.dispose(); }
    }

    // 累積 Delta（外-內）
    private static class DeltaPanel extends JPanel {
        private final java.util.Deque<Integer> pts = new java.util.ArrayDeque<>();
        private int window = 120; private int cum = 0;
        public void pushDelta(int d){ cum += d; pts.addLast(cum); while(pts.size()>window) pts.removeFirst(); repaint(); }
        @Override protected void paintComponent(Graphics g){ super.paintComponent(g); Graphics2D g2=(Graphics2D)g.create(); int w=getWidth(),h=getHeight(); g2.setColor(new Color(250,250,250)); g2.fillRect(0,0,w,h); g2.setColor(new Color(220,220,220)); g2.drawRect(0,0,w-1,h-1); if(pts.isEmpty()){g2.dispose();return;} int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE; for(Integer v:pts){min=Math.min(min,v);max=Math.max(max,v);} if(min==max){min--;max++;} int i=0,px=0,py=h-(pts.peekFirst()-min)*(h-1)/(max-min); for(Integer v:pts){ int x=i*(w-1)/Math.max(1,window-1); int y=h-(v-min)*(h-1)/(max-min); g2.setColor(v>=0? new Color(67,160,71): new Color(198,40,40)); if(i>0) g2.drawLine(px,py,x,y); px=x; py=y; i++; } g2.dispose(); }
    }
}

// === 信號指示器面板（顯示在成交量圖表下方） ===
class SignalIndicatorPanel extends JPanel {
    private final JLabel bullSignalLabel;
    private final JLabel bearSignalLabel;
    private final JLabel bigBuyLabel;
    private final JLabel bigSellLabel;
    private final JLabel tickBuyImbLabel;
    private final JLabel tickSellImbLabel;
    
    private int bullCount = 0;
    private int bearCount = 0;
    private int bigBuyCount = 0;
    private int bigSellCount = 0;
    private int tickBuyImbCount = 0;
    private int tickSellImbCount = 0;
    
    public SignalIndicatorPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 15, 8));
        setPreferredSize(new Dimension(0, 45));
        setBackground(new Color(250, 250, 250));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        
        Font labelFont = new Font("Microsoft JhengHei", Font.PLAIN, 12);
        Font countFont = new Font("Microsoft JhengHei", Font.BOLD, 13);
        
        // 多頭信號
        JPanel bullPanel = createSignalItem("▲ 多頭信號", new Color(239, 83, 80), labelFont, countFont);
        bullSignalLabel = (JLabel) bullPanel.getComponent(1);
        add(bullPanel);
        
        // 空頭信號
        JPanel bearPanel = createSignalItem("▼ 空頭信號", new Color(38, 166, 154), labelFont, countFont);
        bearSignalLabel = (JLabel) bearPanel.getComponent(1);
        add(bearPanel);
        
        // 分隔線
        add(createSeparator());
        
        // 大買單
        JPanel bigBuyPanel = createSignalItem("● 大買單", new Color(239, 83, 80), labelFont, countFont);
        bigBuyLabel = (JLabel) bigBuyPanel.getComponent(1);
        add(bigBuyPanel);
        
        // 大賣單
        JPanel bigSellPanel = createSignalItem("● 大賣單", new Color(38, 166, 154), labelFont, countFont);
        bigSellLabel = (JLabel) bigSellPanel.getComponent(1);
        add(bigSellPanel);
        
        // 分隔線
        add(createSeparator());
        
        // Tick買盤失衡
        JPanel tickBuyPanel = createSignalItem("↑ 買盤失衡", new Color(255, 152, 0), labelFont, countFont);
        tickBuyImbLabel = (JLabel) tickBuyPanel.getComponent(1);
        add(tickBuyPanel);
        
        // Tick賣盤失衡
        JPanel tickSellPanel = createSignalItem("↓ 賣盤失衡", new Color(156, 39, 176), labelFont, countFont);
        tickSellImbLabel = (JLabel) tickSellPanel.getComponent(1);
        add(tickSellPanel);
    }
    
    private JPanel createSignalItem(String label, Color color, Font labelFont, Font countFont) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);
        
        // 提取符號和文字（假設格式為 "符號 文字"）
        String[] parts = label.split(" ", 2);
        String symbol = parts.length > 0 ? parts[0] : "";
        String text = parts.length > 1 ? parts[1] : "";
        
        // 將顏色轉換為HTML格式
        String colorHex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        
        // 使用HTML給符號上色
        String htmlLabel = String.format("<html><span style='color:%s; font-weight:bold;'>%s</span> %s</html>", 
                                         colorHex, symbol, text);
        
        JLabel nameLabel = new JLabel(htmlLabel);
        nameLabel.setFont(labelFont);
        nameLabel.setForeground(new Color(80, 80, 80));
        
        JLabel countLabel = new JLabel("0");
        countLabel.setFont(countFont);
        countLabel.setForeground(color);
        
        panel.add(nameLabel);
        panel.add(countLabel);
        
        return panel;
    }
    
    private JPanel createSeparator() {
        JPanel sep = new JPanel();
        sep.setPreferredSize(new Dimension(1, 25));
        sep.setBackground(new Color(200, 200, 200));
        return sep;
    }
    
    // 更新多頭信號
    public void updateBullSignal(int count) {
        this.bullCount = count;
        bullSignalLabel.setText(String.valueOf(count));
        if (count > 0) {
            bullSignalLabel.setFont(bullSignalLabel.getFont().deriveFont(Font.BOLD, 14f));
        }
    }
    
    // 更新空頭信號
    public void updateBearSignal(int count) {
        this.bearCount = count;
        bearSignalLabel.setText(String.valueOf(count));
        if (count > 0) {
            bearSignalLabel.setFont(bearSignalLabel.getFont().deriveFont(Font.BOLD, 14f));
        }
    }
    
    // 更新大買單
    public void updateBigBuy(int count) {
        this.bigBuyCount = count;
        bigBuyLabel.setText(String.valueOf(count));
    }
    
    // 更新大賣單
    public void updateBigSell(int count) {
        this.bigSellCount = count;
        bigSellLabel.setText(String.valueOf(count));
    }
    
    // 更新Tick買盤失衡
    public void updateTickBuyImb(int count) {
        this.tickBuyImbCount = count;
        tickBuyImbLabel.setText(String.valueOf(count));
    }
    
    // 更新Tick賣盤失衡
    public void updateTickSellImb(int count) {
        this.tickSellImbCount = count;
        tickSellImbLabel.setText(String.valueOf(count));
    }
    
    // 全部更新
    public void updateAllSignals(int bull, int bear, int bigBuy, int bigSell, int tickBuy, int tickSell) {
        updateBullSignal(bull);
        updateBearSignal(bear);
        updateBigBuy(bigBuy);
        updateBigSell(bigSell);
        updateTickBuyImb(tickBuy);
        updateTickSellImb(tickSell);
    }
}
