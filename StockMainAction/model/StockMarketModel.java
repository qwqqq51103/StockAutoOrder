package StockMainAction.model;

import StockMainAction.controller.TechnicalIndicatorsCalculator;
import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import StockMainAction.util.logging.MarketLogger;
import javafx.util.Pair;

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
    private double initialRetailCash = 100000, initialMainForceCash = 2980000;
    private int initialRetails = 1;
    private int marketBehaviorStock = 5000;
    private double marketBehaviorGash = 10000;

    // 線程安全鎖
    private final ReentrantLock orderBookLock = new ReentrantLock();
    private final ReentrantLock marketAnalyzerLock = new ReentrantLock();

    // 日誌記錄器
    private static final MarketLogger logger = MarketLogger.getInstance();

    // 加技術指標計算器作為成員變數
    private TechnicalIndicatorsCalculator technicalCalculator;

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

    /**
     * 構造函數
     */
    public StockMarketModel() {
        initializeSimulation();
        this.technicalCalculator = new TechnicalIndicatorsCalculator();
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
            userInvestor = new PersonalAI(5000000, "Personal", this, orderBook, stock);

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

        // 更新技術指標計算器的價格數據
        // 注意：這裡假設high和low與當前價格相同，實際應用中可能需要真實的高低價數據
        double high = price; // 如果有真實的高價數據，請替換
        double low = price;  // 如果有真實的低價數據，請替換
        technicalCalculator.updatePriceData(price, high, low);

        // 計算新的技術指標
        double[] macdResult = technicalCalculator.calculateMACD();
        double[] bollingerResult = technicalCalculator.calculateBollingerBands();
        double[] kdjResult = technicalCalculator.calculateKDJ();

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
            logger.error("初始化市場庫存檢查: 設定值=" + marketBehaviorStock
                    + "，市場行為持股=" + marketBehavior.getStockInventory()
                    + "，總計算庫存=" + calculateMarketInventory(), "MODEL_INIT");
        }
    }

    /**
     * 計算市場庫存
     */
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

        // 5. 計算市場行為中保留的庫存
        totalInventory += marketBehavior.getStockInventory();

        // 6. 返回市場總庫存量
        return totalInventory;
    }

    /**
     * 計算市價買入的實際成交數量和成本
     */
    public Pair<Integer, Double> calculateActualCost(List<Order> sellOrders, int quantity) {
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

        return new Pair<>(actualQuantity, actualCost);
    }

    /**
     * 計算市價賣出的實際收入
     */
    public Pair<Integer, Double> calculateActualRevenue(List<Order> buyOrders, int quantity) {
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

        return new Pair<>(actualQuantity, actualRevenue);
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

    public boolean isRunning() {
        return isRunning;
    }

    public int getTimeStep() {
        return timeStep;
    }
}
