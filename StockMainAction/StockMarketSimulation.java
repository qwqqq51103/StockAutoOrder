package StockMainAction;

import AIStrategies.RetailInvestorAI;
import AIStrategies.MainForceStrategyWithOrderBook;
import AIStrategies.PersonalAI;
import OrderManagement.OrderBookTable;
import OrderManagement.OrderViewer;
import Analysis.MarketBehavior;
import Analysis.MarketAnalyzer;
import Core.Order;
import Core.OrderBook;
import Core.Stock;
import Logging.LogViewerWindow;
import javax.swing.*;
import java.awt.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.awt.Font;
import java.awt.Color;
import java.util.concurrent.locks.ReentrantLock;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.plot.ValueMarker;
import java.awt.BasicStroke;
import javax.swing.JOptionPane;
import org.jfree.chart.plot.Plot;
import javafx.util.Pair;
import Logging.MarketLogger;

public class StockMarketSimulation {

    private JFrame frame;  // 主 GUI 框架
    private JFrame controlFrame; // 控制視窗框架
    private JLabel stockPriceLabel, retailCashLabel, retailStocksLabel, mainForceCashLabel, mainForceStocksLabel,
            targetPriceLabel, averageCostPriceLabel, fundsLabel, inventoryLabel, weightedAveragePriceLabel;
    private JLabel userStockLabel, userCashLabel, userAvgPriceLabel, userTargetPrice; // 個人資訊標籤
    private JTextArea infoTextArea; // 信息顯示區
    private JFreeChart retailProfitChart, mainForceProfitChart, volumeChart;  // 散戶和主力損益表的圖表
    private ChartPanel retailProfitChartPanel, mainForceProfitChartPanel;  // 散戶和主力損益表的圖表面板
    private Stock stock;  // 股票實例
    private OrderBook orderBook;  // 訂單簿實例
    private XYSeries priceSeries;  // 繪製股價的數據集
    private DefaultCategoryDataset volumeDataset;  // 成交量的數據集
    private int timeStep;  // 時間步驟，控制模擬步驟增量
    private List<RetailInvestorAI> retailInvestors;  // 多個散戶的列表
    private OrderBookTable orderBookTable;  // 顯示訂單簿的表格
    private ScheduledExecutorService executorService;  // 用於啟動模擬的執行緒池
    private Random random = new Random();  // 隨機數產生器
    private MainForceStrategyWithOrderBook mainForce;  // 主力策略實例
    private XYSeries smaSeries;  // 顯示 SMA（簡單移動平均線）的數據集
    private MarketAnalyzer marketAnalyzer;  // 市場分析器
    private MarketBehavior marketBehavior;  // 市場行為模擬
    private List<Color> colorList = new ArrayList<>();  // 用於成交量圖表的顏色列表
    private MatchingEnginePanel matchingEnginePanel;

    private double initialRetailCash = 100000, initialMainForceCash = 200000;  // 初始現金
    private int initialRetails = 1;  // 初始散戶數量
    private int marketBehaviorStock = 5000; //市場數量
    private double marketBehaviorGash = 10000; //市場現金

    private final ReentrantLock orderBookLock = new ReentrantLock();
    private final ReentrantLock marketAnalyzerLock = new ReentrantLock();

    private XYSeries volatilitySeries;  // 顯示波動性的數據集
    private XYSeries rsiSeries;          // 顯示 RSI 的數據集
    private XYSeries wapSeries;          // 顯示加權平均價格的數據集

    // 按鈕
    private JButton stopButton, limitBuyButton, limitSellButton, marketBuyButton, marketSellButton, cancelOrderButton;

    // 用戶投資者 - 這裡改成 PersonalAI
    private PersonalAI userInvestor;

    private static final MarketLogger logger = MarketLogger.getInstance();

