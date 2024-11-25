package StockMainAction;

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

        // 6. 計算新的訂單價格
        double newOrderPrice = currentPrice * (1 + priceChangeRatio);
        double minPrice = currentPrice * 0.9;
        double maxPrice = currentPrice * 1.1;
        newOrderPrice = Math.max(minPrice, Math.min(newOrderPrice, maxPrice));

        // 防止價格為負
        newOrderPrice = Math.max(newOrderPrice, 0.1);

        // 7. 增加隨機浮動
        newOrderPrice += newOrderPrice * (random.nextDouble() - 0.5) * 0.01; // 1% 隨機浮動

        // 8. 根據價格變動決定訂單方向和數量
        int orderVolume = calculateOrderVolume(volatility, recentVolume);
        if (priceChangeRatio > 0) {
            // 檢查是否有足夠的資金來執行買單
            double totalCost = newOrderPrice * orderVolume;
            // 創建並提交買單
            Order buyOrder = new Order("buy", newOrderPrice, orderVolume, this, false, false);
            orderBook.submitBuyOrder(buyOrder, newOrderPrice);
//            System.out.println("MarketBehavior - 生成買單：價格 " + newOrderPrice + "，數量 " + orderVolume);
        } else {
            // 獲取當前可用的庫存量
            int availableStocks = account.getStockInventory();

            // 設置賣單數量為實際可用的庫存和期望賣單量的最小值
            int sellVolume = Math.min(orderVolume, availableStocks);

            // 檢查是否有足夠的庫存來執行賣單
            if (sellVolume > 0) {
                Order sellOrder = new Order("sell", newOrderPrice, sellVolume, this, false, false);
                orderBook.submitSellOrder(sellOrder, newOrderPrice);
//                System.out.println("MarketBehavior - 生成賣單：價格 " + newOrderPrice + "，數量 " + sellVolume);
            } else {
//                System.out.println("MarketBehavior - 股票不足，無法生成市場賣單");
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
     * 計算訂單數量
     *
     * @param volatility 波動性
     * @param recentVolume 最近成交量
     * @return 訂單數量
     */
    private int calculateOrderVolume(double volatility, int recentVolume) {
        int baseVolume = (int) (recentVolume * (0.5 + random.nextDouble())); // 基於最近成交量
        int volatilityAdjustment = (int) (volatility * 1000); // 波動性調整
        return Math.max(1, baseVolume + volatilityAdjustment);
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
