package StockMainAction.model;

import StockMainAction.controller.TechnicalIndicatorsCalculator;
import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import StockMainAction.model.core.Transaction;
import StockMainAction.model.core.TransactionJournal;
import StockMainAction.model.core.ExecutionResult;
import StockMainAction.model.core.OrderSubmissionResult;
import StockMainAction.service.PersonalTradeService;
import StockMainAction.util.logging.MarketLogger;
import StockMainAction.util.logging.LogicAudit;
import java.util.AbstractMap.SimpleEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.SwingUtilities;
import StockMainAction.model.user.UserAccount;

/**
 * 股票市場模型 - 包含核心業務邏輯 作為MVC架構中的Model組件
 */
public class StockMarketModel implements AutoCloseable {

    // 市場核心對象
    private Stock stock;
    private OrderBook orderBook;
    private MarketAnalyzer marketAnalyzer;
    // 多個做市商（提供雙邊流動性）
    private List<MarketBehavior> marketMakers;
    private MainForceStrategyWithOrderBook mainForce;
    private List<RetailInvestorAI> retailInvestors;
    private PersonalAI userInvestor;
    private PersonalTradeService personalTradeService;
    // 小額噪音交易者（主動吃單/侵略性掛單，增加成交與波動）
    private List<NoiseTraderAI> noiseTraders;

    /**
     * 噪音交易者自適應參數（由 UI/分析推送） - longHitRate01 / shortHitRate01: B(+10根)
     * 做多/做空命中率，範圍 0..1 - sampleN: 統計樣本數（太小則 NoiseTrader 不啟用自適應）
     */
    public static final class NoiseSignalQuality {

        public final double longHitRate01;
        public final double shortHitRate01;
        public final int sampleN;
        public final long updatedAtMs;
        public final boolean enabled;

        public NoiseSignalQuality(double longHitRate01, double shortHitRate01, int sampleN, long updatedAtMs, boolean enabled) {
            this.longHitRate01 = longHitRate01;
            this.shortHitRate01 = shortHitRate01;
            this.sampleN = sampleN;
            this.updatedAtMs = updatedAtMs;
            this.enabled = enabled;
        }
    }

    /**
     * Noise trader 自適應參數（可由 UI 調整）
     */
    public static final class NoiseAdaptiveConfig {

        public final boolean enabled;          // 是否啟用自適應
        public final int sampleMin;            // 最小樣本數（小於此值不啟用）
        public final double followHi;          // avgHit >= followHi → 順勢
        public final double followLo;          // avgHit <= followLo → 逆勢
        public final double biasWeight;        // 多空命中率差對買賣偏好影響係數（0..1）
        public final int maxChaseTicks;        // 追價tick上限（順勢且命中率高才追）
        public final double marketProbMin;     // 市價單比例下限
        public final double marketProbMax;     // 市價單比例上限
        public final double cancelProbBase;    // 撤單基礎機率（avgHit=0.5附近）
        public final double cancelProbSlope;   // 撤單機率斜率（命中率越低越容易撤）
        public final double replaceThBase;     // 撤單閾值基礎（偏離mid比例）
        public final double replaceThSlope;    // 撤單閾值斜率（命中率越低越容易撤）

        public NoiseAdaptiveConfig(
                boolean enabled,
                int sampleMin,
                double followHi,
                double followLo,
                double biasWeight,
                int maxChaseTicks,
                double marketProbMin,
                double marketProbMax,
                double cancelProbBase,
                double cancelProbSlope,
                double replaceThBase,
                double replaceThSlope
        ) {
            this.enabled = enabled;
            this.sampleMin = sampleMin;
            this.followHi = followHi;
            this.followLo = followLo;
            this.biasWeight = biasWeight;
            this.maxChaseTicks = maxChaseTicks;
            this.marketProbMin = marketProbMin;
            this.marketProbMax = marketProbMax;
            this.cancelProbBase = cancelProbBase;
            this.cancelProbSlope = cancelProbSlope;
            this.replaceThBase = replaceThBase;
            this.replaceThSlope = replaceThSlope;
        }

        public static NoiseAdaptiveConfig defaults() {
            return new NoiseAdaptiveConfig(
                    true,
                    30,
                    0.55,
                    0.45,
                    0.35,
                    3,
                    0.05,
                    0.85,
                    0.15,
                    1.0,
                    0.004,
                    0.01
            );
        }
    }

    private volatile NoiseAdaptiveConfig noiseAdaptiveConfig = NoiseAdaptiveConfig.defaults();

    private volatile NoiseSignalQuality noiseSignalQuality;

