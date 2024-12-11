package StockMainAction;

import AIStrategies.RetailInvestorAI;
import AIStrategies.MainForceStrategyWithOrderBook;
import OrderManagement.OrderBookTable;
import OrderManagement.OrderViewer;
import Analysis.MarketBehavior;
import Analysis.MarketAnalyzer;
import Core.Order;
import Core.OrderBook;
import Core.Stock;
import OrderManagement.OrderBookListener;
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
import java.util.stream.Collectors;

public class StockMarketSimulation implements OrderBookListener {

    private JFrame frame;  // 主 GUI 框架
    private JFrame controlFrame; // 控制視窗框架
    private JLabel stockPriceLabel, retailCashLabel, retailStocksLabel, mainForceCashLabel, mainForceStocksLabel,
            targetPriceLabel, averageCostPriceLabel, fundsLabel, inventoryLabel, weightedAveragePriceLabel;
    private JLabel userStockLabel, userCashLabel, userAvgPriceLabel; // 個人資訊標籤
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

    private double initialRetailCash = 10000, initialMainForceCash = 500000;  // 初始現金
    private int initialRetails = 10;  // 初始散戶數量
    private int marketBehaviorStock = 50000; //市場數量
    private double marketBehaviorGash = 0; //市場現金

    private final ReentrantLock orderBookLock = new ReentrantLock();
    private final ReentrantLock marketAnalyzerLock = new ReentrantLock();

    private XYSeries volatilitySeries;  // 顯示波動性的數據集
    private XYSeries rsiSeries;          // 顯示 RSI 的數據集
    private XYSeries wapSeries;          // 顯示加權平均價格的數據集

    // 按鈕
    private JButton stopButton, limitBuyButton, limitSellButton, marketBuyButton, marketSellButton;

    // 用戶投資者（假設第一個散戶為用戶）
    private RetailInvestorAI Tara;

