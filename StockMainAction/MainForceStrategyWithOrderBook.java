package StockMainAction;

import java.util.Deque;
import java.util.Iterator;
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
    private double targetPrice;     // 主力目標價位
    private double averageCostPrice; // 目前籌碼的平均成本價格
    private Random random;
    private UserAccount account;  // 主力的 UserAccount
    private int initStock = 0;

    // 目標價的期望利潤率（例如 50%）
    private static final double EXPECTED_PROFIT_MARGIN = 0.5;
    private static final double VOLATILITY_FACTOR = 0.5;

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
        this.random = new Random();
        this.recentVolumes = new LinkedList<>();
        this.recentPrices = new LinkedList<>();
        this.volatility = 0.0;
        this.account = new UserAccount(initialCash, initStock);
    }

    //主力行為
    public void makeDecision() {
        double currentPrice = stock.getPrice();
        double availableFunds = account.getAvailableFunds(); // 使用帳戶資金
        double sma = simulation.getMarketAnalyzer().calculateSMA();  // 使用 MarketAnalyzer 的 SMA
        double volatility = simulation.getMarketAnalyzer().calculateVolatility();  // 使用 MarketAnalyzer 的波動性
        String decisionReason = "";

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;

            // 限制偏差比例的範圍在 -0.5 到 0.5 之間
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

            //decisionReason += String.format("當前股價：%.2f，SMA：%.2f，偏差比例：%.2f%%\n", currentPrice, sma, priceDifferenceRatio * 100);
            // 引入隨機化的門檻值
            double randomBuyThreshold = buyThreshold * (1 + (random.nextDouble() - 0.5) * 0.4);
            double randomSellThreshold = sellThreshold * (1 + (random.nextDouble() - 0.5) * 0.4);

            // 生成操作概率
            double actionProbability = random.nextDouble();

            // 決策邏輯
//            if (actionProbability < 0.05 && getAccumulatedStocks() > 0) {
//                // 5% 機率進行洗盤
//                int washVolume = calculateWashVolume(volatility);
//                double minimumRequiredFunds = stock.getPrice() * Math.max(washVolume, 50); // 計算進行洗盤所需的最低資金
//
//                // 檢查是否有足夠的現金進行洗盤
//                if (account.getAvailableFunds() >= minimumRequiredFunds) {
//                    //decisionReason += String.format("決定進行洗盤，賣出 %d 股。\n", washVolume);
//                    simulateWashTrading(washVolume);
//                } else {
//                    //System.out.println("主力現金不足，無法進行洗盤操作。");
//                    //decisionReason += "主力現金不足，無法進行洗盤操作。\n";
//                }
//
//            } else if (actionProbability < 0.1 && availableFunds > currentPrice) {
//                System.out.println("主力現金 : " + availableFunds);
//                // 5% 機率進行拉抬
//                int liftVolume = calculateLiftVolume();
//                //decisionReason += String.format("決定進行拉抬，買入 %d 股。\n", liftVolume);
//                liftStock(liftVolume);
//
//            } else if (currentPrice >= targetPrice && getAccumulatedStocks() > 0) {
//                // 賣出條件
//                int sellVolume = Math.min(500, getAccumulatedStocks());
//                //decisionReason += String.format("條件：股價超過目標價 %.2f，賣出 %d 股。\n", targetPrice, sellVolume);
//                sellStock(sellVolume);
//
//            } else if (priceDifferenceRatio > randomSellThreshold && getAccumulatedStocks() > 0 && actionProbability > 0.3) {
//                // 賣出條件
//                int sellVolume = (int) (getAccumulatedStocks() * priceDifferenceRatio * (0.8 + 0.4 * random.nextDouble()));
//                sellVolume = Math.max(1, Math.min(sellVolume, getAccumulatedStocks()));
//
//                //decisionReason += String.format("條件：股價高於 SMA 的 %.2f%% 門檻（隨機化後），賣出 %d 股。\n", randomSellThreshold * 100, sellVolume);
//                sellStock(sellVolume);
//
//            } else if (priceDifferenceRatio < -randomBuyThreshold && availableFunds >= currentPrice && actionProbability > 0.3) {
//                // 買入條件
//                int maxAffordableAmount = (int) (availableFunds / currentPrice);
//                int buyVolume = (int) (maxAffordableAmount * Math.abs(priceDifferenceRatio) * (0.8 + 0.4 * random.nextDouble()));
//                buyVolume = Math.max(1, Math.min(buyVolume, 500));
//
//                //decisionReason += String.format("條件：股價低於 SMA 的 %.2f%% 門檻（隨機化後），買入 %d 股。\n", randomBuyThreshold * 100, buyVolume);
//                accumulateStock(buyVolume);
//
//            } else if (actionProbability < 0.15 && availableFunds > stock.getPrice()) {
//                // 15% 的機率以市價買進
//                int buyQuantity = calculateLiftVolume();
//                //System.out.println("主力決定進行市價買進，數量: " + buyQuantity);
//                marketBuy(buyQuantity);
//            } else if (actionProbability < 0.2) {
//                // 5% 的機率取消某個掛單（假設已有掛單）
//                if (!orderBook.getBuyOrders().isEmpty()) {
//                    Order orderToCancel = orderBook.getBuyOrders().get(0); // 例如取消第一個掛單
//                    cancelOrder(orderToCancel.getId());
//                    //System.out.println("主力決定取消掛單 ID: " + orderToCancel.getId());
//                }
//            } else {
//                decisionReason += "主力觀望，無操作。\n";
//                // System.out.println(decisionReason);
//            }
            if (actionProbability < 0.15 && availableFunds > stock.getPrice()) {
                // 15% 的機率以市價買進
                int buyQuantity = calculateLiftVolume();
                //System.out.println("主力決定進行市價買進，數量: " + buyQuantity);
                marketBuy(buyQuantity);
            }

        } else {
            System.out.println("主力正在收集趨勢數據，無法計算 SMA。\n");
        }

        // 您可以選擇在此輸出決策原因
        //System.out.println(decisionReason);
    }

    //市價買入
    public void marketBuy(int quantity) {
        // 創建一個市價訂單（價格設為極大值以確保優先成交）
        Order marketBuyOrder = new Order("buy", Double.MAX_VALUE, quantity, "MainForce", this, account, true, true);

        // 將市價訂單提交到訂單簿
        orderBook.submitBuyOrder(marketBuyOrder, stock.getPrice());
    }

    //取消掛單
    public void cancelOrder(String orderId) {
        // 找到並移除買單
        Order canceledOrder = orderBook.getBuyOrders().stream()
                .filter(order -> order.getId().equals(orderId))
                .findFirst()
                .orElse(null);

        if (canceledOrder != null) {
            // 從買單列表中移除
            orderBook.getBuyOrders().removeIf(order -> order.getId().equals(orderId));

            // 歸還凍結的資金
            double refundAmount = canceledOrder.getPrice() * canceledOrder.getVolume();
            canceledOrder.getTraderAccount().incrementFunds(refundAmount);

            // 打印詳細信息
            System.out.println("已取消買單：");
            System.out.println("訂單ID：" + orderId);
            System.out.println("股票數量：" + canceledOrder.getVolume());
            System.out.println("單價：" + canceledOrder.getPrice());
            System.out.println("已退還資金：" + refundAmount);
        } else {
            // 找到並移除賣單
            canceledOrder = orderBook.getSellOrders().stream()
                    .filter(order -> order.getId().equals(orderId))
                    .findFirst()
                    .orElse(null);

            if (canceledOrder != null) {
                // 從賣單列表中移除
                orderBook.getSellOrders().removeIf(order -> order.getId().equals(orderId));

                // 歸還凍結的股票數量
                canceledOrder.getTraderAccount().incrementStocks(canceledOrder.getVolume());

                // 打印詳細信息
                System.out.println("已取消賣單：");
                System.out.println("訂單ID：" + orderId);
                System.out.println("股票數量：" + canceledOrder.getVolume());
                System.out.println("單價：" + canceledOrder.getPrice());
                System.out.println("已退還股票數量：" + canceledOrder.getVolume());
            } else {
                System.out.println("訂單ID " + orderId + " 未找到，無法取消。");
            }
        }

        // 更新 UI 顯示
        simulation.updateOrderBookDisplay();
    }

    // 根據波動性調整洗盤量
    private int calculateWashVolume(double volatility) {
        return Math.max((int) (volatility * 1000), 50); // 波動性越高，洗盤量越大，最小 50 股
    }

    // 根據市場狀況計算拉抬量
    private int calculateLiftVolume() {
        return random.nextInt(500) + 100; // 隨機決定拉抬量，100 到 600 股之間
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
            //System.out.println("市場買單不足，無法完成賣出操作");
            return;
        }

        if (getAccumulatedStocks() >= volume) {
            Order sellOrder = new Order("sell", price, volume, "MainForce", this, account, false, false);
            orderBook.submitSellOrder(sellOrder, price);
            System.out.println(String.format("主力下賣單 %d 股，價格 %.2f，當前持股 %d 股", volume, price, getAccumulatedStocks()));
        } else {
            System.out.println("主力持股不足，無法賣出\n");
        }
    }

    // 洗盤操作
    public void simulateWashTrading(int volume) {
        if (getAccumulatedStocks() >= volume) {
            double price = stock.getPrice();
            // 創建賣單（大量賣出以壓低股價），將主力帳戶作為交易者傳遞進去
            Order sellOrder = new Order("sell", price + 500, volume, "MainForce", this, account, false, false);
            orderBook.submitSellOrder(sellOrder, price);

            System.out.println(String.format("主力進行洗盤，賣出 %d 股，價格 %.2f", volume, price));
        } else {
            System.out.println("主力持股不足，無法進行洗盤\n");
        }
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
        Order buyOrder = new Order("buy", price, volume, "MainForce", this, account, false, false);
        orderBook.submitBuyOrder(buyOrder, price);
        System.out.println(String.format("主力下買單 %d 股，價格 %.2f，剩餘現金 %.2f 元", volume, price, account.getAvailableFunds()));
    }

    // 拉抬操作
    public void liftStock(int volume) {
        double price = stock.getPrice();
        double cost = price * volume;

        // 創建買單（大量買入以推高股價），將主力帳戶作為交易者傳遞進去
        Order buyOrder = new Order("buy", price, volume, "MainForce", this, account, true, false);
        orderBook.submitBuyOrder(buyOrder, price);

        System.out.println(String.format("主力進行拉抬，買入 %d 股，價格 %.2f，剩餘現金 %.2f 元", volume, price, account.getAvailableFunds()));
    }

    // 在訂單成交時更新現金和持股量
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if (type.equals("buy")) {
            // 已扣減資金，這裡僅更新平均成本價,增加股票
            account.incrementStocks(volume);

            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();

            // 更新目標價
            calculateTargetPrice();

        } else if (type.equals("sell")) {
            // 已增加資金和減少股票數量，這裡僅更新平均成本價
            account.decrementStocks(volume);  // 減少股票數量
            account.incrementFunds(transactionAmount);

            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }
        }

        // 更新界面上的標籤
        simulation.updateLabels();
    }

    // 返回交易紀錄
    public String getTradeLog() {
        return tradeLog.toString();
    }

    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    public double getCash() {
        return account.getAvailableFunds();
    }

    public double getTargetPrice() {
        return targetPrice;
    }

    public double getAverageCostPrice() {
        return averageCostPrice;
    }
}