    public void setNoiseAdaptiveConfig(NoiseAdaptiveConfig cfg) {
        if (cfg == null) {
            return;
        }
        // 最小保護與夾值
        int sampleMin = Math.max(0, cfg.sampleMin);
        double followHi = Math.max(0.0, Math.min(1.0, cfg.followHi));
        double followLo = Math.max(0.0, Math.min(1.0, cfg.followLo));
        if (followLo > followHi) {
            double t = followLo;
            followLo = followHi;
            followHi = t;
        }
        double biasWeight = Math.max(0.0, Math.min(1.0, cfg.biasWeight));
        int maxChaseTicks = Math.max(0, Math.min(10, cfg.maxChaseTicks));
        double mpMin = Math.max(0.0, Math.min(1.0, cfg.marketProbMin));
        double mpMax = Math.max(0.0, Math.min(1.0, cfg.marketProbMax));
        if (mpMin > mpMax) {
            double t = mpMin;
            mpMin = mpMax;
            mpMax = t;
        }
        double cancelBase = Math.max(0.0, Math.min(1.0, cfg.cancelProbBase));
        double cancelSlope = Math.max(0.0, Math.min(5.0, cfg.cancelProbSlope));
        double repBase = Math.max(0.0, Math.min(0.1, cfg.replaceThBase));
        double repSlope = Math.max(0.0, Math.min(0.2, cfg.replaceThSlope));

        noiseAdaptiveConfig = new NoiseAdaptiveConfig(
                cfg.enabled, sampleMin, followHi, followLo, biasWeight, maxChaseTicks,
                mpMin, mpMax, cancelBase, cancelSlope, repBase, repSlope
        );
        // 立刻用新 sampleMin 重新計算 enabled（避免 UI 變更後要等下一輪）
        NoiseSignalQuality q = noiseSignalQuality;
        noiseSignalQuality = new NoiseSignalQuality(q.longHitRate01, q.shortHitRate01, q.sampleN, clock.millis(),
                cfg.enabled && q.sampleN >= sampleMin);
    }

    public NoiseAdaptiveConfig getNoiseAdaptiveConfig() {
        return noiseAdaptiveConfig;
    }

    public void setNoiseSignalQuality(double longHitRate01, double shortHitRate01, int sampleN) {
        double lh = Double.isFinite(longHitRate01) ? Math.max(0.0, Math.min(1.0, longHitRate01)) : 0.5;
        double sh = Double.isFinite(shortHitRate01) ? Math.max(0.0, Math.min(1.0, shortHitRate01)) : 0.5;
        int n = Math.max(0, sampleN);
        NoiseAdaptiveConfig cfg = noiseAdaptiveConfig;
        boolean en = cfg.enabled && n >= Math.max(0, cfg.sampleMin);
        noiseSignalQuality = new NoiseSignalQuality(lh, sh, n, clock.millis(), en);
    }

    public NoiseSignalQuality getNoiseSignalQuality() {
        return noiseSignalQuality;
    }

    // =========================
    // [RETAIL] 散戶策略模型設定（由 UI 設定）
    // =========================
    public enum RetailLogicModel {
        MIXED,          // 混合：保留既有邏輯（預設）
        TREND_FOLLOW,   // 趨勢追隨：趨勢/動能確認才進場
        MEAN_REVERT,    // 均值回歸：超買超賣 + 回到均線附近
        CONSERVATIVE    // 保守：更嚴格進場、低亂交易
    }

    public static final class RetailStrategyConfig {
        public final RetailLogicModel model;
        public final double riskPerTrade;      // 0..1（例如 0.03 = 3%）
        public final double randomTradeProb;   // 0..1（隨機交易觸發機率）
        public final double spreadLimitRatio;  // 0..1（價差比例上限，超過就不交易）
        public final double rsiBuy;            // 0..100
        public final double rsiSell;           // 0..100
        public final double trendEntry;        // 0..1（MA 趨勢強度門檻）
        public final double macdHistEntry;     // >=0（MACD histogram 絕對值門檻）
        public final int minTradeWaitTicks;    // 最小交易間隔（ticks）
        public final int lossCooldownPerLoss;  // 連虧冷卻：每多 1 次連虧增加 ticks

        public RetailStrategyConfig(
                RetailLogicModel model,
                double riskPerTrade,
                double randomTradeProb,
                double spreadLimitRatio,
                double rsiBuy,
                double rsiSell,
                double trendEntry,
                double macdHistEntry,
                int minTradeWaitTicks,
                int lossCooldownPerLoss
        ) {
            this.model = model == null ? RetailLogicModel.MIXED : model;
            this.riskPerTrade = riskPerTrade;
            this.randomTradeProb = randomTradeProb;
            this.spreadLimitRatio = spreadLimitRatio;
            this.rsiBuy = rsiBuy;
            this.rsiSell = rsiSell;
            this.trendEntry = trendEntry;
            this.macdHistEntry = macdHistEntry;
            this.minTradeWaitTicks = minTradeWaitTicks;
            this.lossCooldownPerLoss = lossCooldownPerLoss;
        }

        public static RetailStrategyConfig defaults() {
            return new RetailStrategyConfig(
                    RetailLogicModel.MIXED,
                    0.03,   // 3%
                    0.005,  // 0.5%
                    0.008,  // 0.8%
                    30.0,
                    70.0,
                    0.20,
                    0.02,
                    3,
                    20
            );
        }
    }

