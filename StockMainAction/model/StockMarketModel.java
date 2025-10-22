package StockMainAction.model;

import StockMainAction.controller.TechnicalIndicatorsCalculator;
import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import StockMainAction.model.core.Transaction;
import StockMainAction.util.logging.MarketLogger;
import StockMainAction.util.logging.LogicAudit;
import java.util.AbstractMap.SimpleEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.SwingUtilities;

/**
 * 股票市場模型 - 包含核心業務邏輯 作為MVC架構中的Model組件
 */
public class StockMarketModel {

    // 市場核心對象
    private Stock stock;
    private OrderBook orderBook;
    private MarketAnalyzer marketAnalyzer;
    private MarketBehavior marketBehavior;
    private MainForceStrategyWithOrderBook mainForce;
    private List<RetailInvestorAI> retailInvestors;
    private PersonalAI userInvestor;

    // 模擬控制
    private int timeStep;
    private ScheduledExecutorService executorService;
    private boolean isRunning = false;
    private Random random = new Random();

    // 配置參數
    private double initialRetailCash = 300000, initialMainForceCash = 3000000;
    private int initialRetails = 10;
    private int marketBehaviorStock = 30000;
    private double marketBehaviorGash = -9999999.0;

    // 🆕 成交記錄列表
    private List<Transaction> transactionHistory;
    private static final int MAX_TRANSACTION_HISTORY = 10000; // 最多保留10000筆

    // 線程安全鎖
    private final ReentrantLock orderBookLock = new ReentrantLock();
    private final ReentrantLock marketAnalyzerLock = new ReentrantLock();

    // 日誌記錄器
    private static final MarketLogger logger = MarketLogger.getInstance();

    // 加技術指標計算器作為成員變數
    private TechnicalIndicatorsCalculator technicalCalculator;

    // 最近一次計算的技術指標值（供策略/風控倉位縮放使用）
    private volatile double lastMacdLine = Double.NaN;
    private volatile double lastMacdSignal = Double.NaN;
    private volatile double lastMacdHist = Double.NaN;
    private volatile double lastBollUpper = Double.NaN;
    private volatile double lastBollMiddle = Double.NaN;
    private volatile double lastBollLower = Double.NaN;
    private volatile double lastK = Double.NaN;
    private volatile double lastD = Double.NaN;
    private volatile double lastJ = Double.NaN;

    // 事件模式參數（全域）
    private String eventMode = "一般"; // 一般/新聞/財報
    private int eventWindow = 60;
    private int eventConsecutive = 3;
    private int eventBaseThreshold = 65;
    private int eventThresholdBoost = 0; // 新聞+5，財報+10
    private double eventPositionScale = 1.0; // 新聞0.85，財報0.7

    // 模型監聽器介面 - 用於通知View更新
    public interface ModelListener {

        void onPriceChanged(double price, double sma);

        void onTechnicalIndicatorsUpdated(double volatility, double rsi, double wap);

        // 新增：MACD指標更新事件
        void onMACDUpdated(double macdLine, double signalLine, double histogram);

        // 新增：布林帶指標更新事件  
        void onBollingerBandsUpdated(double upperBand, double middleBand, double lowerBand);

        // 新增：KDJ指標更新事件
        void onKDJUpdated(double kValue, double dValue, double jValue);

        void onVolumeUpdated(int volume);

        void onMarketStateChanged(double retailCash, int retailStocks,
                double mainForceCash, int mainForceStocks,
                double targetPrice, double avgCostPrice,
                double funds, int inventory);

        void onUserAccountUpdated(int stockQuantity, double cash, double avgPrice, double targetPrice);

        void onInfoMessage(String message);

        void onOrderBookChanged();
    }

    public List<ModelListener> listeners = new ArrayList<>();

    // 成交紀錄監聽器介面 - 用於通知View更新
    public interface TransactionListener {

        void onTransactionAdded(Transaction transaction);
    }
    
     private List<TransactionListener> transactionListeners = new ArrayList<>();

    /**
     * 構造函數
     */
    public StockMarketModel() {
        initializeSimulation();
        this.technicalCalculator = new TechnicalIndicatorsCalculator();
        this.transactionHistory = new ArrayList<>();
    }

