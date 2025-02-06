package Analysis;

import Core.Order;
import Core.Trader;
import Core.OrderBook;
import Core.Stock;
import StockMainAction.StockMarketSimulation;
import UserManagement.UserAccount;
import java.util.List;
import java.util.Random;
import java.util.ListIterator;

/**
 * 市場波動 - 負責模擬智能的市場波動。根據市場條件動態生成訂單，並提交給訂單簿。實現 Trader 接口，作為市場的一個參與者。
 */
public class MarketBehavior implements Trader {

    private Random random = new Random();
    private double marketTrend = 0.0; // 市場趨勢，正值表示牛市，負值表示熊市
    private double longTermMeanPrice; // 長期平均價格
    private int timeStep = 0; // 時間步長，用於模擬時間因素
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
        this.account = new UserAccount(initialFunds, initialStocks); // 設定初始資金
        this.simulation = simulation; // 設定模擬實例

        // 輸出帳戶創建信息
        if (this.account != null) {
            System.out.println("MarketBehavior UserAccount created successfully with initial funds: " + initialFunds);
        } else {
            System.out.println("Error: MarketBehavior UserAccount creation failed.");
        }
    }

    /**
     * Trader 接口實現
     */
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
        return "MarketBehavior";
    }

    /**
     * 更新交易者在交易後的帳戶狀態
     *
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if (type.equals("buy")) {
            // 限價單買入：增加股數
            account.incrementStocks(volume);
        } else if (type.equals("sell")) {
            // 現價賣出：增加現金
            account.incrementFunds(transactionAmount);
        }

        // 您可以根據需求更新界面或其他狀態
    }

    /**
     * 更新交易者在交易後的帳戶狀態 因市價單不會經過訂單簿，故使用此函數計算平均價格
     *
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    public void updateAverageCostPrice(String type, int volume, double price) {
    }

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

        // 2. 考慮均值回歸，調整幅度更小
        double meanReversionSpeed = 0.005;
        double meanReversion = meanReversionSpeed * (longTermMeanPrice - currentPrice);

        // 3. 加入時間因素（例如，開盤和收盤時段波動性更大）
        double timeVolatilityFactor = getTimeVolatilityFactor();

        // 4. 模擬突發事件
        double eventImpact = 0.0;
        if (random.nextDouble() < 0.01) { // 1% 機率發生突發事件
            eventImpact = currentPrice * (0.02 * (random.nextDouble() - 0.5));
        }

        // 5. 計算價格變動百分比，並限制範圍
        double priceChangeRatio = drift + shock + meanReversion + eventImpact;
        priceChangeRatio *= timeVolatilityFactor;

        // 6. 根據訂單簿的買賣不平衡調整價格變動
        double orderImbalance = calculateOrderImbalance(orderBook); // 新增
        priceChangeRatio += orderImbalance * 0.005; // 調整係數可以根據需求調整

        // 7. 根據價格層級調整價格變動
        double priceLevelImpact = calculatePriceLevelImpact(orderBook); // 新增
        priceChangeRatio += priceLevelImpact * 0.005; // 調整係數可以根據需求調整

        // 8. 處理大單的影響
        double largeOrderImpact = calculateLargeOrderImpact(orderBook); // 新增
        priceChangeRatio += largeOrderImpact; // 大單影響直接加到價格變動比例

        // 9. 根據成交量調整波動性
        double volumeImpact = (recentVolume > 1000) ? 0.002 : (recentVolume < 100) ? -0.002 : 0.0; // 範例：高成交量增加波動，低成交量減少
        priceChangeRatio += volumeImpact;

        // 10. 計算新的訂單價格
        double newOrderPrice = currentPrice * (1 + priceChangeRatio);
        double minPrice = currentPrice * 0.9;
        double maxPrice = currentPrice * 1.1;
        newOrderPrice = Math.max(minPrice, Math.min(newOrderPrice, maxPrice));

        // 防止價格為負
        newOrderPrice = Math.max(newOrderPrice, 0.1);

        // 11. 增加隨機浮動
        newOrderPrice += newOrderPrice * (random.nextDouble() - 0.5) * 0.01; // 1% 隨機浮動

        // 12. 根據價格變動決定訂單方向和數量(掛單操作)
        int orderVolume = calculateOrderVolume(volatility, recentVolume);
        if (priceChangeRatio > 0) {
            // 檢查是否有足夠的資金來執行買單
            double totalCost = newOrderPrice * orderVolume;
            // 創建並提交買單
            Order buyOrder = new Order("buy", newOrderPrice, orderVolume, this, false, false);
            orderBook.submitBuyOrder(buyOrder, newOrderPrice);
            // System.out.println("MarketBehavior - 生成買單：價格 " + newOrderPrice + "，數量 " + orderVolume);
        } else {
            // 獲取當前可用的庫存量
            int availableStocks = account.getStockInventory();

            // 設置賣單數量為實際可用的庫存和期望賣單量的最小值
            int sellVolume = Math.min(orderVolume, availableStocks);

            // 檢查是否有足夠的庫存來執行賣單
            if (sellVolume > 0) {
                Order sellOrder = new Order("sell", newOrderPrice, sellVolume, this, false, false);
                orderBook.submitSellOrder(sellOrder, newOrderPrice);
                // System.out.println("MarketBehavior - 生成賣單：價格 " + newOrderPrice + "，數量 " + sellVolume);
            } else {
                // System.out.println("MarketBehavior - 股票不足，無法生成市場賣單");
            }
        }

        // 更新長期平均價格
        longTermMeanPrice = (longTermMeanPrice * (timeStep - 1) + currentPrice) / timeStep;

        // 更新市場趨勢（隨機微調）
        marketTrend += (random.nextDouble() - 0.5) * 0.01;
        marketTrend = Math.max(-0.5, Math.min(marketTrend, 0.5));

        // 更新 Stock 的價格
        stock.setPrice(newOrderPrice);

        // 將價格變動傳遞給 MarketAnalyzer
        simulation.getMarketAnalyzer().addPrice(newOrderPrice);
    }

    /**
     * 計算基於訂單簿價格層級的價格調整因子
     *
     * @param orderBook 訂單簿實例
     * @return 基於價格層級的調整因子
     */
    private double calculatePriceLevelImpact(OrderBook orderBook) {
        double impact = 0.0;

        // 考慮前5個價格層級
        int levels = 5;

        List<Order> topBuyOrders = orderBook.getTopBuyOrders(levels);
        List<Order> topSellOrders = orderBook.getTopSellOrders(levels);

        double buySupport = 0.0;
        double sellResistance = 0.0;

        for (int i = 0; i < topBuyOrders.size(); i++) {
            Order buyOrder = topBuyOrders.get(i);
            buySupport += buyOrder.getVolume() * buyOrder.getPrice() / Math.pow(1.05, i); // 給予更高價格更高權重
        }

        for (int i = 0; i < topSellOrders.size(); i++) {
            Order sellOrder = topSellOrders.get(i);
            sellResistance += sellOrder.getVolume() * sellOrder.getPrice() / Math.pow(1.05, i); // 給予更低價格更高權重
        }

        if (buySupport + sellResistance == 0) {
            return 0.0;
        }

        // 計算支持與阻力的差值比例
        impact = (buySupport - sellResistance) / (buySupport + sellResistance);

        return impact;
    }

    /**
     * 計算買賣力量的不平衡
     *
     * @param orderBook 訂單簿實例
     * @return 買賣力量不平衡值（正值表示買方力量強，負值表示賣方力量強）
     */
    private double calculateOrderImbalance(OrderBook orderBook) {
        double buyVolume = 0.0;
        double sellVolume = 0.0;

        // 計算買單總量
        for (Order buyOrder : orderBook.getTopBuyOrders(10)) { // 考慮前10個買單
            buyVolume += buyOrder.getVolume() * buyOrder.getPrice();
        }

        // 計算賣單總量
        for (Order sellOrder : orderBook.getTopSellOrders(10)) { // 考慮前10個賣單
            sellVolume += sellOrder.getVolume() * sellOrder.getPrice();
        }

        // 計算不平衡
        if (buyVolume + sellVolume == 0) {
            return 0.0;
        }
        return (buyVolume - sellVolume) / (buyVolume + sellVolume);
    }

    /**
     * 識別並計算大單對價格的影響
     *
     * @param orderBook 訂單簿實例
     * @return 大單影響因子
     */
    private double calculateLargeOrderImpact(OrderBook orderBook) {
        double largeOrderImpact = 0.0;
        int largeOrderThreshold = 1000; // 定義大單的閾值，根據實際情況調整

        // 檢查前5個買單
        List<Order> topBuyOrders = orderBook.getTopBuyOrders(5);
        for (Order buyOrder : topBuyOrders) {
            if (buyOrder.getVolume() >= largeOrderThreshold) {
                largeOrderImpact += 0.005; // 每個大單增加0.5%的價格影響
            }
        }

        // 檢查前5個賣單
        List<Order> topSellOrders = orderBook.getTopSellOrders(5);
        for (Order sellOrder : topSellOrders) {
            if (sellOrder.getVolume() >= largeOrderThreshold) {
                largeOrderImpact -= 0.005; // 每個大單減少0.5%的價格影響
            }
        }

        return largeOrderImpact;
    }

    /**
     * 計算訂單量
     *
     * @param volatility 波動性
     * @param recentVolume 最近成交量
     * @return 訂單量
     */
    private int calculateOrderVolume(double volatility, int recentVolume) {
        // 基於波動性和最近成交量計算訂單量
        // 這裡僅作為範例，具體算法可以根據需求調整
        int baseVolume = 1000;
        int volume = (int) (baseVolume * (1 + volatility * 0.1) + recentVolume * 0.01);
        return Math.max(volume, 100); // 最小訂單量為10
    }

    /**
     * 獲取時間波動性因子
     *
     * @return 時間波動性因子
     */
    private double getTimeVolatilityFactor() {
        // 假設一天有 240 個時間步長（例如，股票市場的分鐘數）
        int minutesInDay = 240;
        int currentMinute = timeStep % minutesInDay;

        // 開盤和收盤時段波動性較大
        if (currentMinute < 30 || currentMinute > 210) {
            return 1.5; // 波動性增加 50%
        } else {
            return 1.0; // 正常波動性
        }
    }

    /**
     * 獲取可用資金
     *
     * @return 可用資金
     */
    public double getAvailableFunds() {
        return account.getAvailableFunds();
    }

    /**
     * 獲取數量
     *
     * @return 股票數量
     */
    public int getStockInventory() {
        return account.getStockInventory();
    }
}
