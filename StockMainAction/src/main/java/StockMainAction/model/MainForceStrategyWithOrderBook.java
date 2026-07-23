package StockMainAction.model;

import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.user.UserAccount;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.Trader;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import StockMainAction.model.core.OrderSide;
import StockMainAction.model.strategy.OrderIntent;
import StockMainAction.model.strategy.OrderAgeTracker;
import java.time.Clock;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import StockMainAction.util.logging.MarketLogger;
import StockMainAction.util.logging.LogicAudit;
import java.util.Arrays;
import StockMainAction.model.core.Transaction;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 主力策略 - 處理主力的操作策略
 */
public class MainForceStrategyWithOrderBook implements Trader {

    private OrderBook orderBook;
    private Stock stock;
    private StringBuilder tradeLog;
    private double targetPrice;         // 主力目標價位
    private double averageCostPrice;    // 目前籌碼的平均成本價格
    private Random random;
    private UserAccount account;        // 主力的 UserAccount
    private int initStock = 0;
    private StockMarketModel model;
    private double peakTotalAssets = -1.0; // 風險回撤計算用

    // 目標價的期望利潤率（例如 50%）
    private static final double EXPECTED_PROFIT_MARGIN = 0.5;
    private static final double VOLATILITY_FACTOR = 0.5;

    // 最近價格、成交量、波動性
    private Queue<Integer> recentVolumes;
    private double volatility;
    private Deque<Double> recentPrices;

    private double buyThreshold = 0.95;  // 設定新的買入門檻，例如低於 SMA 的 1%
    private double sellThreshold = 3.5; // 設定新的賣出門檻，例如高於 SMA 的 2%

    /* ====== 動態掛單價權重（主力版，可自行調整） ====== */
    private double smaWeight = 0.40;   // 與均線偏離
    private double rsiWeight = 0.25;   // RSI 超買/超賣
    private double volatilityWeight = 0.35;   // 波動度
    private double maxOffsetRatio = 0.10;   // 最多 ±10 %

    private static final MarketLogger logger = MarketLogger.getInstance();

    private static void logOptionalFailure(Exception ex) {
        logger.debugThrottled("主力策略選配訊號降級：" + ex.getMessage(),
                "STRATEGY_FALLBACK", "main-force", 60_000);
    }

    // ===== 主力狀態機（更像主力的操作） =====
    private enum Phase { 待機, 吸籌, 拉抬, 出貨, 洗盤 }
    private Phase phase = Phase.待機;
    private int phaseTicks = 0;          // 當前階段已持續的時間步
    private int cooldownTicks = 0;       // 行動冷卻，避免每步都大量出手

    // 每階段最短/最長持續時間（以 time step 計）- 可由 UI 調整
    private int accumulateMinTicks = 50, accumulateMaxTicks = 400;
    private int markupMinTicks = 20, markupMaxTicks = 150;
    private int distributeMinTicks = 30, distributeMaxTicks = 300;
    private int washMinTicks = 10, washMaxTicks = 100;

    // 撤單統計與參數化的撤換間隔
    private int replaceIntervalTicks = 10;
    private int totalCanceledOrders = 0;
    private int tickCanceledOrders = 0;

    private int getMinTicksForPhase(Phase p) {
        switch (p) {
            case 吸籌: return accumulateMinTicks;
            case 拉抬: return markupMinTicks;
            case 出貨: return distributeMinTicks;
            case 洗盤: return washMinTicks;
            default: return 0;
        }
    }

    private int getMaxTicksForPhase(Phase p) {
        switch (p) {
            case 吸籌: return accumulateMaxTicks;
            case 拉抬: return markupMaxTicks;
            case 出貨: return distributeMaxTicks;
            case 洗盤: return washMaxTicks;
            default: return Integer.MAX_VALUE;
        }
    }

    public String getPhaseName() {
        return phase.name();
    }

    public int getReplaceIntervalTicks() { return replaceIntervalTicks; }
    public void setReplaceIntervalTicks(int v) { replaceIntervalTicks = Math.max(1, Math.min(200, v)); }
    public int getTotalCanceledOrders() { return totalCanceledOrders; }
    public int getTickCanceledOrders() { return tickCanceledOrders; }

    // 手動干預
    private boolean manualLock = false;
    private Phase manualPhase = Phase.待機;
    
    // 訂單管理相關（可由 UI 調整）
    private final OrderAgeTracker orderAges;
    private int orderManagementCounter = 0;
    private int orderManagementIntervalTicks = 20; // 每 N 個週期檢查一次

    // 訂單管理限制（可由 UI 調整）
    private double maxDeviationIdle = 0.05;
    private double maxDeviationAccumulate = 0.08;
    private double maxDeviationMarkup = 0.15;
    private double maxDeviationDistribute = 0.12;
    private double maxDeviationWash = 0.10;
    private long maxAgeIdleMs = 900000;
    private long maxAgeAccumulateMs = 600000;
    private long maxAgeMarkupMs = 180000;
    private long maxAgeDistributeMs = 240000;
    private long maxAgeWashMs = 300000;
    private double markupCancelBuyBelowRatio = 0.92;
    private double markupCancelSellAboveRatio = 1.15;
    private double distributeCancelSellAboveRatio = 1.08;
    private double washCancelBuyBelowRatio = 0.85;
    private double washCancelSellAboveRatio = 1.20;
    // 風險模型參數（可由 UI 調整）
    private double riskExposureWeight = 0.35;
    private double riskUnrealizedWeight = 0.25;
    private double riskDrawdownWeight = 0.20;
    private double riskVolatilityWeight = 0.15;
    private double riskTrendWeight = 0.05;
    private double riskUnrealizedLossFull = 0.12;
    private double riskDrawdownFull = 0.20;
    private double riskVolatilityFull = 0.06;
    private double riskTrendDownFull = 0.05;
    private double riskProfitReliefMax = 0.12;
    private double riskProfitReliefSlope = 0.40;

    /**
     * 主力參數快照 DTO。
     * captureFrom / applyTo 方法集中管理欄位同步，新增欄位時只需在此類修改一處。
     * 靜態巢狀類可存取外部類的 private 成員（Java 規範 JLS §6.6.1）。
     */
    public static class MainForceLimitConfig {
        public int accumulateMinTicks, accumulateMaxTicks;
        public int markupMinTicks, markupMaxTicks;
        public int distributeMinTicks, distributeMaxTicks;
        public int washMinTicks, washMaxTicks;
        public int replaceIntervalTicks;
        public int orderManagementIntervalTicks;
        public double maxDeviationIdle, maxDeviationAccumulate, maxDeviationMarkup, maxDeviationDistribute, maxDeviationWash;
        public long maxAgeIdleMs, maxAgeAccumulateMs, maxAgeMarkupMs, maxAgeDistributeMs, maxAgeWashMs;
        public double markupCancelBuyBelowRatio, markupCancelSellAboveRatio, distributeCancelSellAboveRatio, washCancelBuyBelowRatio, washCancelSellAboveRatio;
        public double riskExposureWeight, riskUnrealizedWeight, riskDrawdownWeight, riskVolatilityWeight, riskTrendWeight;
        public double riskUnrealizedLossFull, riskDrawdownFull, riskVolatilityFull, riskTrendDownFull;
        public double riskProfitReliefMax, riskProfitReliefSlope;

        /** 從策略實例抓取當前所有參數快照 */
        public MainForceLimitConfig captureFrom(MainForceStrategyWithOrderBook src) {
            accumulateMinTicks = src.accumulateMinTicks;
            accumulateMaxTicks = src.accumulateMaxTicks;
            markupMinTicks = src.markupMinTicks;
            markupMaxTicks = src.markupMaxTicks;
            distributeMinTicks = src.distributeMinTicks;
            distributeMaxTicks = src.distributeMaxTicks;
            washMinTicks = src.washMinTicks;
            washMaxTicks = src.washMaxTicks;
            replaceIntervalTicks = src.replaceIntervalTicks;
            orderManagementIntervalTicks = src.orderManagementIntervalTicks;
            maxDeviationIdle = src.maxDeviationIdle;
            maxDeviationAccumulate = src.maxDeviationAccumulate;
            maxDeviationMarkup = src.maxDeviationMarkup;
            maxDeviationDistribute = src.maxDeviationDistribute;
            maxDeviationWash = src.maxDeviationWash;
            maxAgeIdleMs = src.maxAgeIdleMs;
            maxAgeAccumulateMs = src.maxAgeAccumulateMs;
            maxAgeMarkupMs = src.maxAgeMarkupMs;
            maxAgeDistributeMs = src.maxAgeDistributeMs;
            maxAgeWashMs = src.maxAgeWashMs;
            markupCancelBuyBelowRatio = src.markupCancelBuyBelowRatio;
            markupCancelSellAboveRatio = src.markupCancelSellAboveRatio;
            distributeCancelSellAboveRatio = src.distributeCancelSellAboveRatio;
            washCancelBuyBelowRatio = src.washCancelBuyBelowRatio;
            washCancelSellAboveRatio = src.washCancelSellAboveRatio;
            riskExposureWeight = src.riskExposureWeight;
            riskUnrealizedWeight = src.riskUnrealizedWeight;
            riskDrawdownWeight = src.riskDrawdownWeight;
            riskVolatilityWeight = src.riskVolatilityWeight;
            riskTrendWeight = src.riskTrendWeight;
            riskUnrealizedLossFull = src.riskUnrealizedLossFull;
            riskDrawdownFull = src.riskDrawdownFull;
            riskVolatilityFull = src.riskVolatilityFull;
            riskTrendDownFull = src.riskTrendDownFull;
            riskProfitReliefMax = src.riskProfitReliefMax;
            riskProfitReliefSlope = src.riskProfitReliefSlope;
            return this;
        }

