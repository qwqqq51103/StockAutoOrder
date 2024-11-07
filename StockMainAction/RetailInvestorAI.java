package StockMainAction;

import java.util.Iterator;
import java.util.Random;
import java.util.LinkedList;
import java.util.Queue;

/**
 * AI散戶行為
 */
public class RetailInvestorAI {

    private int stocksOwned = 0;
    private static final Random random = new Random();
    private double buyThreshold;
    private double sellThreshold;
    private Queue<Double> priceHistory; // 儲存最近價格數據，用於分析趨勢
    private boolean ignoreThreshold; // 表示是否忽略門檻
    private String traderID; // 散戶的唯一標識符
    private Order Order;
    private UserAccount account;
    private OrderBook orderBook;
    private StockMarketSimulation simulation;

    public RetailInvestorAI(double initialCash, String traderID) {
        this.traderID = traderID;

        // 初始化價格歷史
        this.priceHistory = new LinkedList<>();

        // 設置隨機的買入和賣出門檻
        this.buyThreshold = 0.01 + random.nextDouble() * 0.05; // 1% 到 6%
        this.sellThreshold = 0.03 + random.nextDouble() * 0.09; // 3% 到 12%

        // 隨機決定該散戶是否忽略門檻，設置 30% 的機率
        this.ignoreThreshold = random.nextDouble() < 0.3;

        //
        this.account = new UserAccount(initialCash, stocksOwned);
    }