    private volatile RetailStrategyConfig retailStrategyConfig = RetailStrategyConfig.defaults();

    public RetailStrategyConfig getRetailStrategyConfig() {
        return retailStrategyConfig;
    }

    public void setRetailStrategyConfig(RetailStrategyConfig cfg) {
        if (cfg == null) return;
        // 夾值保護（避免 UI 填錯造成極端行為）
        RetailLogicModel m = cfg.model == null ? RetailLogicModel.MIXED : cfg.model;
        double risk = Double.isFinite(cfg.riskPerTrade) ? Math.max(0.0, Math.min(0.5, cfg.riskPerTrade)) : 0.03;
        double rand = Double.isFinite(cfg.randomTradeProb) ? Math.max(0.0, Math.min(0.2, cfg.randomTradeProb)) : 0.005;
        double spr = Double.isFinite(cfg.spreadLimitRatio) ? Math.max(0.0, Math.min(0.05, cfg.spreadLimitRatio)) : 0.008;
        double rb = Double.isFinite(cfg.rsiBuy) ? Math.max(0.0, Math.min(100.0, cfg.rsiBuy)) : 30.0;
        double rs = Double.isFinite(cfg.rsiSell) ? Math.max(0.0, Math.min(100.0, cfg.rsiSell)) : 70.0;
        double te = Double.isFinite(cfg.trendEntry) ? Math.max(0.0, Math.min(1.0, cfg.trendEntry)) : 0.20;
        double me = Double.isFinite(cfg.macdHistEntry) ? Math.max(0.0, Math.min(1.0, cfg.macdHistEntry)) : 0.02;
        int wait = Math.max(0, Math.min(200, cfg.minTradeWaitTicks));
        int lossCd = Math.max(0, Math.min(500, cfg.lossCooldownPerLoss));

        retailStrategyConfig = new RetailStrategyConfig(m, risk, rand, spr, rb, rs, te, me, wait, lossCd);
        try {
            logger.info(String.format("更新散戶策略模型：%s, risk=%.2f%%, rand=%.2f%%, spread<=%.2f%%",
                    m, risk * 100.0, rand * 100.0, spr * 100.0), "RETAIL_CONFIG");
        } catch (Exception ex) {
            logger.warn("Retail strategy configuration log failed: " + ex.getMessage(), "RETAIL_CONFIG");
        }
    }

    // 模擬控制
    private int timeStep;
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> simulationFuture;
    private final Object simulationLifecycleLock = new Object();
    private volatile boolean isRunning = false;
    private volatile int simulationPeriodMillis = 1000;
    private final Random random;
    private final Clock clock;
    private volatile boolean closed;

    // 配置參數
    private double initialRetailCash = 5000000, initialMainForceCash = 8000000;
    // 個人戶（PERSONAL）初始資金：獨立於散戶初始資金，避免共用造成統計/行為混淆
    private double initialPersonalCash = 10000000;
    private int initialRetails = 15;
    private int marketBehaviorStock = 0;
    private double marketBehaviorGash = -9999999.0;

    // === 玩法參數（可自行調整）===
    private int marketMakerCount = 5;      // 建議 2~5
    private int noiseTraderCount = 8;      // 建議 3~10
    private double marketMakerInitialCash = 6500000; // 每個做市商初始現金
    private int marketMakerInitialStocks = 50000;     // 每個做市商初始持股
    private double noiseTraderInitialCash = 3000000;   // 每個噪音交易者初始現金
    private int noiseTraderInitialStocks = 50000;      // 每個噪音交易者初始持股

    // 用於報酬率計算：記錄初始化時的股價（讓初始持股能換算成初始淨值）
    private double initialStockPrice = 10.0;

    // 🆕 成交記錄列表
    private static final int MAX_TRANSACTION_HISTORY = 1000;
    private final TransactionJournal transactionJournal =
            new TransactionJournal(MAX_TRANSACTION_HISTORY);

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

    /**
     * 交易者快照（提供 UI 顯示用）
     */
    public static class TraderSnapshot {

        public final String traderType;   // 例如 PERSONAL / MAIN_FORCE / MarketBehavior / NoiseTrader1...
        public final String role;         // 類別：個人 / 主力 / 做市 / 噪音 / 散戶
        public final double availableFunds;
        public final double frozenFunds;
        public final int availableStocks;
        public final int frozenStocks;
        public final double totalAssets;
        public final double profitPct;    // 獲利百分比（%），正=獲利、負=虧損
        public final String extra;        // 例如主力 Phase