        /** 將快照參數（含邊界驗證）套用回策略實例 */
        public void applyTo(MainForceStrategyWithOrderBook dst) {
            dst.accumulateMinTicks = Math.max(1, accumulateMinTicks);
            dst.accumulateMaxTicks = Math.max(dst.accumulateMinTicks, accumulateMaxTicks);
            dst.markupMinTicks = Math.max(1, markupMinTicks);
            dst.markupMaxTicks = Math.max(dst.markupMinTicks, markupMaxTicks);
            dst.distributeMinTicks = Math.max(1, distributeMinTicks);
            dst.distributeMaxTicks = Math.max(dst.distributeMinTicks, distributeMaxTicks);
            dst.washMinTicks = Math.max(1, washMinTicks);
            dst.washMaxTicks = Math.max(dst.washMinTicks, washMaxTicks);
            dst.setReplaceIntervalTicks(replaceIntervalTicks);
            dst.orderManagementIntervalTicks = Math.max(1, orderManagementIntervalTicks);
            dst.maxDeviationIdle = clamp(maxDeviationIdle, 0.0, 0.5);
            dst.maxDeviationAccumulate = clamp(maxDeviationAccumulate, 0.0, 0.5);
            dst.maxDeviationMarkup = clamp(maxDeviationMarkup, 0.0, 0.5);
            dst.maxDeviationDistribute = clamp(maxDeviationDistribute, 0.0, 0.5);
            dst.maxDeviationWash = clamp(maxDeviationWash, 0.0, 0.5);
            dst.maxAgeIdleMs = Math.max(1000L, maxAgeIdleMs);
            dst.maxAgeAccumulateMs = Math.max(1000L, maxAgeAccumulateMs);
            dst.maxAgeMarkupMs = Math.max(1000L, maxAgeMarkupMs);
            dst.maxAgeDistributeMs = Math.max(1000L, maxAgeDistributeMs);
            dst.maxAgeWashMs = Math.max(1000L, maxAgeWashMs);
            dst.markupCancelBuyBelowRatio = clamp(markupCancelBuyBelowRatio, 0.5, 1.5);
            dst.markupCancelSellAboveRatio = clamp(markupCancelSellAboveRatio, 0.5, 2.0);
            dst.distributeCancelSellAboveRatio = clamp(distributeCancelSellAboveRatio, 0.5, 2.0);
            dst.washCancelBuyBelowRatio = clamp(washCancelBuyBelowRatio, 0.5, 1.5);
            dst.washCancelSellAboveRatio = clamp(washCancelSellAboveRatio, 0.5, 2.0);
            dst.riskExposureWeight = clamp(riskExposureWeight, 0.0, 1.0);
            dst.riskUnrealizedWeight = clamp(riskUnrealizedWeight, 0.0, 1.0);
            dst.riskDrawdownWeight = clamp(riskDrawdownWeight, 0.0, 1.0);
            dst.riskVolatilityWeight = clamp(riskVolatilityWeight, 0.0, 1.0);
            dst.riskTrendWeight = clamp(riskTrendWeight, 0.0, 1.0);
            dst.riskUnrealizedLossFull = clamp(riskUnrealizedLossFull, 0.01, 1.0);
            dst.riskDrawdownFull = clamp(riskDrawdownFull, 0.01, 1.0);
            dst.riskVolatilityFull = clamp(riskVolatilityFull, 0.005, 1.0);
            dst.riskTrendDownFull = clamp(riskTrendDownFull, 0.005, 1.0);
            dst.riskProfitReliefMax = clamp(riskProfitReliefMax, 0.0, 1.0);
            dst.riskProfitReliefSlope = clamp(riskProfitReliefSlope, 0.0, 2.0);
        }

        private static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }

    /** 取得當前參數快照（供 UI 讀取） */
    public MainForceLimitConfig getLimitConfig() {
        return new MainForceLimitConfig().captureFrom(this);
    }

