package AIStrategies;

import UserManagement.UserAccount;
import Core.Order;
import Core.Trader;
import Core.OrderBook;
import Core.Stock;
import StockMainAction.StockMarketSimulation;
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
    private double sellThreshold = 1.5; // 調低門檻以增加賣出機會
    private boolean ignoreThreshold = false;
    private Queue<Double> priceHistory; // 儲存最近價格數據，用於分析趨勢
    private String traderID; // 散戶的唯一標識符
    private UserAccount account;
    private OrderBook orderBook; // 亦可在 makeDecision 時指定
    private Stock stock;  // 股票實例

    // 停損和止盈價格
    private Double stopLossPrice = null;
    private Double takeProfitPrice = null;

    // 風險管理參數
    private final double riskPerTrade = 0.05; // 每次交易風險不超過 5% 資金 (原本註解寫2%，實際數字可調)

    // 短期和長期 SMA (如有用到可自行擴充)
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

        // 隨機決定該散戶是否忽略門檻，30% 機率
        this.ignoreThreshold = random.nextDouble() < 0.3;

        // 初始化帳戶
        this.account = new UserAccount(initialCash, 0);
    }

    // ========== Trader 介面實作 ==========
    @Override
    public UserAccount getAccount() {
        return account;
    }

    @Override
    public String getTraderType() {
        return "RETAIL_INVESTOR";
    }

    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            // 限價單買入
            account.incrementStocks(volume);
            // System.out.println(String.format("【散戶-限價買入更新】買入 %d 股，價格 %.2f", volume, price));
        } else if ("sell".equals(type)) {
            // 限價單賣出
            account.incrementFunds(transactionAmount);
            // System.out.println(String.format("【散戶-限價賣出更新】賣出 %d 股，價格 %.2f", volume, price));
        }

        // 更新 GUI
        simulation.updateLabels();
    }

    @Override
    public void updateAverageCostPrice(String type, int volume, double price) {
        // 市價單更新，若需更進階的平均成本處理，可在此加上
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            // System.out.println(String.format("【散戶-市價買入更新】買入 %d 股，價格 %.2f", volume, price));
            account.incrementStocks(volume);
        } else if ("sell".equals(type)) {
            // System.out.println(String.format("【散戶-市價賣出更新】賣出 %d 股，價格 %.2f", volume, price));
            account.incrementFunds(transactionAmount);
        }

        simulation.updateLabels();
    }

    // ========== 核心行為決策 ==========
    /**
     * 散戶行為決策
     *
     * @param stock 股票實例
     * @param orderBook 訂單簿實例
     * @param simulation 模擬實例
     */
    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketSimulation simulation) {
        this.orderBook = orderBook; // 如需使用私有函式下單時需有此參考
        double availableFunds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double rsi = simulation.getMarketAnalyzer().getRSI();
        double volatility = simulation.getMarketAnalyzer().getVolatility();
        StringBuilder decisionReason = new StringBuilder();

        // 每次決策時，動態調整買/賣門檻
        double dynamicBuyThreshold = buyThreshold * (0.8 + 0.4 * random.nextDouble());
        double dynamicSellThreshold = sellThreshold * (0.8 + 0.4 * random.nextDouble());

        if (this.orderBook == null) {
            this.orderBook = orderBook; // 確保每次執行時有訂單簿
        }

        if (this.stock == null) {
            this.stock = stock; // 確保 stock 不為 null
        }

        // 可能改變是否忽略門檻
        if (random.nextDouble() < 0.1) {
            ignoreThreshold = !ignoreThreshold;
        }

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));
            double actionProbability = random.nextDouble();

            // 停損 / 止盈
            handleStopLossTakeProfit(currentPrice, decisionReason);

            // 忽略門檻
            if (this.shouldIgnoreThreshold()) {
                if (availableFunds >= currentPrice && priceDifferenceRatio < 0 && actionProbability > 0.3) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                    decisionReason.append("【忽略門檻】隨機決定買入 ").append(buyAmount).append(" 股。");

                    if (random.nextBoolean()) {
                        int actualBuy = 市價買入操作(buyAmount);
                        if (actualBuy > 0) {
                            decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                            setStopLossAndTakeProfit(currentPrice, volatility);
                        } else {
                            decisionReason.append("【失敗】市價買入。資金或掛單量不足。\n");
                        }
                    } else {
                        int actualBuy = 限價買入操作(buyAmount, currentPrice);
                        if (actualBuy > 0) {
                            decisionReason.append("【成功】限價買入 " + actualBuy + " 股。\n");
                            setStopLossAndTakeProfit(currentPrice, volatility);
                        } else {
                            decisionReason.append("【失敗】限價買入。資金或賣單量不足。\n");
                        }
                    }
                } else if (getAccumulatedStocks() > 0 && priceDifferenceRatio > 0 && actionProbability > 0.3) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                    decisionReason.append("【忽略門檻】隨機決定賣出 ").append(sellAmount).append(" 股。");

                    if (random.nextBoolean()) {
                        int actualSell = 市價賣出操作(sellAmount);
                        if (actualSell > 0) {
                            decisionReason.append("【成功】市價賣出 ").append(actualSell).append(" 股。\n");
                        } else {
                            decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                        }
                    } else {
                        int actualSell = 限價賣出操作(sellAmount, currentPrice);
                        if (actualSell > 0) {
                            decisionReason.append("【成功】限價賣出 ").append(actualSell).append(" 股。\n");
                        } else {
                            decisionReason.append("【失敗】限價賣出。持股或買單量不足。\n");
                        }
                    }

                    stopLossPrice = null;
                    takeProfitPrice = null;
                }
            } else {
                // 遵循門檻
                if (priceDifferenceRatio < -dynamicBuyThreshold && availableFunds >= currentPrice && actionProbability > 0.2) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                    decisionReason.append(String.format("【遵循門檻】股價低於 SMA 的 %.2f%% 門檻，買入 %d 股。",
                            dynamicBuyThreshold * 100, buyAmount));

                    if (random.nextBoolean()) {
                        int actualBuy = 市價買入操作(buyAmount);
                        if (actualBuy > 0) {
                            decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                            setStopLossAndTakeProfit(currentPrice, volatility);
                        } else {
                            decisionReason.append("【失敗】市價買入。資金或掛單量不足。\n");
                        }
                    } else {
                        int actualBuy = 限價買入操作(buyAmount, currentPrice);
                        if (actualBuy > 0) {
                            decisionReason.append("【成功】限價買入 " + actualBuy + " 股。\n");
                            setStopLossAndTakeProfit(currentPrice, volatility);
                        } else {
                            decisionReason.append("【失敗】限價買入。資金或賣單量不足。\n");
                        }
                    }
                } else if (priceDifferenceRatio > dynamicSellThreshold && getAccumulatedStocks() > 0 && actionProbability > 0.2) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                    decisionReason.append(String.format("【遵循門檻】股價高於 SMA 的 %.2f%% 門檻，賣出 %d 股。",
                            dynamicSellThreshold * 100, sellAmount));

                    if (random.nextBoolean()) {
                        int actualSell = 市價賣出操作(sellAmount);
                        if (actualSell > 0) {
                            decisionReason.append("【成功】市價賣出 " + actualSell + " 股。\n");
                        } else {
                            decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                        }
                    } else {
                        int actualSell = 限價賣出操作(sellAmount, currentPrice);
                        if (actualSell > 0) {
                            decisionReason.append("【成功】限價賣出 " + actualSell + " 股。\n");
                        } else {
                            decisionReason.append("【失敗】限價賣出。持股或買單量不足。\n");
                        }
                    }

                    stopLossPrice = null;
                    takeProfitPrice = null;
                } else if (random.nextDouble() < 0.05) {
                    executeRandomTransaction(availableFunds, currentPrice, decisionReason, stock);
                }
            }

            // RSI 判斷
            if (rsi < 30 && availableFunds >= currentPrice) {
                int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                decisionReason.append("【RSI < 30】買入訊號，嘗試買入 ").append(buyAmount).append(" 股。");
                int actualBuy = 市價買入操作(buyAmount);
                if (actualBuy > 0) {
                    decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                    setStopLossAndTakeProfit(currentPrice, volatility);
                } else {
                    decisionReason.append("【失敗】市價買入。資金或掛單量不足。\n");
                }
            } else if (rsi > 70 && getAccumulatedStocks() > 0) {
                int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                decisionReason.append("【RSI > 70】賣出訊號，嘗試賣出 ").append(sellAmount).append(" 股。");
                int actualSell = 市價賣出操作(sellAmount);
                if (actualSell > 0) {
                    decisionReason.append("【成功】市價賣出 ").append(actualSell).append(" 股。\n");
                } else {
                    decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                }
                stopLossPrice = null;
                takeProfitPrice = null;
            }

            simulation.updateInfoTextArea(decisionReason.toString());
        } else {
            simulation.updateInfoTextArea("【散戶】尚無法計算 SMA，暫無決策。\n");
        }
    }

    // ========== 停損/止盈處理 ==========
    private void handleStopLossTakeProfit(double currentPrice, StringBuilder decisionReason) {
        // 停損
        if (stopLossPrice != null && currentPrice <= stopLossPrice) {
            int sellAll = getAccumulatedStocks();
            if (sellAll > 0) {
                int actualSell = 市價賣出操作(sellAll);
                if (actualSell > 0) {
                    decisionReason.append("【停損觸發】市價賣出全部 ").append(actualSell).append(" 股。\n");
                } else {
                    decisionReason.append("【停損觸發】失敗，持股或買單量不足。\n");
                }
            }
            stopLossPrice = null;
            takeProfitPrice = null;
        }
        // 止盈
        if (takeProfitPrice != null && currentPrice >= takeProfitPrice) {
            int sellAll = getAccumulatedStocks();
            if (sellAll > 0) {
                int actualSell = 市價賣出操作(sellAll);
                if (actualSell > 0) {
                    decisionReason.append("【止盈觸發】市價賣出全部 ").append(actualSell).append(" 股。\n");
                } else {
                    decisionReason.append("【止盈觸發】失敗，持股或買單量不足。\n");
                }
            }
            stopLossPrice = null;
            takeProfitPrice = null;
        }
    }

    // ========== 隨機操作 ==========
    private void executeRandomTransaction(double availableFunds, double currentPrice, StringBuilder decisionReason, Stock stock) {
        if (random.nextBoolean() && availableFunds >= currentPrice) {
            int buyAmount = random.nextInt(50) + 1; // 1~50 股
            if (random.nextBoolean()) {
                int actualBuy = 市價買入操作(buyAmount);
                if (actualBuy > 0) {
                    decisionReason.append("【隨機操作】市價買入 ").append(actualBuy).append(" 股。\n");
                    setStopLossAndTakeProfit(currentPrice, simulation.getMarketAnalyzer().getVolatility());
                } else {
                    decisionReason.append("【隨機操作】市價買入失敗，資金或掛單不足。\n");
                }
            } else {
                int actualBuy = 限價買入操作(buyAmount, currentPrice);
                if (actualBuy > 0) {
                    decisionReason.append("【隨機操作】限價買入 ").append(actualBuy).append(" 股。\n");
                    setStopLossAndTakeProfit(currentPrice, simulation.getMarketAnalyzer().getVolatility());
                } else {
                    decisionReason.append("【隨機操作】限價買入失敗，資金或賣單不足。\n");
                }
            }
        } else if (getAccumulatedStocks() > 0) {
            int sellAmount = random.nextInt(getAccumulatedStocks()) + 1;
            if (random.nextBoolean()) {
                int actualSell = 市價賣出操作(sellAmount);
                if (actualSell > 0) {
                    decisionReason.append("【隨機操作】市價賣出 ").append(actualSell).append(" 股。\n");
                } else {
                    decisionReason.append("【隨機操作】市價賣出失敗，持股或買單不足。\n");
                }
            } else {
                int actualSell = 限價賣出操作(sellAmount, currentPrice);
                if (actualSell > 0) {
                    decisionReason.append("【隨機操作】限價賣出 ").append(actualSell).append(" 股。\n");
                } else {
                    decisionReason.append("【隨機操作】限價賣出失敗，持股或買單不足。\n");
                }
            }

            stopLossPrice = null;
            takeProfitPrice = null;
        }
    }

    // ========== 實際掛單操作 (成功/失敗印出) ==========
    /**
     * 市價買入操作：先檢查資金，再呼叫 orderBook.marketBuy
     *
     * @param buyAmount 欲買股數
     * @return 實際買入數 (0=失敗)
     */
    private int 市價買入操作(int buyAmount) {
        double price = stock.getPrice();
        double totalCost = price * buyAmount;
        double funds = account.getAvailableFunds();

        if (stock == null) {
            System.out.println("【錯誤】市價買入失敗: stock 為 null");
            return 0;
        }

        if (orderBook == null) {
            System.out.print("【錯誤】市價買入失敗: orderBook 為 null");
            return 0;
        }

        if (funds < totalCost) {
            // 資金不足
            return 0;
        }

        orderBook.marketBuy(this, buyAmount);
        // 若有部分成交亦可視情況而定，這裡直接回傳 buyAmount
        return buyAmount;
    }

    /**
     * 市價賣出操作：先檢查持股，再呼叫 orderBook.marketSell
     *
     * @param sellAmount 欲賣股數
     * @return 實際賣出數 (0=失敗)
     */
    private int 市價賣出操作(int sellAmount) {
        int hold = getAccumulatedStocks();
        if (hold < sellAmount) {
            // 持股不足
            return 0;
        }

        orderBook.marketSell(this, sellAmount);
        return sellAmount;
    }

    /**
     * 限價買入操作：檢查資金 & 市場可賣數量，再掛 buyOrder
     *
     * @param amount 欲買股數
     * @param currentPrice 目前股價
     * @return 實際買入股數 (0=失敗)
     */
    private int 限價買入操作(int amount, double currentPrice) {
        double totalCost = currentPrice * amount;
        double funds = account.getAvailableFunds();

        // 檢查資金
        if (funds < totalCost) {
            return 0;
        }

        // 檢查市場中是否有足夠賣單 (若要模擬失敗的話)
        int availableSell = orderBook.getAvailableSellVolume(currentPrice);
        if (availableSell < amount) {
            return 0;
        }

        // 成功掛限價買單
        Order buyOrder = new Order("buy", currentPrice, amount, this, false, false);
        orderBook.submitBuyOrder(buyOrder, currentPrice);
        return amount;
    }

    /**
     * 限價賣出操作：檢查持股 & 市場可買數量，再掛 sellOrder
     *
     * @param amount 欲賣股數
     * @param currentPrice 目前股價
     * @return 實際賣出股數 (0=失敗)
     */
    private int 限價賣出操作(int amount, double currentPrice) {
        int hold = getAccumulatedStocks();
        if (hold < amount) {
            return 0;
        }
        int availableBuy = orderBook.getAvailableBuyVolume(currentPrice);
        if (availableBuy < amount) {
            return 0;
        }

        // 成功掛限價賣單
        Order sellOrder = new Order("sell", currentPrice, amount, this, false, false);
        orderBook.submitSellOrder(sellOrder, currentPrice);
        return amount;
    }

    // ========== 工具/輔助函數 ==========
    /**
     * 計算交易量，根據波動性調整頭寸大小
     */
    private int calculateTransactionVolume(double availableFunds, double currentPrice, double volatility) {
        // 波動性高 -> 減少頭寸
        double positionSize = (availableFunds / currentPrice) * (1 / (1 + volatility));
        positionSize = positionSize * riskPerTrade;
        return (int) Math.max(1, positionSize);
    }

    /**
     * 計算賣出量，根據波動性調整頭寸大小
     */
    private int calculateSellVolume(double priceDifferenceRatio, double volatility) {
        double positionSize = getAccumulatedStocks() * (0.1 + 0.4 * random.nextDouble()) * (1 / (1 + volatility));
        return (int) Math.max(1, positionSize);
    }

    /**
     * 設置停損 & 止盈價格
     */
    private void setStopLossAndTakeProfit(double currentPrice, double volatility) {
        // 停損: 95%
        stopLossPrice = currentPrice * 0.95;
        // 止盈: 150%
        takeProfitPrice = currentPrice * 1.5;
    }

    /**
     * 是否忽略門檻
     */
    public boolean shouldIgnoreThreshold() {
        return ignoreThreshold;
    }

    /**
     * 獲取散戶持股
     */
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    /**
     * 獲取散戶現金
     */
    public double getCash() {
        return account.getAvailableFunds();
    }

    /**
     * 散戶 ID
     */
    public String getTraderID() {
        return traderID;
    }

    /**
     * 買入門檻
     */
    public double getBuyThreshold() {
        return buyThreshold;
    }

    /**
     * 賣出門檻
     */
    public double getSellThreshold() {
        return sellThreshold;
    }

    /**
     * 動量交易 (若需要可擴充)
     */
    private int calculateMomentumVolume(double volatility) {
        return (int) (500 * volatility * (0.8 + random.nextDouble() * 0.4));
    }
}
