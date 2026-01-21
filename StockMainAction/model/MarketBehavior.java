package StockMainAction.model;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.Trader;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import StockMainAction.StockMarketSimulation;
import StockMainAction.model.user.UserAccount;
import java.util.List;
import java.util.Random;
import StockMainAction.util.logging.MarketLogger;
import StockMainAction.util.logging.LogicAudit;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 市場波動 - 負責模擬智能的市場波動。根據市場條件動態生成訂單，並提交給訂單簿。實現 Trader 接口，作為市場的一個參與者。
 */
public class MarketBehavior implements Trader {

    private Random random = new Random();
    private double marketTrend = 0.0;    // 市場趨勢，正值表示牛市，負值表示熊市
    private double longTermMeanPrice;    // 長期平均價格
    private int timeStep = 0;           // 時間步長，用於模擬時間因素
    private UserAccount account;
    private StockMarketSimulation simulation; // 引用模擬實例
    private StockMarketModel model;
    private final OrderBook orderBook;
    private static final MarketLogger logger = MarketLogger.getInstance();

    // 依現價掛單的百分比偏移量（負值＝低於現價掛買、正值＝高於現價掛賣）
    private double buyPriceOffsetRatio = -0.05; // 例：買單掛在現價 -5%
    private double sellPriceOffsetRatio = 0.05; // 例：賣單掛在現價 +5%
    // 建議放在 MarketBehavior 類別屬性區
    private double smaWeight = 0.4;
    private double imbalanceWeight = 0.3;
    private double momentumWeight = 0.2;
    private double volumeWeight = 0.1;

    private double bullishThreshold = 0.15;  // 多頭分數 ≥ 0.15
    private double bearishThreshold = -0.15; // 空頭分數 ≤ -0.15

    // 需要在類的成員變量中添加
    private long lastOrderTime = 0; // 上次下單時間
    private static final long ORDER_COOLDOWN_MS = 2000; // 下單冷卻時間，2秒

    /**
     * 構造函數
     *
     * @param initialPrice 初始價格
     * @param initialFunds 初始資金
     * @par am initialStocks 初始股票數量
     * @param model 引用 StockMarketModel 實例
     */
    public MarketBehavior(double initialPrice, double initialFunds, int initialStocks, StockMarketModel model, OrderBook orderBook) {
        this.longTermMeanPrice = initialPrice;
        this.account = new UserAccount(initialFunds, initialStocks);
        this.model = model;
        this.orderBook = orderBook;
        this.random = new Random();

        if (this.account != null) {
            logger.info("【市場行為】UserAccount 建立成功，初始資金: " + initialFunds
                    + "，初始持股: " + initialStocks, "MARKET_BEHAVIOR_INIT");
        } else {
            logger.error("【市場行為】UserAccount 建立失敗。", "MARKET_BEHAVIOR_INIT");
        }
    }

    // ======== Trader 介面實作 ========
    @Override
    public UserAccount getAccount() {
        return account;
    }

