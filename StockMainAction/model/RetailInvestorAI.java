package StockMainAction.model;

import StockMainAction.model.user.UserAccount;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.Trader;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import StockMainAction.StockMarketSimulation;
import java.util.Random;
import java.util.LinkedList;
import java.util.Queue;
import javax.swing.JOptionPane;
import java.text.DecimalFormat;
import StockMainAction.util.logging.MarketLogger;
import StockMainAction.util.logging.DecisionFactorLogger;
import StockMainAction.model.core.Transaction;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * AI散戶行為，實現 Trader 接口，自動輸入下單金額
 */
public class RetailInvestorAI implements Trader {

    private StockMarketSimulation simulation;
    private static final Random random = new Random();
    private final Random randProfile; // 每位散戶專屬隨機源（由 traderID 決定）
    private double buyThreshold = 0.95; // 調低門檻以增加買入機會
    private double sellThreshold = 3.5; // 調低門檻以增加賣出機會
    private boolean ignoreThreshold = false;
    private Queue<Double> priceHistory; // 儲存最近價格數據，用於分析趨勢
    private String traderID; // 散戶的唯一標識符
    private final double initialCash; // 新增：每位散戶的初始資金
    private UserAccount account;
    private OrderBook orderBook; // 亦可在 makeDecision 時指定
    private Stock stock;  // 股票實例
    private boolean autoInputOrderAmount = true; // 是否自動輸入訂單金額
    private DecimalFormat decimalFormat = new DecimalFormat("#,##0.00"); // 格式化價格顯示
    private static final MarketLogger logger = MarketLogger.getInstance();
    private StockMarketModel model;

    // 訂單管理相關
    private Map<String, Long> orderCreationTime = new HashMap<>();
    private int orderCancelCounter = 0;
    private static final int ORDER_CANCEL_INTERVAL = 30; // 每30個決策週期檢查一次
    private static final long MAX_ORDER_AGE_MS = 180000; // 3分鐘

    // 每位散戶的策略型別與風格參數
    private enum StrategyProfile { MOMENTUM, CONTRARIAN, VALUE, SCALPER, AGGRESSIVE, CONSERVATIVE, SWING }
    private final StrategyProfile profile;
    private final double riskFactor;          // 0.5 ~ 1.8，放大/縮小下單量（略強化）
    private final double marketOrderBias;     // 0.0 ~ 1.0，偏好市價單比率
    private final double fokBias;             // 0.0 ~ 0.4，偏好 FOK 的少量概率（略強化）

    // 停損和止盈價格
    private Double stopLossPrice = null;
    private Double takeProfitPrice = null;

    // 風險管理參數
    private final double riskPerTrade = 0.2; // 每次交易風險不超過 20% 資金

    // 短期和長期 SMA (如有用到可自行擴充)
    private int shortSmaPeriod = 5;
    private int longSmaPeriod = 20;

    /* ==== 依技術面計算掛單價的權重（可視需要調整） ==== */
    private double smaWeight = 0.50;   // 價格偏離 SMA 的影響
    private double rsiWeight = 0.30;   // RSI 超買超賣的影響
    private double volatilityWeight = 0.20;   // 波動度的影響

    /* 偏移上限（%）；避免掛到離現價過遠的位置 */
    private double maxOffsetRatio = 0.08;   // 最多 ±8%

    /**
     * 構造函數
     *
     * @param initialCash 初始現金
     * @param traderID 散戶的唯一標識符
     * @param model 市場模型
     */
    public RetailInvestorAI(double initialCash, String traderID, StockMarketModel model) {
        this.traderID = traderID;
        this.model = model;
        this.randProfile = new Random(traderID.hashCode());
        this.initialCash = initialCash;

        // 初始化價格歷史
        this.priceHistory = new LinkedList<>();

        // 設置隨機的買入和賣出門檻
        this.buyThreshold = 0.01 + randProfile.nextDouble() * 0.05; // 1% 到 6%
        this.sellThreshold = 0.03 + randProfile.nextDouble() * 0.09; // 3% 到 12%

        // 隨機決定該散戶是否忽略門檻，30% 機率
        this.ignoreThreshold = randProfile.nextDouble() < 0.3;

        // 初始化帳戶
        this.account = new UserAccount(initialCash, 0);

        logger.info("【散戶AI】建立成功，ID: " + traderID + "，初始資金: " + initialCash, "RETAIL_INVESTOR_INIT");

        // === 指派策略型別與風格參數（每位散戶不同且可重現） ===
        this.profile = pickProfileByHash(traderID);
        // 風格：風險、下單偏好因策略不同而異
        switch (this.profile) {
            case MOMENTUM:
                this.riskFactor = 1.35;
                this.marketOrderBias = 0.75;
                this.fokBias = 0.08;
                // 偏重動能：SMA/RSI/波動權重
                this.smaWeight = 0.30; this.rsiWeight = 0.30; this.volatilityWeight = 0.40;
                this.maxOffsetRatio = 0.06;
                break;
            case CONTRARIAN:
                this.riskFactor = 1.05;
                this.marketOrderBias = 0.35;
                this.fokBias = 0.12;
                // 逆向：更信任均線/RSI
                this.smaWeight = 0.55; this.rsiWeight = 0.35; this.volatilityWeight = 0.10;
                this.maxOffsetRatio = 0.10;
                break;
            case VALUE:
                this.riskFactor = 0.95;
                this.marketOrderBias = 0.25;
                this.fokBias = 0.18;
                // 價值：更看重均線
                this.smaWeight = 0.60; this.rsiWeight = 0.20; this.volatilityWeight = 0.20;
                this.maxOffsetRatio = 0.12;
                break;
            case SCALPER:
            default:
                this.riskFactor = 1.6;
                this.marketOrderBias = 0.85;
                this.fokBias = 0.02;
                // 剝頭皮：重視波動
                this.smaWeight = 0.20; this.rsiWeight = 0.20; this.volatilityWeight = 0.60;
                this.maxOffsetRatio = 0.04;
                break;
        }
    }

    private StrategyProfile pickProfileByHash(String id) {
        int h = Math.abs(id.hashCode());
        int mod = h % 100;
        if (mod < 25) return StrategyProfile.MOMENTUM;
        if (mod < 50) return StrategyProfile.CONTRARIAN;
        if (mod < 75) return StrategyProfile.VALUE;
        return StrategyProfile.SCALPER;
    }

    // ========== Trader 介面實作 ==========
    @Override
    public UserAccount getAccount() {
        return account;
    }