    /** 套用 UI 傳入的參數快照（含邊界驗證） */
    public void applyLimitConfig(MainForceLimitConfig c) {
        if (c != null) c.applyTo(this);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public void setManualPhase(String phaseName, boolean lock) {
        try {
            Phase p;
            // 支援兩種命名格式：全大寫（UI使用）和首字母大寫（保持相容性）
            switch (phaseName.toUpperCase()) {
                case "IDLE": 
                case "待機": 
                    p = Phase.待機; 
                    break;
                case "ACCUMULATE": 
                case "ACCUM":
                case "吸籌": 
                    p = Phase.吸籌; 
                    break;
                case "MARKUP": 
                case "拉抬": 
                case "拉升": 
                    p = Phase.拉抬; 
                    break;
                case "DISTRIBUTE": 
                case "DIST":
                case "出貨": 
                case "派發": 
                    p = Phase.出貨; 
                    break;
                case "WASH": 
                case "洗盤": 
                    p = Phase.洗盤; 
                    break;
                default: 
                    // 嘗試直接解析中文enum值
                    try {
                        p = Phase.valueOf(phaseName);
                    } catch (Exception e) {
                        logger.warn("無法識別的階段名稱: " + phaseName + "，保持當前階段", "MAIN_FORCE_PHASE");
                        return;
                    }
                    break;
            }
            this.manualPhase = p;
            this.manualLock = lock;
            if (lock) {
                this.phase = p;
                this.phaseTicks = 0;
                LogicAudit.info("MAIN_FORCE_PHASE", "manual lock -> " + p.name());
                logger.info(String.format("[主力手動控制] 階段已鎖定為：%s (來源：%s)", p.name(), phaseName), "MAIN_FORCE_PHASE");
            } else {
                logger.info(String.format("[主力手動控制] 設定手動階段為：%s (未鎖定，來源：%s)", p.name(), phaseName), "MAIN_FORCE_PHASE");
            }
        } catch (Exception e) {
            logger.error("設定主力手動階段時發生錯誤：" + e.getMessage(), "MAIN_FORCE_PHASE");
        }
    }

    /**
     * 構造函數
     *
     * @param orderBook 訂單簿實例
     * @param stock 股票實例
     * @param simulation 模擬實例
     * @param initialCash 初始現金
     */
    public MainForceStrategyWithOrderBook(OrderBook orderBook, Stock stock, StockMarketModel model, double initialCash) {
        this(orderBook, stock, model, initialCash, new Random(), Clock.systemUTC());
    }

    public MainForceStrategyWithOrderBook(OrderBook orderBook, Stock stock,
            StockMarketModel model, double initialCash, Random random) {
        this(orderBook, stock, model, initialCash, random, Clock.systemUTC());
    }

    public MainForceStrategyWithOrderBook(OrderBook orderBook, Stock stock,
            StockMarketModel model, double initialCash, Random random, Clock clock) {
        this.orderBook = orderBook;
        this.stock = stock;
        this.model = model;
        this.tradeLog = new StringBuilder();
        this.random = java.util.Objects.requireNonNull(random, "random");
        this.orderAges = new OrderAgeTracker(clock);
        this.recentVolumes = new LinkedList<>();
        this.recentPrices = new LinkedList<>();
        this.volatility = 0.0;
        this.account = new UserAccount(initialCash, initStock);
    }

    // Trader 接口實現
    /**
     * 獲取交易者的帳戶
     *
     * @return UserAccount 實例
     */
    @Override
    public UserAccount getAccount() {
        return account;
    }

    /**
     * 獲取交易者的類型
     *
     * @return 交易者類型的字串表示
     */
    @Override
    public String getTraderType() {
        return "MAIN_FORCE";
    }

    /**
     * 更新交易者在交易後的帳戶狀態 (限價單)
     *
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;
        if ("buy".equals(type)) {
            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();
            // 更新目標價
            calculateTargetPrice();
            logger.info(String.format("【限價買入後更新】主力買入 %d 股，成交價 %.2f，更新後平均成本價 %.2f",
                    volume, price, averageCostPrice), "TRANSACTION_UPDATE");
        } else if ("sell".equals(type)) {
            // 限價單賣出：增加現金
            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }
            logger.info(String.format("【限價賣出後更新】主力賣出 %d 股，成交價 %.2f，更新後持股 %d 股",
                    volume, price, getAccumulatedStocks()), "TRANSACTION_UPDATE");
        }

        // 更新界面上的標籤
        // 替換原來的 simulation.updateLabels();
        if (model != null) {
            model.updateLabels();
        } else {
            // 如果 model 為 null，使用日誌記錄
            logger.warn(String.format(
                    "無法更新界面標籤，model 為 null (type=%s, volume=%d, price=%.2f)",
                    type, volume, price
            ), "TRANSACTION_UPDATE");
        }
    }

    /**
     * 更新交易者在交易後的帳戶狀態 (市價單)
     *
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    public void updateAverageCostPrice(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            // 扣款並加股

            // 更新平均成本
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();

            calculateTargetPrice();
            logger.info(String.format("【市價買入後更新】主力買入 %d 股，成交價 %.2f，更新後平均成本價 %.2f",
                    volume, price, averageCostPrice), "TRANSACTION_UPDATE");

        } else if ("sell".equals(type)) {
            // 扣股並加款

            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }
            logger.info(String.format("【市價賣出後更新】主力賣出 %d 股，成交價 %.2f，更新後持股 %d 股",
                    volume, price, getAccumulatedStocks()), "TRANSACTION_UPDATE");
        }

        if (model != null) {
            model.updateLabels();
        } else {
            logger.warn("無法更新界面標籤，model 為 null", "TRANSACTION_UPDATE");
        }
    }

    /**
     * 主力行為：根據市場狀況做出交易決策 - 增強版 支持各種訂單類型和更多策略選擇
     */
    public void makeDecision() {
        // 定期執行訂單管理
        orderManagementCounter++;
        if (orderManagementCounter >= Math.max(1, orderManagementIntervalTicks)) {
            orderManagementCounter = 0;
            manageOutdatedOrders();
        }
        
        try {
            double currentPrice = stock.getPrice();
            double availableFunds = account.getAvailableFunds();
            double sma = model.getMarketAnalyzer().calculateSMA();
            double rsi = model.getMarketAnalyzer().getRSI();
            double volatility = model.getMarketAnalyzer().calculateVolatility();
            double riskFactor = getRiskFactor(); // 計算主力的風險係數

            logger.info(String.format(
                    "主力決策分析：當前價格=%.2f, 可用資金=%.2f, SMA=%.2f, 波動性=%.4f, 風險係數=%.2f",
                    currentPrice, availableFunds, sma, volatility, riskFactor
            ), "MAIN_FORCE_DECISION");

            // 嘗試獲取價格趨勢
            double recentTrend = 0.0;
            try {
                recentTrend = model.getMarketAnalyzer().getRecentPriceTrend();
                logger.debug(String.format("最近價格趨勢: %.4f", recentTrend), "MAIN_FORCE_TREND");
            } catch (Exception e) {
                logger.warn("無法獲取價格趨勢：" + e.getMessage(), "MAIN_FORCE_TREND");
            }

            // Tape/內外盤/牆面訊號（簡版）
            InOutTapeSignal sig = computeInOutAndSpeed();
            WallInfo wall = computeOrderBookWalls();

            // 顯示當前撮合模式（台股固定）
            MatchingMode currentMode = orderBook.getMatchingMode();
            logger.info(String.format("當前撮合模式: %s", currentMode), "MAIN_FORCE_DECISION");

            if (!Double.isNaN(sma)) {
                double priceDifferenceRatio = (currentPrice - sma) / sma;
                // 限制價格差在 [-0.5, 0.5] 之間
                priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

                double actionProbability = random.nextDouble();
                StringBuilder decisionLog = new StringBuilder();

                // ====== 主力階段轉移邏輯 ======
                if (cooldownTicks > 0) {
                    cooldownTicks--;
                }
                phaseTicks++;

                // 若啟用手動鎖定，僅維持手動階段，不自動轉移
                if (manualLock) {
                    phase = manualPhase;
                } else {

                // 根據價格與均線關係、波動與風險因子決定階段
                switch (phase) {
            case 待機:
                        if (priceDifferenceRatio < -0.03 && riskFactor < 0.7) {
                            phase = Phase.吸籌; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "待機 -> 吸籌");
                            decisionLog.append("【切換階段】待機 -> 吸籌\n");
                        } else if (priceDifferenceRatio > 0.04 && getAccumulatedStocks() > 0) {
                            phase = Phase.出貨; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "待機 -> 出貨");
                            decisionLog.append("【切換階段】待機 -> 出貨\n");
                        }
                        break;
                    case 吸籌:
                        if (phaseTicks >= getMinTicksForPhase(Phase.吸籌)
                                && getAccumulatedStocks() > 500 && (recentTrend > 0.02 || sig.outPct >= model.getEventEffectiveThresholdOr(65) || sig.tickImbalance > 0.25)) {
                            phase = Phase.拉抬; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "吸籌 -> 拉抬");
                            decisionLog.append("【切換階段】吸籌 -> 拉抬\n");
                        } else if (phaseTicks > getMaxTicksForPhase(Phase.吸籌)) {
                            phase = Phase.待機; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "吸籌 -> 待機(timeout)");
                        }
                        break;
                    case 拉抬:
                        if (phaseTicks >= getMinTicksForPhase(Phase.拉抬)
                                && currentPrice > calculateTargetPrice() * 0.95) {
                            phase = Phase.出貨; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "拉抬 -> 出貨");
                            decisionLog.append("【切換階段】拉抬 -> 出貨\n");
                        } else if (phaseTicks > getMaxTicksForPhase(Phase.拉抬)) {
                            phase = Phase.待機; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "拉抬 -> 待機(timeout)");
                        }
                        break;
                    case 出貨:
                        if (phaseTicks >= getMinTicksForPhase(Phase.出貨)
                                && getAccumulatedStocks() < 200) {
                            phase = Phase.待機; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "出貨 -> 待機");
                            decisionLog.append("【切換階段】出貨 -> 待機\n");
                        } else if ((recentTrend < -0.03 || sig.inPct >= model.getEventEffectiveThresholdOr(65) || sig.tickImbalance < -0.25) && phaseTicks >= 10) {
                            phase = Phase.洗盤; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "出貨 -> 洗盤");
                            decisionLog.append("【切換階段】出貨 -> 洗盤\n");
                        }
                        break;
                    case 洗盤:
                        if (phaseTicks >= getMinTicksForPhase(Phase.洗盤)
                                && (phaseTicks > getMaxTicksForPhase(Phase.洗盤) || priceDifferenceRatio < -0.02)) {
                            phase = Phase.吸籌; phaseTicks = 0;
                            LogicAudit.info("MAIN_FORCE_PHASE", "洗盤 -> 吸籌");
                            decisionLog.append("【切換階段】洗盤 -> 吸籌\n");
                        }
                        break;
                }
                }

                // ====== 主力階段行為 ======
                // phaseActed：若階段機本 tick 已執行交易，則跳過下方機率策略區，避免雙路徑疊加
                boolean phaseActed = false;
                if (cooldownTicks == 0) {
                    switch (phase) {
                        case 吸籌: {
                            double speedFactor = 1.0 + Math.min(1.0, sig.vps / 800.0);
                            double evtScale = 1.0;
                            try { if (model != null) evtScale = model.getEventPositionScale(); }
                            catch (Exception ignore) { logOptionalFailure(ignore); }
                            int vol = Math.max(50, (int)(calculateValueBuyVolume() * 0.2 * speedFactor * evtScale * computeTechScale()));
                            // 分層掛單：在現價下方多層吸籌
                            int placed = 0;
                            for (int i = 1; i <= 3; i++) {
                                double px = computeBuyLimitPrice(currentPrice * (1 - i * 0.005), sma, rsi, volatility);
                                Order o = Order.createLimitBuyOrder(px, Math.max(10, vol / 3), this);
                                trackOrderCreation(o.getId());
                                executeIntent(orderBook, OrderIntent.limit(OrderSide.BUY,
                                        o.getVolume(), px, "main force ladder buy"));
                                placed += o.getVolume();
                                LogicAudit.info("MAIN_FORCE_ORDER", String.format("ACCUM buy %d @ %.4f", o.getVolume(), px));
                            }
                            // 牆面迴避：若賣側牆明顯，降低撤換間隔，靠近牆下方掛單
                            if (wall.sellWall) {
                                setReplaceIntervalTicks(Math.max(1, getReplaceIntervalTicks() - 2));
                            }
                            // 定期撤換遠離現價的本方買單（保持靠近現價）
                            try {
                                if (phaseTicks % Math.max(1, replaceIntervalTicks) == 0) {
                                    java.util.List<Order> buys = orderBook.getBuyOrders();
                                    int maxCancel = Math.min(5, buys.size());
                                    tickCanceledOrders = 0;
                                    for (int i = buys.size() - 1; i >= 0 && maxCancel > 0; i--) {
                                        Order bo = buys.get(i);
                                        if (bo.getTrader() == this && bo.getPrice() < currentPrice * 0.98) {
                                            if (orderBook.cancelOrder(bo.getId())) {
                                                maxCancel--;
                                                tickCanceledOrders++;
                                                totalCanceledOrders++;
                                                LogicAudit.info("MAIN_FORCE_CANCEL", String.format("buy id=%s px=%.4f", bo.getId(), bo.getPrice()));
                                            }
                                        }
                                    }
                                }
        } catch (Exception ignore) { logOptionalFailure(ignore); }
                            decisionLog.append(String.format("【ACCUMULATE】分層吸籌下單共 %d 股\n", placed));
                            cooldownTicks = 2;
                            phaseActed = true;
                            break;
                        }
                        case 拉抬: {
                            // 市價拉抬 + 撤掉掛在買一上方的賣單
                            double speedFactor = 1.0 + Math.min(1.0, sig.tps / 5.0);
                            // 若賣側牆存在，適度加大拉抬量以突破
                            double wallBoost = wall.sellWall ? 1.3 : 1.0;
                            double flowBoost = 1.0 + Math.max(0.0, Math.min(0.5, sig.tickImbalance)); // 失衡偏多時增加至+50%
                            double evtScale = 1.0;
                            try { if (model != null) evtScale = model.getEventPositionScale(); }
                            catch (Exception ignore) { logOptionalFailure(ignore); }
                            // 指標倉位縮放：MACD 直方 >0 放大、<0 縮小；K>80 減倉，K<20 放大
                            double techScale = 1.0;
                            try {
                                double macdHist = model.getLastMacdHist();
                                double kVal = model.getLastK();
                                if (!Double.isNaN(macdHist)) techScale *= (1.0 + Math.max(-0.2, Math.min(0.2, macdHist * 0.5)));
                                if (!Double.isNaN(kVal)) { if (kVal > 80) techScale *= 0.85; else if (kVal < 20) techScale *= 1.15; }
        } catch (Exception ignore) { logOptionalFailure(ignore); }
                            int vol = Math.max(50, (int)(calculateLiftVolume() * speedFactor * wallBoost * flowBoost * evtScale * techScale));
                            拉抬操作(vol);
                            decisionLog.append(String.format("【MARKUP】市價拉抬 %d 股\n", vol));
                            // 撤掉靠近買一之上的賣單（減少上方阻力）
                            try {
                                java.util.List<Order> sellSnapshot = orderBook.getSellOrders();
                                if (!sellSnapshot.isEmpty()) {
                                    int maxCancel = Math.min(3, sellSnapshot.size());
                                    for (int i = 0; i < maxCancel; i++) {
                                        Order so = sellSnapshot.get(i);
                                        // 只允許主力撤自己的賣單，避免誤傷用戶掛單
                                        if (so != null && so.getTrader() == this && so.getPrice() <= currentPrice * 1.01) {
                                            boolean ok = orderBook.cancelOrder(so.getId());
                                            if (ok) {
                                                decisionLog.append(String.format("【MARKUP】撤銷賣單ID=%s 價格=%.2f 量=%d\n", so.getId(), so.getPrice(), so.getVolume()));
                                                LogicAudit.info("MAIN_FORCE_CANCEL", String.format("sell id=%s px=%.4f", so.getId(), so.getPrice()));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignore) { logOptionalFailure(ignore); }
                            cooldownTicks = 1;
                            phaseActed = true;
                            break;
                        }
                        case 出貨: {
                            // 分批出貨：在現價上方多層掛賣
                            int hold = getAccumulatedStocks();
                            if (hold > 0) {
                                double speedFactor = 1.0 + Math.min(1.0, sig.vps / 800.0);
                                double evtScale = 1.0;
                                try { if (model != null) evtScale = model.getEventPositionScale(); }
                                catch (Exception ignore) { logOptionalFailure(ignore); }
                                int chunk = Math.max(20, (int)(hold / 5 * speedFactor * evtScale * computeTechScale()));
                                int placed = 0;
                                for (int i = 1; i <= 3 && placed < hold; i++) {
                                    double px = computeSellLimitPrice(currentPrice * (1 + i * 0.005), sma, rsi, volatility);
                                    int size = Math.min(chunk, hold - placed);
                                    Order o = Order.createLimitSellOrder(px, size, this);
                                    trackOrderCreation(o.getId());
                                    executeIntent(orderBook, OrderIntent.limit(OrderSide.SELL,
                                            size, px, "main force ladder sell"));
                                    placed += size;
                                    LogicAudit.info("MAIN_FORCE_ORDER", String.format("DIST sell %d @ %.4f", size, px));
                                }
                                // 若買側牆明顯，增加撤換頻率（避免掛單阻塞）
                                if (wall.buyWall) {
                                    setReplaceIntervalTicks(Math.max(1, getReplaceIntervalTicks() - 2));
                                }
                                // 定期撤換遠離現價的本方賣單（保持靠近現價）
                                try {
                                    if (phaseTicks % Math.max(1, replaceIntervalTicks) == 0) {
                                        java.util.List<Order> sells = orderBook.getSellOrders();
                                        int maxCancel = Math.min(5, sells.size());
                                        tickCanceledOrders = 0;
                                        for (int i = sells.size() - 1; i >= 0 && maxCancel > 0; i--) {
                                            Order so = sells.get(i);
                                            if (so.getTrader() == this && so.getPrice() > currentPrice * 1.02) {
                                                if (orderBook.cancelOrder(so.getId())) {
                                                    maxCancel--;
                                                    tickCanceledOrders++;
                                                    totalCanceledOrders++;
                                                    LogicAudit.info("MAIN_FORCE_CANCEL", String.format("sell id=%s px=%.4f", so.getId(), so.getPrice()));
                                                }
                                            }
                                        }
                                    }
        } catch (Exception ignore) { logOptionalFailure(ignore); }
                                decisionLog.append(String.format("【DISTRIBUTE】分層出貨下單共 %d 股\n", placed));
                                cooldownTicks = 2;
                                phaseActed = true;
                            }
                            break;
                        }
                        case 洗盤: {
                            double evtScale = 1.0;
                            try { if (model != null) evtScale = model.getEventPositionScale(); }
                            catch (Exception ignore) { logOptionalFailure(ignore); }
                            int wash = (int)(calculateWashVolume(volatility) * (sig.inPct >= model.getEventEffectiveThresholdOr(65) ? 1.3 : 1.0) * evtScale);
                            洗盤操作(wash);
                            decisionLog.append(String.format("【WASH】洗盤賣出 %d 股\n", wash));
                            cooldownTicks = 2;
                            phaseActed = true;
                            break;
                        }
                        case 待機:
                        default:
                            // 保持少量偵測性單，避免完全沒有存在感
                            if (random.nextDouble() < 0.1 && availableFunds > currentPrice * 50) {
                                吸籌操作(30);
                                decisionLog.append("【IDLE】偵測性吸籌 30 股\n");
                                cooldownTicks = 3;
                                phaseActed = true;
                            }
                            break;
                    }
                }

                // 機率策略區（動量/均值回歸/價值投資等）：
                // 用 !phaseActed 整體保護，確保階段機已行動的 tick 不再疊加機率策略
                if (!phaseActed) {
                    // 1. 動量交易策略（追漲買入）
                    if (actionProbability < 0.1 && currentPrice > sma && volatility > 0.02) {
                        int momentumVolume = calculateMomentumVolume(volatility);
                        decisionLog.append("【動量交易-追漲】");
                        拉抬操作(momentumVolume);

                    // 2. 均值回歸策略
                    } else if (actionProbability < 0.15 && Math.abs(priceDifferenceRatio) > 0.1) {
                        if (priceDifferenceRatio > 0) {
                            int revertSellVolume = calculateRevertSellVolume(priceDifferenceRatio);
                            if (priceDifferenceRatio > 0.2) {
                                decisionLog.append("【均值回歸-市價賣出】極度高於均線");
                                市價賣出操作(revertSellVolume);
                            } else {
                                decisionLog.append("【均值回歸-限價賣出】高於均線");
                                賣出操作(revertSellVolume);
                            }
                        } else {
                            int revertBuyVolume = calculateRevertBuyVolume(priceDifferenceRatio);
                            decisionLog.append("【均值回歸-限價買入】低於均線");
                            吸籌操作(revertBuyVolume);
                        }

                    // 3. 價值投資策略（低估買入）
                    } else if (currentPrice < sma * 0.9 && actionProbability < 0.2) {
                        int valueBuyVolume = calculateValueBuyVolume();
                        if (recentTrend < -0.05) {
                            decisionLog.append("【價值投資-謹慎FOK買入】下跌趨勢中");
                            精確控制買入操作(valueBuyVolume / 3, currentPrice * 0.98);
                        } else {
                            decisionLog.append("【價值投資-限價買入】價格低估");
                            吸籌操作(valueBuyVolume);
                        }

                    // 4. 風險控制策略（風險過高時暫停操作）
                    } else if (riskFactor > 0.8) {
                        decisionLog.append(String.format("【風險管控】風險係數 %.2f 過高，主力暫停操作", riskFactor));
                        logger.info(decisionLog.toString(), "MAIN_FORCE_DECISION");
                        return;

                    // 5. 市價拉抬（追蹤市場流動性）
                    } else if (actionProbability < 0.25 && availableFunds > currentPrice * 100) {
                        int buyQuantity = calculateLiftVolume();
                        if (volatility > 0.03) {
                            decisionLog.append("【強力拉抬】高波動環境");
                            拉抬操作(buyQuantity * 2);
                        } else {
                            decisionLog.append("【常規拉抬】");
                            拉抬操作(buyQuantity);
                        }

                    // 6. 市價減倉（降低風險）
                    } else if (actionProbability < 0.3 && getAccumulatedStocks() > 0) {
                        int sellQuantity = calculateSellVolume();
                        if (rsi > 70) {
                            decisionLog.append("【超買減倉-市價賣出】RSI過高");
                            市價賣出操作(sellQuantity * 2);
                        } else {
                            decisionLog.append("【常規減倉-市價賣出】");
                            市價賣出操作(sellQuantity);
                        }

                    // 7. 洗盤
                    } else if (actionProbability < 0.35 && getAccumulatedStocks() > 200) {
                        decisionLog.append("【常規洗盤】");
                        洗盤操作(calculateWashVolume(volatility));

                    // 8. 隨機取消掛單（log only，實際 cancel 已注解）
                    } else if (actionProbability < 0.4) {
                        java.util.List<Order> buySnapshot = orderBook.getBuyOrders();
                        if (!buySnapshot.isEmpty()) {
                            Order orderToCancel = buySnapshot.get(0);
                            decisionLog.append(String.format("【隨機取消掛單】取消買單 ID: %s，數量: %d 股",
                                    orderToCancel.getId(), orderToCancel.getVolume()));
                        }

                    // 9. 目標價附近精確控制
                    } else if (actionProbability < 0.45 && getTargetPrice() > 0
                            && Math.abs(currentPrice - getTargetPrice()) / getTargetPrice() < 0.05) {
                        if (currentPrice >= getTargetPrice()) {
                            int profitVolume = (int) (getAccumulatedStocks() * 0.3);
                            if (profitVolume > 0) {
                                decisionLog.append("【獲利了結-FOK賣出】已達目標價");
                                精確控制賣出操作(profitVolume, currentPrice);
                            }
                        } else {
                            int topupVolume = calculateValueBuyVolume() / 4;
                            if (topupVolume > 0 && availableFunds > currentPrice * topupVolume) {
                                decisionLog.append("【目標布局-FOK買入】接近目標價");
                                精確控制買入操作(topupVolume, currentPrice);
                            }
                        }

                    // 10. 常規補倉
                    } else if (actionProbability < 0.5) {
                        if (availableFunds > currentPrice * 100 && getAccumulatedStocks() < 500) {
                            decisionLog.append("【常規策略】常規買入");
                            吸籌操作(100);
                        }
                    }
                }

                // 輸出決策日誌
                if (decisionLog.length() > 0) {
                    logger.debug(decisionLog.toString(), "MAIN_FORCE_DECISION");
                }

            } else {
                logger.warn("無法計算 SMA，主力暫停操作", "MAIN_FORCE_DECISION");
            }
        } catch (Exception e) {
            logger.error("主力決策執行異常：" + e.getMessage(), "MAIN_FORCE_DECISION");
            logger.error("異常堆棧追蹤：" + Arrays.toString(e.getStackTrace()), "MAIN_FORCE_DECISION");
        }
    }

    /**
     * 吸籌操作：使用限價買單吸籌
     */
    public int 吸籌操作(int volume) {
        try {
            double limitPrice = computeBuyLimitPrice(
                    stock.getPrice(),
                    model.getMarketAnalyzer().getSMA(),
                    model.getMarketAnalyzer().getRSI(),
                    model.getMarketAnalyzer().getVolatility()
            );

            logger.debug(String.format(
                    "吸籌操作初始化：買入量=%d, 限價=%.2f",
                    volume, limitPrice
            ), "MAIN_FORCE_ACCUMULATE");

            // 檢查可用賣單
            int availableVolume = orderBook.getAvailableSellVolume(limitPrice);
            if (availableVolume < volume) {
                logger.warn(String.format(
                        "吸籌操作失敗：可用賣單不足，需求=%d, 可用=%d",
                        volume, availableVolume
                ), "MAIN_FORCE_ACCUMULATE");
                return 0;
            }

            // 檢查資金
            if (account.getAvailableFunds() < limitPrice * volume) {
                logger.warn(String.format(
                        "吸籌操作失敗：資金不足，需求=%.2f, 可用=%.2f",
                        limitPrice * volume, account.getAvailableFunds()
                ), "MAIN_FORCE_ACCUMULATE");
                return 0;
            }

            // 創建並提交買單
            Order buyOrder = Order.createLimitBuyOrder(limitPrice, volume, this);
            trackOrderCreation(buyOrder.getId());
            executeIntent(orderBook, OrderIntent.limit(OrderSide.BUY,
                    volume, limitPrice, "main force limit buy"));

            logger.info(String.format(
                    "吸籌操作成功：買入 %d 股 @ %.2f",
                    volume, limitPrice
            ), "MAIN_FORCE_ACCUMULATE");

            return volume;
        } catch (Exception e) {
            logger.error(String.format(
                    "吸籌操作異常：買入量=%d, 錯誤=%s",
                    volume, e.getMessage()
            ), "MAIN_FORCE_ACCUMULATE");
            return 0;
        }
    }

    /**
     * 賣出操作：使用限價賣單
     */
    public int 賣出操作(int volume) {
        try {
            double limitPrice = computeSellLimitPrice(
                    stock.getPrice(),
                    model.getMarketAnalyzer().getSMA(),
                    model.getMarketAnalyzer().getRSI(),
                    model.getMarketAnalyzer().getVolatility()
            );

            logger.debug(String.format(
                    "賣出操作初始化：賣出量=%d, 限價=%.2f",
                    volume, limitPrice
            ), "MAIN_FORCE_SELL");

            // 檢查可用買單
            int availableBuy = orderBook.getAvailableBuyVolume(limitPrice);
            if (availableBuy < volume) {
                logger.warn(String.format(
                        "賣出操作失敗：可用買單不足，需求=%d, 可用=%d",
                        volume, availableBuy
                ), "MAIN_FORCE_SELL");
                return 0;
            }

            // 檢查持股
            if (getAccumulatedStocks() < volume) {
                logger.warn(String.format(
                        "賣出操作失敗：持股不足，需求=%d, 可用=%d",
                        volume, getAccumulatedStocks()
                ), "MAIN_FORCE_SELL");
                return 0;
            }

            // 創建並提交賣單
            Order sellOrder = Order.createLimitSellOrder(limitPrice, volume, this);
            trackOrderCreation(sellOrder.getId());
            executeIntent(orderBook, OrderIntent.limit(OrderSide.SELL,
                    volume, limitPrice, "main force limit sell"));

            logger.info(String.format(
                    "賣出操作成功：賣出 %d 股 @ %.2f",
                    volume, limitPrice
            ), "MAIN_FORCE_SELL");

            return volume;
        } catch (Exception e) {
            logger.error(String.format(
                    "賣出操作異常：賣出量=%d, 錯誤=%s",
                    volume, e.getMessage()
            ), "MAIN_FORCE_SELL");
            return 0;
        }
    }

    /**
     * 洗盤操作：大量賣出壓低股價
     */
    public int 洗盤操作(int volume) {
        try {
            double price = stock.getPrice();

            logger.debug(String.format(
                    "洗盤操作初始化：洗盤量=%d, 當前價格=%.2f",
                    volume, price
            ), "MAIN_FORCE_WASH");

            // 檢查持股
            if (getAccumulatedStocks() < volume) {
                logger.warn(String.format(
                        "洗盤操作失敗：持股不足，需求=%d, 可用=%d",
                        volume, getAccumulatedStocks()
                ), "MAIN_FORCE_WASH");
                return 0;
            }

            // 根據洗盤需求決定訂單類型
            if (random.nextDouble() < 0.3) {
                // FOK賣單
                boolean success = executeIntent(orderBook, OrderIntent.fok(OrderSide.SELL,
                        volume, price * 0.99, "main force wash FOK sell")).accepted();
                if (success) {
                    logger.info(String.format(
                            "洗盤操作成功：FOK賣出 %d 股 @ %.2f",
                            volume, price * 0.99
                    ), "MAIN_FORCE_WASH");
                    return volume;
                } else {
                    logger.warn("洗盤操作失敗：FOK賣單未成交", "MAIN_FORCE_WASH");
                    return 0;
                }
            } else {
                // 普通限價賣單
                Order sellOrder = Order.createLimitSellOrder(price, volume, this);
                trackOrderCreation(sellOrder.getId());
                executeIntent(orderBook, OrderIntent.limit(OrderSide.SELL,
                        volume, price, "main force wash limit sell"));

                logger.info(String.format(
                        "洗盤操作成功：限價賣出 %d 股 @ %.2f",
                        volume, price
                ), "MAIN_FORCE_WASH");
                return volume;
            }
        } catch (Exception e) {
            logger.error(String.format(
                    "洗盤操作異常：洗盤量=%d, 錯誤=%s",
                    volume, e.getMessage()
            ), "MAIN_FORCE_WASH");
            return 0;
        }
    }

    /**
     * 拉抬操作：主力市價買入
     */
    public int 拉抬操作(int volume) {
        try {
            double price = stock.getPrice();

            logger.debug(String.format(
                    "拉抬操作初始化：拉抬量=%d, 當前價格=%.2f, 可用資金=%.2f",
                    volume, price, account.getAvailableFunds()
            ), "MAIN_FORCE_LIFT");

            // 檢查資金
            if (account.getAvailableFunds() < price * volume) {
                logger.warn(String.format(
                        "拉抬操作失敗：資金不足，需求=%.2f, 可用=%.2f",
                        price * volume, account.getAvailableFunds()
                ), "MAIN_FORCE_LIFT");
                return 0;
            }

            // 市價單門檻化：僅在強流時放行，否則改用限價靠檔
            boolean usedMarket = false;
            int executedVolume = volume;
            try {
                double tps = model != null ? model.getRecentTPS(40) : 0.0;
                double imb = model != null ? model.getRecentTickImbalance(40) : 0.0;
                boolean strongFlow = (tps >= 2.5) && (imb > 0.20);
                if (strongFlow) {
                    var result = executeIntent(orderBook, OrderIntent.market(OrderSide.BUY,
                            volume, "main force market buy"));
                    executedVolume = result.execution() != null ? result.execution().filledVolume() : 0;
                    usedMarket = true;
                } else {
                    double px = stock.getPrice() * 1.001; // 小幅抬價
                    px = orderBook.adjustPriceToUnit(px);
                    Order o = Order.createLimitBuyOrder(px, volume, this);
                    trackOrderCreation(o.getId());
                    executeIntent(orderBook, OrderIntent.limit(OrderSide.BUY,
                            volume, px, "main force limit buy"));
                }
            } catch (Exception ex) {
                var result = executeIntent(orderBook, OrderIntent.market(OrderSide.BUY,
                        volume, "main force market buy"));
                executedVolume = result.execution() != null ? result.execution().filledVolume() : 0;
                usedMarket = true;
            }

            if (usedMarket) {
                logger.info(String.format(
                        "拉抬操作完成：市價買入 %d/%d 股，預計成本上限 %.2f",
                        executedVolume, volume, price * volume
                ), "MAIN_FORCE_LIFT");
            } else {
                logger.info(String.format(
                        "拉抬操作成功：限價靠檔買入 %d 股，掛價參考 %.2f",
                        volume, price
                ), "MAIN_FORCE_LIFT");
            }

            // 因子日誌（主力）
            try {
                InOutTapeSignal sig = computeInOutAndSpeed();
                WallInfo wall = computeOrderBookWalls();
                int effTh = model != null ? model.getEventEffectiveThresholdOr(65) : 65;
                double posScale = model != null ? model.getEventPositionScale() : 1.0;
                double macdHist = model != null ? model.getLastMacdHist() : Double.NaN;
                double kVal = model != null ? model.getLastK() : Double.NaN;
                long delta = (long)Math.max(0, sig.outPct) - (long)Math.max(0, sig.inPct);
                StockMainAction.util.logging.DecisionFactorLogger.log(
                        "MainForce", getPhaseName(), "LIFT", usedMarket?"MARKET":"LIMIT",
                        volume, price, sig.inPct, sig.outPct, delta, sig.tps, sig.vps, sig.tickImbalance,
                        effTh, posScale, macdHist, kVal, wall.buyWall, wall.sellWall);
        } catch (Exception ignore) { logOptionalFailure(ignore); }

            return usedMarket ? executedVolume : volume;
        } catch (Exception e) {
            logger.error(String.format(
                    "拉抬操作異常：拉抬量=%d, 錯誤=%s",
                    volume, e.getMessage()
            ), "MAIN_FORCE_LIFT");
            return 0;
        }
    }

    /**
     * 市價賣出操作
     */
    public int 市價賣出操作(int volume) {
        try {
            logger.debug(String.format(
                    "市價賣出操作初始化：賣出量=%d, 當前持股=%d",
                    volume, getAccumulatedStocks()
            ), "MAIN_FORCE_MARKET_SELL");

            // 檢查持股
            if (getAccumulatedStocks() < volume) {
                logger.warn(String.format(
                        "市價賣出失敗：持股不足，需求=%d, 可用=%d",
                        volume, getAccumulatedStocks()
                ), "MAIN_FORCE_MARKET_SELL");
                return 0;
            }

            // 市價單門檻化：僅在強流時放行，否則改用限價靠檔
            boolean usedMarket = false;
            int executedVolume = volume;
            try {
                double tps = model != null ? model.getRecentTPS(40) : 0.0;
                double imb = model != null ? model.getRecentTickImbalance(40) : 0.0;
                boolean strongFlow = (tps >= 2.5) && (imb < -0.20);
                if (strongFlow) {
                    var result = executeIntent(orderBook, OrderIntent.market(OrderSide.SELL,
                            volume, "main force market sell"));
                    executedVolume = result.execution() != null ? result.execution().filledVolume() : 0;
                    usedMarket = true;
                } else {
                    double px = stock.getPrice() * 0.999; // 小幅讓價
                    px = orderBook.adjustPriceToUnit(px);
                    Order o = Order.createLimitSellOrder(px, volume, this);
                    trackOrderCreation(o.getId());
                    executeIntent(orderBook, OrderIntent.limit(OrderSide.SELL,
                            volume, px, "main force limit sell"));
                }
            } catch (Exception ex) {
                var result = executeIntent(orderBook, OrderIntent.market(OrderSide.SELL,
                        volume, "main force market sell"));
                executedVolume = result.execution() != null ? result.execution().filledVolume() : 0;
                usedMarket = true;
            }

            if (usedMarket) {
                logger.info(String.format(
                        "市價賣出完成：實際賣出 %d/%d 股",
                        executedVolume, volume
                ), "MAIN_FORCE_MARKET_SELL");
            } else {
                logger.info(String.format(
                        "限價靠檔賣出成功：預計賣出 %d 股",
                        volume
                ), "MAIN_FORCE_MARKET_SELL");
            }

            // 因子日誌（主力）
            try {
                InOutTapeSignal sig = computeInOutAndSpeed();
                WallInfo wall = computeOrderBookWalls();
                int effTh = model != null ? model.getEventEffectiveThresholdOr(65) : 65;
                double posScale = model != null ? model.getEventPositionScale() : 1.0;
                double macdHist = model != null ? model.getLastMacdHist() : Double.NaN;
                double kVal = model != null ? model.getLastK() : Double.NaN;
                long delta = (long)Math.max(0, sig.outPct) - (long)Math.max(0, sig.inPct);
                StockMainAction.util.logging.DecisionFactorLogger.log(
                        "MainForce", getPhaseName(), "SELL", usedMarket?"MARKET":"LIMIT",
                        volume, stock.getPrice(), sig.inPct, sig.outPct, delta, sig.tps, sig.vps, sig.tickImbalance,
                        effTh, posScale, macdHist, kVal, wall.buyWall, wall.sellWall);
        } catch (Exception ignore) { logOptionalFailure(ignore); }

            return usedMarket ? executedVolume : volume;
        } catch (Exception e) {
            logger.error(String.format(
                    "市價賣出異常：賣出量=%d, 錯誤=%s",
                    volume, e.getMessage()
            ), "MAIN_FORCE_MARKET_SELL");
            return 0;
        }
    }

    /**
     * 精確控制買入操作：使用FOK買單
     */
    public int 精確控制買入操作(int volume, double price) {
        try {
            logger.debug(String.format(
                    "精確控制買入操作初始化：買入量=%d, 限價=%.2f, 可用資金=%.2f",
                    volume, price, account.getAvailableFunds()
            ), "MAIN_FORCE_PRECISE_BUY");

            // 檢查資金
            if (account.getAvailableFunds() < price * volume) {
                logger.warn(String.format(
                        "精確控制買入失敗：資金不足，需求=%.2f, 可用=%.2f",
                        price * volume, account.getAvailableFunds()
                ), "MAIN_FORCE_PRECISE_BUY");
                return 0;
            }

            // 使用FOK買單
            boolean success = executeIntent(orderBook, OrderIntent.fok(OrderSide.BUY,
                    volume, price, "main force precise FOK buy")).accepted();

            if (success) {
                logger.info(String.format(
                        "精確控制買入成功：FOK買入 %d 股 @ %.2f",
                        volume, price
                ), "MAIN_FORCE_PRECISE_BUY");
                return volume;
            } else {
                logger.warn(String.format(
                        "精確控制買入失敗：無法完全成交 %d 股 @ %.2f",
                        volume, price
                ), "MAIN_FORCE_PRECISE_BUY");
                return 0;
            }
        } catch (Exception e) {
            logger.error(String.format(
                    "精確控制買入異常：買入量=%d, 錯誤=%s",
                    volume, e.getMessage()
            ), "MAIN_FORCE_PRECISE_BUY");
            return 0;
        }
    }

    /**
     * 精確控制賣出操作：使用FOK賣單
     */
    public int 精確控制賣出操作(int volume, double price) {
        try {
            logger.debug(String.format(
                    "精確控制賣出操作初始化：賣出量=%d, 限價=%.2f, 當前持股=%d",
                    volume, price, getAccumulatedStocks()
            ), "MAIN_FORCE_PRECISE_SELL");

            // 檢查持股
            if (getAccumulatedStocks() < volume) {
                logger.warn(String.format(
                        "精確控制賣出失敗：持股不足，需求=%d, 可用=%d",
                        volume, getAccumulatedStocks()
                ), "MAIN_FORCE_PRECISE_SELL");
                return 0;
            }

            // 使用FOK賣單
            boolean success = executeIntent(orderBook, OrderIntent.fok(OrderSide.SELL,
                    volume, price, "main force precise FOK sell")).accepted();

            if (success) {
                logger.info(String.format(
                        "精確控制賣出成功：FOK賣出 %d 股 @ %.2f",
                        volume, price
                ), "MAIN_FORCE_PRECISE_SELL");
                return volume;
            } else {
                logger.warn(String.format(
                        "精確控制賣出失敗：無法完全成交 %d 股 @ %.2f",
                        volume, price
                ), "MAIN_FORCE_PRECISE_SELL");
                return 0;
            }
        } catch (Exception e) {
            logger.error(String.format(
                    "精確控制賣出異常：賣出量=%d, 錯誤=%s",
                    volume, e.getMessage()
            ), "MAIN_FORCE_PRECISE_SELL");
            return 0;
        }
    }

    // ==== 各種輔助計算函數 ====
    /**
     * 主力買單理想掛價（低接）
     */
    private double computeBuyLimitPrice(double currentPrice, double sma,
            double rsi, double volatility) {

        double smaOffset = (sma == 0) ? 0 : -(currentPrice - sma) / sma; // 低於均線 → 正值
        double rsiOffset = Double.isNaN(rsi) ? 0 : (50.0 - rsi) / 100.0; // RSI 30 → +0.20
        double volOffset = -volatility * 0.6;                            // 波動大 → 掛更低

        double totalOffset = smaOffset * smaWeight
                + rsiOffset * rsiWeight
                + volOffset * volatilityWeight;

        totalOffset = Math.max(-maxOffsetRatio, Math.min(totalOffset, maxOffsetRatio));
        return orderBook.adjustPriceToUnit(currentPrice * (1.0 + totalOffset));
    }

    /**
     * 主力賣單理想掛價（逢高）
     */
    private double computeSellLimitPrice(double currentPrice, double sma,
            double rsi, double volatility) {

        double smaOffset = (sma == 0) ? 0 : (currentPrice - sma) / sma; // 高於均線 → 正值
        double rsiOffset = Double.isNaN(rsi) ? 0 : (rsi - 50.0) / 100.0; // RSI 70 → +0.20
        double volOffset = volatility * 0.6;                            // 波動大 → 掛更高

        double totalOffset = smaOffset * smaWeight
                + rsiOffset * rsiWeight
                + volOffset * volatilityWeight;

        totalOffset = Math.max(-maxOffsetRatio, Math.min(totalOffset, maxOffsetRatio));
        return orderBook.adjustPriceToUnit(currentPrice * (1.0 + totalOffset));
    }

    /**
     * 計算目標價 (平均成本 + 預期利潤 + 波動性因子)
     *
     * @return 目標價
     */
    public double calculateTargetPrice() {
        targetPrice = averageCostPrice * (1 + EXPECTED_PROFIT_MARGIN + VOLATILITY_FACTOR * volatility);
        return targetPrice;
    }

    /**
     * 動量交易 - 計算欲買入量
     *
     * @param volatility 波動性
     * @return 動量交易量
     */
    private int calculateMomentumVolume(double volatility) {
        try {
            int volume = (int) (500 * volatility * (0.8 + random.nextDouble() * 0.4));
            logger.debug(String.format(
                    "動量交易量計算：波動性=%.4f, 計算量=%d",
                    volatility, volume
            ), "MOMENTUM_VOLUME");
            return volume;
        } catch (Exception e) {
            logger.warn("計算動量交易量時發生異常：" + e.getMessage(), "MOMENTUM_VOLUME");
            return 100; // 默认值
        }
    }

    /**
     * 均值回歸 - 計算賣出量 (priceDifferenceRatio > 0)
     *
     * @param priceDifferenceRatio 價格差異比例
     * @return 建議賣出量
     */
    public int calculateRevertSellVolume(double priceDifferenceRatio) {
        int volume = (int) (getAccumulatedStocks() * priceDifferenceRatio * (0.5 + random.nextDouble() * 0.5));
        volume = Math.min(volume, getAccumulatedStocks());
        logger.debug(String.format("【計算均值回歸賣出量】建議賣出 %d 股 (價格差異比例 %.2f)", volume, priceDifferenceRatio), "MAIN_FORCE_CALC");
        return volume;
    }

    /**
     * 均值回歸 - 計算買入量 (priceDifferenceRatio < 0)
     *
     * @param priceDifferenceRatio 價格差異比例
     * @return 建議買入量
     */
    public int calculateRevertBuyVolume(double priceDifferenceRatio) {
        double funds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        int maxAffordable = (int) (funds / currentPrice);
        int volume = (int) (maxAffordable * -priceDifferenceRatio * (0.5 + random.nextDouble() * 0.5));
        logger.debug(String.format("【計算均值回歸買入量】建議買入 %d 股 (可用資金 %.2f, 價格差異 %.2f)",
                volume, funds, priceDifferenceRatio), "MAIN_FORCE_CALC");
        return volume;
    }

    /**
     * 價值投資 - 計算買入量 (股價 < 0.9 * SMA) @ret
     *
     *
     * urn 買入量
     */
    public int calculateValueBuyVolume() {
        double funds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        int volume = (int) (funds / currentPrice * 0.5);
        logger.debug(String.format("【計算價值買入量】建議買入 %d 股 (資金 %.2f, 價格 %.2f)",
                volume, funds, currentPrice), "MAIN_FORCE_CALC");
        return volume;
    }

    /**
     * 根據波動性計算洗盤量
     *
     * @param volatility 波動性
     * @return 洗盤量
     */
    public int calculateWashVolume(double volatility) {
        return Math.max((int) (volatility * 1000), 50); // 最少 50 股
    }

    /**
     * 根據市場狀況計算拉抬量
     *
     * @return 拉抬量
     */
    public int calculateLiftVolume() {
        return random.nextInt(500) + 1; // 1~500 股
    }

    /**
     * 市價賣出計算賣出量 (10%~30% 持股)
     *
     * @return 賣出量
     */
    public int calculateSellVolume() {
        int hold = getAccumulatedStocks();
        if (hold <= 0) {
            return 0;
        }
        double ratio = 0.1 + 0.2 * random.nextDouble();
        int vol = (int) (hold * ratio);
        vol = Math.max(1, vol);
        logger.debug(String.format("【計算市價賣出量】預計賣出 %d 股 (目前持股 %d)", vol, hold), "MAIN_FORCE_CALC");
        return vol;
    }

    /**
     * 取得當前持股數量
     *
     * @return 主力持股量
     */
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    /**
     * 取得主力現金餘額
     *
     * @return 可用資金
     */
    public double getCash() {
        return account.getAvailableFunds();
    }

    /**
     * 取得主力目標價格
     */
    public double getTargetPrice() {
        return targetPrice;
    }

    /**
     * 取得主力平均成本
     */
    public double getAverageCostPrice() {
        return averageCostPrice;
    }

    /**
     * 返回交易紀錄 (如需)
     */
    public String getTradeLog() {
        return tradeLog.toString();
    }

    /**
     * 計算風險係數
     */
    private double getRiskFactor() {
        try {
            double price = stock != null ? stock.getPrice() : 0.0;
            int hold = Math.max(0, getAccumulatedStocks());
            double cash = Math.max(0.0, account.getAvailableFunds());
            double holdingValue = Math.max(0.0, price * hold);
            double totalAssets = cash + holdingValue;
            if (totalAssets <= 0.0) return 0.0;

            // 1) 倉位曝險（滿倉時高，但不會單獨把風險推到 1）
            double exposureRisk = clamp(holdingValue / totalAssets, 0.0, 1.0);

            // 2) 浮虧風險（平均成本以下越深，風險越高；+12% 浮虧視為滿風險）
            double unrealizedLossRisk = 0.0;
            double unrealizedPnlRate = 0.0;
            if (hold > 0 && averageCostPrice > 0.0) {
                unrealizedPnlRate = (price - averageCostPrice) / averageCostPrice;
                if (unrealizedPnlRate < 0.0) {
                    unrealizedLossRisk = clamp((-unrealizedPnlRate) / Math.max(0.01, riskUnrealizedLossFull), 0.0, 1.0);
                }
            }

            // 3) 回撤風險（相對歷史淨值峰值）
            if (peakTotalAssets <= 0.0) peakTotalAssets = totalAssets;
            peakTotalAssets = Math.max(peakTotalAssets, totalAssets);
            double drawdownRisk = 0.0;
            if (peakTotalAssets > 0.0) {
                double dd = (peakTotalAssets - totalAssets) / peakTotalAssets;
                drawdownRisk = clamp(dd / Math.max(0.01, riskDrawdownFull), 0.0, 1.0);
            }

            // 4) 波動風險（波動大時提高保守程度）
            double vol = 0.0;
            try {
                if (model != null && model.getMarketAnalyzer() != null) {
                    vol = Math.max(0.0, model.getMarketAnalyzer().calculateVolatility());
                }
        } catch (Exception ignore) { logOptionalFailure(ignore); }
            double volatilityRisk = clamp(vol / Math.max(0.005, riskVolatilityFull), 0.0, 1.0);

            // 5) 下行趨勢風險（僅在持倉曝險高時加重）
            double trendRisk = 0.0;
            try {
                if (model != null && model.getMarketAnalyzer() != null) {
                    double trend = model.getMarketAnalyzer().getRecentPriceTrend();
                    trendRisk = clamp((-trend) / Math.max(0.005, riskTrendDownFull), 0.0, 1.0) * exposureRisk;
                }
        } catch (Exception ignore) { logOptionalFailure(ignore); }

            double risk = riskExposureWeight * exposureRisk
                    + riskUnrealizedWeight * unrealizedLossRisk
                    + riskDrawdownWeight * drawdownRisk
                    + riskVolatilityWeight * volatilityRisk
                    + riskTrendWeight * trendRisk;

            // 交易順風時給一點緩衝，避免「一滿倉就接近 1.0」
            if (unrealizedPnlRate > 0.0) {
                risk -= Math.min(riskProfitReliefMax, unrealizedPnlRate * riskProfitReliefSlope);
            }
            return clamp(risk, 0.0, 1.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ===== 內外盤與 Tape 速度（簡化自模型交易記錄） =====
    private static class InOutTapeSignal {
        int inPct; // 內盤百分比
        int outPct; // 外盤百分比
        double tps; // 筆/秒 估計
        double vps; // 量/秒 估計
        double tickImbalance; // 近N筆買/賣筆數失衡（買-賣）/N，範圍[-1,1]
    }

    private InOutTapeSignal computeInOutAndSpeed() {
        InOutTapeSignal s = new InOutTapeSignal();
        try {
            java.util.List<Transaction> recent = model.getRecentTransactions(50);
            if (recent.isEmpty()) return s;
            long now = System.currentTimeMillis();
            long earliest = recent.get(0).getTimestamp();
            long spanMs = Math.max(1000L, now - earliest);
            long inVol = 0, outVol = 0, vol = 0;
            int buyTicks = 0, sellTicks = 0;
            for (Transaction t : recent) {
                vol += t.getVolume();
                // 以 buyerInitiated 判定：買方主動=外盤；賣方主動=內盤
                if (t.isBuyerInitiated()) { outVol += t.getVolume(); buyTicks++; } else { inVol += t.getVolume(); sellTicks++; }
            }
            long tot = Math.max(1, inVol + outVol);
            s.inPct = (int)Math.round(inVol * 100.0 / tot);
            s.outPct = 100 - s.inPct;
            double secs = spanMs / 1000.0;
            s.tps = recent.size() / Math.max(1.0, secs);
            s.vps = vol / Math.max(1.0, secs);
            int n = Math.max(1, buyTicks + sellTicks);
            s.tickImbalance = (buyTicks - sellTicks) / (double) n; // >0 偏多，<0 偏空
        } catch (Exception ignore) { logOptionalFailure(ignore); }
        return s;
    }

    private static class WallInfo { boolean buyWall; boolean sellWall; }
    private WallInfo computeOrderBookWalls() {
        WallInfo w = new WallInfo();
        try {
            java.util.List<StockMainAction.model.core.Order> buys = orderBook.getTopBuyOrders(5);
            java.util.List<StockMainAction.model.core.Order> sells = orderBook.getTopSellOrders(5);
            int buySum = buys.stream().mapToInt(StockMainAction.model.core.Order::getVolume).sum();
            int sellSum = sells.stream().mapToInt(StockMainAction.model.core.Order::getVolume).sum();
            int maxB = buys.stream().mapToInt(StockMainAction.model.core.Order::getVolume).max().orElse(0);
            int maxS = sells.stream().mapToInt(StockMainAction.model.core.Order::getVolume).max().orElse(0);
            w.buyWall = buySum>0 && maxB*100/buySum>=40 && maxB>= (int)(1.5 * Math.max(1, maxS));
            w.sellWall = sellSum>0 && maxS*100/sellSum>=40 && maxS>= (int)(1.5 * Math.max(1, maxB));
        } catch (Exception ignore) { logOptionalFailure(ignore); }
        return w;
    }

    // 技術指標倉位縮放：MACD 直方圖與 K 值
    private double computeTechScale() {
        double scale = 1.0;
        try {
            double macdHist = model != null ? model.getLastMacdHist() : Double.NaN;
            double kVal = model != null ? model.getLastK() : Double.NaN;
            if (!Double.isNaN(macdHist)) {
                double adj = Math.max(-0.2, Math.min(0.2, macdHist * 0.5)); // ±20%
                scale *= (1.0 + adj);
            }
            if (!Double.isNaN(kVal)) {
                if (kVal > 80) scale *= 0.85; else if (kVal < 20) scale *= 1.15;
            }
        } catch (Exception ignore) { logOptionalFailure(ignore); }
        return scale;
    }
    
    /**
     * 管理過時或不合理的訂單，並同步清理已成交訂單的追蹤記錄，防止 orderCreationTime 無限增長。
     */
    private void manageOutdatedOrders() {
        if (orderBook == null || model == null) return;
        
        double currentPrice = model.getStock().getPrice();
        double sma = model.getMarketAnalyzer().calculateSMA();
        
        List<Order> myOrders = new ArrayList<>();
        myOrders.addAll(orderBook.getBuyOrders().stream()
            .filter(o -> o.getTrader() == this)
            .collect(Collectors.toList()));
        myOrders.addAll(orderBook.getSellOrders().stream()
            .filter(o -> o.getTrader() == this)
            .collect(Collectors.toList()));

        // ── 清理已成交（從訂單簿消失）的追蹤記錄 ──────────────────────────
        // 只保留目前仍存活於訂單簿的訂單 ID，避免「成交但未撤銷」的記錄無限累積
        if (orderAges.size() > 0) {
            Set<String> activeIds = myOrders.stream()
                .map(Order::getId)
                .collect(Collectors.toSet());
            orderAges.retain(activeIds);
        }
        // ─────────────────────────────────────────────────────────────────
        
        for (Order order : myOrders) {
            boolean shouldCancel = false;
            String reason = "";
            
            // 1. 訂單年齡過大
            long maxAge = getMaxOrderAgeForPhase();
            if (orderAges.isOlderThan(order.getId(), maxAge)) {
                shouldCancel = true;
                reason = "訂單存在時間過長";
            }
            
            // 2. 價格偏離過大（根據階段動態調整）
            double priceDeviation = Math.abs(order.getPrice() - currentPrice) / currentPrice;
            double maxDeviation = getMaxPriceDeviationForPhase();
            
            if (priceDeviation > maxDeviation) {
                shouldCancel = true;
                reason = String.format("價格偏離過大 (%.2f%%)", priceDeviation * 100);
            }
            
            // 3. 與均線的關係（避免不利訂單）
            if (sma > 0) {
                if (order.getType().equals("buy") && order.getPrice() > sma * 1.10) {
                    shouldCancel = true;
                    reason = "買單價格遠高於均線";
                } else if (order.getType().equals("sell") && order.getPrice() < sma * 0.90) {
                    shouldCancel = true;
                    reason = "賣單價格遠低於均線";
                }
            }
            
            // 4. 階段特定邏輯
            if (!shouldCancel && shouldCancelByPhase(order, currentPrice)) {
                shouldCancel = true;
                reason = "階段特定條件觸發";
            }
            
            // 執行取消
            if (shouldCancel) {
                orderBook.cancelOrder(order.getId());
                orderAges.remove(order.getId());
                logger.info(String.format(
                    "[主力訂單管理] 取消%s訂單，價格=%.2f，原因=%s，階段=%s",
                    order.getType(), order.getPrice(), reason, phase.name()
                ), "ORDER_MANAGEMENT");
            }
        }
    }
    
    /**
     * 根據當前階段獲取最大價格偏離
     */
    private double getMaxPriceDeviationForPhase() {
        switch (phase) {
            case 吸籌: return maxDeviationAccumulate;
            case 拉抬: return maxDeviationMarkup;
            case 出貨: return maxDeviationDistribute;
            case 洗盤: return maxDeviationWash;
            case 待機: return maxDeviationIdle;
            default: return maxDeviationIdle;
        }
    }
    
    /**
     * 根據當前階段獲取最大訂單年齡
     */
    private long getMaxOrderAgeForPhase() {
        switch (phase) {
            case 吸籌: return maxAgeAccumulateMs;
            case 拉抬: return maxAgeMarkupMs;
            case 出貨: return maxAgeDistributeMs;
            case 洗盤: return maxAgeWashMs;
            case 待機: return maxAgeIdleMs;
            default: return maxAgeIdleMs;
        }
    }
    
    /**
     * 根據階段判斷是否取消訂單
     */
    private boolean shouldCancelByPhase(Order order, double currentPrice) {
        switch (phase) {
            case 吸籌:
            case 待機:
                // 這些階段不額外加取消條件（交由通用規則處理）
                break;
            case 拉抬:
                // 拉抬階段：取消過低的買單和過高的賣單
                if (order.getType().equals("buy") && order.getPrice() < currentPrice * markupCancelBuyBelowRatio) {
                    return true;
                }
                if (order.getType().equals("sell") && order.getPrice() > currentPrice * markupCancelSellAboveRatio) {
                    return true;
                }
                break;
                
            case 出貨:
                // 出貨階段：取消過高的賣單（需要快速出貨）
                if (order.getType().equals("sell") && order.getPrice() > currentPrice * distributeCancelSellAboveRatio) {
                    return true;
                }
                break;
                
            case 洗盤:
                // 洗盤階段：取消離譜的訂單
                if (order.getType().equals("buy") && order.getPrice() < currentPrice * washCancelBuyBelowRatio) {
                    return true;
                }
                if (order.getType().equals("sell") && order.getPrice() > currentPrice * washCancelSellAboveRatio) {
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }
    
    /**
     * 記錄訂單創建時間
     */
    private void trackOrderCreation(String orderId) {
        if (orderId != null) {
            orderAges.track(orderId);
        }
    }
}