        public TraderSnapshot(String traderType, String role,
                double availableFunds, double frozenFunds,
                int availableStocks, int frozenStocks,
                double totalAssets, double profitPct, String extra) {
            this.traderType = traderType;
            this.role = role;
            this.availableFunds = availableFunds;
            this.frozenFunds = frozenFunds;
            this.availableStocks = availableStocks;
            this.frozenStocks = frozenStocks;
            this.totalAssets = totalAssets;
            this.profitPct = profitPct;
            this.extra = extra;
        }
    }

    /**
     * 提供給 UI：列出所有市場參與者（主力/做市商/噪音/散戶/個人）的資金與持股快照
     */
    public List<TraderSnapshot> getTraderSnapshots() {
        List<TraderSnapshot> out = new ArrayList<>();
        double px = (stock != null ? stock.getPrice() : 0.0);

        // 主力
        if (mainForce != null && mainForce.getAccount() != null) {
            UserAccount acc = mainForce.getAccount();
            double assets = acc.getTotalFunds() + acc.getTotalStocks() * px;
            double initAssets = initialMainForceCash; // 主力初始持股預設 0
            double pct = (initAssets > 0) ? ((assets - initAssets) / initAssets * 100.0) : 0.0;
            out.add(new TraderSnapshot(
                    mainForce.getTraderType(),
                    "主力",
                    acc.getAvailableFunds(),
                    acc.getFrozenFunds(),
                    acc.getStockInventory(),
                    acc.getFrozenStocks(),
                    assets,
                    pct,
                    mainForce.getPhaseName()
            ));
        }

        // 做市商（多個）
        if (marketMakers != null) {
            for (int i = 0; i < marketMakers.size(); i++) {
                MarketBehavior mm = marketMakers.get(i);
                if (mm == null || mm.getAccount() == null) {
                    continue;
                }
                UserAccount acc = mm.getAccount();
                double assets = acc.getTotalFunds() + acc.getTotalStocks() * px;
                double initAssets = marketMakerInitialCash + marketMakerInitialStocks * initialStockPrice;
                double pct = (initAssets > 0) ? ((assets - initAssets) / initAssets * 100.0) : 0.0;
                out.add(new TraderSnapshot(
                        "MarketMaker" + (i + 1),
                        "做市",
                        acc.getAvailableFunds(),
                        acc.getFrozenFunds(),
                        acc.getStockInventory(),
                        acc.getFrozenStocks(),
                        assets,
                        pct,
                        ""
                ));
            }
        }

        // 噪音交易者（多個）
        if (noiseTraders != null) {
            for (NoiseTraderAI nt : noiseTraders) {
                if (nt == null || nt.getAccount() == null) {
                    continue;
                }
                UserAccount acc = nt.getAccount();
                double assets = acc.getTotalFunds() + acc.getTotalStocks() * px;
                double initAssets = noiseTraderInitialCash + noiseTraderInitialStocks * initialStockPrice;
                double pct = (initAssets > 0) ? ((assets - initAssets) / initAssets * 100.0) : 0.0;
                out.add(new TraderSnapshot(
                        nt.getTraderType(),
                        "噪音",
                        acc.getAvailableFunds(),
                        acc.getFrozenFunds(),
                        acc.getStockInventory(),
                        acc.getFrozenStocks(),
                        assets,
                        pct,
                        ""
                ));
            }
        }

        // 散戶（多個）
        if (retailInvestors != null) {
            for (RetailInvestorAI ri : retailInvestors) {
                if (ri == null || ri.getAccount() == null) {
                    continue;
                }
                UserAccount acc = ri.getAccount();
                double assets = acc.getTotalFunds() + acc.getTotalStocks() * px;
                double initAssets = initialRetailCash; // 散戶初始持股預設 0
                double pct = (initAssets > 0) ? ((assets - initAssets) / initAssets * 100.0) : 0.0;
                out.add(new TraderSnapshot(
                        ri.getTraderType(),
                        "散戶",
                        acc.getAvailableFunds(),
                        acc.getFrozenFunds(),
                        acc.getStockInventory(),
                        acc.getFrozenStocks(),
                        assets,
                        pct,
                        ""
                ));
            }
        }

        // 個人
        if (userInvestor != null && userInvestor.getAccount() != null) {
            UserAccount acc = userInvestor.getAccount();
            double assets = acc.getTotalFunds() + acc.getTotalStocks() * px;
            double initAssets = initialPersonalCash; // 個人戶初始持股預設 0
            double pct = (initAssets > 0) ? ((assets - initAssets) / initAssets * 100.0) : 0.0;
            out.add(new TraderSnapshot(
                    userInvestor.getTraderType(),
                    "個人",
                    acc.getAvailableFunds(),
                    acc.getFrozenFunds(),
                    acc.getStockInventory(),
                    acc.getFrozenStocks(),
                    assets,
                    pct,
                    ""
            ));
        }

        return out;
    }

    public List<ModelListener> listeners = new CopyOnWriteArrayList<>();

    // 成交紀錄監聽器介面 - 用於通知View更新
    public interface TransactionListener {

        void onTransactionAdded(Transaction transaction);
    }