    @Override
    public String getTraderType() {
        return "RETAIL_INVESTOR";
    }

    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            try {
                account.consumeFrozenFunds(transactionAmount);
            } catch (Exception e) {
                account.decrementFunds(transactionAmount);
            }
            account.incrementStocks(volume);
            logger.info(String.format("【散戶-限價買入更新】散戶 %s 買入 %d 股，價格 %.2f", traderID, volume, price), "RETAIL_TRANSACTION");
        } else if ("sell".equals(type)) {
            // 限價單賣出
            account.incrementFunds(transactionAmount);
            logger.info(String.format("【散戶-限價賣出更新】散戶 %s 賣出 %d 股，價格 %.2f", traderID, volume, price), "RETAIL_TRANSACTION");
        }

        // 通過 model 更新 UI
        if (model != null) {
            model.notifyListenersOfUpdates();
        }
    }

    @Override
    public void updateAverageCostPrice(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            // 扣款並加股
            account.decrementFunds(transactionAmount);
            account.incrementStocks(volume);
            logger.info(String.format("【散戶-市價買入更新】散戶 %s 買入 %d 股，價格 %.2f", traderID, volume, price), "RETAIL_TRANSACTION");
        } else if ("sell".equals(type)) {
            // 扣股並加款
            account.decrementStocks(volume);
            account.incrementFunds(transactionAmount);
            logger.info(String.format("【散戶-市價賣出更新】散戶 %s 賣出 %d 股，價格 %.2f", traderID, volume, price), "RETAIL_TRANSACTION");
        }

        // 通過 model 更新 UI
        if (model != null) {
            model.notifyListenersOfUpdates();
        }
    }

    // ========== 核心行為決策 ==========
    /**
     * 散戶行為決策
     *
     * @param stock 股票實例
     * @param orderBook 訂單簿實例
     * @param simulation 模擬實例
     */
    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketModel model) {
        // 如果傳入的 model 不為 null，更新當前實例的 model 引用
        if (model != null) {
            this.model = model;
        }
        
        // 定期執行訂單管理
        orderCancelCounter++;
        if (orderCancelCounter >= ORDER_CANCEL_INTERVAL) {
            orderCancelCounter = 0;
            cancelOutdatedOrders();
        }
        
        try {
            logger.debug(String.format(
                    "散戶%s 決策分析開始：可用資金=%.2f, 當前股價=%.2f",
                    traderID, account.getAvailableFunds(), stock.getPrice()
            ), "RETAIL_INVESTOR_DECISION");

            this.orderBook = orderBook; // 如需使用私有函式下單時需有此參考
            double availableFunds = account.getAvailableFunds();
            double currentPrice = stock.getPrice();
            double sma = model.getMarketAnalyzer().calculateSMA();
            double rsi = model.getMarketAnalyzer().getRSI();
            double volatility = model.getMarketAnalyzer().getVolatility();
            StringBuilder decisionReason = new StringBuilder();

            // 每次決策時，動態調整買/賣門檻
            double dynamicBuyThreshold = buyThreshold * (0.8 + 0.4 * random.nextDouble());
            double dynamicSellThreshold = sellThreshold * (0.8 + 0.4 * random.nextDouble());

            logger.debug(String.format(
                    "散戶%s 動態門檻：買入門檻=%.4f, 賣出門檻=%.4f, 是否忽略門檻=%b",
                    traderID, dynamicBuyThreshold, dynamicSellThreshold, ignoreThreshold
            ), "RETAIL_INVESTOR_DECISION");

            if (this.orderBook == null) {
                this.orderBook = orderBook; // 確保每次執行時有訂單簿
                logger.debug(String.format(
                        "散戶%s 初始化訂單簿",
                        traderID
                ), "RETAIL_INVESTOR_DECISION");
            }

            if (this.stock == null) {
                this.stock = stock; // 確保 stock 不為 null
                logger.debug(String.format(
                        "散戶%s 初始化股票參考",
                        traderID
                ), "RETAIL_INVESTOR_DECISION");
            }

            // 可能改變是否忽略門檻
            boolean previousIgnoreThreshold = ignoreThreshold;
            if (random.nextDouble() < 0.1) {
                ignoreThreshold = !ignoreThreshold;
                logger.debug(String.format(
                        "散戶%s 忽略門檻狀態變更：從 %b 變更為 %b",
                        traderID, previousIgnoreThreshold, ignoreThreshold
                ), "RETAIL_INVESTOR_DECISION");
            }

            if (!Double.isNaN(sma)) {
                double priceDifferenceRatio = (currentPrice - sma) / sma;
                priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));
                double actionProbability = random.nextDouble();

                logger.debug(String.format(
                        "散戶%s 市場分析：SMA=%.2f, 價格差異比率=%.4f, 執行概率=%.4f, RSI=%.2f, 波動性=%.4f",
                        traderID, sma, priceDifferenceRatio, actionProbability, rsi, volatility
                ), "RETAIL_INVESTOR_DECISION");

                // 先嘗試依個人策略型別執行一次行為，若已下單則跳過通用邏輯
                boolean acted = actByProfile(currentPrice, sma, rsi, volatility,
                        actionProbability, availableFunds, decisionReason, stock);
                if (acted) {
                    if (model != null) model.sendInfoMessage(decisionReason.toString());
                    logger.debug(String.format(
                            "散戶%s 以個人策略完成決策：%s",
                            traderID, decisionReason.toString()
                    ), "RETAIL_INVESTOR_DECISION");
                    return;
                }

                // 停損 / 止盈
                logger.debug(String.format(
                        "散戶%s 檢查停損/止盈：停損價=%.2f, 止盈價=%.2f, 當前價格=%.2f",
                        traderID, stopLossPrice != null ? stopLossPrice : 0.0, takeProfitPrice != null ? takeProfitPrice : 0.0, currentPrice
                ), "RETAIL_INVESTOR_STOP_POINTS");
                handleStopLossTakeProfit(currentPrice, decisionReason);

                // 忽略門檻
                if (this.shouldIgnoreThreshold()) {
                    logger.info(String.format(
                            "散戶%s 忽略門檻策略：可用資金=%.2f, 價格差異比率=%.4f",
                            traderID, availableFunds, priceDifferenceRatio
                    ), "RETAIL_INVESTOR_DECISION");

                    if (availableFunds >= currentPrice && priceDifferenceRatio < 0 && actionProbability > 0.3) {
                        int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                        decisionReason.append("【忽略門檻】隨機決定買入 ").append(buyAmount).append(" 股。");
                        logger.debug(String.format(
                                "散戶%s 忽略門檻買入：計算買入量=%d",
                                traderID, buyAmount
                        ), "RETAIL_INVESTOR_BUY");

                        if (random.nextBoolean()) {
                            logger.debug(String.format(
                                    "散戶%s 選擇市價買入，嘗試買入數量=%d",
                                    traderID, buyAmount
                            ), "RETAIL_INVESTOR_BUY");
                            int actualBuy = 市價買入操作(buyAmount);
                            if (actualBuy > 0) {
                                logger.info(String.format(
                                        "散戶%s 忽略門檻市價買入成功：買入 %d 股",
                                        traderID, actualBuy
                                ), "RETAIL_INVESTOR_BUY");
                                decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                                setStopLossAndTakeProfit(currentPrice, volatility);
                            } else {
                                logger.warn(String.format(
                                        "散戶%s 忽略門檻市價買入失敗：買入量 %d",
                                        traderID, buyAmount
                                ), "RETAIL_INVESTOR_BUY");
                            }
                        } else {
                            double buyLimitPrice = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                            logger.debug(String.format(
                                    "散戶%s 選擇限價買入，嘗試買入數量=%d，限價=%.2f",
                                    traderID, buyAmount, buyLimitPrice
                            ), "RETAIL_INVESTOR_BUY");
                            int actualBuy = 限價買入操作(buyAmount, buyLimitPrice);
                            if (actualBuy > 0) {
                                logger.info(String.format(
                                        "散戶%s 忽略門檻限價買入成功：買入 %d 股，價格=%.2f",
                                        traderID, actualBuy, buyLimitPrice
                                ), "RETAIL_INVESTOR_BUY");
                                decisionReason.append("【成功】限價買入 " + actualBuy + " 股，價格 " + decimalFormat.format(buyLimitPrice) + "。\n");
                                setStopLossAndTakeProfit(currentPrice, volatility);
                            } else {
                                logger.warn(String.format(
                                        "散戶%s 忽略門檻限價買入失敗：買入量 %d，限價=%.2f",
                                        traderID, buyAmount, buyLimitPrice
                                ), "RETAIL_INVESTOR_BUY");
                                //decisionReason.append("【失敗】限價買入。資金或賣單量不足。\n");
                            }
                        }
                    } else if (getAccumulatedStocks() > 0 && priceDifferenceRatio > 0 && actionProbability > 0.3) {
                        int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                        logger.debug(String.format(
                                "散戶%s 忽略門檻賣出：計算賣出量=%d",
                                traderID, sellAmount
                        ), "RETAIL_INVESTOR_SELL");
                        decisionReason.append("【忽略門檻】隨機決定賣出 ").append(sellAmount).append(" 股。");

                        if (random.nextBoolean()) {
                            logger.debug(String.format(
                                    "散戶%s 選擇市價賣出，嘗試賣出數量=%d",
                                    traderID, sellAmount
                            ), "RETAIL_INVESTOR_SELL");
                            int actualSell = 市價賣出操作(sellAmount);
                            if (actualSell > 0) {
                                logger.info(String.format(
                                        "散戶%s 忽略門檻市價賣出成功：賣出 %d 股",
                                        traderID, actualSell
                                ), "RETAIL_INVESTOR_SELL");
                                decisionReason.append("【成功】市價賣出 ").append(actualSell).append(" 股。\n");
                            } else {
                                logger.warn(String.format(
                                        "散戶%s 忽略門檻市價賣出失敗：賣出量 %d",
                                        traderID, sellAmount
                                ), "RETAIL_INVESTOR_SELL");
                                //decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                            }
                        } else {
                            double sellLimitPrice = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                            logger.debug(String.format(
                                    "散戶%s 選擇限價賣出，嘗試賣出數量=%d，限價=%.2f",
                                    traderID, sellAmount, sellLimitPrice
                            ), "RETAIL_INVESTOR_SELL");
                            int actualSell = 限價賣出操作(sellAmount, sellLimitPrice);
                            if (actualSell > 0) {
                                logger.info(String.format(
                                        "散戶%s 忽略門檻限價賣出成功：賣出 %d 股，價格=%.2f",
                                        traderID, actualSell, sellLimitPrice
                                ), "RETAIL_INVESTOR_SELL");
                                decisionReason.append("【成功】限價賣出 ").append(actualSell).append(" 股，價格 " + decimalFormat.format(sellLimitPrice) + "。\n");
                            } else {
                                logger.warn(String.format(
                                        "散戶%s 忽略門檻限價賣出失敗：賣出量 %d，限價=%.2f",
                                        traderID, sellAmount, sellLimitPrice
                                ), "RETAIL_INVESTOR_SELL");
                                //decisionReason.append("【失敗】限價賣出。持股或買單量不足。\n");
                            }
                        }

                        stopLossPrice = null;
                        takeProfitPrice = null;
                        logger.debug(String.format(
                                "散戶%s 重置停損/止盈設定",
                                traderID
                        ), "RETAIL_INVESTOR_STOP_POINTS");
                    }
                } else {
                    // 遵循門檻
                    logger.debug(String.format(
                            "散戶%s 遵循門檻策略：價格差異比率=%.4f, 買入門檻=%.4f, 賣出門檻=%.4f",
                            traderID, priceDifferenceRatio, -dynamicBuyThreshold, dynamicSellThreshold
                    ), "RETAIL_INVESTOR_DECISION");

                    if (priceDifferenceRatio < -dynamicBuyThreshold && availableFunds >= currentPrice && actionProbability > 0.2) {
                        int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                        logger.debug(String.format(
                                "散戶%s 遵循門檻買入：計算買入量=%d",
                                traderID, buyAmount
                        ), "RETAIL_INVESTOR_BUY");
                        decisionReason.append(String.format("【遵循門檻】股價低於 SMA 的 %.2f%% 門檻，買入 %d 股。",
                                dynamicBuyThreshold * 100, buyAmount));

                        if (random.nextBoolean()) {
                            logger.debug(String.format(
                                    "散戶%s 選擇市價買入，嘗試買入數量=%d",
                                    traderID, buyAmount
                            ), "RETAIL_INVESTOR_BUY");
                            int actualBuy = 市價買入操作(buyAmount);
                            if (actualBuy > 0) {
                                logger.info(String.format(
                                        "散戶%s 遵循門檻市價買入成功：買入 %d 股",
                                        traderID, actualBuy
                                ), "RETAIL_INVESTOR_BUY");
                                decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                                setStopLossAndTakeProfit(currentPrice, volatility);
                            } else {
                                logger.warn(String.format(
                                        "散戶%s 遵循門檻市價買入失敗：買入量 %d",
                                        traderID, buyAmount
                                ), "RETAIL_INVESTOR_BUY");
                                //decisionReason.append("【失敗】市價買入。資金或掛單量不足。\n");
                            }
                        } else {
                            double buyLimitPrice = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                            logger.debug(String.format(
                                    "散戶%s 選擇限價買入，嘗試買入數量=%d，限價=%.2f",
                                    traderID, buyAmount, buyLimitPrice
                            ), "RETAIL_INVESTOR_BUY");
                            int actualBuy = 限價買入操作(buyAmount, buyLimitPrice);
                            if (actualBuy > 0) {
                                logger.info(String.format(
                                        "散戶%s 遵循門檻限價買入成功：買入 %d 股，價格=%.2f",
                                        traderID, actualBuy, buyLimitPrice
                                ), "RETAIL_INVESTOR_BUY");
                                decisionReason.append("【成功】限價買入 " + actualBuy + " 股，價格 " + decimalFormat.format(buyLimitPrice) + "。\n");
                                setStopLossAndTakeProfit(currentPrice, volatility);
                            } else {
                                logger.warn(String.format(
                                        "散戶%s 遵循門檻限價買入失敗：買入量 %d，限價=%.2f",
                                        traderID, buyAmount, buyLimitPrice
                                ), "RETAIL_INVESTOR_BUY");
                                //decisionReason.append("【失敗】限價買入。資金或賣單量不足。\n");
                            }
                        }
                    } else if (priceDifferenceRatio > dynamicSellThreshold && getAccumulatedStocks() > 0 && actionProbability > 0.2) {
                        int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                        logger.debug(String.format(
                                "散戶%s 遵循門檻賣出：計算賣出量=%d",
                                traderID, sellAmount
                        ), "RETAIL_INVESTOR_SELL");
                        decisionReason.append(String.format("【遵循門檻】股價高於 SMA 的 %.2f%% 門檻，賣出 %d 股。",
                                dynamicSellThreshold * 100, sellAmount));

                        if (random.nextBoolean()) {
                            logger.debug(String.format(
                                    "散戶%s 選擇市價賣出，嘗試賣出數量=%d",
                                    traderID, sellAmount
                            ), "RETAIL_INVESTOR_SELL");
                            int actualSell = 市價賣出操作(sellAmount);
                            if (actualSell > 0) {
                                logger.info(String.format(
                                        "散戶%s 遵循門檻市價賣出成功：賣出 %d 股",
                                        traderID, actualSell
                                ), "RETAIL_INVESTOR_SELL");
                                decisionReason.append("【成功】市價賣出 " + actualSell + " 股。\n");
                            } else {
                                logger.warn(String.format(
                                        "散戶%s 遵循門檻市價賣出失敗：賣出量 %d",
                                        traderID, sellAmount
                                ), "RETAIL_INVESTOR_SELL");
                                //decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                            }
                        } else {
                            double sellLimitPrice = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                            logger.debug(String.format(
                                    "散戶%s 選擇限價賣出，嘗試賣出數量=%d，限價=%.2f",
                                    traderID, sellAmount, sellLimitPrice
                            ), "RETAIL_INVESTOR_SELL");
                            int actualSell = 限價賣出操作(sellAmount, sellLimitPrice);
                            if (actualSell > 0) {
                                logger.info(String.format(
                                        "散戶%s 遵循門檻限價賣出成功：賣出 %d 股，價格=%.2f",
                                        traderID, actualSell, sellLimitPrice
                                ), "RETAIL_INVESTOR_SELL");
                                decisionReason.append("【成功】限價賣出 ").append(actualSell).append(" 股，價格 " + decimalFormat.format(sellLimitPrice) + "。\n");
                            } else {
                                logger.warn(String.format(
                                        "散戶%s 遵循門檻限價賣出失敗：賣出量 %d，限價=%.2f",
                                        traderID, sellAmount, sellLimitPrice
                                ), "RETAIL_INVESTOR_SELL");
                                //decisionReason.append("【失敗】限價賣出。持股或買單量不足。\n");
                            }
                        }

                        stopLossPrice = null;
                        takeProfitPrice = null;
                        logger.debug(String.format(
                                "散戶%s 重置停損/止盈設定",
                                traderID
                        ), "RETAIL_INVESTOR_STOP_POINTS");
                    } else if (random.nextDouble() < 0.05) {
                        logger.debug(String.format(
                                "散戶%s 觸發隨機交易機制，執行概率=%.4f",
                                traderID, random.nextDouble()
                        ), "RETAIL_INVESTOR_RANDOM");
                        executeRandomTransaction(availableFunds, currentPrice, decisionReason, stock);
                    }
                }

                // RSI 判斷
                if (rsi < 30 && availableFunds >= currentPrice) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                    decisionReason.append("【RSI < 30】買入訊號，嘗試買入 ").append(buyAmount).append(" 股。");
                    logger.info(String.format(
                            "散戶%s RSI 買入策略：RSI=%.2f, 可用資金=%.2f",
                            traderID, rsi, availableFunds
                    ), "RETAIL_INVESTOR_RSI_BUY");
                    int actualBuy = 市價買入操作(buyAmount);
                    if (actualBuy > 0) {
                        logger.info(String.format(
                                "散戶%s RSI 買入成功：買入 %d 股，RSI=%.2f",
                                traderID, actualBuy, rsi
                        ), "RETAIL_INVESTOR_RSI_BUY");
                        decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                        setStopLossAndTakeProfit(currentPrice, volatility);
                    } else {
                        logger.warn(String.format(
                                "散戶%s RSI 買入失敗：買入量 %d，RSI=%.2f",
                                traderID, buyAmount, rsi
                        ), "RETAIL_INVESTOR_RSI_BUY");
                        //decisionReason.append("【失敗】市價買入。資金或掛單量不足。\n");
                    }
                } else if (rsi > 70 && getAccumulatedStocks() > 0) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                    decisionReason.append("【RSI > 70】賣出訊號，嘗試賣出 ").append(sellAmount).append(" 股。");
                    logger.info(String.format(
                            "散戶%s RSI 賣出策略：RSI=%.2f, 持股數量=%d",
                            traderID, rsi, getAccumulatedStocks()
                    ), "RETAIL_INVESTOR_RSI_SELL");
                    int actualSell = 市價賣出操作(sellAmount);
                    if (actualSell > 0) {
                        logger.info(String.format(
                                "散戶%s RSI 賣出成功：賣出 %d 股，RSI=%.2f",
                                traderID, actualSell, rsi
                        ), "RETAIL_INVESTOR_RSI_SELL");
                        decisionReason.append("【成功】市價賣出 ").append(actualSell).append(" 股。\n");
                    } else {
                        logger.warn(String.format(
                                "散戶%s RSI 賣出失敗：賣出量 %d，RSI=%.2f",
                                traderID, sellAmount, rsi
                        ), "RETAIL_INVESTOR_RSI_SELL");
                        //decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                    }
                    stopLossPrice = null;
                    takeProfitPrice = null;
                    logger.debug(String.format(
                            "散戶%s 重置停損/止盈設定",
                            traderID
                    ), "RETAIL_INVESTOR_STOP_POINTS");
                }

                if (model != null) {
                    model.sendInfoMessage(decisionReason.toString());
                } else {
                    logger.warn(String.format(
                            "散戶%s 無法更新 UI，model 為 null",
                            traderID
                    ), "RETAIL_INVESTOR_DECISION");
                }
                logger.debug(String.format(
                        "散戶%s 決策完成，結果：%s",
                        traderID, decisionReason.toString()
                ), "RETAIL_INVESTOR_DECISION");
            } else {
                if (model != null) {
                    model.sendInfoMessage("【散戶】尚無法計算 SMA，暫無決策。\n");
                } else {
                    logger.warn(String.format(
                            "散戶%s 無法更新 UI，model 為 null",
                            traderID
                    ), "RETAIL_INVESTOR_DECISION");
                }
                logger.warn(String.format(
                        "散戶%s 無法計算 SMA，暫無決策",
                        traderID
                ), "RETAIL_INVESTOR_DECISION");
            }
        } catch (Exception e) {
            logger.error(String.format(
                    "散戶%s 決策過程異常：%s",
                    traderID, e.getMessage()
            ), "RETAIL_INVESTOR_DECISION");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "RETAIL_INVESTOR_DECISION");
        }
    }

