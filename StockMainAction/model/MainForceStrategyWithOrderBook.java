package StockMainAction.model;

import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.user.UserAccount;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.Trader;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import StockMainAction.util.logging.MarketLogger;
import java.util.Arrays;

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

    /**
     * 構造函數
     *
     * @param orderBook 訂單簿實例
     * @param stock 股票實例
     * @param simulation 模擬實例
     * @param initialCash 初始現金
     */
    public MainForceStrategyWithOrderBook(OrderBook orderBook, Stock stock, StockMarketModel model, double initialCash) {
        this.orderBook = orderBook;
        this.stock = stock;
        this.model = model;
        this.tradeLog = new StringBuilder();
        this.random = new Random();
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
            // 限價單買入：增加股數
            account.incrementStocks(volume);
            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();
            // 更新目標價
            calculateTargetPrice();
            System.out.println(String.format("【限價買入後更新】主力買入 %d 股，成交價 %.2f，更新後平均成本價 %.2f",
                    volume, price, averageCostPrice));
        } else if ("sell".equals(type)) {
            // 限價單賣出：增加現金
            account.incrementFunds(transactionAmount);
            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }
            System.out.println(String.format("【限價賣出後更新】主力賣出 %d 股，成交價 %.2f，更新後持股 %d 股",
                    volume, price, getAccumulatedStocks()));
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
            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();

            // 更新目標價
            calculateTargetPrice();

            System.out.println(String.format("【市價買入後更新】主力買入 %d 股，成交價 %.2f，更新後平均成本價 %.2f",
                    volume, price, averageCostPrice));

        } else if ("sell".equals(type)) {
            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }

            System.out.println(String.format("【市價賣出後更新】主力賣出 %d 股，成交價 %.2f，更新後持股 %d 股",
                    volume, price, getAccumulatedStocks()));
        }

        // 更新界面上的標籤
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

            // 顯示當前撮合模式
            MatchingMode currentMode = orderBook.getMatchingMode();
            logger.info(String.format("當前撮合模式: %s", currentMode), "MAIN_FORCE_DECISION");

            if (!Double.isNaN(sma)) {
                double priceDifferenceRatio = (currentPrice - sma) / sma;
                // 限制價格差在 [-0.5, 0.5] 之間
                priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

                double actionProbability = random.nextDouble();
                StringBuilder decisionLog = new StringBuilder();

                // 1. 動量交易策略（追漲買入）
                if (actionProbability < 0.1 && currentPrice > sma && volatility > 0.02) {
                    int momentumVolume = calculateMomentumVolume(volatility);

                    // 根據撮合模式選擇不同的訂單類型
                    if (currentMode == MatchingMode.MARKET_PRESSURE || currentMode == MatchingMode.RANDOM) {
                        // 市場壓力或隨機模式下，使用市價單快速追漲
                        decisionLog.append("【動量交易-市價追漲】");
                        拉抬操作(momentumVolume);
                    } else {
                        // 其他撮合模式下，使用限價單吸籌
                        decisionLog.append("【動量交易-限價吸籌】");
                        吸籌操作(momentumVolume);
                    }

                    // 2. 均值回歸策略
                } else if (actionProbability < 0.15 && Math.abs(priceDifferenceRatio) > 0.1) {
                    if (priceDifferenceRatio > 0) {
                        // 股價高於均線一定幅度 -> 逢高賣出
                        int revertSellVolume = calculateRevertSellVolume(priceDifferenceRatio);

                        // 根據偏離程度決定使用哪種訂單類型
                        if (priceDifferenceRatio > 0.2) {
                            // 極度偏離，使用市價單快速賣出
                            decisionLog.append("【均值回歸-市價賣出】極度高於均線");
                            市價賣出操作(revertSellVolume);
                        } else if (currentMode == MatchingMode.PRICE_TIME) {
                            // 價格時間優先模式下，使用FOK單確保完整成交
                            decisionLog.append("【均值回歸-FOK賣出】高於均線");
                            精確控制賣出操作(revertSellVolume, currentPrice);
                        } else {
                            // 常規情況，使用限價單
                            decisionLog.append("【均值回歸-限價賣出】高於均線");
                            賣出操作(revertSellVolume);
                        }
                    } else {
                        // 股價低於均線一定幅度 -> 逢低買入
                        int revertBuyVolume = calculateRevertBuyVolume(priceDifferenceRatio);

                        // 根據偏離程度和撮合模式決定訂單類型
                        if (priceDifferenceRatio < -0.2 && currentMode != MatchingMode.VOLUME_WEIGHTED) {
                            // 極度低於均線，且不在成交量加權模式下，使用FOK單確保完整買入
                            decisionLog.append("【均值回歸-FOK買入】極度低於均線");
                            精確控制買入操作(revertBuyVolume, currentPrice);
                        } else {
                            // 常規情況，使用限價單
                            decisionLog.append("【均值回歸-限價買入】低於均線");
                            吸籌操作(revertBuyVolume);
                        }
                    }

                    // 3. 價值投資策略（低估買入）
                } else if (currentPrice < sma * 0.9 && actionProbability < 0.2) {
                    int valueBuyVolume = calculateValueBuyVolume();

                    // 根據價格趨勢選擇訂單類型
                    if (recentTrend < -0.05) {
                        // 下跌趨勢明顯，謹慎買入，使用少量的FOK單
                        int reducedVolume = valueBuyVolume / 3;
                        decisionLog.append("【價值投資-謹慎FOK買入】下跌趨勢中");
                        精確控制買入操作(reducedVolume, currentPrice * 0.98);
                    } else {
                        // 常規情況，使用限價單
                        decisionLog.append("【價值投資-限價買入】價格低估");
                        吸籌操作(valueBuyVolume);
                    }

                    // 4. 風險控制策略（風險過高時暫停操作）
                } else if (riskFactor > 0.8) {
                    decisionLog.append(String.format("【風險管控】風險係數 %.2f 過高，主力暫停操作", riskFactor));
                    System.out.println(decisionLog.toString());
                    return;

                    // 5. 市價拉抬（追蹤市場流動性）
                } else if (actionProbability < 0.25 && availableFunds > currentPrice * 100) {
                    int buyQuantity = calculateLiftVolume();

                    // 根據撮合模式和波動性決定如何拉抬
                    if (volatility > 0.03 && currentMode == MatchingMode.MARKET_PRESSURE) {
                        // 高波動 + 市場壓力模式，使用較大量的市價單快速拉抬
                        decisionLog.append("【強力拉抬-市價買入】高波動環境");
                        拉抬操作(buyQuantity * 2);
                    } else {
                        // 常規拉抬
                        decisionLog.append("【常規拉抬-市價買入】");
                        拉抬操作(buyQuantity);
                    }

                    // 6. 市價減倉（降低風險）
                } else if (actionProbability < 0.3 && getAccumulatedStocks() > 0) {
                    int sellQuantity = calculateSellVolume();

                    // 根據RSI指標決定減倉方式
                    if (rsi > 70) {
                        // RSI高，超買狀態，大量減倉
                        decisionLog.append("【超買減倉-市價賣出】RSI過高");
                        市價賣出操作(sellQuantity * 2);
                    } else {
                        // 常規減倉
                        decisionLog.append("【常規減倉-市價賣出】");
                        市價賣出操作(sellQuantity);
                    }

                    // 7. 高風險行為：洗盤
                } else if (actionProbability < 0.35 && getAccumulatedStocks() > 200) {
                    int washVolume = calculateWashVolume(volatility);

                    // 根據當前撮合模式選擇洗盤策略
                    if (currentMode == MatchingMode.VOLUME_WEIGHTED) {
                        // 在成交量加權模式下，洗盤效果更好，使用更大量
                        decisionLog.append("【加強洗盤】成交量加權模式");
                        洗盤操作(washVolume * 2);
                    } else {
                        // 常規洗盤
                        decisionLog.append("【常規洗盤】");
                        洗盤操作(washVolume);
                    }

                    // 8. 隨機取消掛單
                } else if (actionProbability < 0.4) {
                    if (!orderBook.getBuyOrders().isEmpty()) {
                        Order orderToCancel = orderBook.getBuyOrders().get(0);
                        //orderBook.cancelOrder(orderToCancel.getId());
                        decisionLog.append(String.format("【隨機取消掛單】取消買單 ID: %s，數量: %d 股",
                                orderToCancel.getId(), orderToCancel.getVolume()));
                    }

                    // 9. 新增：目標價附近的精確控制（確保利潤）
                } else if (actionProbability < 0.45 && getTargetPrice() > 0
                        && Math.abs(currentPrice - getTargetPrice()) / getTargetPrice() < 0.05) {

                    // 接近目標價，精確控制持倉
                    if (currentPrice >= getTargetPrice()) {
                        // 已達目標價，開始獲利了結
                        int profitVolume = (int) (getAccumulatedStocks() * 0.3); // 賣出30%持股
                        if (profitVolume > 0) {
                            decisionLog.append("【獲利了結-FOK賣出】已達目標價");
                            精確控制賣出操作(profitVolume, currentPrice);
                        }
                    } else {
                        // 接近但未達目標價，可能再加碼一些
                        int topupVolume = calculateValueBuyVolume() / 4; // 小量加碼
                        if (topupVolume > 0 && availableFunds > currentPrice * topupVolume) {
                            decisionLog.append("【目標布局-FOK買入】接近目標價");
                            精確控制買入操作(topupVolume, currentPrice);
                        }
                    }

                    // 10. 新增：撮合模式特殊策略
                } else if (actionProbability < 0.5) {
                    // 根據不同撮合模式採取特殊策略
                    switch (currentMode) {
                        case PRICE_TIME:
                            // 價格時間優先模式下，快速掛單搶先機
                            if (availableFunds > currentPrice * 100 && random.nextBoolean()) {
                                decisionLog.append("【時間優先策略】快速掛買單");
                                吸籌操作(50);
                            }
                            break;

                        case VOLUME_WEIGHTED:
                            // 成交量加權模式下，大單有優勢
                            if (availableFunds > currentPrice * 500 && random.nextBoolean()) {
                                decisionLog.append("【大單策略】大量買入獲取優勢");
                                吸籌操作(500);
                            }
                            break;

                        case MARKET_PRESSURE:
                            // 市場壓力模式下，順勢而為
                            if (recentTrend > 0.02 && availableFunds > currentPrice * 200) {
                                decisionLog.append("【順勢策略】上漲趨勢中買入");
                                拉抬操作(200);
                            } else if (recentTrend < -0.02 && getAccumulatedStocks() > 100) {
                                decisionLog.append("【順勢策略】下跌趨勢中賣出");
                                市價賣出操作(100);
                            }
                            break;

                        case RANDOM:
                            // 隨機模式下，增加FOK單使用頻率
                            if (random.nextBoolean() && availableFunds > currentPrice * 100) {
                                decisionLog.append("【隨機模式策略】FOK買入");
                                精確控制買入操作(100, currentPrice);
                            }
                            break;

                        default:
                            // 標準模式，使用常規策略
                            if (availableFunds > currentPrice * 100 && getAccumulatedStocks() < 500) {
                                decisionLog.append("【標準策略】常規買入");
                                吸籌操作(100);
                            }
                            break;
                    }
                }

                // 輸出決策日誌
                if (decisionLog.length() > 0) {
                    System.out.println(decisionLog.toString());
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
            orderBook.submitBuyOrder(buyOrder, limitPrice);

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
            orderBook.submitSellOrder(sellOrder, limitPrice);

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
                boolean success = orderBook.submitFokSellOrder(price * 0.99, volume, this);
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
                orderBook.submitSellOrder(sellOrder, price);

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

            // 使用市價買單
            orderBook.marketBuy(this, volume);

            logger.info(String.format(
                    "拉抬操作成功：市價買入 %d 股，預計成本上限 %.2f",
                    volume, price * volume
            ), "MAIN_FORCE_LIFT");

            return volume;
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

            // 使用市價賣單
            orderBook.marketSell(this, volume);

            logger.info(String.format(
                    "市價賣出成功：預計賣出 %d 股",
                    volume
            ), "MAIN_FORCE_MARKET_SELL");

            return volume;
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
            boolean success = orderBook.submitFokBuyOrder(price, volume, this);

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
            boolean success = orderBook.submitFokSellOrder(price, volume, this);

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
        volume = Math.min(volume, getAccumulatedStocks()); // 不可超過持股數
        System.out.println(String.format("【計算均值回歸賣出量】建議賣出 %d 股 (價格差異比例 %.2f)", volume, priceDifferenceRatio));
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
        System.out.println(String.format("【計算均值回歸買入量】建議買入 %d 股 (可用資金 %.2f, 價格差異 %.2f)",
                volume, funds, priceDifferenceRatio));
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
        int volume = (int) (funds / currentPrice * 0.5); // 資金的 50% 投入
        System.out.println(String.format("【計算價值買入量】建議買入 %d 股 (資金 %.2f, 價格 %.2f)",
                volume, funds, currentPrice));
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
        System.out.println(String.format("【計算市價賣出量】預計賣出 %d 股 (目前持股 %d)", vol, hold));
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
        double totalAssets = account.getAvailableFunds() + stock.getPrice() * getAccumulatedStocks();
        if (totalAssets <= 0) {
            return 0.0;
        }
        double risk = 1 - (account.getAvailableFunds() / totalAssets);
        return Math.max(0, Math.min(risk, 1));
    }
}
