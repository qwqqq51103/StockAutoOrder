package Analysis;

import Core.Order;
import Core.Trader;
import Core.OrderBook;
import Core.Stock;
import StockMainAction.StockMarketSimulation;
import UserManagement.UserAccount;
import java.util.List;
import java.util.Random;

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
     * @param initialStocks 初始股票數量
     * @param simulation 引用 StockMarketSimulation 實例
     */
    public MarketBehavior(double initialPrice, double initialFunds, int initialStocks, StockMarketSimulation simulation) {
        this.longTermMeanPrice = initialPrice;
        this.account = new UserAccount(initialFunds, initialStocks);
        this.simulation = simulation;

        if (this.account != null) {
            System.out.println("【市場行為】UserAccount 建立成功，初始資金: " + initialFunds + "，初始持股: " + initialStocks);
        } else {
            System.out.println("【市場行為】UserAccount 建立失敗。");
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
            // 限價單買入：增加股數
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
        timeStep++;

        double currentPrice = stock.getPrice();

        // 1-9. 保持原有的市場計算邏輯不變...
        // 1. 計算市場趨勢和波動性
        double drift = marketTrend * 0.01;
        double shock = volatility * random.nextGaussian();

        // 2. 均值回歸
        double meanReversionSpeed = 0.005;
        double meanReversion = meanReversionSpeed * (longTermMeanPrice - currentPrice);

        // 3. 加入時間因素（開盤/收盤波動較大）
        double timeVolatilityFactor = getTimeVolatilityFactor();

        // 4. 突發事件
        double eventImpact = 0.0;
        if (random.nextDouble() < 0.01) {
            eventImpact = currentPrice * (0.02 * (random.nextDouble() - 0.5));
        }

        // 5. 計算價格變動
        double priceChangeRatio = drift + shock + meanReversion + eventImpact;
        priceChangeRatio *= timeVolatilityFactor;

        // 6. 訂單簿買賣不平衡
        double orderImbalance = calculateOrderImbalance(orderBook);
        priceChangeRatio += orderImbalance * 0.005;

        // === A. 技術面 ===
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double smaScore = Double.isNaN(sma) ? 0
                : (currentPrice - sma) / sma; // 正值 → 價格高於均線

        // === B. 訂單簿面 ===
        double imbalanceScore = orderImbalance; // 已是 -1 ~ +1 之間

        // === C. 動能面 ===
        double momentumScore = priceChangeRatio; // -0.x ~ +0.x

        // === D. 量能面 ===
        double avgVol = simulation.getMarketAnalyzer().getRecentAverageVolume();
        double volumeScore = (avgVol == 0) ? 0
                : (recentVolume - avgVol) / avgVol; // 放大 >0、縮量 <0

        // === E. 加權總分 ===
        double sentimentScore
                = smaScore * smaWeight
                + imbalanceScore * imbalanceWeight
                + momentumScore * momentumWeight
                + volumeScore * volumeWeight;

        // 7. 價格層級影響
        double priceLevelImpact = calculatePriceLevelImpact(orderBook);
        priceChangeRatio += priceLevelImpact * 0.005;

        // 8. 大單影響
        double largeOrderImpact = calculateLargeOrderImpact(orderBook);
        priceChangeRatio += largeOrderImpact;

        // 9. 成交量影響
        double volumeImpact = (recentVolume > 1000) ? 0.002 : (recentVolume < 100) ? -0.002 : 0.0;
        priceChangeRatio += volumeImpact;

        // 10. 新訂單價格
        double newOrderPrice = currentPrice * (1 + priceChangeRatio);
        double minPrice = currentPrice * 0.9;
        double maxPrice = currentPrice * 1.1;
        newOrderPrice = Math.max(minPrice, Math.min(newOrderPrice, maxPrice));
        newOrderPrice = Math.max(newOrderPrice, 0.1);

        // 11. 隨機浮動
        newOrderPrice += newOrderPrice * (random.nextDouble() - 0.5) * 0.01;

        // 12. 決定訂單量，並決定要下買單或賣單
        int orderVolume = calculateOrderVolume(volatility, recentVolume);

        // 修改：添加主動交易防抖機制，避免在短時間內多次下單
        long currentTime = System.currentTimeMillis();
        boolean shouldPlaceOrder = true;

        // 如果距離上次下單時間太短，不進行交易
        if (currentTime - lastOrderTime < ORDER_COOLDOWN_MS) {
            shouldPlaceOrder = false;
        }

        // 只有在通過防抖檢查後才執行下單操作
        if (shouldPlaceOrder) {
            if (sentimentScore >= bullishThreshold) {
                // -------- 看多：掛買單 --------
                double customBuyPrice = currentPrice * (1 + buyPriceOffsetRatio);
                customBuyPrice = orderBook.adjustPriceToUnit(customBuyPrice);

                // 檢查資金是否足夠
                double requiredFunds = customBuyPrice * orderVolume;
                if (account.getAvailableFunds() >= requiredFunds) {
                    // 使用改進的下單方法
                    Order buyOrder = Order.createLimitBuyOrder(customBuyPrice, orderVolume, this);
                    orderBook.submitBuyOrder(buyOrder, customBuyPrice);

                    // 記錄此次下單時間
                    lastOrderTime = currentTime;

                    System.out.println(String.format("市場波動 - 看多下單：掛買單 %d 股 @ %.2f",
                            orderVolume, customBuyPrice));
                }
            } else if (sentimentScore <= bearishThreshold) {
                // -------- 看空：掛賣單 --------
                double customSellPrice = currentPrice * (1 + sellPriceOffsetRatio);
                customSellPrice = orderBook.adjustPriceToUnit(customSellPrice);

                int availableStocks = account.getStockInventory();
                int sellVolume = Math.min(orderVolume, availableStocks);

                if (sellVolume > 0) {
                    // 使用改進的下單方法
                    Order sellOrder = Order.createLimitSellOrder(customSellPrice, sellVolume, this);
                    orderBook.submitSellOrder(sellOrder, customSellPrice);

                    // 記錄此次下單時間
                    lastOrderTime = currentTime;

                    System.out.println(String.format("市場波動 - 看空下單：掛賣單 %d 股 @ %.2f",
                            sellVolume, customSellPrice));
                }
            } else {
                // -------- 中性：不掛單或掛小量探盤 --------
                // 可以選擇性地實現小量探盤邏輯
            }
        }

        // 更新長期平均價格
        longTermMeanPrice = (longTermMeanPrice * (timeStep - 1) + currentPrice) / timeStep;

        // 更新市場趨勢
        marketTrend = 0.95 * marketTrend + (random.nextDouble() - 0.5) * 0.01;
        marketTrend = Math.max(-0.5, Math.min(marketTrend, 0.5));

        // 最後更新 Stock 的價格
        stock.setPrice(newOrderPrice);
        // 傳遞到 MarketAnalyzer
        simulation.getMarketAnalyzer().addPrice(newOrderPrice);
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
        double cost = orderPrice * orderVolume;
        double funds = account.getAvailableFunds();
        if (funds < cost) {
            //System.out.println(String.format("【市場行為-買單】失敗：可用資金不足，嘗試買 %d 股(價格 %.2f)，需要 %.2f，剩餘資金 %.2f",
            //        orderVolume, orderPrice, cost, funds));
            return 0;
        }

        // 使用工廠方法創建限價買單
        Order buyOrder = Order.createLimitBuyOrder(orderPrice, orderVolume, this);
        orderBook.submitBuyOrder(buyOrder, orderPrice);

        //System.out.println(String.format("【市場行為-買單】成功：掛買單 %d 股，價格 %.2f，預計成本 %.2f",
        //        orderVolume, orderPrice, cost));
        return orderVolume;
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
        int hold = account.getStockInventory();
        if (hold < orderVolume) {
            //System.out.println(String.format("【市場行為-賣單】失敗：持股不足，想賣 %d 股，實際僅有 %d 股",
            //        orderVolume, hold));
            return 0;
        }

        // 使用工廠方法創建限價賣單
        Order sellOrder = Order.createLimitSellOrder(orderPrice, orderVolume, this);
        orderBook.submitSellOrder(sellOrder, orderPrice);

        //System.out.println(String.format("【市場行為-賣單】成功：掛賣單 %d 股，價格 %.2f",
        //        orderVolume, orderPrice));
        return orderVolume;
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
        // 以估算價格檢查資金是否足夠
        double estimatedCost = estimatedPrice * orderVolume;
        double funds = account.getAvailableFunds();
        if (funds < estimatedCost) {
            //System.out.println(String.format("【市場行為-市價買單】失敗：可用資金不足，嘗試買 %d 股(估價 %.2f)，需要估計 %.2f，剩餘資金 %.2f",
            //        orderVolume, estimatedPrice, estimatedCost, funds));
            return 0;
        }

        // 直接使用 marketBuy 方法執行市價單
        orderBook.marketBuy(this, orderVolume);

        //System.out.println(String.format("【市場行為-市價買單】成功：提交市價買單 %d 股，估計成本 %.2f",
        //        orderVolume, estimatedCost));
        return orderVolume;
    }

    /**
     * 提交市價賣單操作：先檢查持股，再實際提交市價單
     *
     * @param orderBook 訂單簿
     * @param orderVolume 訂單量
     * @return 實際成功賣出股數 (0 表示失敗)
     */
    private int 市價賣單操作(OrderBook orderBook, int orderVolume) {
        int hold = account.getStockInventory();
        if (hold < orderVolume) {
            //System.out.println(String.format("【市場行為-市價賣單】失敗：持股不足，想賣 %d 股，實際僅有 %d 股",
            //        orderVolume, hold));
            return 0;
        }

        // 直接使用 marketSell 方法執行市價單
        orderBook.marketSell(this, orderVolume);

        //System.out.println(String.format("【市場行為-市價賣單】成功：提交市價賣單 %d 股",
        //        orderVolume));
        return orderVolume;
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
        double cost = orderPrice * orderVolume;
        double funds = account.getAvailableFunds();
        if (funds < cost) {
            //System.out.println(String.format("【市場行為-FOK買單】失敗：可用資金不足，嘗試買 %d 股(價格 %.2f)，需要 %.2f，剩餘資金 %.2f",
            //        orderVolume, orderPrice, cost, funds));
            return 0;
        }

        // 使用 submitFokBuyOrder 方法提交FOK買單
        boolean success = orderBook.submitFokBuyOrder(orderPrice, orderVolume, this);

        if (success) {
            //System.out.println(String.format("【市場行為-FOK買單】成功：提交FOK買單 %d 股，價格 %.2f，預計成本 %.2f",
            //        orderVolume, orderPrice, cost));
            return orderVolume;
        } else {
            //System.out.println(String.format("【市場行為-FOK買單】失敗：無法完全滿足，嘗試買 %d 股，價格 %.2f",
            //        orderVolume, orderPrice));
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
        int hold = account.getStockInventory();
        if (hold < orderVolume) {
            //System.out.println(String.format("【市場行為-FOK賣單】失敗：持股不足，想賣 %d 股，實際僅有 %d 股",
            //        orderVolume, hold));
            return 0;
        }

        // 使用 submitFokSellOrder 方法提交FOK賣單
        boolean success = orderBook.submitFokSellOrder(orderPrice, orderVolume, this);

        if (success) {
            //System.out.println(String.format("【市場行為-FOK賣單】成功：提交FOK賣單 %d 股，價格 %.2f",
            //        orderVolume, orderPrice));
            return orderVolume;
        } else {
            //System.out.println(String.format("【市場行為-FOK賣單】失敗：無法完全滿足，嘗試賣 %d 股，價格 %.2f",
            //        orderVolume, orderPrice));
            return 0;
        }
    }

    // ======== 計算影響因子與輔助函數 ========
    /**
     * 計算基於訂單簿價格層級的價格調整因子
     */
    private double calculatePriceLevelImpact(OrderBook orderBook) {
        double impact = 0.0;
        int levels = 5; // 前五個價格層級

        List<Order> topBuyOrders = orderBook.getTopBuyOrders(levels);
        List<Order> topSellOrders = orderBook.getTopSellOrders(levels);

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

        if (buySupport + sellResistance == 0) {
            return 0.0;
        }
        impact = (buySupport - sellResistance) / (buySupport + sellResistance);
        return impact;
    }

    /**
     * 計算買賣力量的不平衡 (前10個買單 / 賣單)
     */
    private double calculateOrderImbalance(OrderBook orderBook) {
        double buyVolume = 0.0;
        double sellVolume = 0.0;

        List<Order> topBuys = orderBook.getTopBuyOrders(10);
        List<Order> topSells = orderBook.getTopSellOrders(10);

        for (Order b : topBuys) {
            buyVolume += b.getVolume() * b.getPrice();
        }
        for (Order s : topSells) {
            sellVolume += s.getVolume() * s.getPrice();
        }

        if (buyVolume + sellVolume == 0) {
            return 0.0;
        }
        return (buyVolume - sellVolume) / (buyVolume + sellVolume);
    }

    /**
     * 大單影響：若有單筆交易量超過 threshold，就調整價格變動
     */
    private double calculateLargeOrderImpact(OrderBook orderBook) {
        double impact = 0.0;
        int largeOrderThreshold = 1000;

        // 檢查前5個買單
        List<Order> topBuys = orderBook.getTopBuyOrders(5);
        for (Order b : topBuys) {
            if (b.getVolume() >= largeOrderThreshold) {
                impact += 0.005;
            }
        }
        // 檢查前5個賣單
        List<Order> topSells = orderBook.getTopSellOrders(5);
        for (Order s : topSells) {
            if (s.getVolume() >= largeOrderThreshold) {
                impact -= 0.005;
            }
        }
        return impact;
    }

    /**
     * 計算訂單量
     */
    private int calculateOrderVolume(double volatility, int recentVolume) {
        // 基於波動性與近期成交量
        int baseVolume = 1000;
        int volume = (int) (baseVolume * (1 + volatility * 0.1) + recentVolume * 0.01);
        return Math.max(volume, 100);
    }

    /**
     * 不同時間對波動性的影響 (如開盤收盤較大)
     */
    private double getTimeVolatilityFactor() {
        int minutesInDay = 240;
        int currentMinute = timeStep % minutesInDay;

        if (currentMinute < 30 || currentMinute > 210) {
            return 1.5;
        }
        return 1.0;
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
