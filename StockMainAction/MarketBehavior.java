package StockMainAction;

import java.util.Random;

/**
 * 市場波動 - 負責模擬智能的市場波動。 根據市場條件動態生成訂單，並提交給訂單簿。
 */
public class MarketBehavior {

    private Random random = new Random();
    private double marketTrend = 0.0; // 市場趨勢，正值表示牛市，負值表示熊市
    private double longTermMeanPrice; // 長期平均價格
    private int timeStep = 0; // 時間步長，用於模擬時間因素
    private UserAccount account;

    public MarketBehavior(double initialPrice, double initialFunds, int initialStocks) {
        this.longTermMeanPrice = initialPrice;
        this.account = new UserAccount(initialFunds, initialStocks); // 設定初始資金

        // 輸出帳戶創建信息
        if (this.account != null) {
            System.out.println("UserAccount created successfully with initial funds: " + initialFunds);
        } else {
            System.out.println("Error: UserAccount creation failed.");
        }
    }

    // 模擬市場波動
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

        // 計算新的訂單價格
        double newOrderPrice = currentPrice * (1 + priceChangeRatio);
        double minPrice = currentPrice * 0.9;
        double maxPrice = currentPrice * 1.1;
        newOrderPrice = Math.max(minPrice, Math.min(newOrderPrice, maxPrice));

        // 防止價格為負
        newOrderPrice = Math.max(newOrderPrice, 0.1);

        // 6. 增加隨機浮動
        newOrderPrice += newOrderPrice * (random.nextDouble() - 0.5) * 0.01; // 1% 隨機浮動

        // 7. 根據價格變動決定訂單方向和數量
        int orderVolume = calculateOrderVolume(volatility, recentVolume);
        if (priceChangeRatio > 0) {
            // 檢查是否有足夠的資金來執行買單
            double totalCost = newOrderPrice * orderVolume;
            if (account.freezeFunds(totalCost)) {
                // 創建並提交買單
                Order buyOrder = new Order("buy", newOrderPrice, orderVolume, "市場買單", this, account, false);
                orderBook.submitBuyOrder(buyOrder, newOrderPrice);
                System.out.println("市場行為生成買單：價格 " + newOrderPrice + "，數量 " + orderVolume);
            } else {
                //System.out.println("資金不足，無法生成市場買單");
            }
        } else {
            // 獲取當前可用的庫存量
            int availableStocks = account.getStockInventory();

            // 設置賣單數量為實際可用的庫存和期望賣單量的最小值
            int sellVolume = Math.min(orderVolume, availableStocks);

            // 檢查是否有足夠的庫存來執行賣單
            if (sellVolume > 0) {
                Order sellOrder = new Order("sell", newOrderPrice, sellVolume, "市場賣單", this, account, false);
                orderBook.submitSellOrder(sellOrder, newOrderPrice);
                System.out.println("市場行為生成賣單：價格 " + newOrderPrice + "，數量 " + sellVolume);
            } else {
                //System.out.println("股票不足，無法生成市場賣單");
            }
        }

        // 更新長期平均價格
        longTermMeanPrice = (longTermMeanPrice * (timeStep - 1) + currentPrice) / timeStep;

        // 更新市場趨勢（隨機微調）
        marketTrend += (random.nextDouble() - 0.5) * 0.01;
        marketTrend = Math.max(-0.5, Math.min(marketTrend, 0.5));
    }

    // 計算訂單數量
    private int calculateOrderVolume(double volatility, int recentVolume) {
        int baseVolume = (int) (recentVolume * (0.5 + random.nextDouble())); // 基於最近成交量
        int volatilityAdjustment = (int) (volatility * 1000); // 波動性調整
        return Math.max(1, baseVolume + volatilityAdjustment);
    }

    // 獲取時間波動性因子
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

    public double getAvailableFunds() {
        return account.getAvailableFunds(); // 假設 UserAccount 有這個方法
    }

    public int getStockInventory() {
        return account.getStockInventory(); // 假設 UserAccount 有這個方法
    }

    public UserAccount getAccount() {
        return account;
    }

}