    private List<TransactionListener> transactionListeners = new CopyOnWriteArrayList<>();

    /**
     * 構造函數
     */
    public StockMarketModel() {
        this(new Random(), Clock.systemUTC());
    }

    public StockMarketModel(long randomSeed, Clock clock) {
        this(new Random(randomSeed), clock);
    }

    public StockMarketModel(Random random, Clock clock) {
        this.random = java.util.Objects.requireNonNull(random, "random");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.noiseSignalQuality = new NoiseSignalQuality(0.5, 0.5, 0, clock.millis(), false);
        initializeSimulation();
        this.technicalCalculator = new TechnicalIndicatorsCalculator();
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
        if (eff < 1) {
            eff = 1;
        }
        if (eff > 99) {
            eff = 99;
        }
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
            orderBook = new OrderBook(this, clock);
            logger.info("OrderBook 初始化完成", "MODEL_INIT");
            // 設置默認撮合模式（台股固定）
            orderBook.setMatchingMode(MatchingMode.TWSE_STRICT);
            logger.info("設置默認撮合模式：" + orderBook.getMatchingMode(), "MODEL_INIT");

            stock = new Stock("台積電", 10, 1000);
            try { initialStockPrice = stock.getPrice(); }
            catch (Exception ex) {
                initialStockPrice = 10.0;
                logger.warn("Initial stock price fallback: " + ex.getMessage(), "MODEL_INIT");
            }

            // 初始化做市商（多個）
            initializeMarketMakers(marketMakerCount);

            timeStep = 0;
            marketAnalyzer = new MarketAnalyzer(2); // 設定適當的SMA週期

            // 初始化主力
            mainForce = new MainForceStrategyWithOrderBook(
                    orderBook, stock, this, initialMainForceCash, childRandom(), clock);

            // 初始化散戶
            initializeRetailInvestors(initialRetails);

            // 初始化用戶投資者
            userInvestor = new PersonalAI(
                    initialPersonalCash, "Personal", this, orderBook, stock, childRandom());
            personalTradeService = new PersonalTradeService(userInvestor);

            // 初始化噪音交易者（多個）
            initializeNoiseTraders(noiseTraderCount);

            logger.info("市場模型初始化完成", "MODEL_INIT");
        } catch (Exception e) {
            logger.error("市場模型初始化失敗: " + e.getMessage(), "MODEL_INIT");
        }
    }

    private void initializeMarketMakers(int count) {
        marketMakers = new ArrayList<>();
        int n = Math.max(0, count);
        for (int i = 0; i < n; i++) {
            // 注意：舊的 marketBehaviorGash 可能是負值（會導致無法掛買單），改用正的初始資金/持股
            MarketBehavior mm = new MarketBehavior(
                    stock != null ? stock.getPrice() : 10.0,
                    marketMakerInitialCash,
                    marketMakerInitialStocks,
                    this,
                    orderBook,
                    childRandom()
            );
            marketMakers.add(mm);
        }
    }

    private double getMarketMakersTotalFunds() {
        if (marketMakers == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (MarketBehavior mm : marketMakers) {
            if (mm != null && mm.getAccount() != null) {
                sum += mm.getAccount().getAvailableFunds();
            }
        }
        return sum;
    }

    private int getMarketMakersTotalStocks() {
        if (marketMakers == null) {
            return 0;
        }
        int sum = 0;
        for (MarketBehavior mm : marketMakers) {
            if (mm != null && mm.getAccount() != null) {
                sum += mm.getAccount().getStockInventory();
                sum += mm.getAccount().getFrozenStocks();
            }
        }
        return sum;
    }

    private void initializeNoiseTraders(int count) {
        noiseTraders = new ArrayList<>();
        int n = Math.max(0, count);
        for (int i = 0; i < n; i++) {
            NoiseTraderAI nt = new NoiseTraderAI(
                    noiseTraderInitialCash,
                    noiseTraderInitialStocks,
                    "NoiseTrader" + (i + 1),
                    this,
                    orderBook,
                    stock,
                    childRandom()
            );
            noiseTraders.add(nt);
        }
    }

    /**
     * 初始化多個自動化散戶
     */
    private void initializeRetailInvestors(int numberOfInvestors) {
        retailInvestors = new ArrayList<>();
        for (int i = 0; i < numberOfInvestors; i++) {
            RetailInvestorAI investor = new RetailInvestorAI(
                    initialRetailCash, "RetailInvestor" + (i + 1), this, childRandom(), clock);
            retailInvestors.add(investor);
        }
    }

    private Random childRandom() {
        return new Random(random.nextLong());
    }

    /**
     * 啟動自動價格波動
     */
    public void startAutoPriceFluctuation() {
        synchronized (simulationLifecycleLock) {
            if (closed) {
                throw new IllegalStateException("StockMarketModel is closed");
            }
            if (isRunning) return;

        logger.info("啟動市場價格波動模擬", "MARKET_SIMULATION");

        int initialDelay = 0;
        int period = simulationPeriodMillis; // 執行間隔（單位：毫秒）

            isRunning = true;
            executorService = Executors.newScheduledThreadPool(1);
            simulationFuture = executorService.scheduleAtFixedRate(() -> {
            try {
                if (!isRunning || Thread.currentThread().isInterrupted()) {
                    return;
                }
                timeStep++;

                // 1. 市場行為：模擬市場的訂單提交
                try {
                    double vol = marketAnalyzer.calculateVolatility();
                    int recentVol = (int) marketAnalyzer.getRecentAverageVolume();

                    // 1a. 多個做市商：提供雙邊掛單
                    if (marketMakers != null) {
                        for (MarketBehavior mm : marketMakers) {
                            try {
                                mm.marketFluctuation(stock, orderBook, vol, recentVol);
                            } catch (Exception ex) {
                                logger.warn("Market maker tick failed: " + ex.getMessage(), "MARKET_BEHAVIOR");
                            }
                        }
                    }

                    // 1b. 噪音交易者：小額主動吃單/侵略性掛單，增加成交機會
                    if (noiseTraders != null) {
                        NoiseSignalQuality q = noiseSignalQuality;
                        NoiseAdaptiveConfig cfg = noiseAdaptiveConfig;
                        for (NoiseTraderAI nt : noiseTraders) {
                            try {
                                nt.setNoiseSignalQuality(q);
                                nt.setNoiseAdaptiveConfig(cfg);
                                nt.makeDecision();
                            } catch (Exception ex) {
                                logger.warn("Noise trader tick failed: " + ex.getMessage(), "MARKET_BEHAVIOR");
                            }
                        }
                    }
                    logger.info(String.format("市場行為模擬：時間步長 %d", timeStep), "MARKET_BEHAVIOR");
                } catch (Exception e) {
                    logger.error("市場行為模擬發生錯誤：" + e.getMessage(), "MARKET_BEHAVIOR");
                }

                // 2. 散戶行為：執行散戶決策
                try {
                    executeRetailInvestorDecisions();
                } catch (Exception e) {
                    logger.error("散戶決策發生錯誤：" + e.getMessage(), "RETAIL_BEHAVIOR");
                }

                // 3. 主力行為：執行主力決策
                try {
                    mainForce.makeDecision();
                } catch (Exception e) {
                    logger.error("主力決策發生錯誤：" + e.getMessage(), "MAINFORCE_BEHAVIOR");
                }

                // 4. 處理訂單簿，撮合訂單（需加鎖保護）
                try {
                    orderBookLock.lock(); // 加鎖
                    orderBook.processOrders(stock);
                } catch (Exception e) {
                    logger.error("訂單簿處理發生錯誤：" + e.getMessage(), "ORDER_PROCESSING");
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
                } finally {
                    marketAnalyzerLock.unlock(); // 解鎖
                    validateMarketInventory();
                }
            } catch (Exception e) {
                logger.error("主模擬流程發生未處理的錯誤：" + e.getMessage(), "MARKET_SIMULATION");
            }
            }, initialDelay, period, TimeUnit.MILLISECONDS);
        }
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

        // 計算新的技術指標（避免單次指標異常中斷整個模擬 tick）
        double[] macdResult = null;
        double[] bollingerResult = null;
        double[] kdjResult = null;
        try {
            if (technicalCalculator != null) {
                // 更新技術指標計算器的價格數據（改為使用近期高低價，避免KDJ失真）
                double high = marketAnalyzer.getRecentHigh(20);
                double low = marketAnalyzer.getRecentLow(20);
                if (Double.isNaN(high)) high = price;
                if (Double.isNaN(low)) low = price;
                technicalCalculator.updatePriceData(price, high, low);

                try { macdResult = technicalCalculator.calculateMACD(); }
                catch (RuntimeException ex) { logger.warn("MACD calculation failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage(), "MARKET_ANALYSIS"); }
                try { bollingerResult = technicalCalculator.calculateBollingerBands(); }
                catch (Exception ex) { logger.warn("Bollinger calculation failed: " + ex.getMessage(), "MARKET_ANALYSIS"); }
                try { kdjResult = technicalCalculator.calculateKDJ(); }
                catch (Exception ex) { logger.warn("KDJ calculation failed: " + ex.getMessage(), "MARKET_ANALYSIS"); }
            }
        } catch (Exception ex) {
            logger.warn("Technical indicator update failed: " + ex.getMessage(), "MARKET_ANALYSIS");
        }

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

        final double[] macd = macdResult;
        final double[] bollinger = bollingerResult;
        final double[] kdj = kdjResult;
        runOnEdt(() -> listeners.forEach(listener -> {
            // 原有的通知
            listener.onPriceChanged(price, sma);
            listener.onTechnicalIndicatorsUpdated(volatility, rsi, wap);

            // 新增的技術指標通知
            if (macd != null) {
                listener.onMACDUpdated(macd[0], macd[1], macd[2]);
            }

            if (bollinger != null) {
                listener.onBollingerBandsUpdated(bollinger[0], bollinger[1], bollinger[2]);
            }

            if (kdj != null) {
                listener.onKDJUpdated(kdj[0], kdj[1], kdj[2]);
            }

            // 原有的其他通知
            listener.onMarketStateChanged(
                    getAverageRetailCash(),
                    getAverageRetailStocks(),
                    mainForce.getAccount().getAvailableFunds(),
                    mainForce.getAccount().getStockInventory(),
                    mainForce.getTargetPrice(),
                    mainForce.getAverageCostPrice(),
                    getMarketMakersTotalFunds(),
                    getMarketMakersTotalStocks()
            );
            listener.onUserAccountUpdated(
                    userInvestor.getAccount().getStockInventory(),
                    userInvestor.getAccount().getAvailableFunds(),
                    userInvestor.getAverageCostPrice(),
                    userInvestor.getTakeProfitPrice()
            );
            listener.onOrderBookChanged();
        }));
    }

    // 可選：提供獲取技術指標計算器的方法（用於調試或配置）
    public TechnicalIndicatorsCalculator getTechnicalCalculator() {
        return technicalCalculator;
    }

    // ===== 指標值 Getter（NaN 表示暫無） =====
    public double getLastMacdLine() {
        return lastMacdLine;
    }

    public double getLastMacdSignal() {
        return lastMacdSignal;
    }

    public double getLastMacdHist() {
        return lastMacdHist;
    }

    public double getLastBollUpper() {
        return lastBollUpper;
    }

    public double getLastBollMiddle() {
        return lastBollMiddle;
    }

    public double getLastBollLower() {
        return lastBollLower;
    }

    public double getLastK() {
        return lastK;
    }

    public double getLastD() {
        return lastD;
    }

    public double getLastJ() {
        return lastJ;
    }

    // ===== 近期 Tape 統計（供策略與風控使用） =====
    public double getRecentTPS(int n) {
        try {
            java.util.List<Transaction> recent = getRecentTransactions(Math.max(1, n));
            if (recent.isEmpty()) {
                return 0.0;
            }
            long now = clock.millis();
            long earliest = recent.get(0).getTimestamp();
            double secs = Math.max(1.0, (now - earliest) / 1000.0);
            return recent.size() / secs;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getRecentVPS(int n) {
        try {
            java.util.List<Transaction> recent = getRecentTransactions(Math.max(1, n));
            if (recent.isEmpty()) {
                return 0.0;
            }
            long now = clock.millis();
            long earliest = recent.get(0).getTimestamp();
            double secs = Math.max(1.0, (now - earliest) / 1000.0);
            long vol = 0;
            for (Transaction t : recent) {
                vol += t.getVolume();
            }
            return vol / secs;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getRecentTickImbalance(int n) {
        try {
            java.util.List<Transaction> recent = getRecentTransactions(Math.max(1, n));
            if (recent.isEmpty()) {
                return 0.0;
            }
            int buy = 0, sell = 0;
            for (Transaction t : recent) {
                if (t.isBuyerInitiated()) {
                    buy++;
                } else {
                    sell++;
                }
            }
            int tot = Math.max(1, buy + sell);
            return (buy - sell) / (double) tot; // [-1,1]
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void setSimulationPeriodMillis(int periodMillis) {
        int bounded = Math.max(50, Math.min(10_000, periodMillis));
        boolean restart;
        synchronized (simulationLifecycleLock) {
            if (simulationPeriodMillis == bounded) {
                return;
            }
            simulationPeriodMillis = bounded;
            restart = isRunning;
        }
        if (restart) {
            stopAutoPriceFluctuation();
            startAutoPriceFluctuation();
        }
    }

    public int getSimulationPeriodMillis() {
        return simulationPeriodMillis;
    }

    /**
     * 停止自動價格波動
     */
    public void stopAutoPriceFluctuation() {
        ScheduledExecutorService executorToStop;
        ScheduledFuture<?> futureToCancel;
        synchronized (simulationLifecycleLock) {
            if (!isRunning && executorService == null) return;
            logger.info("停止市場價格波動模擬", "MARKET_SIMULATION");
            isRunning = false;
            futureToCancel = simulationFuture;
            executorToStop = executorService;
            simulationFuture = null;
            executorService = null;
        }

        // 先取消週期任務（shutdown 並不一定會停止已提交的 scheduleAtFixedRate 任務）
        try {
            if (futureToCancel != null) futureToCancel.cancel(true);
        } catch (Exception ex) {
            logger.warn("Simulation task cancellation failed: " + ex.getMessage(), "MARKET_SIMULATION");
        }
        if (executorToStop != null && !executorToStop.isShutdown()) {
            executorToStop.shutdownNow();
            try {
                if (!executorToStop.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorToStop.shutdownNow();
                    logger.warn("強制關閉模擬執行緒池", "MARKET_SIMULATION");
                }
            } catch (InterruptedException e) {
                executorToStop.shutdownNow();
                logger.error(e, "MARKET_SIMULATION");
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        synchronized (simulationLifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        stopAutoPriceFluctuation();
        if (orderBook != null) {
            orderBook.close();
        }
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
        int initialInventory = calculateExpectedInitialInventory();
        if (calculatedInventory != initialInventory) {
            String msg = "市場庫存檢查: 設定值(全市場初始總股數)=" + initialInventory
                    + "，做市商合計持股=" + getMarketMakersTotalStocks()
                    + "，總計算庫存=" + calculatedInventory;
            logger.error(msg, "MODEL_INIT");
            LogicAudit.warn("INVENTORY_CHECK", msg);
        } else {
            LogicAudit.info("INVENTORY_CHECK", "ok total=" + calculatedInventory);
        }
    }

    private int calculateExpectedInitialInventory() {
        int marketMakerStocks = Math.multiplyExact(
                Math.max(0, marketMakerCount), Math.max(0, marketMakerInitialStocks));
        int noiseStocks = Math.multiplyExact(
                Math.max(0, noiseTraderCount), Math.max(0, noiseTraderInitialStocks));
        return Math.addExact(marketMakerStocks, noiseStocks);
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

        // 做市商帳戶（合計）
        int marketAvail = 0;
        int marketFrozen = 0;
        if (marketMakers != null) {
            for (MarketBehavior mm : marketMakers) {
                if (mm != null && mm.getAccount() != null) {
                    marketAvail += mm.getAccount().getStockInventory();
                    marketFrozen += mm.getAccount().getFrozenStocks();
                }
            }
        }
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
        return executeMarketBuyResult(quantity).filledVolume() > 0;
    }

    public ExecutionResult executeMarketBuyResult(int quantity) {
        return personalTradeService.marketBuy(quantity);
    }

    /**
     * 市價賣出操作
     */
    public boolean executeMarketSell(int quantity) {
        return executeMarketSellResult(quantity).filledVolume() > 0;
    }

    public ExecutionResult executeMarketSellResult(int quantity) {
        return personalTradeService.marketSell(quantity);
    }

    /**
     * 限價買入操作
     */
    public boolean executeLimitBuy(int quantity, double price) {
        return executeLimitBuyResult(quantity, price).accepted();
    }

    public OrderSubmissionResult executeLimitBuyResult(int quantity, double price) {
        return personalTradeService.limitBuy(quantity, price);
    }

    /**
     * 限價賣出操作
     */
    public boolean executeLimitSell(int quantity, double price) {
        return executeLimitSellResult(quantity, price).accepted();
    }

    public OrderSubmissionResult executeLimitSellResult(int quantity, double price) {
        return personalTradeService.limitSell(quantity, price);
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
        runOnEdt(() -> listeners.forEach(listener -> listener.onInfoMessage(message)));
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
        runOnEdt(() -> listeners.forEach(listener -> listener.onVolumeUpdated(txVolume)));
    }

    /**
     * 更新訂單簿顯示
     */
    public void updateOrderBookDisplay() {
        runOnEdt(() -> listeners.forEach(ModelListener::onOrderBookChanged));
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

        } finally {
            orderBookLock.unlock();
        }
    }

    // 添加成交記錄的方法
    public void addTransaction(Transaction transaction) {
        transactionJournal.add(transaction);
        notifyTransactionAdded(transaction);
    }

    private void notifyTransactionAdded(Transaction transaction) {
        runOnEdt(() -> {
            transactionListeners.forEach(listener -> listener.onTransactionAdded(transaction));
            listeners.forEach(listener -> listener.onInfoMessage(String.format(
                    "新成交：%s %d股 @ %.2f",
                    transaction.isBuyerInitiated() ? "買入" : "賣出",
                    transaction.getVolume(), transaction.getPrice())));
        });
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
        return transactionJournal.all();
    }

    // 獲取最近N筆成交記錄
    public List<Transaction> getRecentTransactions(int n) {
        return transactionJournal.recent(n);
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
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

    // 新增：取得做市商清單（唯讀快照）
    public List<MarketBehavior> getMarketMakers() {
        return marketMakers == null ? new ArrayList<>() : new ArrayList<>(marketMakers);
    }

    // 新增：取得噪音交易者清單（唯讀快照）
    public List<NoiseTraderAI> getNoiseTraders() {
        return noiseTraders == null ? new ArrayList<>() : new ArrayList<>(noiseTraders);
    }

    // 新增：取得散戶清單（唯讀快照）
    public List<RetailInvestorAI> getRetailInvestors() {
        return new ArrayList<>(retailInvestors);
    }

    // 新增：取得初始資金設定
    public double getInitialRetailCash() {
        return initialRetailCash;
    }

    public double getInitialPersonalCash() {
        return initialPersonalCash;
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
