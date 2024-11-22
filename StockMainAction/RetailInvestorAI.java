package StockMainAction;

import java.util.Iterator;
import java.util.Random;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Queue;

/**
 * AI散戶行為
 */
public class RetailInvestorAI {

    private int stocksOwned = 0;
    private StockMarketSimulation simulation;
    private static final Random random = new Random();
    private double buyThreshold; //購買門檻
    private double sellThreshold; //賣出門檻
    private Queue<Double> priceHistory; // 儲存最近價格數據，用於分析趨勢
    private boolean ignoreThreshold; // 表示是否忽略門檻
    private String traderID; // 散戶的唯一標識符
    private Order Order;
    private UserAccount account;

    public RetailInvestorAI(double initialCash, String traderID, StockMarketSimulation simulation) {
        this.traderID = traderID;
        this.simulation = simulation;

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

    //散戶行為
    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketSimulation simulation) {
        double availableFunds = account.getAvailableFunds(); // 使用帳戶資金
        double currentPrice = stock.getPrice();
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        StringBuilder decisionReason = new StringBuilder();

        // 每次決策時，動態調整買入和賣出門檻
        double dynamicBuyThreshold = buyThreshold * (0.8 + 0.4 * random.nextDouble()); // 在 80% 到 120% 之間波動
        double dynamicSellThreshold = sellThreshold * (0.8 + 0.4 * random.nextDouble());

        // 有一定機率改變是否忽略門檻的狀態
        if (random.nextDouble() < 0.1) {
            ignoreThreshold = !ignoreThreshold;
        }

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

            double actionProbability = random.nextDouble();

            if (this.shouldIgnoreThreshold()) {
                // 忽略門檻時有一定機率進行買賣操作
                if (availableFunds >= currentPrice && priceDifferenceRatio < 0 && actionProbability > 0.3) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, priceDifferenceRatio);
                    decisionReason.append("忽略門檻，隨機決定買入 ").append(buyAmount).append(" 股。\n");

                    if (random.nextBoolean()) {
                        marketBuy(buyAmount, stock, orderBook);
                        decisionReason.append("使用市價買入。\n");
                    } else {
                        buyStock(stock, buyAmount, orderBook, decisionReason.toString());
                        decisionReason.append("使用限價買入。\n");
                    }
                } else if (getAccumulatedStocks() > 0 && priceDifferenceRatio > 0 && actionProbability > 0.3) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio);
                    decisionReason.append("忽略門檻，隨機決定賣出 ").append(sellAmount).append(" 股。\n");

                    if (random.nextBoolean()) {
                        marketSell(sellAmount, stock, orderBook);
                        decisionReason.append("使用市價賣出。\n");
                    } else {
                        sellStock(stock, sellAmount, orderBook, decisionReason.toString());
                        decisionReason.append("使用限價賣出。\n");
                    }
                }
            } else {
                // 遵循門檻設置的操作
                if (priceDifferenceRatio < -dynamicBuyThreshold && availableFunds >= currentPrice && actionProbability > 0.2) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, priceDifferenceRatio);
                    decisionReason.append("條件：股價低於 SMA 的 ").append(dynamicBuyThreshold * 100).append("% 門檻，買入 ").append(buyAmount).append(" 股。\n");

                    if (random.nextBoolean()) {
                        marketBuy(buyAmount, stock, orderBook);
                        decisionReason.append("使用市價買入。\n");
                    } else {
                        buyStock(stock, buyAmount, orderBook, decisionReason.toString());
                        decisionReason.append("使用限價買入。\n");
                    }
                } else if (priceDifferenceRatio > dynamicSellThreshold && getAccumulatedStocks() > 0 && actionProbability > 0.2) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio);
                    decisionReason.append("條件：股價高於 SMA 的 ").append(dynamicSellThreshold * 100).append("% 門檻，賣出 ").append(sellAmount).append(" 股。\n");

                    if (random.nextBoolean()) {
                        marketSell(sellAmount, stock, orderBook);
                        decisionReason.append("使用市價賣出。\n");
                    } else {
                        sellStock(stock, sellAmount, orderBook, decisionReason.toString());
                        decisionReason.append("使用限價賣出。\n");
                    }
                } else if (random.nextDouble() < 0.05) {
                    executeRandomTransaction(availableFunds, currentPrice, decisionReason, stock, orderBook);
                }
            }

            simulation.updateInfoTextArea(decisionReason.toString());

        } else {
            simulation.updateInfoTextArea("散戶正在收集趨勢數據，無法計算 SMA。\n");
        }
    }

    //計算交易量
    private int calculateTransactionVolume(double availableFunds, double currentPrice, double priceDifferenceRatio) {
        int maxAffordableAmount = (int) (availableFunds / currentPrice);
        return Math.max(1, Math.min((int) (maxAffordableAmount * Math.abs(priceDifferenceRatio) * (0.5 + 0.5 * random.nextDouble())), 500));
    }

    //賣出交易量
    private int calculateSellVolume(double priceDifferenceRatio) {
        return Math.max(1, Math.min((int) (getAccumulatedStocks() * priceDifferenceRatio * (0.5 + 0.5 * random.nextDouble())), getAccumulatedStocks()));
    }

    //隨機操作
    private void executeRandomTransaction(double availableFunds, double currentPrice, StringBuilder decisionReason, Stock stock, OrderBook orderBook) {
        if (random.nextBoolean() && availableFunds >= currentPrice) {
            int buyAmount = random.nextInt(50) + 1;
            if (random.nextBoolean()) {
                marketBuy(buyAmount, stock, orderBook);
                decisionReason.append("隨機市價買入操作。\n");
            } else {
                buyStock(stock, buyAmount, orderBook, "隨機限價買入操作。\n");
            }
        } else if (getAccumulatedStocks() > 0) {
            int sellAmount = random.nextInt(getAccumulatedStocks()) + 1;
            if (random.nextBoolean()) {
                marketSell(sellAmount, stock, orderBook);
                decisionReason.append("隨機市價賣出操作。\n");
            } else {
                sellStock(stock, sellAmount, orderBook, "隨機限價賣出操作。\n");
            }
        }
    }

    //散戶市價買入
    public void marketBuy(int quantity, Stock stock, OrderBook orderBook) {
        double remainingFunds = account.getAvailableFunds();
        int remainingQuantity = quantity;
        double marketTotalTransactionValue = 0.0;
        int marketTotalTransactionVolume = 0;

        // 使用 ListIterator 安全地遍歷賣單列表
        ListIterator<Order> iterator = orderBook.getSellOrders().listIterator();
        while (iterator.hasNext() && remainingQuantity > 0) {
            Order sellOrder = iterator.next();
            double transactionPrice = sellOrder.getPrice();
            int transactionVolume = Math.min(sellOrder.getVolume(), remainingQuantity);
            double transactionCost = transactionPrice * transactionVolume;

            // 檢查是否為自己的訂單
            if (sellOrder.getTrader() == this) {
//                 System.out.println("散戶 跳過與自己的訂單匹配，賣單資訊：" + sellOrder);
                continue; // 跳過自己的訂單
            }

            // 檢查資金是否足夠
            if (remainingFunds >= transactionCost) {
                remainingFunds -= transactionCost;
                remainingQuantity -= transactionVolume;

                // 更新持股量和資金
                account.incrementStocks(transactionVolume);
                account.decrementFunds(transactionCost);

                System.out.println("散戶 市價買進，價格: " + transactionPrice + "，數量: " + transactionVolume);

                // 累加市價單的成交值和成交量
                marketTotalTransactionValue += transactionPrice * transactionVolume;
                marketTotalTransactionVolume += transactionVolume;

                // 如果賣單已全部成交，移除該賣單
                if (sellOrder.getVolume() == transactionVolume) {
                    iterator.remove();
                } else {
                    sellOrder.setVolume(sellOrder.getVolume() - transactionVolume);
                }
            } else {
//                System.out.println("散戶 資金不足，無法完成市價買進剩餘數量");
                break;
            }
        }

        // 更新累計的成交值和成交量，供後續使用
        if (marketTotalTransactionVolume > 0) {
            orderBook.addMarketTransactionData(marketTotalTransactionValue, marketTotalTransactionVolume);
        }

        // 更新用戶界面
        simulation.updateLabels();
        simulation.updateVolumeChart(marketTotalTransactionVolume);
        simulation.updateOrderBookDisplay();
    }

    //散戶市價賣出
    public void marketSell(int quantity, Stock stock, OrderBook orderBook) {
        double remainingQuantity = quantity;
        double marketTotalTransactionValue = 0.0;
        int marketTotalTransactionVolume = 0;

        // 使用 ListIterator 安全地遍歷買單列表
        ListIterator<Order> iterator = orderBook.getBuyOrders().listIterator();
        while (iterator.hasNext() && remainingQuantity > 0) {
            Order buyOrder = iterator.next();
            double transactionPrice = buyOrder.getPrice();
            int transactionVolume = Math.min(buyOrder.getVolume(), (int) remainingQuantity);
            double transactionRevenue = transactionPrice * transactionVolume;

            // 檢查是否為自己的訂單
            if (buyOrder.getTrader() == this) {
//                System.out.println("散戶 跳過與自己的訂單匹配，買單資訊：" + buyOrder);
                continue; // 跳過自己的訂單
            }

            // 檢查持股量是否足夠
            if (account.getStockInventory() >= transactionVolume) {
                account.decrementStocks(transactionVolume);
                remainingQuantity -= transactionVolume;

                // 增加資金
                account.incrementFunds(transactionRevenue);

                System.out.println("散戶 市價賣出，價格: " + transactionPrice + "，數量: " + transactionVolume);

                // 累加市價單的成交值和成交量
                marketTotalTransactionValue += transactionPrice * transactionVolume;
                marketTotalTransactionVolume += transactionVolume;

                // 如果買單已全部成交，移除該買單
                if (buyOrder.getVolume() == transactionVolume) {
                    iterator.remove();
                } else {
                    buyOrder.setVolume(buyOrder.getVolume() - transactionVolume);
                }
            } else {
//                System.out.println("散戶 持股不足，無法完成市價賣出剩餘數量");
                break;
            }
        }

        // 更新累計的成交值和成交量，供後續使用
        if (marketTotalTransactionVolume > 0) {
            orderBook.addMarketTransactionData(marketTotalTransactionValue, marketTotalTransactionVolume);
        }

        // 更新用戶界面
        simulation.updateLabels();
        simulation.updateVolumeChart(marketTotalTransactionVolume);
        simulation.updateOrderBookDisplay();
    }

    // 買入股票，生成並提交買單
    private void buyStock(Stock stock, int amount, OrderBook orderBook, String decisionReason) {
        double availableFunds = account.getAvailableFunds(); // 使用帳戶資金
        double price = stock.getPrice();
        double totalCost = price * amount;

        if (availableFunds >= totalCost) {
            // 使用散戶的 UserAccount 創建買單
            Order buyOrder = new Order("buy", price, amount, "RetailInvestor", this, account, false, false);
            orderBook.submitBuyOrder(buyOrder, price);

            // 可以選擇輸出買入操作資訊
//             System.out.println(decisionReason);
//            System.out.println(String.format("散戶下買單 %d 股，價格 %.2f，剩餘現金 %.2f 元", amount, price, availableFunds));
        } else {
//            System.out.println("現金不足，無法執行買入操作。\n");
        }
    }

    // 賣出股票，生成並提交賣單
    private void sellStock(Stock stock, int amount, OrderBook orderBook, String decisionReason) {
        if (getAccumulatedStocks() >= amount) {
            double price = stock.getPrice();

            // 使用散戶的 UserAccount 創建賣單
            Order sellOrder = new Order("sell", price, amount, "RetailInvestor", this, account, false, false);
            orderBook.submitSellOrder(sellOrder, price);

            // 可以選擇輸出賣出操作資訊
//             System.out.println(decisionReason);
//            System.out.println(String.format("散戶下賣單 %d 股，價格 %.2f，剩餘持股 %d 股", amount, price, getAccumulatedStocks()));
        } else {
//            System.out.println("持有股票不足，無法執行賣出操作。\n");
        }
    }

    //在訂單成交時更新現金和持股量
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume; // 計算交易金額

        if (type.equals("buy")) {
            //限價單買入 不扣資金 增加股數 
            account.incrementStocks(volume); // 增加股票

            System.out.println(String.format("散戶限價成交買入 %d 股，價格 %.2f，總金額 %.2f", volume, price, transactionAmount));

        } else if (type.equals("sell")) {
            //現價賣出 不扣股數 增加現金
            account.incrementFunds(transactionAmount); // 增加資金

            System.out.println(String.format("散戶限價成交賣出 %d 股，價格 %.2f，總金額 %.2f", volume, price, transactionAmount));
        }

        // 更新界面上的標籤
//    simulation.updateLabels();
    }

    //購買門檻
    public double getBuyThreshold() {
        return buyThreshold;
    }

    //賣出門檻
    public double getSellThreshold() {
        return sellThreshold;
    }

    //表示是否忽略門檻
    public boolean shouldIgnoreThreshold() {
        return ignoreThreshold;
    }

    //獲取散戶的唯一標識符
    public String getTraderID() {
        return traderID;
    }

    //獲取散戶股票數量
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    //獲取散戶現金
    public double getCash() {
        return account.getAvailableFunds();
    }

    //獲取帳戶
    public UserAccount getAccount() {
        return account;
    }
}