    // 啟動價格波動模擬
    private void startAutoPriceFluctuation() {
        int initialDelay = 0; // 初始延遲
        int period = 500; // 執行間隔（單位：毫秒）

        executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(() -> {
            try {
                timeStep++;

                // 1. 市場行為：模擬市場的訂單提交
                try {
                    marketBehavior.marketFluctuation(
                            stock,
                            orderBook,
                            marketAnalyzer.calculateVolatility(),
                            (int) marketAnalyzer.getRecentAverageVolume());
                    logger.info(String.format("市場行為模擬：時間步長 %d", timeStep), "MARKET_BEHAVIOR");
                } catch (Exception e) {
                    logger.error("市場行為模擬發生錯誤：" + e.getMessage(), "MARKET_BEHAVIOR");
                    e.printStackTrace();
                }

                // 2. 散戶行為：執行散戶決策
                try {
                    executeRetailInvestorDecisions();
                } catch (Exception e) {
                    System.err.println("散戶決策發生錯誤：" + e.getMessage());
                    e.printStackTrace();
                }

                // 3. 主力行為：執行主力決策
                try {
                    mainForce.makeDecision();
                } catch (Exception e) {
                    System.err.println("主力決策發生錯誤：" + e.getMessage());
                    e.printStackTrace();
                }

                // 4. 處理訂單簿，撮合訂單（需加鎖保護）
                try {
                    orderBookLock.lock(); // 加鎖
                    orderBook.processOrders(stock);
                } catch (Exception e) {
                    System.err.println("訂單簿處理發生錯誤：" + e.getMessage());
                    e.printStackTrace();
                } finally {
                    orderBookLock.unlock(); // 解鎖
                }

                // 5. 更新市場分析數據（需加鎖保護）
                try {
                    double newPrice = stock.getPrice();
                    int newVolume = stock.getVolume(); // 確保 Stock 類別有 getVolume 方法
                    marketAnalyzerLock.lock(); // 加鎖
                    double sma = marketAnalyzer.calculateSMA();
                    double volatility = marketAnalyzer.calculateVolatility();

                    // 更新圖表和界面顯示（界面更新不需要加鎖，單獨操作）
                    SwingUtilities.invokeLater(() -> {
                        try {
                            updateMarketBehaviorDisplay(); // 更新資金和庫存
                            updateLabels();
                            updatePriceChart();
                            updateTechnicalIndicators(); // 更新技術指標
                            updateProfitTab();
                            updateOrderBookDisplay();
                            updateUserInfo(); // 更新用戶資訊顯示
//                            updateVolumeChart(1);
                        } catch (Exception e) {
                            System.err.println("界面更新發生錯誤：" + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    System.err.println("市場分析數據更新發生錯誤：" + e.getMessage());
                    e.printStackTrace();
                } finally {
                    marketAnalyzerLock.unlock(); // 解鎖
                    validateMarketInventory(); //
                }
            } catch (Exception e) {
                logger.error("主模擬流程發生未處理的錯誤：" + e.getMessage(), "MARKET_SIMULATION");
                e.printStackTrace();
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    // 獲取 SMA
    public MarketAnalyzer getMarketAnalyzer() {
        return marketAnalyzer;
    }

    // 讓所有自動化散戶做出決策並提交訂單
    private void executeRetailInvestorDecisions() {
        for (RetailInvestorAI investor : retailInvestors) {
            investor.makeDecision(stock, orderBook, this);
        }
    }

    // 停止自動價格波動
    public void stopAutoPriceFluctuation() {
        logger.info("停止市場價格波動模擬", "MARKET_SIMULATION");
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                    logger.warn("強制關閉模擬執行緒池", "MARKET_SIMULATION");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                logger.error(e, "MARKET_SIMULATION");
                Thread.currentThread().interrupt();
            }
        }
    }

    // 初始化 構造函數中添加日誌記錄
    public StockMarketSimulation() {
        logger.info("初始化股票市場模擬", "SYSTEM_INIT");
        try {
            initializeSimulation();
            initializeCharts();
            initializeGUI();
            initializeControlFrame();
            addButtonActions();
            startAutoPriceFluctuation();
            logger.info("股票市場模擬初始化完成", "SYSTEM_INIT");
        } catch (Exception e) {
            logger.error(e, "SYSTEM_INIT");
            throw new RuntimeException("股票市場模擬初始化失敗", e);
        }
    }

    // 初始化多個自動化散戶
    private void initializeRetailInvestors(int numberOfInvestors) {
        retailInvestors = new ArrayList<>();
        for (int i = 0; i < numberOfInvestors; i++) {
            RetailInvestorAI investor = new RetailInvestorAI(initialRetailCash, "RetailInvestor" + (i + 1), this);
            retailInvestors.add(investor);
        }
    }

    // 初始化模擬數據和圖表
    private void initializeSimulation() {
        stock = new Stock("台積電", 10, 1000);

        // 初始化 MarketBehavior，將市場的資金和庫存管理統一放在 MarketBehavior 中
        marketBehavior = new MarketBehavior(stock.getPrice(), marketBehaviorGash, marketBehaviorStock, this); // 初始化市場行為，包括資金和庫存

        // 初始化 OrderBook，將 MarketBehavior 作為一個 Trader 參與
        orderBook = new OrderBook(this); // 假設 OrderBook 的構造函數現在只需要模擬實例

        timeStep = 0;
        marketAnalyzer = new MarketAnalyzer(2); // 設定適當的 SMA 週期

        mainForce = new MainForceStrategyWithOrderBook(orderBook, stock, this, initialMainForceCash); // 設置初始現金

        initializeRetailInvestors(initialRetails); // 初始化散戶

        // 單獨初始化用戶投資者
        userInvestor = new PersonalAI(5000000, "Personal", this, orderBook, stock);

        // priceSeries 和其他數據系列已在 initializeCharts() 中初始化
        String[] columnNames = {"買量", "買價", "賣價", "賣量"};
        Object[][] initialData = new Object[10][4];
        orderBookTable = new OrderBookTable();
    }

    // 初始化圖表
    private void initializeCharts() {
        priceSeries = new XYSeries("股價");
        priceSeries.add(timeStep, stock.getPrice());
        volumeDataset = new DefaultCategoryDataset();

        smaSeries = new XYSeries("SMA");
        smaSeries.add(timeStep, marketAnalyzer.calculateSMA());

        // 初始化新增的技術指標數據系列
        volatilitySeries = new XYSeries("波動性");
        volatilitySeries.add(timeStep, marketAnalyzer.calculateVolatility());

        rsiSeries = new XYSeries("RSI");
        rsiSeries.add(timeStep, marketAnalyzer.getRSI());

        wapSeries = new XYSeries("加權平均價格");
        wapSeries.add(timeStep, marketAnalyzer.getWeightedAveragePrice());

        // 初始化 colorList
        colorList = new ArrayList<>();
    }

    // 初始化控制視窗
    private void initializeControlFrame() {
        controlFrame = new JFrame("股票市場模擬 - 控制視窗");
        controlFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        controlFrame.setSize(400, 400); // 增加窗口大小以容納新增的元件
        controlFrame.setLocationRelativeTo(null); // 居中顯示

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayout(9, 1, 10, 10)); // 增加行數，適應新增按鈕

        // 個人資訊標籤
        JPanel userInfoPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        userStockLabel = new JLabel("個人股票數量: " + userInvestor.getAccount().getStockInventory());
        userCashLabel = new JLabel("個人金錢餘額: " + String.format("%.2f", userInvestor.getAccount().getAvailableFunds()));
        userAvgPriceLabel = new JLabel("個人平均價: " + String.format("%.2f", userInvestor.getAverageCostPrice()));
        userTargetPrice = new JLabel("個人目標價: " + String.format("%.2f", userInvestor.getTakeProfitPrice()));
        userInfoPanel.add(userStockLabel);
        userInfoPanel.add(userCashLabel);
        userInfoPanel.add(userAvgPriceLabel);
        userInfoPanel.add(userTargetPrice);

        // 按鈕
        limitBuyButton = new JButton("限價買入");
        limitSellButton = new JButton("限價賣出");
        marketBuyButton = new JButton("市價買入");
        marketSellButton = new JButton("市價賣出");
        cancelOrderButton = new JButton("取消訂單");
        JButton viewOrdersButton = new JButton("查看訂單"); // 新增按鈕
        stopButton = new JButton("停止");

        // 添加元件到控制面板
        controlPanel.add(new JLabel("個人資訊:")); // 標題
        controlPanel.add(userInfoPanel);
        controlPanel.add(limitBuyButton);
        controlPanel.add(limitSellButton);
        controlPanel.add(marketBuyButton);
        controlPanel.add(marketSellButton);
        controlPanel.add(cancelOrderButton);
        controlPanel.add(viewOrdersButton); // 添加新按鈕
        controlPanel.add(stopButton);

        // 添加事件處理器給新按鈕
        viewOrdersButton.addActionListener(e -> {
            OrderViewer orderViewer = new OrderViewer(orderBook);
            orderViewer.setVisible(true);
        });

        controlFrame.add(controlPanel, BorderLayout.CENTER);

        controlFrame.setVisible(true);
    }

    /**
     * 修改 initializeGUI 方法，整合 MatchingEnginePanel
     */
    private void initializeGUI() {
        frame = new JFrame("股票市場模擬");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1600, 900); // 增加窗口大小以容納新增的元件
        frame.setLocationRelativeTo(null); // 居中顯示

        // 創建撮合引擎控制面板
        MatchingEnginePanel matchingEnginePanel = new MatchingEnginePanel(orderBook);

        JPanel chartPanel = new JPanel(new GridLayout(4, 1)); // 調整為4行1列
        chartPanel.add(new ChartPanel(createPriceChart()));
        chartPanel.add(new ChartPanel(createVolatilityChart()));
        chartPanel.add(new ChartPanel(createRSIChart()));
        chartPanel.add(new ChartPanel(createVolumeChart())); // 新增成交量圖表
        // 如果需要，可以新增 WAP 圖表
        // chartPanel.add(new ChartPanel(createWAPChart()));

        JPanel labelPanel = new JPanel(new GridLayout(5, 2, 10, 10)); // 調整為5行2列，增加間距
        initializeLabels(labelPanel);

        // 將撮合引擎面板添加到標籤面板下方
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(labelPanel, BorderLayout.CENTER);
        southPanel.add(matchingEnginePanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanel, southPanel);
        splitPane.setResizeWeight(0.7);

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoTextArea = new JTextArea(8, 30);
        infoTextArea.setEditable(false);
        JScrollPane infoScrollPane = new JScrollPane(infoTextArea);
        JSplitPane infoSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, orderBookTable.getScrollPane(), infoScrollPane);
        infoSplitPane.setResizeWeight(0.3);

        // 主界面分頁面板
        JTabbedPane tabbedPane = new JTabbedPane();

        // 將主圖表添加到第一個分頁
        JPanel mainPanel = new JPanel(new BorderLayout());
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane, infoSplitPane);
        mainSplitPane.setResizeWeight(0.8);
        mainPanel.add(mainSplitPane, BorderLayout.CENTER);
        tabbedPane.addTab("市場圖表", mainPanel);

        // 創建損益表分頁
        JPanel profitPanel = new JPanel(new GridLayout(2, 1));
        retailProfitChart = createProfitChart("散戶損益", "散戶", retailInvestors.size());
        mainForceProfitChart = createProfitChart("主力損益", "主力", 1);
        ChartPanel retailProfitChartPanel = new ChartPanel(retailProfitChart);
        ChartPanel mainForceProfitChartPanel = new ChartPanel(mainForceProfitChart);
        profitPanel.add(retailProfitChartPanel);
        profitPanel.add(mainForceProfitChartPanel);
        tabbedPane.addTab("損益表", profitPanel);

        // 創建技術指標分頁
        JPanel indicatorsPanel = new JPanel(new GridLayout(3, 1));
        indicatorsPanel.add(new ChartPanel(createVolatilityChart()));
        indicatorsPanel.add(new ChartPanel(createRSIChart()));
        indicatorsPanel.add(new ChartPanel(createWAPChart())); // 如有需要，新增 WAP 圖表
        tabbedPane.addTab("技術指標", indicatorsPanel);

        // 創建交易設置分頁，包含撮合引擎控制面板
        JPanel settingsPanel = new JPanel(new BorderLayout());

        // 第二種方式：將撮合引擎控制面板放在新的"交易設置"分頁中
        // 可以在此頁面添加其他交易設置控件
        JPanel tradingSettingsPanel = new JPanel();
        tradingSettingsPanel.setLayout(new BoxLayout(tradingSettingsPanel, BoxLayout.Y_AXIS));
        tradingSettingsPanel.add(matchingEnginePanel);

        // 可以添加更多設置控件
        // ...
        settingsPanel.add(tradingSettingsPanel, BorderLayout.NORTH);
        JPanel emptyPanel = new JPanel();
        emptyPanel.setPreferredSize(new Dimension(600, 400));
        settingsPanel.add(emptyPanel, BorderLayout.CENTER);

        tabbedPane.addTab("交易設置", settingsPanel);

        // 將主分頁面板添加至主視窗
        frame.add(tabbedPane, BorderLayout.CENTER);

        // 設置關閉事件
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopAutoPriceFluctuation();
                System.exit(0);
            }
        });

