package StockMainAction;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * 主力策略 - 處理主力的操作策略
 */
public class MainForceStrategyWithOrderBook {

    private OrderBook orderBook;
    private Stock stock;
    private StockMarketSimulation simulation;
    private StringBuilder tradeLog;
    private int accumulatedStocks;  // 主力持股量
    private double forceCash; // 主力現金
    private double targetPrice;     // 主力目標價位
    private double averageCostPrice; // 目前籌碼的平均成本價格
    private Random random;
    private UserAccount account;  // 主力的 UserAccount

    // 目標價的期望利潤率（例如 50%）
    private static final double EXPECTED_PROFIT_MARGIN = 0.5;
    private static final double VOLATILITY_FACTOR = 0.5;
    private double initialForceCash; // 初始現金
    private int initialAccumulatedStocks; // 初始持股量

    // 最近價格、成交量、波動性
    private Queue<Integer> recentVolumes;
    private double volatility;
    private Deque<Double> recentPrices;

    private double buyThreshold = 0.01;  // 設定新的買入門檻，例如低於 SMA 的 1%
    private double sellThreshold = 0.02; // 設定新的賣出門檻，例如高於 SMA 的 2%

    public MainForceStrategyWithOrderBook(OrderBook orderBook, Stock stock, StockMarketSimulation simulation, double initialCash) {
        this.orderBook = orderBook;
        this.stock = stock;
        this.simulation = simulation;
        this.tradeLog = new StringBuilder();
        this.accumulatedStocks = 0;
        this.forceCash = initialCash; // 設置初始現金
        this.initialForceCash = initialCash; // 保存初始現金值
        this.initialAccumulatedStocks = 0; // 初始持股量為 0
        this.random = new Random();
        this.recentVolumes = new LinkedList<>();
        this.recentPrices = new LinkedList<>();
        this.volatility = 0.0;
        this.account = new UserAccount(initialCash, initialAccumulatedStocks);
    }

    public void makeDecision() {
        double currentPrice = stock.getPrice();
        double sma = simulation.getMarketAnalyzer().calculateSMA();  // 使用 MarketAnalyzer 的 SMA
        double volatility = simulation.getMarketAnalyzer().calculateVolatility();  // 使用 MarketAnalyzer 的波動性
        String decisionReason = "";

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;

            // 限制偏差比例的範圍在 -0.5 到 0.5 之間
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

            decisionReason += String.format("當前股價：%.2f，SMA：%.2f，偏差比例：%.2f%%\n", currentPrice, sma, priceDifferenceRatio * 100);

            // 引入隨機化的門檻值
            double randomBuyThreshold = buyThreshold * (1 + (random.nextDouble() - 0.5) * 0.4);
            double randomSellThreshold = sellThreshold * (1 + (random.nextDouble() - 0.5) * 0.4);

            // 生成操作概率
            double actionProbability = random.nextDouble();

            // 決策邏輯
            if (actionProbability < 0.05 && accumulatedStocks > 0) {
                // 5% 機率進行洗盤
                int washVolume = calculateWashVolume(volatility);
                decisionReason += String.format("決定進行洗盤，賣出 %d 股。\n", washVolume);
                simulateWashTrading(washVolume);

            } else if (actionProbability < 0.1 && forceCash > currentPrice) {
                // 5% 機率進行拉抬
                int liftVolume = calculateLiftVolume();
                decisionReason += String.format("決定進行拉抬，買入 %d 股。\n", liftVolume);
                liftStock(liftVolume);

            } else if (currentPrice >= targetPrice && accumulatedStocks > 0) {
                // 賣出條件
                int sellVolume = Math.min(500, accumulatedStocks);
                decisionReason += String.format("條件：股價超過目標價 %.2f，賣出 %d 股。\n", targetPrice, sellVolume);
                sellStock(sellVolume);

            } else if (priceDifferenceRatio > randomSellThreshold && accumulatedStocks > 0 && actionProbability > 0.3) {
                // 賣出條件
                int sellVolume = (int) (accumulatedStocks * priceDifferenceRatio * (0.8 + 0.4 * random.nextDouble()));
                sellVolume = Math.max(1, Math.min(sellVolume, accumulatedStocks));

                decisionReason += String.format("條件：股價高於 SMA 的 %.2f%% 門檻（隨機化後），賣出 %d 股。\n", randomSellThreshold * 100, sellVolume);
                sellStock(sellVolume);

            } else if (priceDifferenceRatio < -randomBuyThreshold && forceCash >= currentPrice && actionProbability > 0.3) {
                // 買入條件
                int maxAffordableAmount = (int) (forceCash / currentPrice);
                int buyVolume = (int) (maxAffordableAmount * Math.abs(priceDifferenceRatio) * (0.8 + 0.4 * random.nextDouble()));
                buyVolume = Math.max(1, Math.min(buyVolume, 500));

                decisionReason += String.format("條件：股價低於 SMA 的 %.2f%% 門檻（隨機化後），買入 %d 股。\n", randomBuyThreshold * 100, buyVolume);
                accumulateStock(buyVolume);

            } else {
                decisionReason += "主力觀望，無操作。\n";
                // System.out.println(decisionReason);
            }

        } else {
            System.out.println("主力正在收集趨勢數據，無法計算 SMA。\n");
        }

