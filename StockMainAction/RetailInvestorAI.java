package StockMainAction;

import java.util.Random;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Queue;

/**
 * AI散戶行為，實現 Trader 接口
 */
public class RetailInvestorAI implements Trader {

    private StockMarketSimulation simulation;
    private static final Random random = new Random();
    private double buyThreshold = 0.95; // 調低門檻以增加買入機會
    private double sellThreshold = 1.05; // 調低門檻以增加賣出機會
    private boolean ignoreThreshold = false;
    private Queue<Double> priceHistory; // 儲存最近價格數據，用於分析趨勢
    private String traderID; // 散戶的唯一標識符
    private UserAccount account;
    private OrderBook orderBook;

    // 停損和止盈價格
    private Double stopLossPrice = null;
    private Double takeProfitPrice = null;

    // 風險管理參數
    private final double riskPerTrade = 0.02; // 每次交易風險不超過 2% 資金

    // 短期和長期 SMA
    private int shortSmaPeriod = 5;
    private int longSmaPeriod = 20;

    /**
     * 構造函數
     *
     * @param initialCash 初始現金
     * @param traderID 散戶的唯一標識符
     * @param simulation 模擬實例
     */
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

        // 初始化帳戶
        this.account = new UserAccount(initialCash, 0);
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
        return "RetailInvestor";
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
        double transactionAmount = price * volume; // 計算交易金額

        if (type.equals("buy")) {
            // 限價單買入：增加股數
            account.incrementStocks(volume);

            // 更新平均成本價（如果需要）
            // 此處可以根據需求進行實現
//            System.out.println(String.format("散戶 限價成交買入 %d 股，價格 %.2f，總金額 %.2f", volume, price, transactionAmount));
        } else if (type.equals("sell")) {
            // 現價賣出：增加現金
            account.incrementFunds(transactionAmount);

//            System.out.println(String.format("散戶限價 成交賣出 %d 股，價格 %.2f，總金額 %.2f", volume, price, transactionAmount));
        }