        frame.setVisible(true);
        // 打開日誌視窗
        openLogViewer();
    }

    // 添加按鈕事件處理器
    private void addButtonActions() {
        // 停止按鈕
        stopButton.addActionListener(e -> {
            if (executorService != null && !executorService.isShutdown()) {
                stopAutoPriceFluctuation();
                stopButton.setText("開始");
                updateInfoTextArea("模擬已停止。");
            } else {
                startAutoPriceFluctuation();
                stopButton.setText("停止");
                updateInfoTextArea("模擬已開始。");
            }
        });

        // ＝＝＝＝＝＝＝＝＝＝＝ 限價買入按鈕 ＝＝＝＝＝＝＝＝＝＝＝
        limitBuyButton.addActionListener(e -> {
            // 先輸入股數
            String qtyStr = JOptionPane.showInputDialog(
                    controlFrame, "輸入購買股數:", "限價買入", JOptionPane.PLAIN_MESSAGE);
            if (qtyStr == null) {
                return;   // 取消
            }
            // 再輸入價格
            String priceStr = JOptionPane.showInputDialog(
                    controlFrame, "輸入掛單價格:", "限價買入", JOptionPane.PLAIN_MESSAGE);
            if (priceStr == null) {
                return; // 取消
            }
            try {
                int quantity = Integer.parseInt(qtyStr.trim());
                double limitPrice = Double.parseDouble(priceStr.trim());

                if (quantity <= 0 || limitPrice <= 0) {
                    JOptionPane.showMessageDialog(controlFrame,
                            "股數與價格都必須大於 0。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                double totalCost = limitPrice * quantity;
                if (userInvestor.getAccount().getAvailableFunds() < totalCost) {
                    JOptionPane.showMessageDialog(controlFrame,
                            "資金不足以購買 " + quantity + " 股。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 送出限價買單
                int result = userInvestor.限價買入操作(quantity, limitPrice);
                if (result == 0) {
                    JOptionPane.showMessageDialog(controlFrame,
                            "限價買入失敗：可能資金不足、市場無對應賣單，或價格超出允許範圍。",
                            "失敗", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                updateInfoTextArea(String.format(
                        "限價買入 %d 股 @ %.2f，總成本 %.2f", quantity, limitPrice, totalCost));
                updateOrderBookDisplay();
                updateUserInfo();

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(controlFrame,
                        "請輸入有效的數字。", "錯誤", JOptionPane.ERROR_MESSAGE);
            }
        });

// ＝＝＝＝＝＝＝＝＝＝＝ 限價賣出按鈕 ＝＝＝＝＝＝＝＝＝＝＝
        limitSellButton.addActionListener(e -> {
            // 先輸入股數
            String qtyStr = JOptionPane.showInputDialog(
                    controlFrame, "輸入賣出股數:", "限價賣出", JOptionPane.PLAIN_MESSAGE);
            if (qtyStr == null) {
                return;   // 取消
            }
            // 再輸入價格
            String priceStr = JOptionPane.showInputDialog(
                    controlFrame, "輸入掛單價格:", "限價賣出", JOptionPane.PLAIN_MESSAGE);
            if (priceStr == null) {
                return; // 取消
            }
            try {
                int quantity = Integer.parseInt(qtyStr.trim());
                double limitPrice = Double.parseDouble(priceStr.trim());

                if (quantity <= 0 || limitPrice <= 0) {
                    JOptionPane.showMessageDialog(controlFrame,
                            "股數與價格都必須大於 0。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (userInvestor.getAccount().getStockInventory() < quantity) {
                    JOptionPane.showMessageDialog(controlFrame,
                            "持股不足以賣出 " + quantity + " 股。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                double totalRevenue = limitPrice * quantity;

                // 送出限價賣單
                int result = userInvestor.限價賣出操作(quantity, limitPrice);
                if (result == 0) {
                    JOptionPane.showMessageDialog(controlFrame,
                            "限價賣出失敗：可能持股不足、市場無對應買單，或價格超出允許範圍。",
                            "失敗", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                updateInfoTextArea(String.format(
                        "限價賣出 %d 股 @ %.2f，總收入 %.2f", quantity, limitPrice, totalRevenue));
                updateOrderBookDisplay();
                updateUserInfo();

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(controlFrame,
                        "請輸入有效的數字。", "錯誤", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 市價買入按鈕
        marketBuyButton.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(controlFrame, "輸入購買股數:", "市價買入", JOptionPane.PLAIN_MESSAGE);
            if (input != null) {
                try {
                    int quantity = Integer.parseInt(input);
                    if (quantity <= 0) {
                        JOptionPane.showMessageDialog(controlFrame, "股數必須大於0。", "錯誤", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    // 計算實際成交數量和成本
                    Pair<Integer, Double> result = calculateActualCost(orderBook.getSellOrders(), quantity);
                    int actualQuantity = result.getKey(); // 實際成交數量
                    double actualCost = result.getValue(); // 實際成交成本

                    // 執行市價單
                    if (actualQuantity > 0) {
                        userInvestor.市價買入操作(actualQuantity);
                        updateInfoTextArea("市價買入 " + actualQuantity + " 股，實際成本：" + String.format("%.2f", actualCost));

                        // 更新訂單簿和用戶資訊
                        updateOrderBookDisplay();
                        updateUserInfo();

                        if (actualQuantity < quantity) {
                            JOptionPane.showMessageDialog(controlFrame,
                                    "市價買入部分成交，已完成 " + actualQuantity + " 股，剩餘需求未滿足。",
                                    "部分成交",
                                    JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(controlFrame, "市場中無足夠賣單，無法完成市價買入。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(controlFrame, "請輸入有效的股數。", "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // 市價賣出按鈕
        marketSellButton.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(controlFrame, "輸入賣出股數:", "市價賣出", JOptionPane.PLAIN_MESSAGE);
            if (input != null) {
                try {
                    int quantity = Integer.parseInt(input);
                    if (quantity <= 0) {
                        JOptionPane.showMessageDialog(controlFrame, "股數必須大於0。", "錯誤", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    // 檢查用戶是否有足夠的持股
                    if (userInvestor.getAccount().getStockInventory() >= quantity) {
                        // 計算實際成交數量和收入
                        Pair<Integer, Double> result = calculateActualRevenue(orderBook.getBuyOrders(), quantity);
                        int actualQuantity = result.getKey(); // 實際成交數量
                        double actualRevenue = result.getValue(); // 實際收入

                        // 執行市價賣出
                        if (actualQuantity > 0) {
                            userInvestor.市價賣出操作(actualQuantity);
                            updateInfoTextArea("市價賣出 " + actualQuantity + " 股，實際收入：" + String.format("%.2f", actualRevenue));

                            // 更新訂單簿和用戶資訊
                            updateOrderBookDisplay();
                            updateUserInfo();

                            if (actualQuantity < quantity) {
                                JOptionPane.showMessageDialog(controlFrame,
                                        "市價賣出部分成交，已完成 " + actualQuantity + " 股，剩餘需求未滿足。",
                                        "部分成交",
                                        JOptionPane.INFORMATION_MESSAGE);
                            }
                        } else {
                            JOptionPane.showMessageDialog(controlFrame, "市場中無足夠買單，無法完成市價賣出。", "錯誤", JOptionPane.ERROR_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(controlFrame, "持股不足以賣出 " + quantity + " 股。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(controlFrame, "請輸入有效的股數。", "錯誤", JOptionPane.ERROR_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(controlFrame, "市價賣出失敗：" + ex.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });

        cancelOrderButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(controlFrame,
                    "確定要取消所有掛單嗎？",
                    "確認取消訂單",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                // 取消所有買單
                for (Order order : new ArrayList<>(orderBook.getBuyOrders())) {
                    orderBook.cancelOrder(order.getId());
                }

                // 取消所有賣單
                for (Order order : new ArrayList<>(orderBook.getSellOrders())) {
                    orderBook.cancelOrder(order.getId());
                }

                // 更新 UI
                updateInfoTextArea("所有訂單已取消。");
                updateOrderBookDisplay();
            }
        });

    }

    /**
     * 計算市價賣出的實際收入
     *
     * @param buyOrders 買單列表
     * @param quantity 賣出數量
     * @return [實際成交數量, 成交收入]
     */
    public Pair<Integer, Double> calculateActualRevenue(List<Order> buyOrders, int quantity) {
        double actualRevenue = 0.0; // 累計實際成交收入
        int actualQuantity = 0; // 實際成交數量
        int remainingQuantity = quantity; // 剩餘需求量

        for (Order buyOrder : buyOrders) {
            if (remainingQuantity <= 0) {
                break; // 如果需求已滿足，結束
            }
            int transactionVolume = Math.min(remainingQuantity, buyOrder.getVolume());
            actualRevenue += transactionVolume * buyOrder.getPrice(); // 累加收入
            actualQuantity += transactionVolume; // 累加成交數量
            remainingQuantity -= transactionVolume; // 減少剩餘需求
        }

        return new Pair<>(actualQuantity, actualRevenue); // 返回實際成交數量和總收入
    }

    /**
     * 計算市價單的實際成交成本
     *
     * @param sellOrders 賣單列表
     * @param quantity 市價單購買數量
     * @return [實際成交數量, 成交成本]
     */
    public Pair<Integer, Double> calculateActualCost(List<Order> sellOrders, int quantity) {
        double actualCost = 0.0; // 累計實際成交成本
        int actualQuantity = 0; // 實際成交數量
        int remainingQuantity = quantity; // 剩餘需求量

        for (Order sellOrder : sellOrders) {
            if (remainingQuantity <= 0) {
                break; // 如果需求已滿足，結束
            }
            int transactionVolume = Math.min(remainingQuantity, sellOrder.getVolume());
            actualCost += transactionVolume * sellOrder.getPrice(); // 累加成本
            actualQuantity += transactionVolume; // 累加成交數量
            remainingQuantity -= transactionVolume; // 減少剩餘需求
        }

        return new Pair<>(actualQuantity, actualCost); // 返回實際成交數量和總成本
    }

    // 初始化欄位資訊
    private void initializeLabels(JPanel panel) {
        stockPriceLabel = createLabel(panel, "股票價格: " + String.format("%.2f", stock.getPrice()));
        retailCashLabel = createLabel(panel, "散戶平均現金: " + String.format("%.2f", getAverageRetailCash()));
        retailStocksLabel = createLabel(panel, "散戶平均持股: " + getAverageRetailStocks());
        // 新增主力的現金與籌碼標籤
        mainForceCashLabel = createLabel(panel, "主力現金: " + String.format("%.2f", mainForce.getAccount().getAvailableFunds()));
        mainForceStocksLabel = createLabel(panel, "主力持有籌碼: " + mainForce.getAccount().getStockInventory());
        // 新增主力目標價位和平均成本價格的顯示
        targetPriceLabel = createLabel(panel, "主力目標價位: " + String.format("%.2f", mainForce.getTargetPrice()));
        averageCostPriceLabel = createLabel(panel, "主力平均成本: " + String.format("%.2f", mainForce.getAverageCostPrice()));
        fundsLabel = createLabel(panel, "市場可用資金: " + String.format("%.2f", marketBehavior.getAvailableFunds()));
        inventoryLabel = createLabel(panel, "市場庫存: " + marketBehavior.getStockInventory());
        // 新增加權平均價格標籤
        weightedAveragePriceLabel = createLabel(panel, "加權平均價格: " + String.format("%.2f", marketAnalyzer.getWeightedAveragePrice()));
    }

    // 創建欄位
    private JLabel createLabel(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14)); // 設定字型
        panel.add(label);
        return label;
    }

    // 更新訊息區域並自動滾動到底部
    public void updateInfoTextArea(String message) {
        SwingUtilities.invokeLater(() -> {
            infoTextArea.append(message + "\n");
            // 自動滾動到最新內容
            infoTextArea.setCaretPosition(infoTextArea.getDocument().getLength());
        });
    }

    // 更新散戶資訊
    private void updateRetailAIInfo() {
        retailCashLabel.setText("散戶平均現金: " + String.format("%.2f", getAverageRetailCash()));
        retailStocksLabel.setText("散戶平均持股: " + getAverageRetailStocks());
    }

    // 獲取散戶平均金錢
    private double getAverageRetailCash() {
        return retailInvestors.stream()
                .mapToDouble(investor -> investor.getAccount().getAvailableFunds())
                .average()
                .orElse(0.0);
    }

    // 獲取散戶平均股票數量
    private int getAverageRetailStocks() {
        int totalStocks = 0;
        for (RetailInvestorAI investor : retailInvestors) {
            totalStocks += investor.getAccount().getStockInventory();  // 假設 getStockInventory() 返回持股數量
        }
        return retailInvestors.size() > 0 ? totalStocks / retailInvestors.size() : 0;
    }

    // 更新主力的現金、持股數、目標價和平均成本
    public void updateLabels() {
        SwingUtilities.invokeLater(() -> {
            stockPriceLabel.setText("股票價格: " + String.format("%.2f", stock.getPrice()));
            mainForceCashLabel.setText("主力現金: " + String.format("%.2f", mainForce.getAccount().getAvailableFunds()));
            mainForceStocksLabel.setText("主力持有籌碼: " + mainForce.getAccount().getStockInventory());
            // 更新主力目標價位和平均成本價格
            targetPriceLabel.setText("主力目標價位: " + String.format("%.2f", mainForce.getTargetPrice()));
            averageCostPriceLabel.setText("主力平均成本: " + String.format("%.2f", mainForce.getAverageCostPrice()));
            // 更新散戶資訊
            updateRetailAIInfo();
            // 更新加權平均價格標籤
            weightedAveragePriceLabel.setText("加權平均價格: " + String.format("%.2f", marketAnalyzer.getWeightedAveragePrice()));
        });
    }

    // 更新市場庫存、資金
    public void updateMarketBehaviorDisplay() {
        double funds = marketBehavior.getAvailableFunds();
        int stockInventory = marketBehavior.getStockInventory();

        // 更新 UI 標籤或顯示區域
        SwingUtilities.invokeLater(() -> {
            fundsLabel.setText("可用資金 : " + String.format("%.2f", funds));
            inventoryLabel.setText("庫存 : " + stockInventory);
        });
    }

    /**
     * 更新訂單簿顯示 - 增強版，顯示訂單類型和撮合模式
     */
    public void updateOrderBookDisplay() {
        SwingUtilities.invokeLater(() -> {
            // 創建一個更大的數據表以容納更多信息
            // 買單部分: [數量, 價格, 類型]
            // 賣單部分: [價格, 數量, 類型]
            Object[][] updatedData = new Object[12][6]; // 增加兩行用於顯示標題和撮合模式

            // 第一行顯示當前撮合模式
            updatedData[0][0] = "當前撮合模式:";
            updatedData[0][1] = orderBook.getMatchingMode().toString();
            updatedData[0][2] = "";
            updatedData[0][3] = "";
            updatedData[0][4] = "";
            updatedData[0][5] = "";

            // 第二行顯示列標題
            updatedData[1][0] = "買單數量";
            updatedData[1][1] = "買單價格";
            updatedData[1][2] = "買單類型";
            updatedData[1][3] = "賣單價格";
            updatedData[1][4] = "賣單數量";
            updatedData[1][5] = "賣單類型";

            List<Order> buyOrders = orderBook.getTopBuyOrders(10);
            List<Order> sellOrders = orderBook.getTopSellOrders(10);

            for (int i = 0; i < 10; i++) {
                int rowIndex = i + 2; // 前兩行已用於標題

                // 填充買單
                if (i < buyOrders.size()) {
                    Order buyOrder = buyOrders.get(i);
                    if (buyOrder != null && buyOrder.getTrader() != null) {
                        updatedData[rowIndex][0] = buyOrder.getVolume();

                        // 處理市價單的價格顯示
                        if (buyOrder.isMarketOrder()) {
                            updatedData[rowIndex][1] = "市價";
                        } else {
                            updatedData[rowIndex][1] = String.format("%.2f", buyOrder.getPrice());
                        }

                        // 顯示訂單類型
                        if (buyOrder.isMarketOrder()) {
                            updatedData[rowIndex][2] = "市價單";
                        } else if (buyOrder.isFillOrKill()) {
                            updatedData[rowIndex][2] = "FOK單";
                        } else {
                            updatedData[rowIndex][2] = "限價單";
                        }
                    } else {
                        updatedData[rowIndex][0] = "";
                        updatedData[rowIndex][1] = "";
                        updatedData[rowIndex][2] = "";
                    }
                } else {
                    updatedData[rowIndex][0] = "";
                    updatedData[rowIndex][1] = "";
                    updatedData[rowIndex][2] = "";
                }

                // 填充賣單
                if (i < sellOrders.size()) {
                    Order sellOrder = sellOrders.get(i);
                    if (sellOrder != null && sellOrder.getTrader() != null) {
                        // 處理市價單的價格顯示
                        if (sellOrder.isMarketOrder()) {
                            updatedData[rowIndex][3] = "市價";
                        } else {
                            updatedData[rowIndex][3] = String.format("%.2f", sellOrder.getPrice());
                        }

                        updatedData[rowIndex][4] = sellOrder.getVolume();

                        // 顯示訂單類型
                        if (sellOrder.isMarketOrder()) {
                            updatedData[rowIndex][5] = "市價單";
                        } else if (sellOrder.isFillOrKill()) {
                            updatedData[rowIndex][5] = "FOK單";
                        } else {
                            updatedData[rowIndex][5] = "限價單";
                        }
                    } else {
                        updatedData[rowIndex][3] = "";
                        updatedData[rowIndex][4] = "";
                        updatedData[rowIndex][5] = "";
                    }
                } else {
                    updatedData[rowIndex][3] = "";
                    updatedData[rowIndex][4] = "";
                    updatedData[rowIndex][5] = "";
                }
            }

            // 使用修改後的數據更新訂單簿表格
            orderBookTable.updateData(updatedData);
        });
    }

    // 更新損益表
    private void updateProfitTab() {
        DefaultCategoryDataset retailDataset = (DefaultCategoryDataset) retailProfitChart.getCategoryPlot().getDataset();
        DefaultCategoryDataset mainForceDataset = (DefaultCategoryDataset) mainForceProfitChart.getCategoryPlot().getDataset();

        // 遍歷所有散戶，更新現金、持股和損益數據
        for (int i = 0; i < retailInvestors.size(); i++) {
            RetailInvestorAI investor = retailInvestors.get(i);
            String category = "散戶" + (i + 1);

            // 更新現金與持股數據
            retailDataset.setValue(investor.getAccount().getAvailableFunds(), "現金", category);
            retailDataset.setValue(investor.getAccount().getStockInventory(), "持股", category);

            // 計算損益
            double profit = (investor.getAccount().getStockInventory() * stock.getPrice()) + investor.getAccount().getAvailableFunds() - initialRetailCash;
            retailDataset.setValue(profit, "損益", category);
        }

        // 更新主力的現金、持股和損益數據
        String mainCategory = "主力1";
        mainForceDataset.setValue(mainForce.getAccount().getAvailableFunds(), "現金", mainCategory);
        mainForceDataset.setValue(mainForce.getAccount().getStockInventory(), "持股", mainCategory);
        double mainForceProfit = (mainForce.getAccount().getStockInventory() * stock.getPrice()) + mainForce.getAccount().getAvailableFunds() - initialMainForceCash;
        mainForceDataset.setValue(mainForceProfit, "損益", mainCategory);

        // 刷新圖表以顯示最新數據
        retailProfitChart.fireChartChanged();
        mainForceProfitChart.fireChartChanged();
    }

    // 創建股價走勢圖，包含 priceSeries 和 smaSeries。
    private JFreeChart createPriceChart() {
        XYSeriesCollection dataset = new XYSeriesCollection();

        // 添加 priceSeries 和 smaSeries 到數據集
        if (priceSeries != null) {
            dataset.addSeries(priceSeries);
        }
        if (smaSeries != null) {
            dataset.addSeries(smaSeries);
        }

        return createChart("股價走勢", dataset);
    }

    // 創建市場波動性 分頁
    private JFreeChart createVolatilityChart() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(volatilitySeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "市場波動性", "時間", "波動性",
                dataset, PlotOrientation.VERTICAL, false, true, false
        );
        setChartFont(chart);

        XYPlot plot = chart.getXYPlot();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false); // 波動性可能不包含零

        return chart;
    }

    // 創建相對強弱指數 分頁
    private JFreeChart createRSIChart() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(rsiSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "相對強弱指數 (RSI)", "時間", "RSI",
                dataset, PlotOrientation.VERTICAL, false, true, false
        );
        setChartFont(chart);

        XYPlot plot = chart.getXYPlot();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 100.0); // RSI 的標準範圍

        // 添加超買和超賣水平線
        plot.addRangeMarker(new ValueMarker(70.0, Color.RED, new BasicStroke(1.0f)));
        plot.addRangeMarker(new ValueMarker(30.0, Color.GREEN, new BasicStroke(1.0f)));

        return chart;
    }

    // 加權平均價格 分頁
    private JFreeChart createWAPChart() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(wapSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "加權平均價格 (WAP)", "時間", "WAP",
                dataset, PlotOrientation.VERTICAL, false, true, false
        );
        setChartFont(chart);

        XYPlot plot = chart.getXYPlot();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);

        return chart;
    }

    // 創建成交量柱狀圖，並初始化顏色渲染
    private JFreeChart createVolumeChart() {
        JFreeChart volumeChart = ChartFactory.createBarChart(
                "成交量", "時間", "成交量", volumeDataset, PlotOrientation.VERTICAL, false, true, false
        );

        CategoryPlot plot = volumeChart.getCategoryPlot();
        BarRenderer renderer = new BarRenderer() {
            @Override
            public Paint getItemPaint(int row, int column) {
                // 確保 colorList 長度足夠
                while (colorList.size() <= column) {
                    double currentPrice = (column < priceSeries.getItemCount())
                            ? priceSeries.getY(column).doubleValue()
                            : stock.getPrice();  // 當價格序列不足時，使用當前股價
                    double previousPrice = (column > 0 && column - 1 < priceSeries.getItemCount())
                            ? priceSeries.getY(column - 1).doubleValue()
                            : currentPrice;

                    Color color;
                    if (currentPrice > previousPrice) {
                        color = Color.GREEN;  // 上漲顯示綠色
                    } else if (currentPrice < previousPrice) {
                        color = Color.RED;  // 下跌顯示紅色
                    } else {
                        color = Color.BLUE;  // 無變化顯示藍色
                    }
                    colorList.add(color);
                }

                // 確保 colorList 大小不超過 100 筆
                if (colorList.size() > 30) {
                    colorList.remove(0);
                }

                return colorList.get(column);
            }
        };

        renderer.setBarPainter(new StandardBarPainter()); // 移除漸變效果
        plot.setRenderer(renderer);

        // 設定中文字型
        Font font = new Font("Microsoft JhengHei", Font.PLAIN, 12);
        volumeChart.getTitle().setFont(font);
        plot.getDomainAxis().setLabelFont(font); // X 軸標籤
        plot.getDomainAxis().setTickLabelFont(font); // X 軸刻度
        plot.getRangeAxis().setLabelFont(font); // Y 軸標籤
        plot.getRangeAxis().setTickLabelFont(font); // Y 軸刻度

        return volumeChart;
    }

    // 創建散戶和主力的損益柱狀圖
    private JFreeChart createProfitChart(String title, String categoryPrefix, int count) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        // 初始化數據
        for (int i = 1; i <= count; i++) {
            dataset.addValue(0, "現金", categoryPrefix + i);
            dataset.addValue(0, "持股", categoryPrefix + i);
            dataset.addValue(0, "損益", categoryPrefix + i);
        }

        JFreeChart chart = ChartFactory.createBarChart(
                title, "分類", "數值",
                dataset, PlotOrientation.HORIZONTAL, true, true, false
        );

        // 設置支援中文的字型
        Font font = new Font("Microsoft JhengHei", Font.PLAIN, 12);
        chart.getTitle().setFont(font);

        CategoryPlot plot = chart.getCategoryPlot();
        CategoryAxis categoryAxis = plot.getDomainAxis();
        ValueAxis valueAxis = plot.getRangeAxis();

        // 設置分類軸和數值軸的字型
        categoryAxis.setLabelFont(font);
        categoryAxis.setTickLabelFont(font);
        valueAxis.setLabelFont(font);
        valueAxis.setTickLabelFont(font);

        // 設置圖例項目字型
        chart.getLegend().setItemFont(font);

        // 自定義圖表顏色與樣式
        BarRenderer renderer = new BarRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);    // 現金
        renderer.setSeriesPaint(1, Color.GREEN);   // 持股
        renderer.setSeriesPaint(2, Color.ORANGE);  // 損益

        plot.setRenderer(renderer);
        return chart;
    }

    // 創建一般 XY 圖表
    private JFreeChart createChart(String title, XYSeriesCollection dataset) {
        JFreeChart chart = ChartFactory.createXYLineChart(title, "時間", "價格", dataset, PlotOrientation.VERTICAL, true, true, false);
        setChartFont(chart);
        return chart;
    }

    // 更新股價走勢圖和 SMA
    private void updatePriceChart() {
        priceSeries.add(timeStep, stock.getPrice());
        keepSeriesWithinLimit(priceSeries, 9000000); // 保留最新 100 筆數據
        smaSeries.add(timeStep, marketAnalyzer.getSMA());
        keepSeriesWithinLimit(smaSeries, 9000000); // 保留最新 100 筆數據
    }

    // 新增方法來更新其他技術指標
    private void updateTechnicalIndicators() {
        // 更新波動性
        double volatility = marketAnalyzer.calculateVolatility();
        volatilitySeries.add(timeStep, volatility);
        keepSeriesWithinLimit(volatilitySeries, 100); // 保留最新 100 筆數據

        // 更新 RSI
        double rsi = marketAnalyzer.getRSI();
        rsiSeries.add(timeStep, rsi);
        keepSeriesWithinLimit(rsiSeries, 100); // 保留最新 100 筆數據

        // 更新加權平均價格
        double wap = marketAnalyzer.getWeightedAveragePrice();
        wapSeries.add(timeStep, wap);
        keepSeriesWithinLimit(wapSeries, 100); // 保留最新 100 筆數據
    }

    // 初始化累積的成交量變量
    private static int accumulatedVolume = 0;
    private int previousTimeStep = -1;

    /**
     * 更新成交量圖表，僅保留最新的 100 筆數據。
     *
     * @param volume 本次成交的量
     */
    public void updateVolumeChart(int volume) {
        accumulatedVolume += volume;

        // 確認是否進入新的時間步長
        if (isNewTimeStep()) {
            while (previousTimeStep < timeStep - 1) {
                previousTimeStep++;

                // 確保時間步長不超過 volumeDataset 的範圍
                if (volumeDataset.getColumnCount() >= 30) {
                    String firstColumnKey = (String) volumeDataset.getColumnKeys().get(0);
                    volumeDataset.removeColumn(firstColumnKey);
                }

                if (!volumeDataset.getColumnKeys().contains(String.valueOf(previousTimeStep))) {
                    volumeDataset.addValue(0, "Volume", String.valueOf(previousTimeStep));
                }

                keepCategoryDatasetWithinLimit(volumeDataset, 30);
            }

            // 添加當前時間步長的成交量
            if (!volumeDataset.getColumnKeys().contains(String.valueOf(timeStep))) {
                volumeDataset.addValue(accumulatedVolume, "Volume", String.valueOf(timeStep));
            } else {
                volumeDataset.setValue(accumulatedVolume, "Volume", String.valueOf(timeStep));
            }

            keepCategoryDatasetWithinLimit(volumeDataset, 30);

            // 取得當前和前一時間步長的價格
            double currentPrice = stock.getPrice();
            double previousPrice = (timeStep > 0 && timeStep - 1 < priceSeries.getItemCount())
                    ? priceSeries.getY(timeStep - 1).doubleValue()
                    : currentPrice;

            if (timeStep >= priceSeries.getItemCount()) {
                priceSeries.add(timeStep, currentPrice);
                SwingUtilities.invokeLater(() -> keepSeriesWithinLimit(priceSeries, 9000000));
            }

            // 根據價格變動確定顏色
            Color color = Color.BLUE; // 預設為藍色
            if (currentPrice > previousPrice) {
                color = Color.GREEN; // 價格上漲 → 綠色
            } else if (currentPrice < previousPrice) {
                color = Color.RED; // 價格下跌 → 紅色
            }

            // 確保 colorList 只保留最新 10 筆
            if (colorList.size() >= 30) {
                colorList.remove(0);
            }
            colorList.add(color);

            // 重置成交量並更新步長
            accumulatedVolume = 0;
            previousTimeStep = timeStep;
            timeStep++;
        }
    }

    private boolean isNewTimeStep() {
        return timeStep > previousTimeStep;
    }

    // 限制 XYSeries 中的數據點數量，僅保留最新的 maxPoints 筆數據
    private void keepSeriesWithinLimit(XYSeries series, int maxPoints) {
        while (series.getItemCount() > maxPoints) {
            series.remove(0); // 移除最舊的數據點
        }
    }

    // 限制 DefaultCategoryDataset 中的類別數量，僅保留最新的 maxCategories 筆數據
    private void keepCategoryDatasetWithinLimit(DefaultCategoryDataset dataset, int maxCategories) {
        while (dataset.getColumnCount() > maxCategories) {
            String firstCategory = (String) dataset.getColumnKeys().get(0);
            dataset.removeColumn(firstCategory);
            if (!colorList.isEmpty()) {
                colorList.remove(0); // 移除最舊的顏色
            }
        }
    }

    // 設定字體
    private void setChartFont(JFreeChart chart) {
        Font titleFont = new Font("Microsoft JhengHei", Font.BOLD, 18);
        Font axisFont = new Font("Microsoft JhengHei", Font.PLAIN, 12);
        chart.getTitle().setFont(titleFont);

        Plot plot = chart.getPlot();
        if (plot instanceof XYPlot) {
            XYPlot xyPlot = (XYPlot) plot;
            xyPlot.getDomainAxis().setLabelFont(axisFont);
            xyPlot.getDomainAxis().setTickLabelFont(axisFont);
            xyPlot.getRangeAxis().setLabelFont(axisFont);
            xyPlot.getRangeAxis().setTickLabelFont(axisFont);
        } else if (plot instanceof CategoryPlot) {
            CategoryPlot categoryPlot = (CategoryPlot) plot;
            categoryPlot.getDomainAxis().setLabelFont(axisFont);
            categoryPlot.getDomainAxis().setTickLabelFont(axisFont);
            categoryPlot.getRangeAxis().setLabelFont(axisFont);
            categoryPlot.getRangeAxis().setTickLabelFont(axisFont);
        }
    }

    // 在 main 方法中添加日誌
    public static void main(String[] args) {
        MarketLogger logger = MarketLogger.getInstance();
        logger.info("股票市場模擬程式啟動", "APPLICATION_START");

        SwingUtilities.invokeLater(() -> {
            try {
                new StockMarketSimulation();
                logger.info("股票市場模擬程式初始化完成", "APPLICATION_START");
            } catch (Exception e) {
                logger.error("股票市場模擬程式啟動失敗", "APPLICATION_START");
                logger.error(e, "APPLICATION_START");
                JOptionPane.showMessageDialog(null,
                        "股票市場模擬程式啟動失敗：" + e.getMessage(),
                        "錯誤",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * 啟動日誌查看視窗
     */
    public void openLogViewer() {
        SwingUtilities.invokeLater(() -> {
            new LogViewerWindow();
        });
    }

    // 檢查市場庫存
    public void validateMarketInventory() {
        int calculatedInventory = calculateMarketInventory();
        int initialInventory = marketBehaviorStock; // 更新為初始市場庫存
        if (calculatedInventory != initialInventory) {
            System.err.println("市場庫存異常：預期 " + initialInventory + "，實際 " + calculatedInventory);
        }
    }

    // 計算市場庫存
    public int calculateMarketInventory() {
        int totalInventory = 0;

        // 1. 計算主力的股票持有量
        totalInventory += mainForce.getAccount().getStockInventory();

        // 2. 計算散戶的股票持有量
        for (RetailInvestorAI investor : retailInvestors) {
            totalInventory += investor.getAccount().getStockInventory();
        }

        // 3. 計算用戶投資者的股票持有量
        if (userInvestor != null) {
            totalInventory += userInvestor.getAccount().getStockInventory();
        }

        // 4. 計算市場訂單中未成交的賣單總量
        for (Order sellOrder : orderBook.getSellOrders()) {
            totalInventory += sellOrder.getVolume();
        }

        // 5. 計算市場行為中保留的庫存（若有）
        totalInventory += marketBehavior.getStockInventory();

        // 6. 返回市場總庫存量
        return totalInventory;
    }

    // 更新用戶資訊(個人戶AI)
    private void updateUserInfo() {
        SwingUtilities.invokeLater(() -> {
            userStockLabel.setText("個人股票數量: " + userInvestor.getAccount().getStockInventory());
            userCashLabel.setText("個人金錢餘額: " + String.format("%.2f", userInvestor.getAccount().getAvailableFunds()));
            // 假設 PersonalAI 也有跟 RetailInvestorAI 一樣的 updateAverageCostPrice() 機制:
            userAvgPriceLabel.setText("個人股票均價: "
                    + String.format("%.2f", userInvestor.getAverageCostPrice()));
            // 若 PersonalAI 有自訂「目標價」概念，也可在此更新:
            userTargetPrice.setText("個人目標價: " + String.format("%.2f", userInvestor.getTakeProfitPrice()));
            // ↑ 需確定 PersonalAI 有 getTakeProfitPrice() 之類方法才可顯示
        });
    }

}