// ========== 停損/止盈處理 ==========
    private void handleStopLossTakeProfit(double currentPrice, StringBuilder decisionReason) {
        // 停損
        if (stopLossPrice != null && currentPrice <= stopLossPrice) {
            logger.info(String.format(
                    "散戶%s 觸發停損：停損價=%.2f, 當前價格=%.2f",
                    traderID, stopLossPrice, currentPrice
            ), "RETAIL_INVESTOR_STOP_LOSS");

            int sellAll = getAccumulatedStocks();
            if (sellAll > 0) {
                logger.debug(String.format(
                        "散戶%s 停損賣出：嘗試賣出數量=%d",
                        traderID, sellAll
                ), "RETAIL_INVESTOR_STOP_LOSS");

                int actualSell = 市價賣出操作(sellAll);
                if (actualSell > 0) {
                    logger.info(String.format(
                            "散戶%s 停損賣出成功：賣出 %d 股",
                            traderID, actualSell
                    ), "RETAIL_INVESTOR_STOP_LOSS");
                    decisionReason.append("【停損觸發】市價賣出全部 ").append(actualSell).append(" 股。\n");
                } else {
                    logger.warn(String.format(
                            "散戶%s 停損賣出失敗：賣出量 %d",
                            traderID, sellAll
                    ), "RETAIL_INVESTOR_STOP_LOSS");
                    //decisionReason.append("【停損觸發】失敗，持股或買單量不足。\n");
                }
            }
            stopLossPrice = null;
            takeProfitPrice = null;
            logger.debug(String.format(
                    "散戶%s 停損後重置停損/止盈設定",
                    traderID
            ), "RETAIL_INVESTOR_STOP_POINTS");
        }
        // 止盈
        if (takeProfitPrice != null && currentPrice >= takeProfitPrice) {
            logger.info(String.format(
                    "散戶%s 觸發止盈：止盈價=%.2f, 當前價格=%.2f",
                    traderID, takeProfitPrice, currentPrice
            ), "RETAIL_INVESTOR_TAKE_PROFIT");

            int sellAll = getAccumulatedStocks();
            if (sellAll > 0) {
                logger.debug(String.format(
                        "散戶%s 止盈賣出：嘗試賣出數量=%d",
                        traderID, sellAll
                ), "RETAIL_INVESTOR_TAKE_PROFIT");

                int actualSell = 市價賣出操作(sellAll);
                if (actualSell > 0) {
                    logger.info(String.format(
                            "散戶%s 止盈賣出成功：賣出 %d 股",
                            traderID, actualSell
                    ), "RETAIL_INVESTOR_TAKE_PROFIT");
                    decisionReason.append("【止盈觸發】市價賣出全部 ").append(actualSell).append(" 股。\n");
                } else {
                    logger.warn(String.format(
                            "散戶%s 止盈賣出失敗：賣出量 %d",
                            traderID, sellAll
                    ), "RETAIL_INVESTOR_TAKE_PROFIT");
                    //decisionReason.append("【止盈觸發】失敗，持股或買單量不足。\n");
                }
            }
            stopLossPrice = null;
            takeProfitPrice = null;
            logger.debug(String.format(
                    "散戶%s 止盈後重置停損/止盈設定",
                    traderID
            ), "RETAIL_INVESTOR_STOP_POINTS");
        }
    }

    // ========== 隨機操作 ==========
    /**
     * 隨機交易 - 增強版，加入不同訂單類型
     */
    private void executeRandomTransaction(double availableFunds, double currentPrice, StringBuilder decisionReason, Stock stock) {
        double sma = model.getMarketAnalyzer().calculateSMA();
        double rsi = model.getMarketAnalyzer().getRSI();
        double volatility = model.getMarketAnalyzer().getVolatility();

        // 選擇交易類型
        String txType = random.nextDouble() < 0.5 ? "buy" : "sell";
        logger.debug(String.format(
                "散戶%s 隨機交易：選擇交易類型=%s, 可用資金=%.2f, 當前價格=%.2f",
                traderID, txType, availableFunds, currentPrice
        ), "RETAIL_INVESTOR_RANDOM");

        if ("buy".equals(txType) && availableFunds >= currentPrice) {
            int buyAmount = random.nextInt(50) + 1; // 1~50 股
            logger.debug(String.format(
                    "散戶%s 隨機買入：決定買入數量=%d",
                    traderID, buyAmount
            ), "RETAIL_INVESTOR_RANDOM");

            // 選擇訂單類型
            double orderTypeRandom = random.nextDouble();
            if (orderTypeRandom < 0.4) {
                // 40% 機率市價單
                logger.debug(String.format(
                        "散戶%s 隨機買入：選擇市價單，機率=%.4f",
                        traderID, orderTypeRandom
                ), "RETAIL_INVESTOR_RANDOM");

                int actualBuy = 市價買入操作(buyAmount);
                if (actualBuy > 0) {
                    logger.info(String.format(
                            "散戶%s 隨機市價買入成功：買入 %d 股",
                            traderID, actualBuy
                    ), "RETAIL_INVESTOR_RANDOM");
                    decisionReason.append("【隨機操作】市價買入 ").append(actualBuy).append(" 股。\n");
                    setStopLossAndTakeProfit(currentPrice, model.getMarketAnalyzer().getVolatility());
                } else {
                    logger.warn(String.format(
                            "散戶%s 隨機市價買入失敗：買入量 %d",
                            traderID, buyAmount
                    ), "RETAIL_INVESTOR_RANDOM");
                }
            } else if (orderTypeRandom < 0.9) {
                // 50% 機率限價單
                double buyLimitPrice = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                logger.debug(String.format(
                        "散戶%s 隨機買入：選擇限價單，機率=%.4f, 限價=%.2f",
                        traderID, orderTypeRandom, buyLimitPrice
                ), "RETAIL_INVESTOR_RANDOM");

                int actualBuy = 限價買入操作(buyAmount, buyLimitPrice);
                if (actualBuy > 0) {
                    logger.info(String.format(
                            "散戶%s 隨機限價買入成功：買入 %d 股，價格=%.2f",
                            traderID, actualBuy, buyLimitPrice
                    ), "RETAIL_INVESTOR_RANDOM");
                    decisionReason.append("【隨機操作】限價買入 ").append(actualBuy).append(" 股，價格 " + decimalFormat.format(buyLimitPrice) + "。\n");
                    setStopLossAndTakeProfit(currentPrice, model.getMarketAnalyzer().getVolatility());
                } else {
                    logger.warn(String.format(
                            "散戶%s 隨機限價買入失敗：買入量 %d，限價=%.2f",
                            traderID, buyAmount, buyLimitPrice
                    ), "RETAIL_INVESTOR_RANDOM");
                }
            } else {
                // 10% 機率FOK單
                double buyPrice = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                logger.debug(String.format(
                        "散戶%s 隨機買入：選擇FOK單，機率=%.4f, 價格=%.2f",
                        traderID, orderTypeRandom, buyPrice
                ), "RETAIL_INVESTOR_RANDOM");

                boolean success = orderBook.submitFokBuyOrder(buyPrice, buyAmount, this);
                if (success) {
                    logger.info(String.format(
                            "散戶%s 隨機FOK買入成功：買入 %d 股，價格=%.2f",
                            traderID, buyAmount, buyPrice
                    ), "RETAIL_INVESTOR_RANDOM");
                    decisionReason.append("【隨機操作】FOK買入 ").append(buyAmount).append(" 股，價格 " + decimalFormat.format(buyPrice) + "。\n");
                    setStopLossAndTakeProfit(currentPrice, model.getMarketAnalyzer().getVolatility());
                } else {
                    logger.warn(String.format(
                            "散戶%s 隨機FOK買入失敗：買入量 %d，價格=%.2f",
                            traderID, buyAmount, buyPrice
                    ), "RETAIL_INVESTOR_RANDOM");
                    decisionReason.append("【隨機操作】FOK買入失敗，無法完全滿足。\n");
                }
            }
        } else if (getAccumulatedStocks() > 0) {
            int sellAmount = random.nextInt(getAccumulatedStocks()) + 1;
            logger.debug(String.format(
                    "散戶%s 隨機賣出：決定賣出數量=%d, 持股數量=%d",
                    traderID, sellAmount, getAccumulatedStocks()
            ), "RETAIL_INVESTOR_RANDOM");

            // 選擇訂單類型
            double orderTypeRandom = random.nextDouble();
            if (orderTypeRandom < 0.4) {
                // 40% 機率市價單
                logger.debug(String.format(
                        "散戶%s 隨機賣出：選擇市價單，機率=%.4f",
                        traderID, orderTypeRandom
                ), "RETAIL_INVESTOR_RANDOM");

                int actualSell = 市價賣出操作(sellAmount);
                if (actualSell > 0) {
                    logger.info(String.format(
                            "散戶%s 隨機市價賣出成功：賣出 %d 股",
                            traderID, actualSell
                    ), "RETAIL_INVESTOR_RANDOM");
                    decisionReason.append("【隨機操作】市價賣出 ").append(actualSell).append(" 股。\n");
                } else {
                    logger.warn(String.format(
                            "散戶%s 隨機市價賣出失敗：賣出量 %d",
                            traderID, sellAmount
                    ), "RETAIL_INVESTOR_RANDOM");
                }
            } else if (orderTypeRandom < 0.9) {
                // 50% 機率限價單
                double sellLimitPrice = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                logger.debug(String.format(
                        "散戶%s 隨機賣出：選擇限價單，機率=%.4f, 限價=%.2f",
                        traderID, orderTypeRandom, sellLimitPrice
                ), "RETAIL_INVESTOR_RANDOM");

                int actualSell = 限價賣出操作(sellAmount, sellLimitPrice);
                if (actualSell > 0) {
                    logger.info(String.format(
                            "散戶%s 隨機限價賣出成功：賣出 %d 股，價格=%.2f",
                            traderID, actualSell, sellLimitPrice
                    ), "RETAIL_INVESTOR_RANDOM");
                    decisionReason.append("【隨機操作】限價賣出 ").append(actualSell).append(" 股，價格 " + decimalFormat.format(sellLimitPrice) + "。\n");
                } else {
                    logger.warn(String.format(
                            "散戶%s 隨機限價賣出失敗：賣出量 %d，限價=%.2f",
                            traderID, sellAmount, sellLimitPrice
                    ), "RETAIL_INVESTOR_RANDOM");
                }
            } else {
                // 10% 機率FOK單
                double sellPrice = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                logger.debug(String.format(
                        "散戶%s 隨機賣出：選擇FOK單，機率=%.4f, 價格=%.2f",
                        traderID, orderTypeRandom, sellPrice
                ), "RETAIL_INVESTOR_RANDOM");

                boolean success = orderBook.submitFokSellOrder(sellPrice, sellAmount, this);
                if (success) {
                    logger.info(String.format(
                            "散戶%s 隨機FOK賣出成功：賣出 %d 股，價格=%.2f",
                            traderID, sellAmount, sellPrice
                    ), "RETAIL_INVESTOR_RANDOM");
                    decisionReason.append("【隨機操作】FOK賣出 ").append(sellAmount).append(" 股，價格 " + decimalFormat.format(sellPrice) + "。\n");
                } else {
                    logger.warn(String.format(
                            "散戶%s 隨機FOK賣出失敗：賣出量 %d，價格=%.2f",
                            traderID, sellAmount, sellPrice
                    ), "RETAIL_INVESTOR_RANDOM");
                    decisionReason.append("【隨機操作】FOK賣出失敗，無法完全滿足。\n");
                }
            }

            stopLossPrice = null;
            takeProfitPrice = null;
            logger.debug(String.format(
                    "散戶%s 隨機賣出後重置停損/止盈設定",
                    traderID
            ), "RETAIL_INVESTOR_STOP_POINTS");
        } else {
            logger.debug(String.format(
                    "散戶%s 隨機交易：無法執行交易，資金不足或無持股",
                    traderID
            ), "RETAIL_INVESTOR_RANDOM");
        }
    }

    /**
     * 根據技術指標回傳「買單」理想掛價
     */
    private double computeBuyLimitPrice(double currentPrice, double sma,
            double rsi, double volatility) {

        // 1. 價格偏離：低於 SMA → 想買，取負值
        double smaOffset = (sma == 0) ? 0
                : (currentPrice - sma) / sma;          // 正：高於均線，負：低於均線
        smaOffset = -smaOffset;                      // 低於均線 => 負偏移 → 買單掛更高？反向

        // 2. RSI：RSI < 50 偏向買方；用 (50 - RSI) / 100 作為偏移
        double rsiOffset = (Double.isNaN(rsi)) ? 0
                : (50.0 - rsi) / 100.0;                // RSI 30 → +0.20；RSI 70 → -0.20

        // 3. 波動度：波動越大 → 掛單價離現價遠一點（保守）
        double volOffset = -volatility * 0.5;        // volatility ≈ 0.02 → -0.01

        // 4. 加權
        double totalOffset = smaOffset * smaWeight
                + rsiOffset * rsiWeight
                + volOffset * volatilityWeight;

        // 5. 限制在 ±maxOffsetRatio
        totalOffset = Math.max(-maxOffsetRatio,
                Math.min(totalOffset, maxOffsetRatio));

        // 6. 計算掛單價並回傳（向下取價差）
        double limitPrice = currentPrice * (1.0 + totalOffset);
        return orderBook.adjustPriceToUnit(limitPrice);
    }

    /**
     * 根據技術指標回傳「賣單」理想掛價
     */
    private double computeSellLimitPrice(double currentPrice, double sma,
            double rsi, double volatility) {

        double smaOffset = (sma == 0) ? 0
                : (currentPrice - sma) / sma;          // 高於均線 → 正值（想賣）

        double rsiOffset = (Double.isNaN(rsi)) ? 0
                : (rsi - 50.0) / 100.0;                // RSI 70 → +0.20；RSI 30 → -0.20

        double volOffset = volatility * 0.5;         // 波動大 → 再遠離一點

        double totalOffset = smaOffset * smaWeight
                + rsiOffset * rsiWeight
                + volOffset * volatilityWeight;

        totalOffset = Math.max(-maxOffsetRatio,
                Math.min(totalOffset, maxOffsetRatio));

        double limitPrice = currentPrice * (1.0 + totalOffset);
        return orderBook.adjustPriceToUnit(limitPrice);
    }

    // ========== 實際掛單操作 (成功/失敗印出) ==========
    /**
     * 修改散戶AI類的市價買入操作方法 - 使用新的市價單API
     */
    private int 市價買入操作(int buyAmount) {
        double price = stock.getPrice();
        double totalCost = price * buyAmount;
        double funds = account.getAvailableFunds();

        if (stock == null) {
            return 0;
        }

        if (orderBook == null) {
            return 0;
        }

        if (funds < totalCost) {
            // 資金不足
            return 0;
        }

        // 市價單門檻化：僅在強趨勢與高速度時允許
        try {
            double tps = model != null ? model.getRecentTPS(40) : 0.0;
            double imb = model != null ? model.getRecentTickImbalance(40) : 0.0;
            boolean strongFlow = (tps >= 2.0) && (imb > 0.15); // 可調參
            if (!strongFlow) {
                // 降級為限價單：靠近最優賣一
                double px = stock.getPrice() * 1.001; // 小幅抬價
                px = orderBook.adjustPriceToUnit(px);
                Order buyOrder = Order.createLimitBuyOrder(px, buyAmount, this);
                orderBook.submitBuyOrder(buyOrder, px);
                return buyAmount;
            }
        } catch (Exception ignore) {}

        // 使用新的市價買單API
        orderBook.marketBuy(this, buyAmount);
        return buyAmount;
    }

    /**
     * 修改散戶AI類的市價賣出操作方法 - 使用新的市價單API
     */
    private int 市價賣出操作(int sellAmount) {
        int hold = getAccumulatedStocks();
        if (hold < sellAmount) {
            // 持股不足
            return 0;
        }

        // 市價單門檻化：僅在強趨勢與高速度時允許
        try {
            double tps = model != null ? model.getRecentTPS(40) : 0.0;
            double imb = model != null ? model.getRecentTickImbalance(40) : 0.0;
            boolean strongFlow = (tps >= 2.0) && (imb < -0.15); // 可調參
            if (!strongFlow) {
                // 降級為限價單：靠近最優買一
                double px = stock.getPrice() * 0.999; // 小幅讓價
                px = orderBook.adjustPriceToUnit(px);
                Order sellOrder = Order.createLimitSellOrder(px, sellAmount, this);
                orderBook.submitSellOrder(sellOrder, px);
                return sellAmount;
            }
        } catch (Exception ignore) {}

        // 使用新的市價賣單API
        orderBook.marketSell(this, sellAmount);
        return sellAmount;
    }

    /**
     * 限價買入操作 - 增強版，支援多種訂單類型
     *
     * @param amount 欲買股數
     * @param suggestedPrice 系統計算的建議價格
     * @return 實際買入股數 (0=失敗)
     */
    private int 限價買入操作(int amount, double suggestedPrice) {
        double funds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();

        // 處理價格輸入 - 使用智能決策或手動輸入
        double finalPrice = 處理買入價格輸入(amount, suggestedPrice, currentPrice, funds);

        if (finalPrice <= 0) {
            return 0;
        }

        // 檢查資金
        double totalCost = finalPrice * amount;
        if (funds < totalCost) {
            if (!autoInputOrderAmount) {
                showError("資金不足，交易取消");
            }
            return 0;
        }

        // 決定訂單類型 (根據隨機性和當前模式)
        if (random.nextDouble() < 0.1) {
            // 10% 機率使用FOK訂單
            boolean success = orderBook.submitFokBuyOrder(finalPrice, amount, this);
            if (success) {
                System.out.println("提交FOK買單成功: " + amount + "股，價格 " + finalPrice);
                return amount;
            } else {
                System.out.println("提交FOK買單失敗: 無法完全滿足");
                return 0;
            }
        } else {
            // 90% 機率使用普通限價單
            Order buyOrder = Order.createLimitBuyOrder(finalPrice, amount, this);
            trackOrderCreation(buyOrder.getId());
            orderBook.submitBuyOrder(buyOrder, finalPrice);
            return amount;
        }
    }

    /**
     * 處理買入價格輸入 - 增強版，讓AI散戶可以根據市場情況自由決定價格
     *
     * @param amount 買入數量
     * @param suggestedPrice 建議價格
     * @param currentPrice 當前市價
     * @param funds 可用資金
     * @return 最終決定的價格
     */
    private double 處理買入價格輸入(int amount, double suggestedPrice, double currentPrice, double funds) {
        if (!autoInputOrderAmount) {
            // 手動模式邏輯保持不變
            String message = String.format(
                    "散戶 %s 限價買入\n欲買入數量: %d 股\n建議價格: %s\n目前市價: %s\n可用資金: %s\n請輸入買入價格 (或直接確認使用建議價格):",
                    traderID, amount,
                    decimalFormat.format(suggestedPrice),
                    decimalFormat.format(currentPrice),
                    decimalFormat.format(funds)
            );

            String inputPrice = JOptionPane.showInputDialog(null, message, decimalFormat.format(suggestedPrice));

            if (inputPrice == null) {
                return -1; // 取消
            }

            try {
                if (!inputPrice.trim().isEmpty()) {
                    inputPrice = inputPrice.replace(",", "");
                    double parsed = Double.parseDouble(inputPrice);
                    return orderBook.adjustPriceToUnit(parsed);
                }
            } catch (NumberFormatException e) {
                showError("價格格式錯誤，交易取消");
                return -1;
            }

            return orderBook.adjustPriceToUnit(suggestedPrice);
        } else {
            // 自動模式 - 增強AI決策能力
            return 智能決定買入價格(suggestedPrice, currentPrice);
        }
    }

    /**
     * 智能決定買入價格 考慮多種因素來確定一個合理的買入價格
     *
     * @param suggestedPrice 系統建議價格
     * @param currentPrice 當前市價
     * @return 智能決定的買入價格
     */
    private double 智能決定買入價格(double suggestedPrice, double currentPrice) {
        // 獲取市場分析數據
        double sma = model.getMarketAnalyzer().calculateSMA();
        double rsi = model.getMarketAnalyzer().getRSI();
        double volatility = model.getMarketAnalyzer().getVolatility();

        logger.debug(String.format(
                "散戶%s 智能買入定價開始：當前價格=%.2f, 建議價格=%.2f, SMA=%.2f, RSI=%.2f, 波動性=%.4f",
                traderID, currentPrice, suggestedPrice, sma, rsi, volatility
        ), "RETAIL_INVESTOR_PRICING");

        // 1. 基於市場狀況的決策因子
        double marketConditionFactor = 0.0;

        // 1.1 RSI因子 (RSI低時更願意買入，但價格可能會更低)
        double rsiFactor = 0.0;
        if (!Double.isNaN(rsi)) {
            // RSI < 30 (超賣) - 願意更接近市價買入
            // RSI > 70 (超買) - 應該出價更低以等待回調
            if (rsi < 30) {
                rsiFactor = 0.02; // 願意付出接近市價
            } else if (rsi > 70) {
                rsiFactor = -0.05; // 降低買入價以等待回調
            } else {
                // 30-70 之間線性映射
                rsiFactor = 0.02 - ((rsi - 30) / 40.0) * 0.07;
            }
        }

        // 1.2 波動率因子 (高波動應該更謹慎)
        double volatilityFactor = Math.min(-0.01 * volatility * 100, -0.001);

        // 1.3 價格與SMA的關係 (低於SMA時更願意買入)
        double smaDiffFactor = 0.0;
        if (!Double.isNaN(sma) && sma > 0) {
            double priceDiffPercent = (currentPrice - sma) / sma;
            if (priceDiffPercent < -0.05) {
                // 已經低於SMA 5%，可能是好買點
                smaDiffFactor = 0.01;
            } else if (priceDiffPercent > 0.05) {
                // 高於SMA 5%，應該等待下跌
                smaDiffFactor = -0.02;
            } else {
                // 接近SMA，適度調整
                smaDiffFactor = -priceDiffPercent * 0.2;
            }
        }

        // 2. 個性化因子 (每個散戶有不同特性)
        double personalityFactor = 0.0;

        // 2.1 隨機性 (模擬散戶常有的不理性)
        double randomFactor = (random.nextDouble() - 0.5) * 0.04;

        // 2.2 風險偏好 (可以根據traderID固定或其他特徵來決定)
        double riskAppetite = (traderID.hashCode() % 10) / 100.0; // -0.05 ~ 0.04

        // 2.3 急迫感 (想要快速成交還是願意等待更好價格)
        double urgencyFactor = 0.0;
        if (ignoreThreshold) {
            // 忽略閾值的交易者通常更急迫
            urgencyFactor = 0.02;
        } else {
            // 遵守閾值的通常更有耐心
            urgencyFactor = -0.01;
        }

        // 3. 市場技術分析建議價與當前價的差異
        double suggestedDiff = (suggestedPrice - currentPrice) / currentPrice;
        double technicalFactor = suggestedDiff * 0.5; // 採納50%的技術建議

        // 4. 綜合所有因子
        marketConditionFactor = rsiFactor + volatilityFactor + smaDiffFactor;
        personalityFactor = randomFactor + riskAppetite + urgencyFactor;

        // 計算最終價格調整因子 (各因子權重可調)
        double priceFactor = marketConditionFactor * 0.5 + personalityFactor * 0.3 + technicalFactor * 0.2;

        // 限制調整範圍在 -8% ~ +3% 之間 (買入價通常不會高太多)
        priceFactor = Math.max(-0.08, Math.min(priceFactor, 0.03));

        // 計算最終價格
        double finalPrice = currentPrice * (1 + priceFactor);

        // 調整為市場最小單位並返回
        double adjustedPrice = orderBook.adjustPriceToUnit(finalPrice);

        logger.debug(String.format(
                "散戶%s 智能買入定價因子：RSI因子=%.4f, 波動因子=%.4f, SMA因子=%.4f, 隨機因子=%.4f, 風險偏好=%.4f, 急迫因子=%.4f",
                traderID, rsiFactor, volatilityFactor, smaDiffFactor, randomFactor, riskAppetite, urgencyFactor
        ), "RETAIL_INVESTOR_PRICING");

        logger.info(String.format(
                "散戶%s 智能買入定價結果：市價=%.2f, 計算價格=%.2f, 價格調整=%.2f%%, 市場因子=%.4f, 個性因子=%.4f, 技術因子=%.4f",
                traderID, currentPrice, adjustedPrice, priceFactor * 100, marketConditionFactor, personalityFactor, technicalFactor
        ), "RETAIL_INVESTOR_PRICING");

        return adjustedPrice;
    }

    /**
     * 限價賣出操作 - 增強版，支援多種訂單類型
     *
     * @param amount 欲賣股數
     * @param suggestedPrice 系統計算的建議價格
     * @return 實際賣出股數 (0=失敗)
     */
    private int 限價賣出操作(int amount, double suggestedPrice) {
        int hold = getAccumulatedStocks();
        double currentPrice = stock.getPrice();

        // 檢查持股是否足夠
        if (hold < amount) {
            if (!autoInputOrderAmount) {
                showError("持股不足，交易取消");
            }
            return 0;
        }

        // 處理價格輸入 - 使用智能決策或手動輸入
        double finalPrice = 處理賣出價格輸入(amount, suggestedPrice, currentPrice, hold);

        if (finalPrice <= 0) {
            return 0; // 使用者取消或輸入錯誤
        }

        // 決定訂單類型 (根據隨機性和當前模式)
        if (random.nextDouble() < 0.1) {
            // 10% 機率使用FOK訂單
            boolean success = orderBook.submitFokSellOrder(finalPrice, amount, this);
            if (success) {
                System.out.println("提交FOK賣單成功: " + amount + "股，價格 " + finalPrice);
                return amount;
            } else {
                System.out.println("提交FOK賣單失敗: 無法完全滿足");
                return 0;
            }
        } else {
            // 90% 機率使用普通限價單
            Order sellOrder = Order.createLimitSellOrder(finalPrice, amount, this);
            trackOrderCreation(sellOrder.getId());
            orderBook.submitSellOrder(sellOrder, finalPrice);
            return amount;
        }
    }

    /**
     * 處理賣出價格輸入 - 增強版，讓AI散戶可以根據市場情況自由決定價格
     *
     * @param amount 賣出數量
     * @param suggestedPrice 建議價格
     * @param currentPrice 當前市價
     * @param hold 持有股數
     * @return 最終決定的價格
     */
    private double 處理賣出價格輸入(int amount, double suggestedPrice, double currentPrice, int hold) {
        if (!autoInputOrderAmount) {
            // 手動模式邏輯保持不變
            String message = String.format(
                    "散戶 %s 限價賣出\n欲賣出數量: %d 股\n建議價格: %s\n目前市價: %s\n持有股數: %d\n請輸入賣出價格 (或直接確認使用建議價格):",
                    traderID, amount,
                    decimalFormat.format(suggestedPrice),
                    decimalFormat.format(currentPrice),
                    hold
            );

            System.out.println("AI限價賣出操作：" + message);

            String inputPrice = JOptionPane.showInputDialog(null, message, decimalFormat.format(suggestedPrice));

            if (inputPrice == null) {
                return -1; // 表示使用者取消
            }

            try {
                if (!inputPrice.trim().isEmpty()) {
                    inputPrice = inputPrice.replace(",", "");
                    double parsed = Double.parseDouble(inputPrice);
                    return orderBook.adjustPriceToUnit(parsed);
                }
            } catch (NumberFormatException e) {
                showError("價格格式錯誤，交易取消");
                return -1;
            }

            return orderBook.adjustPriceToUnit(suggestedPrice);
        } else {
            // 自動模式 - 增強AI決策能力
            return 智能決定賣出價格(suggestedPrice, currentPrice);
        }
    }

    /**
     * 智能決定賣出價格 考慮多種因素來確定一個合理的賣出價格
     *
     * @param suggestedPrice 系統建議價格
     * @param currentPrice 當前市價
     * @return 智能決定的賣出價格
     */
    private double 智能決定賣出價格(double suggestedPrice, double currentPrice) {
        // 獲取市場分析數據
        double sma = model.getMarketAnalyzer().calculateSMA();
        double rsi = model.getMarketAnalyzer().getRSI();
        double volatility = model.getMarketAnalyzer().getVolatility();

        logger.debug(String.format(
                "散戶%s 智能賣出定價開始：當前價格=%.2f, 建議價格=%.2f, SMA=%.2f, RSI=%.2f, 波動性=%.4f",
                traderID, currentPrice, suggestedPrice, sma, rsi, volatility
        ), "RETAIL_INVESTOR_PRICING");

        // 1. 基於市場狀況的決策因子
        double marketConditionFactor = 0.0;

        // 1.1 RSI因子 (RSI高時更願意賣出，但希望價格更高)
        double rsiFactor = 0.0;
        if (!Double.isNaN(rsi)) {
            // RSI > 70 (超買) - 市場可能會繼續上漲，要求更高價格
            // RSI < 30 (超賣) - 市場可能還會下跌，應盡快賣出
            if (rsi > 70) {
                rsiFactor = 0.03; // 要求更高價格
            } else if (rsi < 30) {
                rsiFactor = -0.02; // 接受更低價格以確保成交
            } else {
                // 30-70 之間線性映射
                rsiFactor = -0.02 + ((rsi - 30) / 40.0) * 0.05;
            }
        }

        logger.debug(String.format(
                "散戶%s 智能賣出RSI因子：RSI=%.2f, RSI因子=%.4f",
                traderID, rsi, rsiFactor
        ), "RETAIL_INVESTOR_PRICING");

        // 1.2 波動率因子 (高波動時要求風險溢價)
        double volatilityFactor = Math.min(0.02 * volatility * 100, 0.05);

        logger.debug(String.format(
                "散戶%s 智能賣出波動因子：波動性=%.4f, 波動因子=%.4f",
                traderID, volatility, volatilityFactor
        ), "RETAIL_INVESTOR_PRICING");

        // 1.3 價格與SMA的關係 (高於SMA時更願意賣出)
        double smaDiffFactor = 0.0;
        if (!Double.isNaN(sma) && sma > 0) {
            double priceDiffPercent = (currentPrice - sma) / sma;
            if (priceDiffPercent > 0.05) {
                // 已經高於SMA 5%，可能是好賣點
                smaDiffFactor = 0.02;
            } else if (priceDiffPercent < -0.05) {
                // 低於SMA 5%，應該等待反彈
                smaDiffFactor = 0.04;
            } else {
                // 接近SMA，適度調整
                smaDiffFactor = priceDiffPercent * 0.3;
            }
        }

        logger.debug(String.format(
                "散戶%s 智能賣出SMA因子：SMA=%.2f, 價格差異百分比=%.2f%%, SMA因子=%.4f",
                traderID, sma, (sma > 0 ? ((currentPrice - sma) / sma) * 100 : 0), smaDiffFactor
        ), "RETAIL_INVESTOR_PRICING");

        // 2. 個性化因子 (每個散戶有不同特性)
        double personalityFactor = 0.0;

        // 2.1 隨機性 (模擬散戶常有的不理性)
        double randomFactor = (random.nextDouble() - 0.3) * 0.05; // 偏向略高的隨機值

        logger.debug(String.format(
                "散戶%s 智能賣出隨機因子：隨機因子=%.4f",
                traderID, randomFactor
        ), "RETAIL_INVESTOR_PRICING");

        // 2.2 風險偏好 (可以根據traderID固定或其他特徵來決定)
        double riskAppetite = (traderID.hashCode() % 10) / 100.0; // -0.05 ~ 0.04

        logger.debug(String.format(
                "散戶%s 智能賣出風險偏好：風險偏好=%.4f, traderID=%s",
                traderID, riskAppetite, traderID
        ), "RETAIL_INVESTOR_PRICING");

        // 2.3 急迫感 (想要快速成交還是願意等待更好價格)
        double urgencyFactor = 0.0;
        if (ignoreThreshold) {
            // 忽略閾值的交易者通常更急迫
            urgencyFactor = -0.01;
        } else {
            // 遵守閾值的通常更有耐心
            urgencyFactor = 0.02;
        }

        logger.debug(String.format(
                "散戶%s 智能賣出急迫因子：忽略門檻=%b, 急迫因子=%.4f",
                traderID, ignoreThreshold, urgencyFactor
        ), "RETAIL_INVESTOR_PRICING");

        // 3. 市場技術分析建議價與當前價的差異
        double suggestedDiff = (suggestedPrice - currentPrice) / currentPrice;
        double technicalFactor = suggestedDiff * 0.6; // 採納60%的技術建議

        logger.debug(String.format(
                "散戶%s 智能賣出技術因子：建議價格=%.2f, 當前價格=%.2f, 差異百分比=%.2f%%, 技術因子=%.4f",
                traderID, suggestedPrice, currentPrice, suggestedDiff * 100, technicalFactor
        ), "RETAIL_INVESTOR_PRICING");

        // 4. 綜合所有因子
        marketConditionFactor = rsiFactor + volatilityFactor + smaDiffFactor;
        personalityFactor = randomFactor + riskAppetite + urgencyFactor;

        // 計算最終價格調整因子 (各因子權重可調)
        double priceFactor = marketConditionFactor * 0.45 + personalityFactor * 0.3 + technicalFactor * 0.25;

        logger.debug(String.format(
                "散戶%s 智能賣出綜合因子：市場因子=%.4f(權重=45%%), 個性因子=%.4f(權重=30%%), 技術因子=%.4f(權重=25%%)",
                traderID, marketConditionFactor, personalityFactor, technicalFactor
        ), "RETAIL_INVESTOR_PRICING");

        // 限制調整範圍在 -3% ~ +8% 之間 (賣出價通常不會低太多)
        double originalPriceFactor = priceFactor;
        priceFactor = Math.max(-0.03, Math.min(priceFactor, 0.08));

        if (originalPriceFactor != priceFactor) {
            logger.debug(String.format(
                    "散戶%s 智能賣出範圍調整：原始調整因子=%.4f, 限制後因子=%.4f",
                    traderID, originalPriceFactor, priceFactor
            ), "RETAIL_INVESTOR_PRICING");
        }

        // 計算最終價格
        double finalPrice = currentPrice * (1 + priceFactor);

        // 調整為市場最小單位並返回
        double adjustedPrice = orderBook.adjustPriceToUnit(finalPrice);

        if (finalPrice != adjustedPrice) {
            logger.debug(String.format(
                    "散戶%s 智能賣出市場單位調整：計算價格=%.4f, 調整後價格=%.4f",
                    traderID, finalPrice, adjustedPrice
            ), "RETAIL_INVESTOR_PRICING");
        }

        logger.info(String.format(
                "散戶%s 智能賣出定價結果：市價=%.2f, 計算價格=%.2f, 價格調整=%.2f%%, 市場因子=%.4f, 個性因子=%.4f, 技術因子=%.4f",
                traderID, currentPrice, adjustedPrice, priceFactor * 100, marketConditionFactor, personalityFactor, technicalFactor
        ), "RETAIL_INVESTOR_PRICING");

        return adjustedPrice;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "錯誤", JOptionPane.ERROR_MESSAGE);
    }

    // ========== 工具/輔助函數 ==========
    /**
     * 計算交易量，根據波動性調整頭寸大小
     */
    private int calculateTransactionVolume(double availableFunds, double currentPrice, double volatility) {
        // 波動性高 -> 減少頭寸
        double positionSize = (availableFunds / currentPrice) * (1 / (1 + volatility));
        // 略強化：提高基本投入比例，讓散戶更有對打能力
        double base = riskPerTrade * 1.25; // 原 0.2 → 0.25
        double eventScale = 1.0;
        try { if (model != null) eventScale = model.getEventPositionScale(); } catch (Exception ignore) {}
        // 指標倉位縮放：MACD 多頭偏 >0 放大、空頭 <0 縮小；K>80 減倉，K<20 放大
        double techScale = 1.0;
        try {
            double macdHist = model.getLastMacdHist();
            double k = model.getLastK();
            if (!Double.isNaN(macdHist)) {
                techScale *= (1.0 + Math.max(-0.2, Math.min(0.2, macdHist * 0.5))); // ±20%
            }
            if (!Double.isNaN(k)) {
                if (k > 80) techScale *= 0.85; else if (k < 20) techScale *= 1.15;
            }
        } catch (Exception ignore) {}
        positionSize = positionSize * base * this.riskFactor * eventScale * techScale;
        int result = (int) Math.max(1, positionSize);

        logger.debug(String.format(
                "散戶%s 計算買入量：可用資金=%.2f, 當前價格=%.2f, 波動性=%.4f, 風險係數=%.4f, 計算結果=%d",
                traderID, availableFunds, currentPrice, volatility, riskPerTrade, result
        ), "RETAIL_INVESTOR_VOLUME");

        return result;
    }

    /**
     * 計算賣出量，根據波動性調整頭寸大小
     */
    private int calculateSellVolume(double priceDifferenceRatio, double volatility) {
        double eventScale = 1.0;
        try { if (model != null) eventScale = model.getEventPositionScale(); } catch (Exception ignore) {}
        double positionSize = getAccumulatedStocks() * (0.15 + 0.45 * random.nextDouble()) * (1 / (1 + volatility)) * eventScale;
        int result = (int) Math.max(1, positionSize);

        logger.debug(String.format(
                "散戶%s 計算賣出量：當前持股=%d, 價格差異比率=%.4f, 波動性=%.4f, 計算結果=%d",
                traderID, getAccumulatedStocks(), priceDifferenceRatio, volatility, result
        ), "RETAIL_INVESTOR_VOLUME");

        return result;
    }

    /**
     * 設置停損 & 止盈價格
     */
    private void setStopLossAndTakeProfit(double currentPrice, double volatility) {
        // 停損: 95%
        stopLossPrice = currentPrice * 0.95;
        // 止盈: 150%
        takeProfitPrice = currentPrice * 1.5;

        logger.debug(String.format(
                "散戶%s 設置新停損/止盈點：入場價=%.2f, 停損價=%.2f, 止盈價=%.2f, 波動性=%.4f",
                traderID, currentPrice, stopLossPrice, takeProfitPrice, volatility
        ), "RETAIL_INVESTOR_STOP_POINTS");
    }

    /**
     * 是否忽略門檻
     */
    public boolean shouldIgnoreThreshold() {
        return ignoreThreshold;
    }

    /**
     * 獲取散戶持股
     */
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    /**
     * 獲取散戶現金
     */
    public double getCash() {
        return account.getAvailableFunds();
    }

    /**
     * 散戶 ID
     */
    public String getTraderID() {
        return traderID;
    }

    /**
     * 初始資金（用於個別損益計算）
     */
    public double getInitialCash() {
        return initialCash;
    }

    /**
     * 買入門檻
     */
    public double getBuyThreshold() {
        return buyThreshold;
    }

    /**
     * 賣出門檻
     */
    public double getSellThreshold() {
        return sellThreshold;
    }

    /**
     * 動量交易 (若需要可擴充)
     */
    private int calculateMomentumVolume(double volatility) {
        return (int) (500 * volatility * (0.8 + random.nextDouble() * 0.4));
    }

    private boolean actByProfile(double currentPrice, double sma, double rsi, double volatility,
                                 double actionProbability, double availableFunds,
                                 StringBuilder decisionReason, Stock stock) {
        try {
            // 個人化下單量倍率
            double riskAdj = Math.max(0.5, Math.min(this.riskFactor, 1.5));

            switch (this.profile) {
                case MOMENTUM: {
                    // 趨勢向上且波動偏高 → 偏好市價追價小量買入
                    if (sma > 0 && currentPrice > sma && volatility > 0.02 && actionProbability < 0.35) {
                        int buyAmount = (int) Math.max(1, calculateTransactionVolume(availableFunds, currentPrice, volatility) * 0.5 * riskAdj);
                        if (buyAmount > 0) {
                            if (random.nextDouble() < marketOrderBias) {
                                int done = 市價買入操作(buyAmount);
                                if (done > 0) {
                                    decisionReason.append("[MOMENTUM] 市價追漲 ").append(done).append(" 股\n");
                                    setStopLossAndTakeProfit(currentPrice, volatility);
                                    return true;
                                }
                            } else {
                                double px = computeBuyLimitPrice(currentPrice, sma, rsi, volatility * 0.8);
                                int done = 限價買入操作(buyAmount, px);
                                if (done > 0) {
                                    decisionReason.append("[MOMENTUM] 限價跟隨 ").append(done).append(" 股 @").append(px).append("\n");
                                    setStopLossAndTakeProfit(currentPrice, volatility);
                                    return true;
                                }
                            }
                        }
                    }
                    break;
                }
                case CONTRARIAN: {
                    // 當前價顯著偏離 SMA 就反向操作
                    if (sma > 0) {
                        double diff = (currentPrice - sma) / sma;
                        if (diff < -0.04 && availableFunds >= currentPrice && actionProbability < 0.5) {
                            // 低於均線 → 逆向買入
                            int buyAmount = (int) Math.max(1, calculateTransactionVolume(availableFunds, currentPrice, volatility) * 0.7 * riskAdj);
                            double px = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                            int done = 限價買入操作(buyAmount, px);
                            if (done > 0) {
                                decisionReason.append("[CONTRARIAN] 逆向抄底 ").append(done).append(" 股 @").append(px).append("\n");
                                setStopLossAndTakeProfit(currentPrice, volatility);
                                return true;
                            }
                        } else if (diff > 0.06 && getAccumulatedStocks() > 0 && actionProbability < 0.5) {
                            // 高於均線 → 逆向獲利了結
                            int sellAmount = (int) Math.max(1, calculateSellVolume(diff, volatility) * 0.6 * riskAdj);
                            double px = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                            int done = 限價賣出操作(sellAmount, px);
                            if (done > 0) {
                                decisionReason.append("[CONTRARIAN] 逆向了結 ").append(done).append(" 股 @").append(px).append("\n");
                                return true;
                            }
                        }
                    }
                    break;
                }
                case VALUE: {
                    // 低估（價格低於 SMA 一定幅度）才分批買入，偏好多用 FOK 小筆成交
                    if (sma > 0) {
                        double diff = (currentPrice - sma) / sma;
                        if (diff < -0.08 && actionProbability < 0.4) {
                            int buyAmount = (int) Math.max(1, calculateTransactionVolume(availableFunds, currentPrice, volatility) * 0.4 * riskAdj);
                            double px = computeBuyLimitPrice(currentPrice, sma, rsi, volatility * 1.2);
                            if (random.nextDouble() < fokBias) {
                                boolean ok = orderBook.submitFokBuyOrder(px, buyAmount, this);
                                if (ok) {
                                    decisionReason.append("[VALUE] FOK 價值買入 ").append(buyAmount).append(" 股 @").append(px).append("\n");
                                    setStopLossAndTakeProfit(currentPrice, volatility);
                                    return true;
                                }
                            } else {
                                int done = 限價買入操作(buyAmount, px);
                                if (done > 0) {
                                    decisionReason.append("[VALUE] 限價價值買入 ").append(done).append(" 股 @").append(px).append("\n");
                                    setStopLossAndTakeProfit(currentPrice, volatility);
                                    return true;
                                }
                            }
                        }
                    }
                    break;
                }
                case SCALPER: {
                    // 高波動/微利快進快出：小量市價單
                    if (volatility > 0.03) {
                        if (actionProbability < 0.3 && availableFunds >= currentPrice) {
                            int buyAmount = Math.max(1, (int) (5 * riskAdj));
                            int done = 市價買入操作(buyAmount);
                            if (done > 0) {
                                decisionReason.append("[SCALPER] 市價快進 ").append(done).append(" 股\n");
                                setStopLossAndTakeProfit(currentPrice, volatility);
                                return true;
                            }
                        } else if (actionProbability > 0.7 && getAccumulatedStocks() > 0) {
                            int sellAmount = Math.max(1, (int) (5 * riskAdj));
                            int done = 市價賣出操作(sellAmount);
                            if (done > 0) {
                                decisionReason.append("[SCALPER] 市價快出 ").append(done).append(" 股\n");
                                return true;
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.warn("actByProfile 異常：" + e.getMessage(), "RETAIL_INVESTOR_DECISION");
        }
        return false;
    }
    
    /**
     * 取消過時或不合理的訂單
     */
    private void cancelOutdatedOrders() {
        if (orderBook == null || model == null) return;
        
        double currentPrice = model.getStock().getPrice();
        long currentTime = System.currentTimeMillis();
        java.util.List<Order> myOrders = new java.util.ArrayList<>();
        
        // 收集所有自己的訂單
        myOrders.addAll(orderBook.getBuyOrders().stream()
            .filter(o -> o.getTrader() == this)
            .collect(Collectors.toList()));
        myOrders.addAll(orderBook.getSellOrders().stream()
            .filter(o -> o.getTrader() == this)
            .collect(Collectors.toList()));
        
        for (Order order : myOrders) {
            boolean shouldCancel = false;
            String reason = "";
            
            // 基本取消規則
            // 1. 訂單年齡過大
            Long creationTime = orderCreationTime.get(order.getId());
            if (creationTime != null && (currentTime - creationTime) > MAX_ORDER_AGE_MS) {
                shouldCancel = true;
                reason = "訂單存在時間過長";
            }
            
            // 2. 價格偏離過大（根據策略調整）
            double priceDeviation = Math.abs(order.getPrice() - currentPrice) / currentPrice;
            double maxDeviation = getMaxPriceDeviation();
            
            if (priceDeviation > maxDeviation) {
                shouldCancel = true;
                reason = String.format("價格偏離過大 (%.2f%%)", priceDeviation * 100);
            }
            
            // 3. 策略特定的取消邏輯
            if (!shouldCancel && shouldCancelByStrategy(order, currentPrice)) {
                shouldCancel = true;
                reason = "策略特定條件觸發";
            }
            
            // 執行取消
            if (shouldCancel) {
                orderBook.cancelOrder(order.getId());
                orderCreationTime.remove(order.getId());
                logger.info(String.format(
                    "【散戶訂單取消】%s %s訂單，價格=%.2f，原因=%s",
                    traderID, order.getType(), order.getPrice(), reason
                ), "RETAIL_ORDER_CANCEL");
            }
        }
    }
    
    /**
     * 根據當前策略獲取最大價格偏離
     */
    private double getMaxPriceDeviation() {
        switch (profile) {
            case AGGRESSIVE: return 0.15; // 激進型：15%
            case CONSERVATIVE: return 0.05; // 保守型：5%
            case MOMENTUM: return 0.20; // 動量型：20%
            case CONTRARIAN: return 0.12; // 逆勢型：12%
            case SCALPER: return 0.03; // 短線型：3%
            case SWING: return 0.10; // 波段型：10%
            case VALUE: return 0.08; // 價值型：8%
            default: return 0.10;
        }
    }
    
    /**
     * 根據策略判斷是否取消訂單
     */
    private boolean shouldCancelByStrategy(Order order, double currentPrice) {
        switch (profile) {
            case SCALPER:
                // 短線型：對小波動敏感，快速調整
                return Math.abs(order.getPrice() - currentPrice) / currentPrice > 0.02;
                
            case MOMENTUM:
                // 動量型：如果價格趨勢改變則取消
                if (order.getType().equals("buy") && currentPrice < order.getPrice() * 0.95) {
                    return true;
                }
                if (order.getType().equals("sell") && currentPrice > order.getPrice() * 1.05) {
                    return true;
                }
                break;
                
            case CONSERVATIVE:
                // 保守型：嚴格控制風險
                Long creationTime = orderCreationTime.get(order.getId());
                if (creationTime != null && (System.currentTimeMillis() - creationTime) > 60000) {
                    return true; // 1分鐘未成交就取消
                }
                break;
        }
        return false;
    }
    
    /**
     * 記錄訂單創建時間
     */
    private void trackOrderCreation(String orderId) {
        if (orderId != null) {
            orderCreationTime.put(orderId, System.currentTimeMillis());
        }
    }
}