        // 您可以選擇在此輸出決策原因
        System.out.println(decisionReason);
    }

    // 根據波動性調整洗盤量
    private int calculateWashVolume(double volatility) {
        return Math.max((int) (volatility * 1000), 50); // 波動性越高，洗盤量越大，最小 50 股
    }

    // 根據市場狀況計算拉抬量
    private int calculateLiftVolume() {
        return random.nextInt(500) + 100; // 隨機決定拉抬量，100 到 600 股之間
    }

    // 吸籌操作並更新平均成本價格
    public void accumulateStock(int volume) {
        double price = stock.getPrice();
        double cost = price * volume;

        // 檢查市場中是否有足夠的賣單
        int availableVolume = orderBook.getAvailableSellVolume(price); // 假設此方法返回賣單的總量
        if (availableVolume < volume) {
            System.out.println("市場賣單不足，無法完成吸籌操作");
            return;
        }

        // 檢查並凍結資金
        if (!account.freezeFunds(cost)) {
            System.out.println("主力現金不足，無法進行吸籌操作");
            return;
        }

        // 創建並提交買單
        Order buyOrder = new Order("buy", price, volume, "MainForce", this, account, false);
        orderBook.submitBuyOrder(buyOrder, price);
        System.out.println(String.format("主力下買單 %d 股，價格 %.2f，剩餘現金 %.2f 元", volume, price, forceCash));
    }

    // 計算目標價
    public double calculateTargetPrice() {
        // 基於平均成本和波動性來動態調整目標價
        targetPrice = averageCostPrice * (1 + EXPECTED_PROFIT_MARGIN + VOLATILITY_FACTOR * volatility);
        return targetPrice;
    }

    // 主力賣出操作，並考慮調整平均成本價
    public void sellStock(int volume) {
        double price = stock.getPrice();

        // 檢查市場中是否有足夠的買單
        int availableVolume = orderBook.getAvailableBuyVolume(price); // 假設此方法返回買單的總量
        if (availableVolume < volume) {
            System.out.println("市場買單不足，無法完成賣出操作");
            return;
        }

        if (accumulatedStocks >= volume) {
            Order sellOrder = new Order("sell", price, volume, "MainForce", this, account, false);
            orderBook.submitSellOrder(sellOrder, price);
            System.out.println(String.format("主力下賣單 %d 股，價格 %.2f，當前持股 %d 股", volume, price, accumulatedStocks));
        } else {
            System.out.println("主力持股不足，無法賣出\n");
        }
    }

    // 洗盤操作
    public void simulateWashTrading(int volume) {
        if (accumulatedStocks >= volume) {
            double price = stock.getPrice();
            // 創建賣單（大量賣出以壓低股價），將主力帳戶作為交易者傳遞進去
            Order sellOrder = new Order("sell", price, volume, "MainForce", this, account, false);
            orderBook.submitSellOrder(sellOrder, price);

            System.out.println(String.format("主力進行洗盤，賣出 %d 股，價格 %.2f", volume, price));
        } else {
            System.out.println("主力持股不足，無法進行洗盤\n");
        }
    }

    // 拉抬操作
    public void liftStock(int volume) {
        double price = stock.getPrice();
        double cost = price * volume;

        if (account.freezeFunds(cost)) {
            // 創建買單（大量買入以推高股價），將主力帳戶作為交易者傳遞進去
            Order buyOrder = new Order("buy", price, volume, "MainForce", this, account, true);
            orderBook.submitBuyOrder(buyOrder, price);

            System.out.println(String.format("主力進行拉抬，買入 %d 股，價格 %.2f，剩餘現金 %.2f 元", volume, price, forceCash));
        } else {
            System.out.println("主力現金不足，無法進行拉抬\n");
        }
    }

    // 在訂單成交時更新現金和持股量
    public void updateAfterTransaction(String type, int volume, double price) {
        if (type.equals("buy")) {
            forceCash -= price * volume;
            accumulatedStocks += volume;
            // 更新平均成本價
            double totalInvestment = averageCostPrice * (accumulatedStocks - volume) + price * volume;
            averageCostPrice = totalInvestment / accumulatedStocks;
            // 更新目標價
            calculateTargetPrice();
            simulation.updateLabels();
        } else if (type.equals("sell")) {
            forceCash += price * volume;
            accumulatedStocks -= volume;
            // 若持股為零，重置平均成本價
            if (accumulatedStocks == 0) {
                averageCostPrice = 0.0;
            }
            simulation.updateLabels();
        }
    }

    // 返回交易紀錄
    public String getTradeLog() {
        return tradeLog.toString();
    }

    public int getAccumulatedStocks() {
        return accumulatedStocks;
    }

    public double getCash() {
        return forceCash;
    }

    public double getTargetPrice() {
        return targetPrice;
    }

    public double getAverageCostPrice() {
        return averageCostPrice;
    }

    public void updateCash(double amount) {
        forceCash += amount;
    }
}