    // 更新價格數據並計算價格趨勢
    private void updatePriceHistory(double currentPrice) {
        if (priceHistory.size() >= 10) { // 假設儲存最近10次價格
            priceHistory.poll();
        }
        priceHistory.add(currentPrice);
    }

    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketSimulation simulation) {
        double availableFunds = account.getAvailableFunds(); // 使用帳戶資金
        double currentPrice = stock.getPrice();
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        String decisionReason = "";

        // 每次決策時，動態調整買入和賣出門檻
        double dynamicBuyThreshold = buyThreshold * (0.8 + 0.4 * random.nextDouble()); // 在 80% 到 120% 之間波動
        double dynamicSellThreshold = sellThreshold * (0.8 + 0.4 * random.nextDouble());

        // 有一定機率改變是否忽略門檻的狀態
        if (random.nextDouble() < 0.1) { // 10% 的機率切換忽略門檻的狀態
            ignoreThreshold = !ignoreThreshold;
        }

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;

            // 限制偏差比例的範圍在 -0.5 到 0.5 之間
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

            //decisionReason += String.format("當前股價：%.2f，SMA：%.2f，偏差比例：%.2f%%\n", currentPrice, sma, priceDifferenceRatio * 100);
            // 引入操作機率
            double actionProbability = random.nextDouble();

            if (this.shouldIgnoreThreshold()) {
                // 當忽略門檻時，有一定機率進行買賣操作
                if (availableFunds >= currentPrice && priceDifferenceRatio < 0 && actionProbability > 0.3) {
                    int maxAffordableAmount = (int) (availableFunds / currentPrice);
                    int buyAmount = (int) (maxAffordableAmount * Math.abs(priceDifferenceRatio) * (0.5 + 0.5 * random.nextDouble()));
                    buyAmount = Math.max(1, Math.min(buyAmount, 500)); // 限制買入量範圍

                    decisionReason += String.format("忽略門檻，隨機決定買入 %d 股。\n", buyAmount);
                    buyStock(stock, buyAmount, orderBook, decisionReason);

                } else if (getAccumulatedStocks() > 0 && priceDifferenceRatio > 0 && actionProbability > 0.3) {
                    int sellAmount = (int) (getAccumulatedStocks() * priceDifferenceRatio * (0.5 + 0.5 * random.nextDouble()));
                    sellAmount = Math.max(1, Math.min(sellAmount, getAccumulatedStocks())); // 限制賣出量範圍

                    decisionReason += String.format("忽略門檻，隨機決定賣出 %d 股。\n", sellAmount);
                    sellStock(stock, sellAmount, orderBook, decisionReason);
                } else {
                    //decisionReason += "散戶觀望，無操作。\n";
                }
            } else {
                // 當不忽略門檻時，遵循門檻設置，有一定機率進行操作
                if (priceDifferenceRatio < -dynamicBuyThreshold && availableFunds >= currentPrice && actionProbability > 0.2) {
                    int maxAffordableAmount = (int) (availableFunds / currentPrice);
                    int buyAmount = (int) (maxAffordableAmount * Math.abs(priceDifferenceRatio) * (0.5 + 0.5 * random.nextDouble()));
                    buyAmount = Math.max(1, Math.min(buyAmount, 500)); // 限制買入量範圍

                    decisionReason += String.format("條件：股價低於 SMA 的 %.2f%% 門檻（動態調整後），買入 %d 股。\n", dynamicBuyThreshold * 100, buyAmount);
                    buyStock(stock, buyAmount, orderBook, decisionReason);

                } else if (priceDifferenceRatio > dynamicSellThreshold && getAccumulatedStocks() > 0 && actionProbability > 0.2) {
                    int sellAmount = (int) (getAccumulatedStocks() * priceDifferenceRatio * (0.5 + 0.5 * random.nextDouble()));
                    sellAmount = Math.max(1, Math.min(sellAmount, getAccumulatedStocks())); // 限制賣出量範圍

                    decisionReason += String.format("條件：股價高於 SMA 的 %.2f%% 門檻（動態調整後），賣出 %d 股。\n", dynamicSellThreshold * 100, sellAmount);
                    sellStock(stock, sellAmount, orderBook, decisionReason);
                } else if (random.nextDouble() < 0.05) { // 5% 機率進行隨機操作
                    if (random.nextBoolean() && availableFunds >= currentPrice) {
                        int buyAmount = random.nextInt(50) + 1;
                        buyStock(stock, buyAmount, orderBook, "隨機買入操作。\n");
                    } else if (getAccumulatedStocks() > 0) {
                        int sellAmount = random.nextInt(getAccumulatedStocks()) + 1;
                        sellStock(stock, sellAmount, orderBook, "隨機賣出操作。\n");
                    }
                } else {
                    //decisionReason += "散戶觀望，無操作。\n";
                }
            }
            // 可以選擇輸出決策原因
            simulation.updateInfoTextArea(decisionReason);

        } else {
            // 無法計算 SMA，散戶觀望
            //simulation.updateInfoTextArea("散戶正在收集趨勢數據，無法計算 SMA。\n");
        }
    }

    // 市價買入方法
    private void marketBuy(Stock stock, OrderBook orderBook, int quantity) {
        double remainingFunds = account.getAvailableFunds();
        int remainingQuantity = quantity;

        for (Iterator<Order> iterator = orderBook.getSellOrders().iterator(); iterator.hasNext() && remainingQuantity > 0;) {
            Order sellOrder = iterator.next();
            double transactionPrice = sellOrder.getPrice();
            int transactionVolume = Math.min(sellOrder.getVolume(), remainingQuantity);
            double transactionCost = transactionPrice * transactionVolume;

            if (remainingFunds >= transactionCost) {
                remainingFunds -= transactionCost;
                remainingQuantity -= transactionVolume;

                account.incrementStocks(transactionVolume);
                account.decrementFunds(transactionCost);

                System.out.println("散戶市價買進，價格: " + transactionPrice + "，數量: " + transactionVolume);

                if (sellOrder.getVolume() == transactionVolume) {
                    iterator.remove();
                } else {
                    sellOrder.setVolume(sellOrder.getVolume() - transactionVolume);
                }
            } else {
                System.out.println("散戶現金不足，無法完成市價買進");
                break;
            }
        }
        simulation.updateLabels();
    }

    // 買入股票，生成並提交買單
    private void buyStock(Stock stock, int amount, OrderBook orderBook, String decisionReason) {
        double availableFunds = account.getAvailableFunds(); // 使用帳戶資金
        double price = stock.getPrice();
        double totalCost = price * amount;
        if (availableFunds >= totalCost) {
            // 使用散戶的 UserAccount 創建買單
            Order buyOrder = new Order("buy", price, amount, "散戶", this, account, false, false);
            orderBook.submitBuyOrder(buyOrder, price);

            // 在訂單成交時更新現金和持股量，這裡暫不更新
            // 可以選擇輸出買入操作資訊
            // System.out.println(decisionReason);
            // System.out.println(String.format("散戶下買單 %d 股，價格 %.2f，剩餘現金 %.2f 元", amount, price, cash));
        } else {
            // System.out.println("現金不足，無法執行買入操作。\n");
        }
    }

    // 賣出股票，生成並提交賣單
    private void sellStock(Stock stock, int amount, OrderBook orderBook, String decisionReason) {
        if (getAccumulatedStocks() >= amount) {
            double price = stock.getPrice();

            // 使用散戶的 UserAccount 創建賣單
            Order sellOrder = new Order("sell", price, amount, "散戶", this, account, false, false);
            orderBook.submitSellOrder(sellOrder, price);

            // 在訂單成交時更新現金和持股量，這裡暫不更新
            // 可以選擇輸出賣出操作資訊
            // System.out.println(decisionReason);
            // System.out.println(String.format("散戶下賣單 %d 股，價格 %.2f，剩餘持股 %d 股", amount, price, getAccumulatedStocks()));
        } else {
            // System.out.println("持有股票不足，無法執行賣出操作。\n");
        }
    }

    // 在訂單成交時更新現金和持股量
    public void updateAfterTransaction(String type, int volume, double price) {
        double availableFunds = account.getAvailableFunds(); // 使用帳戶資金
        if (type.equals("buy")) {
            availableFunds -= price * volume;
            account.incrementStocks(volume);
        } else if (type.equals("sell")) {
            availableFunds += price * volume;
            account.decrementFunds(availableFunds);
        }
    }

    public double getBuyThreshold() {
        return buyThreshold;
    }

    public double getSellThreshold() {
        return sellThreshold;
    }

    public boolean shouldIgnoreThreshold() {
        return ignoreThreshold;
    }

    public String getTraderID() {
        return traderID;
    }

    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    public double getCash() {
        return account.getAvailableFunds();
    }
}