    // 設定事件模式參數（由 UI 下發）
    public void setEventParams(int window, int consecutive, int baseThreshold, String mode) {
        this.eventWindow = Math.max(1, window);
        this.eventConsecutive = Math.max(1, consecutive);
        this.eventBaseThreshold = Math.max(1, Math.min(99, baseThreshold));
        this.eventMode = (mode == null ? "一般" : mode);
        if ("新聞".equals(this.eventMode)) {
            this.eventThresholdBoost = 5;
            this.eventPositionScale = 0.85;
        } else if ("財報".equals(this.eventMode)) {
            this.eventThresholdBoost = 10;
            this.eventPositionScale = 0.70;
        } else {
            this.eventThresholdBoost = 0;
            this.eventPositionScale = 1.0;
        }
        // 通知 UI
        sendInfoMessage(String.format("事件模式：%s（門檻+%d%%，倉位係數=%.2f）", this.eventMode, this.eventThresholdBoost, this.eventPositionScale));
    }

    // 取得事件模式下的有效門檻值（若未設定則回傳傳入的預設）
    public int getEventEffectiveThresholdOr(int fallback) {
        int base = (eventBaseThreshold > 0 ? eventBaseThreshold : fallback);
        int eff = base + eventThresholdBoost;
        if (eff < 1) eff = 1; if (eff > 99) eff = 99;
        return eff;
    }

    // 倉位縮放係數（散戶/主力用）
    public double getEventPositionScale() {
        return eventPositionScale;
    }

    /**
     * 初始化模擬環境
     */
    public void initializeSimulation() {
        logger.info("初始化股票市場模型", "MODEL_INIT");
        try {
            // 初始化訂單簿
            orderBook = new OrderBook(this);
            logger.info("OrderBook 初始化完成", "MODEL_INIT");
            // 設置默認撮合模式
            orderBook.setMatchingMode(MatchingMode.PRICE_TIME);
            logger.info("設置默認撮合模式：" + orderBook.getMatchingMode(), "MODEL_INIT");

            stock = new Stock("台積電", 10, 1000);

            // 初始化市場行為
            this.marketBehavior = new MarketBehavior(10.0, marketBehaviorGash, marketBehaviorStock, this, orderBook);

            timeStep = 0;
            marketAnalyzer = new MarketAnalyzer(2); // 設定適當的SMA週期

            // 初始化主力
            mainForce = new MainForceStrategyWithOrderBook(orderBook, stock, this, initialMainForceCash);

            // 初始化散戶
            initializeRetailInvestors(initialRetails);

            // 初始化用戶投資者
            userInvestor = new PersonalAI(initialRetailCash, "Personal", this, orderBook, stock);

            logger.info("市場模型初始化完成", "MODEL_INIT");
        } catch (Exception e) {
            logger.error("市場模型初始化失敗: " + e.getMessage(), "MODEL_INIT");
            e.printStackTrace();
        }
    }

    /**
     * 初始化多個自動化散戶
     */
    private void initializeRetailInvestors(int numberOfInvestors) {
        retailInvestors = new ArrayList<>();
        for (int i = 0; i < numberOfInvestors; i++) {
            RetailInvestorAI investor = new RetailInvestorAI(initialRetailCash, "RetailInvestor" + (i + 1), this);
            retailInvestors.add(investor);
        }
    }

    /**
     * 啟動自動價格波動
     */
    public void startAutoPriceFluctuation() {
        if (isRunning) {
            return;
        }

        logger.info("啟動市場價格波動模擬", "MARKET_SIMULATION");

        int initialDelay = 0;
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
                    logger.error("散戶決策發生錯誤：" + e.getMessage(), "RETAIL_BEHAVIOR");
                    e.printStackTrace();
                }

                // 3. 主力行為：執行主力決策
                try {
                    mainForce.makeDecision();
                } catch (Exception e) {
                    logger.error("主力決策發生錯誤：" + e.getMessage(), "MAINFORCE_BEHAVIOR");
                    e.printStackTrace();
                }