    // 啟動價格波動模擬
    private void startAutoPriceFluctuation() {
        int initialDelay = 0; // 初始延遲
        int period = 100; // 執行間隔（單位：毫秒）

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
                } catch (Exception e) {
                    System.err.println("市場行為模擬發生錯誤：" + e.getMessage());
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
                    // 注意：已在 processOrders 和 marketBuy/marketSell 中調用 addTransaction
                    // 這裡可能不需要再次調用
                    // marketAnalyzer.addTransaction(newPrice, newVolume);
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
                    validateMarketInventory();
                }
            } catch (Exception e) {
                System.err.println("主執行流程發生未處理的錯誤：" + e.getMessage());
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
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    // 初始化
    public StockMarketSimulation() {
        initializeCharts();          // 先初始化圖表相關對象
        initializeSimulation();      // 再初始化模擬數據和訂單簿
        initializeGUI();             // 初始化 GUI 顯示
        initializeControlFrame();    // 初始化控制視窗
        addButtonActions();          // 為控制視窗的按鈕添加事件處理器
        startAutoPriceFluctuation(); // 啟動自動波動
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

        // 初始化 OrderBook，將 StockMarketSimulation 作為監聽者
        orderBook = new OrderBook(this); // 假設 OrderBook 的構造函數現在需要 StockMarketSimulation 實例
        orderBook.addOrderBookListener(this);

        timeStep = 0;
        marketAnalyzer = new MarketAnalyzer(2); // 設定適當的 SMA 週期

        mainForce = new MainForceStrategyWithOrderBook(orderBook, stock, this, initialMainForceCash); // 設置初始現金

        initializeRetailInvestors(initialRetails); // 初始化散戶

        // 單獨初始化用戶投資者
        Tara = new RetailInvestorAI(9999999, "Personal", this); // 修改為 "Personal" 類型

        // priceSeries 和其他數據系列已在 initializeCharts() 中初始化
        String[] columnNames = {"買量", "買價", "賣價", "賣量"};
        Object[][] initialData = new Object[10][4];
        orderBookTable = new OrderBookTable(initialData, columnNames);

        // 放置一些初始買單和賣單（可選）
        for (int i = 0; i < 10; i++) {
            // Order initialBuyOrder = new Order("buy", 10.0 + i * 0.1, 200, mainForce, false, false);
            // orderBook.submitBuyOrder(initialBuyOrder, initialBuyOrder.getPrice());
            // Order initialSellOrder = new Order("sell", 10.0 - i * 0.1, 250, marketBehavior, false, false);
            // orderBook.submitSellOrder(initialSellOrder, initialSellOrder.getPrice());
        }
    }

    // 初始化圖表
    private void initializeCharts() {
        priceSeries = new XYSeries("股價");
        priceSeries.add(timeStep, stock != null ? stock.getPrice() : 0); // 防止 stock 為 null
        volumeDataset = new DefaultCategoryDataset();

        smaSeries = new XYSeries("SMA");
        smaSeries.add(timeStep, marketAnalyzer != null ? marketAnalyzer.calculateSMA() : 0);

        // 初始化新增的技術指標數據系列
        volatilitySeries = new XYSeries("波動性");
        volatilitySeries.add(timeStep, marketAnalyzer != null ? marketAnalyzer.calculateVolatility() : 0);

        rsiSeries = new XYSeries("RSI");
        rsiSeries.add(timeStep, marketAnalyzer != null ? marketAnalyzer.getRSI() : 0);

        wapSeries = new XYSeries("加權平均價格");
        wapSeries.add(timeStep, marketAnalyzer != null ? marketAnalyzer.getWeightedAveragePrice() : 0);

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
        controlPanel.setLayout(new GridLayout(8, 1, 10, 10)); // 增加行數，適應新增按鈕

        // 個人資訊標籤
        JPanel userInfoPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        userStockLabel = new JLabel("個人股票數量: " + (Tara != null ? Tara.getAccount().getStockInventory() : 0));
        userCashLabel = new JLabel("個人金錢餘額: " + String.format("%.2f", (Tara != null ? Tara.getAccount().getAvailableFunds() : 0)));
        userInfoPanel.add(userStockLabel);
        userInfoPanel.add(userCashLabel);

        // 按鈕
        limitBuyButton = new JButton("限價買入");
        limitSellButton = new JButton("限價賣出");
        marketBuyButton = new JButton("市價買入");
        marketSellButton = new JButton("市價賣出");
        JButton viewOrdersButton = new JButton("查看訂單"); // 新增按鈕
        stopButton = new JButton("停止");

        // 添加元件到控制面板
        controlPanel.add(new JLabel("個人資訊:")); // 標題
        controlPanel.add(userInfoPanel);
        controlPanel.add(limitBuyButton);
        controlPanel.add(limitSellButton);
        controlPanel.add(marketBuyButton);
        controlPanel.add(marketSellButton);
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

    // 設定 GUI 結構
    private void initializeGUI() {
        frame = new JFrame("股票市場模擬");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1600, 900); // 增加窗口大小以容納新增的元件
        frame.setLocationRelativeTo(null); // 居中顯示

        JPanel chartPanel = new JPanel(new GridLayout(4, 1)); // 調整為4行1列
        chartPanel.add(new ChartPanel(createPriceChart()));
        chartPanel.add(new ChartPanel(createVolatilityChart()));
        chartPanel.add(new ChartPanel(createRSIChart()));
        chartPanel.add(new ChartPanel(createVolumeChart())); // 新增成交量圖表

        // 如果需要，可以新增 WAP 圖表
        // chartPanel.add(new ChartPanel(createWAPChart()));
        // 更新 GridLayout 的行數為 4 或更多，根據新增的圖表數量
        JPanel labelPanel = new JPanel(new GridLayout(5, 2, 10, 10)); // 調整為5行2列，增加間距
        initializeLabels(labelPanel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanel, labelPanel);
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
        retailProfitChart = createProfitChart("散戶損益", "散戶", retailInvestors != null ? retailInvestors.size() : 0);
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

        // 限價買入按鈕
        limitBuyButton.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(controlFrame, "輸入購買股數:", "限價買入", JOptionPane.PLAIN_MESSAGE);
            if (input != null) {
                try {
                    int quantity = Integer.parseInt(input);
                    if (quantity <= 0) {
                        JOptionPane.showMessageDialog(controlFrame, "股數必須大於0。", "錯誤", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    double currentPrice = stock.getPrice();
                    double totalCost = currentPrice * quantity;
                    if (Tara.getAccount().getAvailableFunds() >= totalCost) {
                        // 提交限價買單到訂單簿
                        Order buyOrder = new Order("buy", currentPrice, quantity, Tara, false, false); // 限價單
                        orderBook.submitBuyOrder(buyOrder, buyOrder.getPrice());
                        updateInfoTextArea("限價買入 " + quantity + " 股，總成本：" + String.format("%.2f", totalCost));
                        updateOrderBookDisplay(); // 更新訂單簿表格
                        updateUserInfo(); // 更新用戶資訊
                    } else {
                        JOptionPane.showMessageDialog(controlFrame, "資金不足以購買 " + quantity + " 股。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(controlFrame, "請輸入有效的股數。", "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // 限價賣出按鈕
        limitSellButton.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(controlFrame, "輸入賣出股數:", "限價賣出", JOptionPane.PLAIN_MESSAGE);
            if (input != null) {
                try {
                    int quantity = Integer.parseInt(input);
                    if (quantity <= 0) {
                        JOptionPane.showMessageDialog(controlFrame, "股數必須大於0。", "錯誤", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (Tara.getAccount().getStockInventory() >= quantity) {
                        double currentPrice = stock.getPrice();
                        double totalRevenue = currentPrice * quantity;
                        // 提交限價賣單到訂單簿
                        Order sellOrder = new Order("sell", currentPrice, quantity, Tara, false, false); // 限價單
                        orderBook.submitSellOrder(sellOrder, sellOrder.getPrice());
                        updateInfoTextArea("限價賣出 " + quantity + " 股，總收入：" + String.format("%.2f", totalRevenue));
                        updateOrderBookDisplay(); // 更新訂單簿表格
                        updateUserInfo(); // 更新用戶資訊
                    } else {
                        JOptionPane.showMessageDialog(controlFrame, "持股不足以賣出 " + quantity + " 股。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(controlFrame, "請輸入有效的股數。", "錯誤", JOptionPane.ERROR_MESSAGE);
                }
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
                    double currentPrice = stock.getPrice();
                    double totalCost = currentPrice * quantity;

                    // 檢查用戶是否有足夠的資金購買指定數量的股票
                    if (Tara.getAccount().getAvailableFunds() >= totalCost) {
                        // 調用訂單簿中的市價買入方法
                        orderBook.marketBuy(Tara, quantity);

                        // 更新信息區域
                        updateInfoTextArea("市價買入 " + quantity + " 股，總成本：" + String.format("%.2f", totalCost));

                        // 更新訂單簿表格和用戶資訊
                        updateOrderBookDisplay(); // 更新訂單簿表格
                        updateUserInfo(); // 更新用戶資訊
                    } else {
                        JOptionPane.showMessageDialog(controlFrame, "資金不足以購買 " + quantity + " 股。", "錯誤", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(controlFrame, "請輸入有效的股數。", "錯誤", JOptionPane.ERROR_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(controlFrame, "市價買入失敗：" + ex.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
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

                    // 檢查用戶是否有足夠的持股賣出指定數量的股票
                    if (Tara.getAccount().getStockInventory() >= quantity) {
                        double currentPrice = stock.getPrice();
                        double totalRevenue = currentPrice * quantity;

                        // 調用訂單簿中的市價賣出方法
                        orderBook.marketSell(Tara, quantity);

                        // 更新信息區域
                        updateInfoTextArea("市價賣出 " + quantity + " 股，總收入：" + String.format("%.2f", totalRevenue));

                        // 更新訂單簿表格和用戶資訊
                        updateOrderBookDisplay(); // 更新訂單簿表格
                        updateUserInfo(); // 更新用戶資訊
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
    }

    // 初始化欄位資訊
    private void initializeLabels(JPanel panel) {
        stockPriceLabel = createLabel(panel, "股票價格: " + String.format("%.2f", stock != null ? stock.getPrice() : 0.0));
        retailCashLabel = createLabel(panel, "散戶平均現金: " + String.format("%.2f", getAverageRetailCash()));
        retailStocksLabel = createLabel(panel, "散戶平均持股: " + getAverageRetailStocks()); // 假設這是整數，不需要格式化

        // 修改以下兩行，使用 %d 來格式化整數
        mainForceCashLabel = createLabel(panel, "主力現金: " + String.format("%.2f", mainForce != null ? mainForce.getAccount().getAvailableFunds() : 0.0));
        mainForceStocksLabel = createLabel(panel, "主力持有籌碼: " + String.format("%d", mainForce != null ? mainForce.getAccount().getStockInventory() : 0));

        targetPriceLabel = createLabel(panel, "主力目標價位: " + String.format("%.2f", mainForce != null ? mainForce.getTargetPrice() : 0.0));
        averageCostPriceLabel = createLabel(panel, "主力平均成本: " + String.format("%.2f", mainForce != null ? mainForce.getAverageCostPrice() : 0.0));
        fundsLabel = createLabel(panel, "市場可用資金: " + String.format("%.2f", marketBehavior != null ? marketBehavior.getAvailableFunds() : 0.0));

        // 如果 marketBehavior.getStockInventory() 返回的是整數，則應使用 %d
        inventoryLabel = createLabel(panel, "市場庫存: " + String.format("%d", marketBehavior != null ? marketBehavior.getStockInventory() : 0));
        weightedAveragePriceLabel = createLabel(panel, "加權平均價格: " + String.format("%.2f", marketAnalyzer != null ? marketAnalyzer.getWeightedAveragePrice() : 0.0));
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
        if (retailInvestors == null || retailInvestors.isEmpty()) {
            return 0.0;
        }
        return retailInvestors.stream()
                .mapToDouble(investor -> investor.getAccount().getAvailableFunds())
                .average()
                .orElse(0.0);
    }

    // 獲取散戶平均股票數量
    private int getAverageRetailStocks() {
        if (retailInvestors == null || retailInvestors.isEmpty()) {
            return 0;
        }
        int totalStocks = 0;
        for (RetailInvestorAI investor : retailInvestors) {
            totalStocks += investor.getAccount().getStockInventory();  // 假設 getStockInventory() 返回持股數量
        }
        return retailInvestors.size() > 0 ? totalStocks / retailInvestors.size() : 0;
    }

    // 更新主力的現金、持股數、目標價和平均成本
    public void updateLabels() {
        SwingUtilities.invokeLater(() -> {
            stockPriceLabel.setText("股票價格: " + String.format("%.2f", stock != null ? stock.getPrice() : 0.0));
            mainForceCashLabel.setText("主力現金: " + String.format("%.2f", mainForce != null ? mainForce.getAccount().getAvailableFunds() : 0.0));
            mainForceStocksLabel.setText("主力持有籌碼: " + String.format("%d", mainForce != null ? mainForce.getAccount().getStockInventory() : 0));
            // 更新主力目標價位和平均成本價格
            targetPriceLabel.setText("主力目標價位: " + String.format("%.2f", mainForce != null ? mainForce.getTargetPrice() : 0.0));
            averageCostPriceLabel.setText("主力平均成本: " + String.format("%.2f", mainForce != null ? mainForce.getAverageCostPrice() : 0.0));
            // 更新散戶資訊
            updateRetailAIInfo();
            // 更新加權平均價格標籤
            weightedAveragePriceLabel.setText("加權平均價格: " + String.format("%.2f", marketAnalyzer != null ? marketAnalyzer.getWeightedAveragePrice() : 0.0));
        });
    }

    // 更新市場庫存、資金
    public void updateMarketBehaviorDisplay() {
        double funds = marketBehavior != null ? marketBehavior.getAvailableFunds() : 0;
        int stockInventory = marketBehavior != null ? marketBehavior.getStockInventory() : 0;

        // 更新 UI 標籤或顯示區域
        SwingUtilities.invokeLater(() -> {
            fundsLabel.setText("可用資金 : " + String.format("%.2f", funds));
            inventoryLabel.setText("庫存 : " + stockInventory);
        });
    }

    // 更新訂單簿顯示
    public void updateOrderBookDisplay() {
        SwingUtilities.invokeLater(() -> {
            Object[][] updatedData = new Object[10][4];
            List<Order> buyOrders = orderBook.getTopBuyOrders(10);
            List<Order> sellOrders = orderBook.getTopSellOrders(10);

            for (int i = 0; i < 10; i++) {
                // 填充買單
                if (i < buyOrders.size()) {
                    Order buyOrder = buyOrders.get(i);
                    if (buyOrder != null && buyOrder.getTrader() != null) {
                        updatedData[i][0] = buyOrder.getVolume();
                        updatedData[i][1] = String.format("%.2f", buyOrder.getPrice());
                    } else {
                        updatedData[i][0] = "";
                        updatedData[i][1] = "";
                    }
                } else {
                    updatedData[i][0] = "";
                    updatedData[i][1] = "";
                }

                // 填充賣單
                if (i < sellOrders.size()) {
                    Order sellOrder = sellOrders.get(i);
                    if (sellOrder != null && sellOrder.getTrader() != null) {
                        updatedData[i][2] = String.format("%.2f", sellOrder.getPrice());
                        updatedData[i][3] = sellOrder.getVolume();
                    } else {
                        updatedData[i][2] = "";
                        updatedData[i][3] = "";
                    }
                } else {
                    updatedData[i][2] = "";
                    updatedData[i][3] = "";
                }
            }
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

    // 創建市場波動性
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

    // 創建相對強弱指數
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

    // 加權平均價格
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
                if (colorList.size() <= column) {
                    double currentPrice = stock.getPrice();
                    double previousPrice = column > 0 && priceSeries.getItemCount() > column - 1
                            ? priceSeries.getY(column - 1).doubleValue()
                            : currentPrice;
                    Color color = currentPrice > previousPrice ? Color.RED : Color.GREEN;
                    colorList.add(color);
                }
                // 確保 colorList 的大小不超過 100
                if (colorList.size() > 100) {
                    colorList.remove(0);
                }
                return colorList.get(column);
            }
        };
        renderer.setBarPainter(new StandardBarPainter()); // 移除漸變效果
        plot.setRenderer(renderer);

        // 設定中文字型
        Font font = new Font("Microsoft JhengHei", Font.PLAIN, 12);

        // 設定圖表標題字型
        volumeChart.getTitle().setFont(font);

        // 設定 X 軸和 Y 軸的字型
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
        if (priceSeries != null && stock != null) {
            priceSeries.add(timeStep, stock.getPrice());
            keepSeriesWithinLimit(priceSeries, 100); // 保留最新 100 筆數據
        }

        if (smaSeries != null && marketAnalyzer != null) {
            smaSeries.add(timeStep, marketAnalyzer.getSMA());
            keepSeriesWithinLimit(smaSeries, 100); // 保留最新 100 筆數據
        }
    }

    // 新增方法來更新其他技術指標
    private void updateTechnicalIndicators() {
        // 更新波動性
        if (volatilitySeries != null && marketAnalyzer != null) {
            double volatility = marketAnalyzer.calculateVolatility();
            volatilitySeries.add(timeStep, volatility);
            keepSeriesWithinLimit(volatilitySeries, 100); // 保留最新 100 筆數據
        }

        // 更新 RSI
        if (rsiSeries != null && marketAnalyzer != null) {
            double rsi = marketAnalyzer.getRSI();
            rsiSeries.add(timeStep, rsi);
            keepSeriesWithinLimit(rsiSeries, 100); // 保留最新 100 筆數據
        }

        // 更新加權平均價格
        if (wapSeries != null && marketAnalyzer != null) {
            double wap = marketAnalyzer.getWeightedAveragePrice();
            wapSeries.add(timeStep, wap);
            keepSeriesWithinLimit(wapSeries, 100); // 保留最新 100 筆數據
        }
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
        // 調試輸出
        System.out.println("updateVolumeChart called with volume: " + volume + ", timeStep: " + timeStep + ", currentPrice: " + (stock != null ? stock.getPrice() : 0));

        // 確保 priceSeries 不為 null
        if (priceSeries == null) {
            System.err.println("priceSeries is null!");
            return;
        }

        // 累加每次成交的量到總成交量
        accumulatedVolume += volume;

        // 僅在新的時間步長時才更新圖表
        if (isNewTimeStep()) {
            // 如果累積成交量為 0，也強制添加一個零成交量的條目
            volumeDataset.addValue(accumulatedVolume, "Volume", String.valueOf(timeStep));
            keepCategoryDatasetWithinLimit(volumeDataset, 100); // 保留最新 100 筆數據

            // 取得當前股價，並確保與前一價格進行比較
            double currentPrice = stock.getPrice();
            double previousPrice = timeStep > 0 && (timeStep - 1) < priceSeries.getItemCount()
                    ? priceSeries.getY(timeStep - 1).doubleValue()
                    : currentPrice;

            // 若時間步長大於當前 priceSeries 的項數，則新增當前股價
            if (timeStep >= priceSeries.getItemCount()) {
                priceSeries.add(timeStep, currentPrice);
                keepSeriesWithinLimit(priceSeries, 100); // 保留最新 100 筆數據
            }

            // 設定顏色：累積成交量為 0 時設為藍色；若當前價格高於前一價格設為紅色，否則設為綠色
            Color color = accumulatedVolume == 0 ? Color.BLUE : (currentPrice > previousPrice ? Color.RED : Color.GREEN);
            colorList.add(color);

            // 確保 colorList 的大小不超過 100
            if (colorList.size() > 100) {
                colorList.remove(0);
            }

            // 重置累積成交量
            accumulatedVolume = 0;

            // 自增時間步長
            timeStep++;
        }
    }

    // 用於檢查是否進入新的時間步長
    private boolean isNewTimeStep() {
        if (timeStep != previousTimeStep) {
            previousTimeStep = timeStep; // 更新 previousTimeStep 為當前的 timeStep
            return true; // 表示進入新的時間步長
        }
        return false; // 否則仍在當前的時間步長內
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

    // 主程式
    public static void main(String[] args) {
        SwingUtilities.invokeLater(StockMarketSimulation::new);
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
        if (mainForce != null) {
            totalInventory += mainForce.getAccount().getStockInventory();
        }

        // 2. 計算散戶的股票持有量
        if (retailInvestors != null) {
            for (RetailInvestorAI investor : retailInvestors) {
                totalInventory += investor.getAccount().getStockInventory();
            }
        }

        // 3. 計算用戶投資者的股票持有量
        if (Tara != null) {
            totalInventory += Tara.getAccount().getStockInventory();
        }

        // 4. 計算市場訂單中未成交的賣單總量
        if (orderBook != null) {
            for (Order sellOrder : orderBook.getSellOrders()) {
                totalInventory += sellOrder.getVolume();
            }
        }

        // 5. 計算市場行為中保留的庫存（若有）
        if (marketBehavior != null) {
            totalInventory += marketBehavior.getStockInventory();
        }

        // 6. 返回市場總庫存量
        return totalInventory;
    }

    // 更新用戶資訊顯示
    private void updateUserInfo() {
        SwingUtilities.invokeLater(() -> {
            userStockLabel.setText("個人股票數量: " + (Tara != null ? Tara.getAccount().getStockInventory() : 0));
            userCashLabel.setText("個人金錢餘額: " + String.format("%.2f", (Tara != null ? Tara.getAccount().getAvailableFunds() : 0)));
            // userAvgPriceLabel.setText("個人股票均價: " + String.format("%.2f", userInvestor.getAccount().getAverageCostPrice()));
        });
    }

    // 實現 OrderBookListener 的方法
    @Override
    public void onOrderBookUpdated() {
        // 當訂單簿更新時，自動刷新相關顯示
        SwingUtilities.invokeLater(this::updateOrderBookDisplay);
    }
}