    @Override
    public String getTraderType() {
        return "MarketBehavior";
    }

    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if (type.equals("buy")) {
            try {
                account.consumeFrozenFunds(transactionAmount);
            } catch (Exception e) {
                account.decrementFunds(transactionAmount);
            }
            account.incrementStocks(volume);
            System.out.println(String.format("【市場行為-限價買入後更新】買入 %d 股，成交價 %.2f", volume, price));
        } else if (type.equals("sell")) {
            // 限價單賣出：增加現金
            account.incrementFunds(transactionAmount);
            System.out.println(String.format("【市場行為-限價賣出後更新】賣出 %d 股，成交價 %.2f", volume, price));
        }
        // 可在此更新介面標籤或其他 UI
    }

    @Override
    public void updateAverageCostPrice(String type, int volume, double price) {
        // 市價單的狀態更新，如需更細緻的平均成本計算，可在此實作
        double transactionAmount = price * volume;
        if ("buy".equals(type)) {
            System.out.println(String.format("【市場行為-市價買入後更新】買入 %d 股，成交價 %.2f", volume, price));
            account.incrementStocks(volume);
        } else if ("sell".equals(type)) {
            System.out.println(String.format("【市場行為-市價賣出後更新】賣出 %d 股，成交價 %.2f", volume, price));
            account.incrementFunds(transactionAmount);
        }
    }

    /**
     * 市場波動邏輯 - 修正版，避免重複下單問題
     *
     * @param stock 股票實例
     * @param orderBook 訂單簿實例
     * @param volatility 波動性
     * @param recentVolume 最近成交量
     */
    public void marketFluctuation(Stock stock, OrderBook orderBook, double volatility, int recentVolume) {
        try {
            timeStep++;

            LogicAudit.info("MARKET_BEHAVIOR", String.format("start t=%d price=%.4f vol=%.4f recentVol=%d",
                    timeStep, stock.getPrice(), volatility, recentVolume));

            logger.debug(String.format(
                    "市場波動開始：時間步=%d, 當前價格=%.2f, 波動率=%.4f, 近期成交量=%d",
                    timeStep, stock.getPrice(), volatility, recentVolume
            ), "MARKET_BEHAVIOR");

            double currentPrice = stock.getPrice();

            // 1. 計算市場趨勢和波動性
            double drift = marketTrend * 0.01;
            double shock = volatility * random.nextGaussian();

            logger.debug(String.format(
                    "市場趨勢計算：趨勢因子=%.4f, 隨機衝擊=%.4f",
                    drift, shock
            ), "MARKET_BEHAVIOR");

            // 2. 均值回歸
            double meanReversionSpeed = 0.005;
            double meanReversion = meanReversionSpeed * (longTermMeanPrice - currentPrice);

            logger.debug(String.format(
                    "均值回歸：長期均價=%.2f, 回歸因子=%.4f",
                    longTermMeanPrice, meanReversion
            ), "MARKET_BEHAVIOR");

            // 3. 加入時間因素（開盤/收盤波動較大）
            double timeVolatilityFactor = getTimeVolatilityFactor();

            logger.debug(String.format(
                    "時間波動因子：時間步=%d, 因子=%.4f",
                    timeStep, timeVolatilityFactor
            ), "MARKET_BEHAVIOR");

            // 4. 突發事件
            double eventImpact = 0.0;
            if (random.nextDouble() < 0.01) {
                eventImpact = currentPrice * (0.02 * (random.nextDouble() - 0.5));
                logger.info(String.format(
                        "突發事件發生：影響=%.4f",
                        eventImpact
                ), "MARKET_BEHAVIOR");
            }

            // 5. 計算價格變動
            double priceChangeRatio = drift + shock + meanReversion + eventImpact;
            priceChangeRatio *= timeVolatilityFactor;

            logger.debug(String.format(
                    "初始價格變動計算：變動比率=%.4f",
                    priceChangeRatio
            ), "MARKET_BEHAVIOR");

            // 6. 訂單簿買賣不平衡
            double orderImbalance = calculateOrderImbalance(orderBook);
            priceChangeRatio += orderImbalance * 0.005;

            logger.debug(String.format(
                    "訂單不平衡影響：不平衡度=%.4f, 調整後變動比率=%.4f",
                    orderImbalance, priceChangeRatio
            ), "MARKET_BEHAVIOR");

            // === A. 技術面 ===
            double sma = model.getMarketAnalyzer().calculateSMA();
            double smaScore = Double.isNaN(sma) ? 0
                    : (currentPrice - sma) / sma; // 正值 → 價格高於均線

            logger.debug(String.format(
                    "技術面分析：SMA=%.2f, SMA分數=%.4f",
                    sma, smaScore
            ), "MARKET_BEHAVIOR");

            // === B. 訂單簿面 ===
            double imbalanceScore = orderImbalance; // 已是 -1 ~ +1 之間

            logger.debug(String.format(
                    "訂單簿分析：不平衡分數=%.4f",
                    imbalanceScore
            ), "MARKET_BEHAVIOR");

            // === C. 動能面 ===
            double momentumScore = priceChangeRatio; // -0.x ~ +0.x

            logger.debug(String.format(
                    "動能面分析：動能分數=%.4f",
                    momentumScore
            ), "MARKET_BEHAVIOR");

            // === D. 量能面 ===
            double avgVol = model.getMarketAnalyzer().getRecentAverageVolume();
            double volumeScore = (avgVol == 0) ? 0
                    : (recentVolume - avgVol) / avgVol; // 放大 >0、縮量 <0

            logger.debug(String.format(
                    "量能面分析：平均成交量=%.2f, 量能分數=%.4f",
                    avgVol, volumeScore
            ), "MARKET_BEHAVIOR");

            // === E. 加權總分 ===
            double sentimentScore
                    = smaScore * smaWeight
                    + imbalanceScore * imbalanceWeight
                    + momentumScore * momentumWeight
                    + volumeScore * volumeWeight;

            logger.info(String.format(
                    "市場情緒綜合分數：%.4f (技術面權重=%.2f, 訂單簿權重=%.2f, 動能權重=%.2f, 量能權重=%.2f)",
                    sentimentScore, smaWeight, imbalanceWeight, momentumWeight, volumeWeight
            ), "MARKET_BEHAVIOR");

            // 7. 價格層級影響
            double priceLevelImpact = calculatePriceLevelImpact(orderBook);
            priceChangeRatio += priceLevelImpact * 0.005;

            logger.debug(String.format(
                    "價格層級影響：層級影響=%.4f, 調整後變動比率=%.4f",
                    priceLevelImpact, priceChangeRatio
            ), "MARKET_BEHAVIOR");

            // 8. 大單影響
            double largeOrderImpact = calculateLargeOrderImpact(orderBook);
            priceChangeRatio += largeOrderImpact;

            logger.debug(String.format(
                    "大單影響：大單影響=%.4f, 調整後變動比率=%.4f",
                    largeOrderImpact, priceChangeRatio
            ), "MARKET_BEHAVIOR");

            // 9. 成交量影響
            double volumeImpact = (recentVolume > 1000) ? 0.002 : (recentVolume < 100) ? -0.002 : 0.0;
            priceChangeRatio += volumeImpact;

            logger.debug(String.format(
                    "成交量影響：成交量=%d, 影響=%.4f, 調整後變動比率=%.4f",
                    recentVolume, volumeImpact, priceChangeRatio
            ), "MARKET_BEHAVIOR");

            // 10. 新訂單價格
            double newOrderPrice = currentPrice * (1 + priceChangeRatio);
            double minPrice = currentPrice * 0.9;
            double maxPrice = currentPrice * 1.1;
            double originalNewPrice = newOrderPrice;
            newOrderPrice = Math.max(minPrice, Math.min(newOrderPrice, maxPrice));
            newOrderPrice = Math.max(newOrderPrice, 0.1);

            if (originalNewPrice != newOrderPrice) {
                logger.debug(String.format(
                        "價格限制調整：原始計算價格=%.2f, 限制後價格=%.2f",
                        originalNewPrice, newOrderPrice
                ), "MARKET_BEHAVIOR");
            }

            // 11. 隨機浮動
            double randomFactor = (random.nextDouble() - 0.5) * 0.01;
            double priceBeforeRandom = newOrderPrice;
            newOrderPrice += newOrderPrice * randomFactor;

            logger.debug(String.format(
                    "隨機價格浮動：隨機因子=%.4f, 浮動前價格=%.2f, 浮動後價格=%.2f",
                    randomFactor, priceBeforeRandom, newOrderPrice
            ), "MARKET_BEHAVIOR");

            // 12. 決定訂單量，並決定要下買單或賣單
            int orderVolume = calculateOrderVolume(volatility, recentVolume);

            logger.debug(String.format(
                    "訂單量計算：波動率=%.4f, 近期成交量=%d, 計算訂單量=%d",
                    volatility, recentVolume, orderVolume
            ), "MARKET_BEHAVIOR");

            // 修改：添加主動交易防抖機制，避免在短時間內多次下單
            long currentTime = System.currentTimeMillis();
            boolean shouldPlaceOrder = true;

            // 如果距離上次下單時間太短，不進行交易
            if (currentTime - lastOrderTime < ORDER_COOLDOWN_MS) {
                shouldPlaceOrder = false;
                logger.debug(String.format(
                        "交易防抖：距上次下單時間過短(%d ms)，跳過此次下單",
                        currentTime - lastOrderTime
                ), "MARKET_BEHAVIOR");
            }

            // 先計算本步的 bestBid/bestAsk/mid/makerOffset，供下單與撤單共同使用
            List<Order> bestBids = orderBook.getTopBuyOrders(1);
            List<Order> bestAsks = orderBook.getTopSellOrders(1);

            double bestBid = bestBids.isEmpty() ? 0 : bestBids.get(0).getPrice();
            double bestAsk = bestAsks.isEmpty() ? 0 : bestAsks.get(0).getPrice();

            double mid;
            if (bestBid > 0 && bestAsk > 0 && bestBid <= bestAsk) {
                mid = (bestBid + bestAsk) / 2.0;
            } else {
                mid = currentPrice;
            }

            double spreadRatio = (bestBid > 0 && bestAsk > 0 && mid > 0)
                    ? (bestAsk - bestBid) / mid : 0.01;

            double makerOffset = Math.max(0.001, Math.min(0.02, 0.25 * spreadRatio + 0.3 * Math.abs(volatility)));

            // 只有在通過防抖檢查後才執行下單操作
            if (shouldPlaceOrder) {
                double rawBuyPrice = mid * (1 - makerOffset);
                double rawSellPrice = mid * (1 + makerOffset);

                double buyPrice = orderBook.adjustPriceToUnit(rawBuyPrice);
                double sellPrice = orderBook.adjustPriceToUnit(rawSellPrice);

                if (bestBid > 0) buyPrice = Math.min(buyPrice, bestBid);
                if (bestAsk > 0) sellPrice = Math.max(sellPrice, bestAsk);

                double buyBias = sentimentScore >= bullishThreshold ? 1.5 : (sentimentScore <= bearishThreshold ? 0.5 : 1.0);
                double sellBias = sentimentScore <= bearishThreshold ? 1.5 : (sentimentScore >= bullishThreshold ? 0.5 : 1.0);

                int maxBuyVolume = (int) Math.floor(account.getAvailableFunds() / Math.max(0.01, buyPrice));
                int maxSellVolume = account.getStockInventory();

                int baseVol = Math.max(100, (int) (orderVolume * 0.5));
                int buyVolume = (int) Math.min(maxBuyVolume, Math.ceil(baseVol * buyBias));
                int sellVolume = (int) Math.min(maxSellVolume, Math.ceil(baseVol * sellBias));

                boolean placed = false;

                if (buyVolume > 0 && buyPrice > 0) {
                    Order buyOrder = Order.createLimitBuyOrder(buyPrice, buyVolume, this);
                    orderBook.submitBuyOrder(buyOrder, buyPrice);
                    placed = true;
                    logger.info(String.format("做市掛買：%d 股 @ %.2f (mid=%.2f, spread=%.3f%%)",
                            buyVolume, buyPrice, mid, spreadRatio * 100), "MARKET_BEHAVIOR_MM");
                }

                if (sellVolume > 0 && sellPrice > 0) {
                    Order sellOrder = Order.createLimitSellOrder(sellPrice, sellVolume, this);
                    orderBook.submitSellOrder(sellOrder, sellPrice);
                    placed = true;
                    logger.info(String.format("做市掛賣：%d 股 @ %.2f (mid=%.2f, spread=%.3f%%)",
                            sellVolume, sellPrice, mid, spreadRatio * 100), "MARKET_BEHAVIOR_MM");
                }

                if (!placed) {
                    logger.debug("做市：因資金/持股限制，本步未下單", "MARKET_BEHAVIOR_MM");
                } else {
                    lastOrderTime = currentTime;
                }
            }

            // 做市撤單：不論本步是否下單，都移除遠離中間價的自家掛單，保持緊跟報價
            try {
                double replaceThreshold = makerOffset * 2.0 + 0.002; // 超出此比率則撤單重掛

                java.util.List<Order> buys = orderBook.getBuyOrders();
                for (int i = buys.size() - 1; i >= 0; i--) {
                    Order ob = buys.get(i);
                    if (ob == null || ob.getTrader() != this) continue;
                    if (ob.getPrice() > 0 && mid > 0) {
                        double diff = (mid - ob.getPrice()) / mid; // 買單價低於 mid 太多
                        if (diff > replaceThreshold) {
                            boolean ok = orderBook.cancelOrder(ob.getId());
                            if (ok) {
                                LogicAudit.info("MM_CANCEL", String.format("buy id=%s px=%.4f mid=%.4f diff=%.4f",
                                        ob.getId(), ob.getPrice(), mid, diff));
                            }
                        }
                    }
                }

                java.util.List<Order> sells = orderBook.getSellOrders();
                for (int i = sells.size() - 1; i >= 0; i--) {
                    Order os = sells.get(i);
                    if (os == null || os.getTrader() != this) continue;
                    if (os.getPrice() > 0 && mid > 0) {
                        double diff = (os.getPrice() - mid) / mid; // 賣單價高於 mid 太多
                        if (diff > replaceThreshold) {
                            boolean ok = orderBook.cancelOrder(os.getId());
                            if (ok) {
                                LogicAudit.info("MM_CANCEL", String.format("sell id=%s px=%.4f mid=%.4f diff=%.4f",
                                        os.getId(), os.getPrice(), mid, diff));
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}

            // 更新長期平均價格
            double oldLongTermMeanPrice = longTermMeanPrice;
            longTermMeanPrice = (longTermMeanPrice * (timeStep - 1) + currentPrice) / timeStep;

            logger.debug(String.format(
                    "更新長期平均價格：舊均價=%.2f, 新均價=%.2f",
                    oldLongTermMeanPrice, longTermMeanPrice
            ), "MARKET_BEHAVIOR");

            // 更新市場趨勢
            double oldMarketTrend = marketTrend;
            marketTrend = 0.95 * marketTrend + (random.nextDouble() - 0.5) * 0.01;
            marketTrend = Math.max(-0.5, Math.min(marketTrend, 0.5));

            logger.debug(String.format(
                    "更新市場趨勢：舊趨勢=%.4f, 新趨勢=%.4f",
                    oldMarketTrend, marketTrend
            ), "MARKET_BEHAVIOR");

            // 台股撮合：股價（last price）應由「成交」決定，不應由 MarketBehavior 直接 setPrice 造價。
            // 這裡保留 newOrderPrice 作為下單參考，但不直接改寫 stock.price，也不推進非成交的價格序列。

            logger.debug(String.format(
                    "市場波動結束：時間步=%d",
                    timeStep
            ), "MARKET_BEHAVIOR");
        } catch (Exception e) {
            logger.error(String.format(
                    "市場波動異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR");
        }
    }

    // ======== 下單功能函數 ========
    /**
     * 提交限價買單操作：先檢查資金，再實際掛單
     *
     * @param orderBook 訂單簿
     * @param orderPrice 訂單價格
     * @param orderVolume 訂單量
     * @return 實際成功買入股數 (0 表示失敗)
     */
    private int 限價買單操作(OrderBook orderBook, double orderPrice, int orderVolume) {
        try {
            logger.debug(String.format(
                    "開始限價買單操作：價格=%.2f, 數量=%d",
                    orderPrice, orderVolume
            ), "MARKET_BEHAVIOR_BUY");

            double cost = orderPrice * orderVolume;
            double funds = account.getAvailableFunds();

            if (funds < cost) {
                logger.warn(String.format(
                        "限價買單資金不足：需要=%.2f, 可用=%.2f, 數量=%d, 價格=%.2f",
                        cost, funds, orderVolume, orderPrice
                ), "MARKET_BEHAVIOR_BUY");
                return 0;
            }

            // 使用工廠方法創建限價買單
            Order buyOrder = Order.createLimitBuyOrder(orderPrice, orderVolume, this);
            orderBook.submitBuyOrder(buyOrder, orderPrice);

            logger.info(String.format(
                    "限價買單提交成功：數量=%d, 價格=%.2f, 預計成本=%.2f",
                    orderVolume, orderPrice, cost
            ), "MARKET_BEHAVIOR_BUY");

            return orderVolume;
        } catch (Exception e) {
            logger.error(String.format(
                    "限價買單異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_BUY");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_BUY");

            return 0;
        }
    }

    /**
     * 提交限價賣單操作：先檢查持股，再實際掛單
     *
     * @param orderBook 訂單簿
     * @param orderPrice 訂單價格
     * @param orderVolume 訂單量
     * @return 實際成功賣出股數 (0 表示失敗)
     */
    private int 限價賣單操作(OrderBook orderBook, double orderPrice, int orderVolume) {
        try {
            logger.debug(String.format(
                    "開始限價賣單操作：價格=%.2f, 數量=%d",
                    orderPrice, orderVolume
            ), "MARKET_BEHAVIOR_SELL");

            int hold = account.getStockInventory();

            if (hold < orderVolume) {
                logger.warn(String.format(
                        "限價賣單持股不足：需要=%d, 可用=%d, 價格=%.2f",
                        orderVolume, hold, orderPrice
                ), "MARKET_BEHAVIOR_SELL");
                return 0;
            }

            // 使用工廠方法創建限價賣單
            Order sellOrder = Order.createLimitSellOrder(orderPrice, orderVolume, this);
            orderBook.submitSellOrder(sellOrder, orderPrice);

            logger.info(String.format(
                    "限價賣單提交成功：數量=%d, 價格=%.2f, 預計收入=%.2f",
                    orderVolume, orderPrice, orderPrice * orderVolume
            ), "MARKET_BEHAVIOR_SELL");

            return orderVolume;
        } catch (Exception e) {
            logger.error(String.format(
                    "限價賣單異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_SELL");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_SELL");

            return 0;
        }
    }

    /**
     * 提交市價買單操作：先檢查資金估算值，再實際提交市價單
     *
     * @param orderBook 訂單簿
     * @param estimatedPrice 估算價格（用於檢查資金）
     * @param orderVolume 訂單量
     * @return 實際成功買入股數 (0 表示失敗)
     */
    private int 市價買單操作(OrderBook orderBook, double estimatedPrice, int orderVolume) {
        try {
            logger.debug(String.format(
                    "開始市價買單操作：估算價格=%.2f, 數量=%d",
                    estimatedPrice, orderVolume
            ), "MARKET_BEHAVIOR_BUY");

            // 以估算價格檢查資金是否足夠
            double estimatedCost = estimatedPrice * orderVolume;
            double funds = account.getAvailableFunds();

            if (funds < estimatedCost) {
                logger.warn(String.format(
                        "市價買單資金不足：需要(估計)=%.2f, 可用=%.2f, 數量=%d, 估算價格=%.2f",
                        estimatedCost, funds, orderVolume, estimatedPrice
                ), "MARKET_BEHAVIOR_BUY");
                return 0;
            }

            // 直接使用 marketBuy 方法執行市價單
            logger.debug(String.format(
                    "執行市價買單：數量=%d, 估算價格=%.2f",
                    orderVolume, estimatedPrice
            ), "MARKET_BEHAVIOR_BUY");

            orderBook.marketBuy(this, orderVolume);

            logger.info(String.format(
                    "市價買單提交成功：數量=%d, 估計成本=%.2f",
                    orderVolume, estimatedCost
            ), "MARKET_BEHAVIOR_BUY");

            return orderVolume;
        } catch (Exception e) {
            logger.error(String.format(
                    "市價買單異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_BUY");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_BUY");

            return 0;
        }
    }

    /**
     * 提交市價賣單操作：先檢查持股，再實際提交市價單
     *
     * @param orderBook 訂單簿
     * @param orderVolume 訂單量
     * @return 實際成功賣出股數 (0 表示失敗)
     */
    private int 市價賣單操作(OrderBook orderBook, int orderVolume) {
        try {
            logger.debug(String.format(
                    "開始市價賣單操作：數量=%d",
                    orderVolume
            ), "MARKET_BEHAVIOR_SELL");

            int hold = account.getStockInventory();

            if (hold < orderVolume) {
                logger.warn(String.format(
                        "市價賣單持股不足：需要=%d, 可用=%d",
                        orderVolume, hold
                ), "MARKET_BEHAVIOR_SELL");
                return 0;
            }

            // 直接使用 marketSell 方法執行市價單
            logger.debug(String.format(
                    "執行市價賣單：數量=%d",
                    orderVolume
            ), "MARKET_BEHAVIOR_SELL");

            orderBook.marketSell(this, orderVolume);

            logger.info(String.format(
                    "市價賣單提交成功：數量=%d",
                    orderVolume
            ), "MARKET_BEHAVIOR_SELL");

            return orderVolume;
        } catch (Exception e) {
            logger.error(String.format(
                    "市價賣單異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_SELL");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_SELL");

            return 0;
        }
    }

    /**
     * 提交FOK買單操作：先檢查資金，再實際提交FOK單
     *
     * @param orderBook 訂單簿
     * @param orderPrice 訂單價格
     * @param orderVolume 訂單量
     * @return 實際成功買入股數 (0 表示失敗)
     */
    private int FOK買單操作(OrderBook orderBook, double orderPrice, int orderVolume) {
        try {
            logger.debug(String.format(
                    "開始FOK買單操作：價格=%.2f, 數量=%d",
                    orderPrice, orderVolume
            ), "MARKET_BEHAVIOR_FOK_BUY");

            double cost = orderPrice * orderVolume;
            double funds = account.getAvailableFunds();

            if (funds < cost) {
                logger.warn(String.format(
                        "FOK買單資金不足：需要=%.2f, 可用=%.2f, 數量=%d, 價格=%.2f",
                        cost, funds, orderVolume, orderPrice
                ), "MARKET_BEHAVIOR_FOK_BUY");
                return 0;
            }

            // 使用 submitFokBuyOrder 方法提交FOK買單
            logger.debug(String.format(
                    "提交FOK買單：價格=%.2f, 數量=%d",
                    orderPrice, orderVolume
            ), "MARKET_BEHAVIOR_FOK_BUY");

            boolean success = orderBook.submitFokBuyOrder(orderPrice, orderVolume, this);

            if (success) {
                logger.info(String.format(
                        "FOK買單成功：數量=%d, 價格=%.2f, 預計成本=%.2f",
                        orderVolume, orderPrice, cost
                ), "MARKET_BEHAVIOR_FOK_BUY");
                return orderVolume;
            } else {
                logger.warn(String.format(
                        "FOK買單失敗：無法完全滿足，數量=%d, 價格=%.2f",
                        orderVolume, orderPrice
                ), "MARKET_BEHAVIOR_FOK_BUY");
                return 0;
            }
        } catch (Exception e) {
            logger.error(String.format(
                    "FOK買單異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_FOK_BUY");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_FOK_BUY");

            return 0;
        }
    }

    /**
     * 提交FOK賣單操作：先檢查持股，再實際提交FOK單
     *
     * @param orderBook 訂單簿
     * @param orderPrice 訂單價格
     * @param orderVolume 訂單量
     * @return 實際成功賣出股數 (0 表示失敗)
     */
    private int FOK賣單操作(OrderBook orderBook, double orderPrice, int orderVolume) {
        try {
            logger.debug(String.format(
                    "開始FOK賣單操作：價格=%.2f, 數量=%d",
                    orderPrice, orderVolume
            ), "MARKET_BEHAVIOR_FOK_SELL");

            int hold = account.getStockInventory();

            if (hold < orderVolume) {
                logger.warn(String.format(
                        "FOK賣單持股不足：需要=%d, 可用=%d, 價格=%.2f",
                        orderVolume, hold, orderPrice
                ), "MARKET_BEHAVIOR_FOK_SELL");
                return 0;
            }

            // 使用 submitFokSellOrder 方法提交FOK賣單
            logger.debug(String.format(
                    "提交FOK賣單：價格=%.2f, 數量=%d",
                    orderPrice, orderVolume
            ), "MARKET_BEHAVIOR_FOK_SELL");

            boolean success = orderBook.submitFokSellOrder(orderPrice, orderVolume, this);

            if (success) {
                logger.info(String.format(
                        "FOK賣單成功：數量=%d, 價格=%.2f, 預計收入=%.2f",
                        orderVolume, orderPrice, orderPrice * orderVolume
                ), "MARKET_BEHAVIOR_FOK_SELL");
                return orderVolume;
            } else {
                logger.warn(String.format(
                        "FOK賣單失敗：無法完全滿足，數量=%d, 價格=%.2f",
                        orderVolume, orderPrice
                ), "MARKET_BEHAVIOR_FOK_SELL");
                return 0;
            }
        } catch (Exception e) {
            logger.error(String.format(
                    "FOK賣單異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_FOK_SELL");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_FOK_SELL");

            return 0;
        }
    }

    // ======== 計算影響因子與輔助函數 ========
    /**
     * 計算基於訂單簿價格層級的價格調整因子
     */
    private double calculatePriceLevelImpact(OrderBook orderBook) {
        try {
            logger.debug("開始計算價格層級影響", "MARKET_BEHAVIOR_ANALYSIS");

            double impact = 0.0;
            int levels = 5; // 前五個價格層級

            List<Order> topBuyOrders = orderBook.getTopBuyOrders(levels);
            List<Order> topSellOrders = orderBook.getTopSellOrders(levels);

            logger.debug(String.format(
                    "價格層級分析：取得買單=%d個, 賣單=%d個",
                    topBuyOrders.size(), topSellOrders.size()
            ), "MARKET_BEHAVIOR_ANALYSIS");

            double buySupport = 0.0;
            double sellResistance = 0.0;

            for (int i = 0; i < topBuyOrders.size(); i++) {
                Order buyOrder = topBuyOrders.get(i);
                buySupport += buyOrder.getVolume() * buyOrder.getPrice() / Math.pow(1.05, i);
            }

            for (int i = 0; i < topSellOrders.size(); i++) {
                Order sellOrder = topSellOrders.get(i);
                sellResistance += sellOrder.getVolume() * sellOrder.getPrice() / Math.pow(1.05, i);
            }

            logger.debug(String.format(
                    "價格層級支撐阻力：買單支撐=%.2f, 賣單阻力=%.2f",
                    buySupport, sellResistance
            ), "MARKET_BEHAVIOR_ANALYSIS");

            if (buySupport + sellResistance == 0) {
                logger.debug("價格層級影響：買賣總量為零，返回0", "MARKET_BEHAVIOR_ANALYSIS");
                return 0.0;
            }
            impact = (buySupport - sellResistance) / (buySupport + sellResistance);

            logger.debug(String.format(
                    "價格層級影響結果：%.4f",
                    impact
            ), "MARKET_BEHAVIOR_ANALYSIS");
            return impact;
        } catch (Exception e) {
            logger.error(String.format(
                    "計算價格層級影響異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_ANALYSIS");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_ANALYSIS");

            return 0.0;
        }
    }

    /**
     * 計算買賣力量的不平衡 (前10個買單 / 賣單)
     */
    private double calculateOrderImbalance(OrderBook orderBook) {
        try {
            logger.debug(String.format(
                    "開始計算訂單不平衡"
            ), "MARKET_BEHAVIOR_ANALYSIS");

            double buyVolume = 0.0;
            double sellVolume = 0.0;

            List<Order> topBuys = orderBook.getTopBuyOrders(10);
            List<Order> topSells = orderBook.getTopSellOrders(10);

            logger.debug(String.format(
                    "訂單不平衡分析：取得買單=%d個, 賣單=%d個",
                    topBuys.size(), topSells.size()
            ), "MARKET_BEHAVIOR_ANALYSIS");

            for (Order b : topBuys) {
                buyVolume += b.getVolume() * b.getPrice();
            }
            for (Order s : topSells) {
                sellVolume += s.getVolume() * s.getPrice();
            }

            logger.debug(String.format(
                    "訂單不平衡總量：買單總量=%.2f, 賣單總量=%.2f",
                    buyVolume, sellVolume
            ), "MARKET_BEHAVIOR_ANALYSIS");

            if (buyVolume + sellVolume == 0) {
                logger.debug(String.format(
                        "訂單不平衡計算：買賣總量為零，返回0"
                ), "MARKET_BEHAVIOR_ANALYSIS");
                return 0.0;
            }

            double imbalance = (buyVolume - sellVolume) / (buyVolume + sellVolume);

            logger.debug(String.format(
                    "訂單不平衡結果：%.4f",
                    imbalance
            ), "MARKET_BEHAVIOR_ANALYSIS");
            return imbalance;
        } catch (Exception e) {
            logger.error(String.format(
                    "計算訂單不平衡異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_ANALYSIS");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_ANALYSIS");

            return 0.0;
        }
    }

    /**
     * 大單影響：若有單筆交易量超過 threshold，就調整價格變動
     */
    private double calculateLargeOrderImpact(OrderBook orderBook) {
        try {
            if (orderBook == null) {
                return 0.0;
            }
            double impact = 0.0;
            int largeOrderThreshold = 1000;

            // 檢查前5個買單
            List<Order> topBuys = orderBook.getTopBuyOrders(5);
            if (topBuys != null) {
                for (Order b : topBuys) {
                    if (b != null && b.getVolume() >= largeOrderThreshold) {
                        impact += 0.005;
                    }
                }
            }
            // 檢查前5個賣單
            List<Order> topSells = orderBook.getTopSellOrders(5);
            if (topSells != null) {
                for (Order s : topSells) {
                    if (s != null && s.getVolume() >= largeOrderThreshold) {
                        impact -= 0.005;
                    }
                }
            }
            return impact;
        } catch (Exception e) {
            logger.error("計算大單影響異常：" + e.getMessage(), "MARKET_BEHAVIOR_ANALYSIS");
            return 0.0;
        }
    }

    /**
     * 計算訂單量
     */
    private int calculateOrderVolume(double volatility, int recentVolume) {
        try {
            logger.debug(String.format(
                    "開始計算訂單量：波動率=%.4f, 近期成交量=%d",
                    volatility, recentVolume
            ), "MARKET_BEHAVIOR_VOLUME");

            // 基於波動性與近期成交量
            int baseVolume = 1000;
            int calculatedVolume = (int) (baseVolume * (1 + volatility * 0.1) + recentVolume * 0.01);
            int finalVolume = Math.max(calculatedVolume, 100);

            logger.debug(String.format(
                    "訂單量計算結果：基礎量=%d, 計算量=%d, 最終量=%d",
                    baseVolume, calculatedVolume, finalVolume
            ), "MARKET_BEHAVIOR_VOLUME");

            return finalVolume;
        } catch (Exception e) {
            logger.error(String.format(
                    "計算訂單量異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_VOLUME");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_VOLUME");

            return 100; // 默認最小訂單量
        }
    }

    /**
     * 不同時間對波動性的影響 (如開盤收盤較大)
     */
    private double getTimeVolatilityFactor() {
        try {
            int minutesInDay = 240;
            int currentMinute = timeStep % minutesInDay;

            double factor = (currentMinute < 30 || currentMinute > 210) ? 1.5 : 1.0;

            logger.debug(String.format(
                    "時間波動因子計算：時間步=%d, 模擬分鐘=%d, 計算因子=%.2f",
                    timeStep, currentMinute, factor
            ), "MARKET_BEHAVIOR_TIME");

            return factor;
        } catch (Exception e) {
            logger.error(String.format(
                    "計算時間波動因子異常：%s",
                    e.getMessage()
            ), "MARKET_BEHAVIOR_TIME");

            // 詳細的異常堆棧
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString(), "MARKET_BEHAVIOR_TIME");

            return 1.0; // 默認因子
        }
    }

    // ======== 取得餘額 / 持股數 ========
    public double getAvailableFunds() {
        return account.getAvailableFunds();
    }

    public int getStockInventory() {
        return account.getStockInventory();
    }

    // Setter，讓外部隨時可調
    public void setBuyPriceOffsetRatio(double ratio) {
        this.buyPriceOffsetRatio = ratio;
    }

    public void setSellPriceOffsetRatio(double ratio) {
        this.sellPriceOffsetRatio = ratio;
    }
}