                // 4. 處理訂單簿，撮合訂單（需加鎖保護）
                try {
                    orderBookLock.lock(); // 加鎖
                    orderBook.processOrders(stock);
                } catch (Exception e) {
                    logger.error("訂單簿處理發生錯誤：" + e.getMessage(), "ORDER_PROCESSING");
                    e.printStackTrace();
                } finally {
                    orderBookLock.unlock(); // 解鎖
                }

                // 5. 更新市場分析數據
                try {
                    marketAnalyzerLock.lock(); // 加鎖

                    // 通知監聽器市場狀態更新
                    notifyListenersOfUpdates();
                } catch (Exception e) {
                    logger.error("市場分析數據更新發生錯誤：" + e.getMessage(), "MARKET_ANALYSIS");
                    e.printStackTrace();
                } finally {
                    marketAnalyzerLock.unlock(); // 解鎖
                    validateMarketInventory();
                }
            } catch (Exception e) {
                logger.error("主模擬流程發生未處理的錯誤：" + e.getMessage(), "MARKET_SIMULATION");
                e.printStackTrace();
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS);

        isRunning = true;
    }

    /**
     * 通知所有監聽器更新
     */
    public void notifyListenersOfUpdates() {
        double price = stock.getPrice();
        double sma = marketAnalyzer.calculateSMA();
        double volatility = marketAnalyzer.calculateVolatility();
        double rsi = marketAnalyzer.getRSI();
        double wap = marketAnalyzer.getWeightedAveragePrice();

        // 更新技術指標計算器的價格數據（改為使用近期高低價，避免KDJ失真）
        double high = marketAnalyzer.getRecentHigh(technicalCalculator != null ? 20 : 20);
        double low = marketAnalyzer.getRecentLow(technicalCalculator != null ? 20 : 20);
        if (Double.isNaN(high)) high = price;
        if (Double.isNaN(low)) low = price;
        technicalCalculator.updatePriceData(price, high, low);

        // 計算新的技術指標
        double[] macdResult = technicalCalculator.calculateMACD();
        double[] bollingerResult = technicalCalculator.calculateBollingerBands();
        double[] kdjResult = technicalCalculator.calculateKDJ();

        // 保存最近一次指標值
        if (macdResult != null) {
            lastMacdLine = macdResult[0];
            lastMacdSignal = macdResult[1];
            lastMacdHist = macdResult[2];
        }
        if (bollingerResult != null) {
            lastBollUpper = bollingerResult[0];
            lastBollMiddle = bollingerResult[1];
            lastBollLower = bollingerResult[2];
        }
        if (kdjResult != null) {
            lastK = kdjResult[0];
            lastD = kdjResult[1];
            lastJ = kdjResult[2];
        }

        // 通知所有監聽器
        for (ModelListener listener : listeners) {
            // 原有的通知
            listener.onPriceChanged(price, sma);
            listener.onTechnicalIndicatorsUpdated(volatility, rsi, wap);

            // 新增的技術指標通知
            if (macdResult != null) {
                listener.onMACDUpdated(macdResult[0], macdResult[1], macdResult[2]);
            }

            if (bollingerResult != null) {
                listener.onBollingerBandsUpdated(bollingerResult[0], bollingerResult[1], bollingerResult[2]);
            }

            if (kdjResult != null) {
                listener.onKDJUpdated(kdjResult[0], kdjResult[1], kdjResult[2]);
            }

            // 原有的其他通知
            listener.onMarketStateChanged(
                    getAverageRetailCash(),
                    getAverageRetailStocks(),
                    mainForce.getAccount().getAvailableFunds(),
                    mainForce.getAccount().getStockInventory(),
                    mainForce.getTargetPrice(),
                    mainForce.getAverageCostPrice(),
                    marketBehavior.getAvailableFunds(),
                    marketBehavior.getStockInventory()
            );
            listener.onUserAccountUpdated(
                    userInvestor.getAccount().getStockInventory(),
                    userInvestor.getAccount().getAvailableFunds(),
                    userInvestor.getAverageCostPrice(),
                    userInvestor.getTakeProfitPrice()
            );
            listener.onOrderBookChanged();
        }
    }

    // 可選：提供獲取技術指標計算器的方法（用於調試或配置）
    public TechnicalIndicatorsCalculator getTechnicalCalculator() {
        return technicalCalculator;
    }

    // ===== 指標值 Getter（NaN 表示暫無） =====
    public double getLastMacdLine() { return lastMacdLine; }
    public double getLastMacdSignal() { return lastMacdSignal; }
    public double getLastMacdHist() { return lastMacdHist; }
    public double getLastBollUpper() { return lastBollUpper; }
    public double getLastBollMiddle() { return lastBollMiddle; }
    public double getLastBollLower() { return lastBollLower; }
    public double getLastK() { return lastK; }
    public double getLastD() { return lastD; }
    public double getLastJ() { return lastJ; }

    // ===== 近期 Tape 統計（供策略與風控使用） =====
    public double getRecentTPS(int n) {
        try {
            java.util.List<Transaction> recent = getRecentTransactions(Math.max(1, n));
            if (recent.isEmpty()) return 0.0;
            long now = System.currentTimeMillis();
            long earliest = recent.get(0).getTimestamp();
            double secs = Math.max(1.0, (now - earliest) / 1000.0);
            return recent.size() / secs;
        } catch (Exception e) { return 0.0; }
    }

    public double getRecentVPS(int n) {
        try {
            java.util.List<Transaction> recent = getRecentTransactions(Math.max(1, n));
            if (recent.isEmpty()) return 0.0;
            long now = System.currentTimeMillis();
            long earliest = recent.get(0).getTimestamp();
            double secs = Math.max(1.0, (now - earliest) / 1000.0);
            long vol = 0; for (Transaction t : recent) vol += t.getVolume();
            return vol / secs;
        } catch (Exception e) { return 0.0; }
    }

    public double getRecentTickImbalance(int n) {
        try {
            java.util.List<Transaction> recent = getRecentTransactions(Math.max(1, n));
            if (recent.isEmpty()) return 0.0;
            int buy=0, sell=0; for (Transaction t : recent) { if (t.isBuyerInitiated()) buy++; else sell++; }
            int tot = Math.max(1, buy + sell);
            return (buy - sell) / (double) tot; // [-1,1]
        } catch (Exception e) { return 0.0; }
    }

    /**
     * 停止自動價格波動
     */
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
        isRunning = false;
    }

    /**
     * 讓所有自動化散戶做出決策並提交訂單
     */
    private void executeRetailInvestorDecisions() {
        for (RetailInvestorAI investor : retailInvestors) {
            investor.makeDecision(stock, orderBook, this);
        }
    }

    /**
     * 獲取散戶平均金錢
     */
    public double getAverageRetailCash() {
        return retailInvestors.stream()
                .mapToDouble(investor -> investor.getAccount().getAvailableFunds())
                .average()
                .orElse(0.0);
    }

    /**
     * 獲取散戶平均股票數量
     */
    public int getAverageRetailStocks() {
        int totalStocks = 0;
        for (RetailInvestorAI investor : retailInvestors) {
            totalStocks += investor.getAccount().getStockInventory();
        }
        return retailInvestors.size() > 0 ? totalStocks / retailInvestors.size() : 0;
    }

    /**
     * 檢查市場庫存
     */
    public void validateMarketInventory() {
        int calculatedInventory = calculateMarketInventory();
        int initialInventory = marketBehaviorStock;
        if (calculatedInventory != initialInventory) {
            String msg = "初始化市場庫存檢查: 設定值=" + marketBehaviorStock
                    + "，市場行為持股=" + marketBehavior.getStockInventory()
                    + "，總計算庫存=" + calculatedInventory;
            logger.error(msg, "MODEL_INIT");
            LogicAudit.warn("INVENTORY_CHECK", msg);
        } else {
            LogicAudit.info("INVENTORY_CHECK", "ok total=" + calculatedInventory);
        }
    }

    /**
     * 計算市場庫存
     */
    public int calculateMarketInventory() {
        int totalInventory = 0;

        // 以帳戶帳本為準：可用 + 凍結
        int mainForceAvail = mainForce.getAccount().getStockInventory();
        int mainForceFrozen = mainForce.getAccount().getFrozenStocks();
        totalInventory += mainForceAvail + mainForceFrozen;

        int sumRetailAvail = 0;
        int sumRetailFrozen = 0;
        for (RetailInvestorAI investor : retailInvestors) {
            sumRetailAvail += investor.getAccount().getStockInventory();
            sumRetailFrozen += investor.getAccount().getFrozenStocks();
        }
        totalInventory += sumRetailAvail + sumRetailFrozen;

        if (userInvestor != null) {
            totalInventory += userInvestor.getAccount().getStockInventory();
            totalInventory += userInvestor.getAccount().getFrozenStocks();
        }

        // 市場行為帳戶
        int marketAvail = marketBehavior.getStockInventory();
        int marketFrozen = marketBehavior.getAccount().getFrozenStocks();
        totalInventory += marketAvail + marketFrozen;

        // 稽核分解
        LogicAudit.info("INVENTORY_BREAKDOWN", String.format(
                "main(avail=%d,frozen=%d) retail(avail=%d,frozen=%d) user(a=%d,f=%d) market(a=%d,f=%d)",
                mainForceAvail, mainForceFrozen,
                sumRetailAvail, sumRetailFrozen,
                userInvestor != null ? userInvestor.getAccount().getStockInventory() : 0,
                userInvestor != null ? userInvestor.getAccount().getFrozenStocks() : 0,
                marketAvail, marketFrozen));

        return totalInventory;
    }

    /**
     * 計算市價買入的實際成交數量和成本
     */
    public SimpleEntry<Integer, Double> calculateActualCost(List<Order> sellOrders, int quantity) {
        double actualCost = 0.0;
        int actualQuantity = 0;
        int remainingQuantity = quantity;

        for (Order sellOrder : sellOrders) {
            if (remainingQuantity <= 0) {
                break;
            }
            int transactionVolume = Math.min(remainingQuantity, sellOrder.getVolume());
            actualCost += transactionVolume * sellOrder.getPrice();
            actualQuantity += transactionVolume;
            remainingQuantity -= transactionVolume;
        }

        return new SimpleEntry<>(actualQuantity, actualCost);
    }

    /**
     * 計算市價賣出的實際收入
     */
    public SimpleEntry<Integer, Double> calculateActualRevenue(List<Order> buyOrders, int quantity) {
        double actualRevenue = 0.0;
        int actualQuantity = 0;
        int remainingQuantity = quantity;

        for (Order buyOrder : buyOrders) {
            if (remainingQuantity <= 0) {
                break;
            }
            int transactionVolume = Math.min(remainingQuantity, buyOrder.getVolume());
            actualRevenue += transactionVolume * buyOrder.getPrice();
            actualQuantity += transactionVolume;
            remainingQuantity -= transactionVolume;
        }

        return new SimpleEntry<>(actualQuantity, actualRevenue);
    }

    /**
     * 市價買入操作
     */
    public boolean executeMarketBuy(int quantity) {
        return userInvestor.市價買入操作(quantity) > 0;
    }

    /**
     * 市價賣出操作
     */
    public boolean executeMarketSell(int quantity) {
        return userInvestor.市價賣出操作(quantity) > 0;
    }

    /**
     * 限價買入操作
     */
    public boolean executeLimitBuy(int quantity, double price) {
        return userInvestor.限價買入操作(quantity, price) > 0;
    }

    /**
     * 限價賣出操作
     */
    public boolean executeLimitSell(int quantity, double price) {
        return userInvestor.限價賣出操作(quantity, price) > 0;
    }

    /**
     * 更改撮合模式
     */
    public void changeMatchingMode(MatchingMode mode) {
        try {
            if (orderBook != null) {
                orderBook.setMatchingMode(mode);
                // 通知所有監聽器
                notifyListenersOfUpdates();
                logger.info("模型層成功更改撮合模式：" + mode, "MARKET_MODEL");
            } else {
                logger.error("無法更改撮合模式：OrderBook 為 null", "MARKET_MODEL");
            }
        } catch (Exception e) {
            logger.error("更改撮合模式時發生錯誤：" + e.getMessage(), "MARKET_MODEL");
        }
    }

    /**
     * 發送資訊訊息給所有監聽器
     *
     * @param message 要發送的訊息
     */
    public void sendInfoMessage(String message) {
        for (ModelListener listener : listeners) {
            listener.onInfoMessage(message);
        }
    }

    /**
     * 添加模型監聽器
     */
    public void addModelListener(ModelListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除模型監聽器
     */
    public void removeModelListener(ModelListener listener) {
        listeners.remove(listener);
    }

    /**
     * 更新界面標籤 替代 simulation.updateLabels() 方法
     */
    public void updateLabels() {
        // 通知所有監聽器更新界面
        notifyListenersOfUpdates();
    }

    /**
     * 更新交易量圖表
     *
     * @param txVolume 交易量
     */
    public void updateVolumeChart(int txVolume) {
        // 通知監聽器更新交易量圖表
        for (ModelListener listener : listeners) {
            listener.onVolumeUpdated(txVolume);
        }
    }

    /**
     * 更新訂單簿顯示
     */
    public void updateOrderBookDisplay() {
        // 通知監聽器訂單簿已變更
        for (ModelListener listener : listeners) {
            listener.onOrderBookChanged();
        }
    }

    /**
     * 多線程安全地更新訂單簿顯示
     */
    public void swingUpdateOrderBookDisplay() {
        // 在 Swing 線程中更新訂單簿顯示
        SwingUtilities.invokeLater(() -> {
            updateOrderBookDisplay();
        });
    }

    /**
     * 取消所有訂單
     */
    public void cancelAllOrders() {
        orderBookLock.lock();
        try {
            // 取消所有買單
            for (Order order : new ArrayList<>(orderBook.getBuyOrders())) {
                orderBook.cancelOrder(order.getId());
            }

            // 取消所有賣單
            for (Order order : new ArrayList<>(orderBook.getSellOrders())) {
                orderBook.cancelOrder(order.getId());
            }

            // 通知訂單簿已更新
            for (ModelListener listener : listeners) {
                listener.onOrderBookChanged();
            }
        } finally {
            orderBookLock.unlock();
        }
    }

    // 添加成交記錄的方法
    public void addTransaction(Transaction transaction) {
        transactionHistory.add(transaction);

        // 限制記錄數量，避免記憶體溢出
        if (transactionHistory.size() > MAX_TRANSACTION_HISTORY) {
            transactionHistory.remove(0); // 移除最舊的記錄
        }

        // 通知監聽器（如果需要即時更新視窗）
        notifyTransactionAdded(transaction);
    }

    private void notifyTransactionAdded(Transaction transaction)    {
        // 通知所有成交監聽器
        for (TransactionListener listener : transactionListeners) {
            listener.onTransactionAdded(transaction);
        }

        // 原有的通知
        for (ModelListener listener : listeners) {
            listener.onInfoMessage(String.format("新成交：%s %d股 @ %.2f",
                    transaction.isBuyerInitiated() ? "買入" : "賣出",
                    transaction.getVolume(),
                    transaction.getPrice()));
        }
    }

    public void addTransactionListener(TransactionListener listener) {
        if (!transactionListeners.contains(listener)) {
            transactionListeners.add(listener);
        }
    }

    public void removeTransactionListener(TransactionListener listener) {
        transactionListeners.remove(listener);
    }

    // 獲取成交記錄
    public List<Transaction> getTransactionHistory() {
        return new ArrayList<>(transactionHistory); // 返回副本
    }

    // 獲取最近N筆成交記錄
    public List<Transaction> getRecentTransactions(int n) {
        int size = transactionHistory.size();
        if (size <= n) {
            return new ArrayList<>(transactionHistory);
        }
        return new ArrayList<>(transactionHistory.subList(size - n, size));
    }

    // ======== Getter 方法 ========
    public Stock getStock() {
        return stock;
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }

    public MarketAnalyzer getMarketAnalyzer() {
        return marketAnalyzer;
    }

    public MainForceStrategyWithOrderBook getMainForce() {
        return mainForce;
    }

    public PersonalAI getUserInvestor() {
        return userInvestor;
    }

    // 新增：取得散戶清單（唯讀快照）
    public List<RetailInvestorAI> getRetailInvestors() {
        return new ArrayList<>(retailInvestors);
    }

    // 新增：取得初始資金設定
    public double getInitialRetailCash() {
        return initialRetailCash;
    }

    public double getInitialMainForceCash() {
        return initialMainForceCash;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getTimeStep() {
        return timeStep;
    }
}
