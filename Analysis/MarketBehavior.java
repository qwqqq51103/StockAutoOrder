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

    // ======== 市場波動邏輯 ========
    /**
     * 模擬市場波動
     *
     * @param stock 股票實例
     * @param orderBook 訂單簿實例
     * @param volatility 波動性
     * @param recentVolume 最近成交量
     */
    public void marketFluctuation(Stock stock, OrderBook orderBook, double volatility, int recentVolume) {
        timeStep++;

        double currentPrice = stock.getPrice();

        // 1. 計算市場趨勢和波動性
        double drift = marketTrend * 0.0001;
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

        // 12. 計算訂單量，並決定要下買單或賣單
        int orderVolume = calculateOrderVolume(volatility, recentVolume);

        if (priceChangeRatio > 0) {
            // 嘗試以「功能函數」方式下買單
            int actualBuy = 市場買單操作(orderBook, newOrderPrice, orderVolume);
            if (actualBuy > 0) {
                // 可在此顯示成功資訊（若需要再額外印出）
            }
        } else {
            // 下賣單前檢查實際可用持股
            int availableStocks = account.getStockInventory();
            int sellVolume = Math.min(orderVolume, availableStocks);
            if (sellVolume > 0) {
                int actualSell = 市場賣單操作(orderBook, newOrderPrice, sellVolume);
                if (actualSell > 0) {
                    // 可在此顯示成功資訊（若需要再額外印出）
                }
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
     * 提交買單操作：先檢查資金，再實際掛單
     *
     * @param orderBook 訂單簿
     * @param orderPrice 訂單價格
     * @param orderVolume 訂單量
     * @return 實際成功買入股數 (0 表示失敗)
     */
    private int 市場買單操作(OrderBook orderBook, double orderPrice, int orderVolume) {
        double cost = orderPrice * orderVolume;
        double funds = account.getAvailableFunds();

        if (funds < cost) {
            System.out.println(String.format("【市場行為-買單】失敗：可用資金不足，嘗試買 %d 股(價格 %.2f)，需要 %.2f，剩餘資金 %.2f",
                    orderVolume, orderPrice, cost, funds));
            return 0;
        }

        Order buyOrder = new Order("buy", orderPrice, orderVolume, this, false, false);
        orderBook.submitBuyOrder(buyOrder, orderPrice);

        System.out.println(String.format("【市場行為-買單】成功：掛買單 %d 股，價格 %.2f，預計成本 %.2f",
                orderVolume, orderPrice, cost));
        return orderVolume;
    }

    /**
     * 提交賣單操作：先檢查持股，再實際掛單
     *
     * @param orderBook 訂單簿
     * @param orderPrice 訂單價格
     * @param orderVolume 訂單量
     * @return 實際成功賣出股數 (0 表示失敗)
     */
    private int 市場賣單操作(OrderBook orderBook, double orderPrice, int orderVolume) {
        int hold = account.getStockInventory();
        if (hold < orderVolume) {
            System.out.println(String.format("【市場行為-賣單】失敗：持股不足，想賣 %d 股，實際僅有 %d 股",
                    orderVolume, hold));
            return 0;
        }

        Order sellOrder = new Order("sell", orderPrice, orderVolume, this, false, false);
        orderBook.submitSellOrder(sellOrder, orderPrice);

        System.out.println(String.format("【市場行為-賣單】成功：掛賣單 %d 股，價格 %.2f",
                orderVolume, orderPrice));
        return orderVolume;
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
}