        // 更新界面上的標籤
        simulation.updateLabels();
    }

    /**
     * 更新價格數據並計算價格趨勢
     *
     * @param currentPrice 當前價格
     */
    private void updatePriceHistory(double currentPrice) {
        if (priceHistory.size() >= 10) { // 假設儲存最近10次價格
            priceHistory.poll();
        }
        priceHistory.add(currentPrice);
    }

    /**
     * 散戶行為決策
     *
     * @param stock 股票實例
     * @param orderBook 訂單簿實例
     * @param simulation 模擬實例
     */
    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketSimulation simulation) {
        double availableFunds = account.getAvailableFunds(); // 使用帳戶資金
        double currentPrice = stock.getPrice();
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double rsi = simulation.getMarketAnalyzer().getRSI();
        double volatility = simulation.getMarketAnalyzer().getVolatility();
        StringBuilder decisionReason = new StringBuilder();

        // 每次決策時，動態調整買入和賣出門檻
        double dynamicBuyThreshold = buyThreshold * (0.8 + 0.4 * random.nextDouble()); // 在 80% 到 120% 之間波動
        double dynamicSellThreshold = sellThreshold * (0.8 + 0.4 * random.nextDouble());

        // 有一定機率改變是否忽略門檻的狀態
        if (random.nextDouble() < 0.1) {
            ignoreThreshold = !ignoreThreshold;
        }

        // 調試輸出 SMA 和 RSI 值
//        System.out.println("RetailInvestorAI - SMA 值: " + sma + ", RSI 值: " + rsi + ", 波動性: " + volatility);

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

            double actionProbability = random.nextDouble();

            // 停損和止盈邏輯
            if (stopLossPrice != null && currentPrice <= stopLossPrice) {
                // 觸發停損
                int sellAmount = getAccumulatedStocks();
                if (sellAmount > 0) {
                    orderBook.marketSell(this, sellAmount);
                    decisionReason.append("觸發停損，賣出全部股票。\n");
                }
                stopLossPrice = null;
                takeProfitPrice = null;
            }

            if (takeProfitPrice != null && currentPrice >= takeProfitPrice) {
                // 觸發止盈
                int sellAmount = getAccumulatedStocks();
                if (sellAmount > 0) {
                    orderBook.marketSell(this, sellAmount);
                    decisionReason.append("觸發止盈，賣出全部股票。\n");
                }
                stopLossPrice = null;
                takeProfitPrice = null;
            }

            if (this.shouldIgnoreThreshold()) {
                // 忽略門檻時有一定機率進行買賣操作
                if (availableFunds >= currentPrice && priceDifferenceRatio < 0 && actionProbability > 0.3) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                    decisionReason.append("忽略門檻，隨機決定買入 ").append(buyAmount).append(" 股。\n");

                    if (random.nextBoolean()) {
                        orderBook.marketBuy(this, buyAmount);
                        decisionReason.append("使用市價買入。\n");
                    } else {
                        buyStock(stock, buyAmount, orderBook, "隨機限價買入操作。\n");
                        decisionReason.append("使用限價買入。\n");
                    }

                    // 設置停損和止盈價格
                    setStopLossAndTakeProfit(currentPrice, volatility);
                } else if (getAccumulatedStocks() > 0 && priceDifferenceRatio > 0 && actionProbability > 0.3) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                    decisionReason.append("忽略門檻，隨機決定賣出 ").append(sellAmount).append(" 股。\n");

                    if (random.nextBoolean()) {
                        orderBook.marketSell(this, sellAmount);
                        decisionReason.append("使用市價賣出。\n");
                    } else {
                        sellStock(stock, sellAmount, orderBook, "隨機限價賣出操作。\n");
                        decisionReason.append("使用限價賣出。\n");
                    }

                    // 清除停損和止盈價格
                    stopLossPrice = null;
                    takeProfitPrice = null;
                }
            } else {
                // 遵循門檻設置的操作
                if (priceDifferenceRatio < -dynamicBuyThreshold && availableFunds >= currentPrice && actionProbability > 0.2) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                    decisionReason.append("條件：股價低於 SMA 的 ").append(dynamicBuyThreshold * 100).append("% 門檻，買入 ").append(buyAmount).append(" 股。\n");

                    if (random.nextBoolean()) {
                        orderBook.marketBuy(this, buyAmount);
                        decisionReason.append("使用市價買入。\n");
                    } else {
                        buyStock(stock, buyAmount, orderBook, "限價買入操作。\n");
                        decisionReason.append("使用限價買入。\n");
                    }

                    // 設置停損和止盈價格
                    setStopLossAndTakeProfit(currentPrice, volatility);
                } else if (priceDifferenceRatio > dynamicSellThreshold && getAccumulatedStocks() > 0 && actionProbability > 0.2) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                    decisionReason.append("條件：股價高於 SMA 的 ").append(dynamicSellThreshold * 100).append("% 門檻，賣出 ").append(sellAmount).append(" 股。\n");

                    if (random.nextBoolean()) {
                        orderBook.marketSell(this, sellAmount);
                        decisionReason.append("使用市價賣出。\n");
                    } else {
                        sellStock(stock, sellAmount, orderBook, "限價賣出操作。\n");
                        decisionReason.append("使用限價賣出。\n");
                    }

                    // 清除停損和止盈價格
                    stopLossPrice = null;
                    takeProfitPrice = null;
                } else if (random.nextDouble() < 0.05) {
                    executeRandomTransaction(availableFunds, currentPrice, decisionReason, stock, orderBook);
                }
            }

            // 根據 RSI 判斷是否超買或超賣
            if (rsi < 30 && availableFunds >= currentPrice) {
                // RSI 超賣，發出買入信號
                int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                decisionReason.append("RSI < 30，發出買入信號，買入 ").append(buyAmount).append(" 股。\n");
                orderBook.marketBuy(this, buyAmount);
                setStopLossAndTakeProfit(currentPrice, volatility);
            } else if (rsi > 70 && getAccumulatedStocks() > 0) {
                // RSI 超買，發出賣出信號
                int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                decisionReason.append("RSI > 70，發出賣出信號，賣出 ").append(sellAmount).append(" 股。\n");
                orderBook.marketSell(this, sellAmount);
                stopLossPrice = null;
                takeProfitPrice = null;
            }

            simulation.updateInfoTextArea(decisionReason.toString());

        } else {
            simulation.updateInfoTextArea("散戶正在收集趨勢數據，無法計算 SMA。\n");
        }
    }

    /**
     * 計算交易量，根據波動性調整頭寸大小
     *
     * @param availableFunds 可用資金
     * @param currentPrice 當前價格
     * @param volatility 市場波動性
     * @return 交易量
     */
    private int calculateTransactionVolume(double availableFunds, double currentPrice, double volatility) {
        // 根據波動性調整頭寸大小，波動性高時減少頭寸
        double positionSize = (availableFunds / currentPrice) * (1 / (1 + volatility));
        positionSize = positionSize * riskPerTrade; // 每次交易風險不超過 2% 資金
        return (int) Math.max(1, positionSize);
    }

    /**
     * 計算賣出量，根據波動性調整頭寸大小
     *
     * @param priceDifferenceRatio 價格差異比率
     * @param volatility 市場波動性
     * @return 賣出量
     */
    private int calculateSellVolume(double priceDifferenceRatio, double volatility) {
        // 根據波動性調整頭寸大小，波動性高時減少頭寸
        double positionSize = getAccumulatedStocks() * (0.1 + 0.4 * random.nextDouble()) * (1 / (1 + volatility));
        return (int) Math.max(1, positionSize);
    }

    /**
     * 設置停損和止盈價格
     *
     * @param currentPrice 當前價格
     * @param volatility 市場波動性
     */
    private void setStopLossAndTakeProfit(double currentPrice, double volatility) {
        // 停損設定為當前價格的 95%
        stopLossPrice = currentPrice * 0.95;

        // 止盈設定為當前價格的 105%
        takeProfitPrice = currentPrice * 1.05;

        System.out.println("RetailInvestorAI - 設置停損價格: " + stopLossPrice + ", 設置止盈價格: " + takeProfitPrice);
    }

    /**
     * 執行隨機交易
     *
     * @param availableFunds 可用資金
     * @param currentPrice 當前價格
     * @param decisionReason 決策原因
     * @param stock 股票實例
     * @param orderBook 訂單簿實例
     */
    private void executeRandomTransaction(double availableFunds, double currentPrice, StringBuilder decisionReason, Stock stock, OrderBook orderBook) {
//        System.out.println("RetailInvestorAI - 執行隨機交易，availableFunds: " + availableFunds + ", currentPrice: " + currentPrice);
        if (orderBook == null) {
            System.out.println("Error: orderBook is null!");
        }
        if (stock == null) {
            System.out.println("Error: stock is null!");
        }
        if (decisionReason == null) {
            System.out.println("Error: decisionReason is null!");
        }

        if (random.nextBoolean() && availableFunds >= currentPrice) {
            int buyAmount = random.nextInt(50) + 1;
            if (random.nextBoolean()) {
                orderBook.marketBuy(this, buyAmount);
                decisionReason.append("隨機市價買入操作。\n");
            } else {
                buyStock(stock, buyAmount, orderBook, "隨機限價買入操作。\n");
                decisionReason.append("隨機限價買入操作。\n");
            }

            // 設置停損和止盈價格
            setStopLossAndTakeProfit(currentPrice, simulation.getMarketAnalyzer().getVolatility());
        } else if (getAccumulatedStocks() > 0) {
            int sellAmount = random.nextInt(getAccumulatedStocks()) + 1;
            if (random.nextBoolean()) {
                orderBook.marketSell(this, sellAmount);
                decisionReason.append("隨機市價賣出操作。\n");
            } else {
                sellStock(stock, sellAmount, orderBook, "隨機限價賣出操作。\n");
                decisionReason.append("隨機限價賣出操作。\n");
            }

            // 清除停損和止盈價格
            stopLossPrice = null;
            takeProfitPrice = null;
        }
    }

    /**
     * 買入股票，生成並提交買單
     *
     * @param stock 股票實例
     * @param amount 購買量
     * @param orderBook 訂單簿實例
     * @param decisionReason 決策理由
     */
    private void buyStock(Stock stock, int amount, OrderBook orderBook, String decisionReason) {
        double availableFunds = account.getAvailableFunds(); // 使用帳戶資金
        double price = stock.getPrice();
        double totalCost = price * amount;

        if (availableFunds >= totalCost) {
            // 使用散戶的 UserAccount 創建買單
            Order buyOrder = new Order("buy", price, amount, this, false, false);
            orderBook.submitBuyOrder(buyOrder, price);

            // 可以選擇輸出買入操作資訊
            // System.out.println(decisionReason);
            // System.out.println(String.format("散戶下買單 %d 股，價格 %.2f，剩餘現金 %.2f 元", amount, price, availableFunds));
        } else {
            // System.out.println("現金不足，無法執行買入操作。\n");
        }
    }

    /**
     * 賣出股票，生成並提交賣單
     *
     * @param stock 股票實例
     * @param amount 賣出量
     * @param orderBook 訂單簿實例
     * @param decisionReason 賣出理由
     */
    private void sellStock(Stock stock, int amount, OrderBook orderBook, String decisionReason) {
        if (getAccumulatedStocks() >= amount) {
            double price = stock.getPrice();

            // 使用散戶的 UserAccount 創建賣單
            Order sellOrder = new Order("sell", price, amount, this, false, false);
            orderBook.submitSellOrder(sellOrder, price);

            // 可以選擇輸出賣出操作資訊
            // System.out.println(decisionReason);
            // System.out.println(String.format("散戶下賣單 %d 股，價格 %.2f，剩餘持股 %d 股", amount, price, getAccumulatedStocks()));
        } else {
            // System.out.println("持有股票不足，無法執行賣出操作。\n");
        }
    }

    /**
     * 購買門檻
     *
     * @return 買入門檻
     */
    public double getBuyThreshold() {
        return buyThreshold;
    }

    /**
     * 賣出門檻
     *
     * @return 賣出門檻
     */
    public double getSellThreshold() {
        return sellThreshold;
    }

    /**
     * 表示是否忽略門檻
     *
     * @return 是否忽略門檻
     */
    public boolean shouldIgnoreThreshold() {
        return ignoreThreshold;
    }

    /**
     * 獲取散戶的唯一標識符
     *
     * @return traderID
     */
    public String getTraderID() {
        return traderID;
    }

    /**
     * 獲取散戶股票數量
     *
     * @return 股票數量
     */
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    /**
     * 獲取散戶現金
     *
     * @return 現金
     */
    public double getCash() {
        return account.getAvailableFunds();
    }

    /**
     * 動量交易
     *
     * @param volatility 波動性
     * @return 動量交易量
     */
    private int calculateMomentumVolume(double volatility) {
        return (int) (500 * volatility * (0.8 + random.nextDouble() * 0.4));
    }
}
