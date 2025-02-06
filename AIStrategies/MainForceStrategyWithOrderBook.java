package AIStrategies;

import UserManagement.UserAccount;
import Core.Order;
import Core.Trader;
import Core.OrderBook;
import Core.Stock;
import StockMainAction.StockMarketSimulation;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Queue;
import java.util.Random;

/**
 * 主力策略 - 處理主力的操作策略
 */
public class MainForceStrategyWithOrderBook implements Trader {

    private OrderBook orderBook;
    private Stock stock;
    private StockMarketSimulation simulation;
    private StringBuilder tradeLog;
    private double targetPrice;         // 主力目標價位
    private double averageCostPrice;    // 目前籌碼的平均成本價格
    private Random random;
    private UserAccount account;        // 主力的 UserAccount
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

    /**
     * 構造函數
     *
     * @param orderBook 訂單簿實例
     * @param stock 股票實例
     * @param simulation 模擬實例
     * @param initialCash 初始現金
     */
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

    // Trader 接口實現
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
        return "MAIN_FORCE";
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

            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();

            // 更新目標價
            calculateTargetPrice();

        } else if (type.equals("sell")) {
            // 現價賣出：增加現金
            account.incrementFunds(transactionAmount);

            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }
        }

        // 更新界面上的標籤
        simulation.updateLabels();
    }

    /**
     * 更新交易者在交易後的帳戶狀態
     * 因市價單不會經過訂單簿，故使用此函數計算平均價格
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    public void updateAverageCostPrice(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if (type.equals("buy")) {
            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();

            // 更新目標價
            calculateTargetPrice();

        } else if (type.equals("sell")) {
            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }
        }

        // 更新界面上的標籤
        simulation.updateLabels();
    }

    // 主力行為
    public void makeDecision() {
        double currentPrice = stock.getPrice();
        double availableFunds = account.getAvailableFunds();
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double volatility = simulation.getMarketAnalyzer().calculateVolatility();
        double riskFactor = getRiskFactor(); // 計算主力的風險係數

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

            double randomBuyThreshold = buyThreshold * (1 + (random.nextDouble() - 0.5) * 0.4);
            double randomSellThreshold = sellThreshold * (1 + (random.nextDouble() - 0.5) * 0.4);
            double actionProbability = random.nextDouble();

            // 1. 動量交易策略
            if (actionProbability < 0.1 && currentPrice > sma && volatility > 0.02) {
                int momentumVolume = calculateMomentumVolume(volatility);
                accumulateStock(momentumVolume);
                System.out.println("動量交易：追漲買入 " + momentumVolume + " 股。");

                // 2. 均值回歸策略
            } else if (actionProbability < 0.15 && Math.abs(priceDifferenceRatio) > 0.1) {
                if (priceDifferenceRatio > 0) {
                    int revertSellVolume = calculateRevertSellVolume(priceDifferenceRatio);
                    sellStock(revertSellVolume);
                    System.out.println("均值回歸：賣出 " + revertSellVolume + " 股。");
                } else {
                    int revertBuyVolume = calculateRevertBuyVolume(priceDifferenceRatio);
                    accumulateStock(revertBuyVolume);
                    System.out.println("均值回歸：買入 " + revertBuyVolume + " 股。");
                }

                // 3. 價值投資策略
            } else if (currentPrice < sma * 0.9 && actionProbability < 0.2) {
                int valueBuyVolume = calculateValueBuyVolume();
                accumulateStock(valueBuyVolume);
                System.out.println("價值投資：低估買入 " + valueBuyVolume + " 股。");

                // 4. 風險控制策略
            } else if (riskFactor > 0.7) {
                //System.out.println("風險過高，主力觀望，暫停操作。");
                return;

                // 5. 市價買入（追蹤市場流動性）
            } else if (actionProbability < 0.25 && availableFunds > currentPrice) {
                int buyQuantity = calculateLiftVolume();
                orderBook.marketBuy(this, buyQuantity);
                //System.out.println("市價買入：買入 " + buyQuantity + " 股。");

                // 6. 市價賣出（減少風險）
            } else if (actionProbability < 0.3 && getAccumulatedStocks() > 0) {
                int sellQuantity = calculateSellVolume();
                orderBook.marketSell(this, sellQuantity);
                //System.out.println("市價賣出：賣出 " + sellQuantity + " 股。");

                // 7. 高風險行為：洗盤
            } else if (actionProbability < 0.35 && getAccumulatedStocks() > 0) {
                int washVolume = calculateWashVolume(volatility);
                simulateWashTrading(washVolume);
                //System.out.println("洗盤：模擬交易 " + washVolume + " 股。");

                // 8. 隨機取消掛單
            } else if (actionProbability < 0.4) {
                if (!orderBook.getBuyOrders().isEmpty()) {
                    Order orderToCancel = orderBook.getBuyOrders().get(0);
                    orderBook.cancelOrder(orderToCancel.getId());
                    //System.out.println("取消掛單，單號：" + orderToCancel.getId());
                }
            }

        } else {
            System.out.println("主力正在收集趨勢數據，無法計算 SMA。\n");
        }
    }

    /**
     * 根據波動性調整洗盤量
     *
     * @param volatility 波動性
     * @return 洗盤量
     */
    private int calculateWashVolume(double volatility) {
        return Math.max((int) (volatility * 1000), 50); // 波動性越高，洗盤量越大，最小 50 股
    }

    /**
     * 根據市場狀況計算拉抬量
     *
     * @return 拉抬量
     */
    private int calculateLiftVolume() {
        return random.nextInt(500) + 1; // 隨機決定拉抬量，1 到 500 股之間
    }

    /**
     * 計算目標價
     *
     * @return 目標價
     */
    public double calculateTargetPrice() {
        // 基於平均成本和波動性來動態調整目標價
        targetPrice = averageCostPrice * (1 + EXPECTED_PROFIT_MARGIN + VOLATILITY_FACTOR * volatility);
        return targetPrice;
    }

    /**
     * 主力賣出操作，並考慮調整平均成本價
     *
     * @param volume 賣出量
     */
    public void sellStock(int volume) {
        double price = stock.getPrice();

        // 檢查市場中是否有足夠的買單
        int availableVolume = orderBook.getAvailableBuyVolume(price); // 假設此方法返回買單的總量
        if (availableVolume < volume) {
            //System.out.println("市場買單不足，無法完成賣出操作");
            return;
        }

        if (getAccumulatedStocks() >= volume) {
            Order sellOrder = new Order("sell", price, volume, this, false, false);
            orderBook.submitSellOrder(sellOrder, price);
            //System.out.println(String.format("主力下賣單 %d 股，價格 %.2f，當前持股 %d 股", volume, price, getAccumulatedStocks()));
        } else {
            //System.out.println("主力持股不足，無法賣出\n");
        }
    }

    /**
     * 洗盤操作
     *
     * @param volume 洗盤量
     */
    public void simulateWashTrading(int volume) {
        if (getAccumulatedStocks() >= volume) {
            double price = stock.getPrice();
            // 創建賣單（大量賣出以壓低股價），將主力帳戶作為交易者傳遞進去
            Order sellOrder = new Order("sell", price, volume, this, false, false);
            orderBook.submitSellOrder(sellOrder, price);

            //System.out.println(String.format("主力進行洗盤，賣出 %d 股，價格 %.2f", volume, price));
        } else {
            //System.out.println("主力持股不足，無法進行洗盤\n");
        }
    }

    /**
     * 吸籌操作並更新平均成本價格
     *
     * @param volume 吸籌量
     */
    public void accumulateStock(int volume) {

        double price = stock.getPrice();
        double cost = price * volume;

        // 檢查市場中是否有足夠的賣單
        int availableVolume = orderBook.getAvailableSellVolume(price); // 假設此方法返回賣單的總量
        if (availableVolume < volume) {
            //System.out.println("市場賣單不足，無法完成吸籌操作");
            return;
        }

        // 創建並提交買單
        Order buyOrder = new Order("buy", price, volume, this, false, false);
        orderBook.submitBuyOrder(buyOrder, price);
        //System.out.println(String.format("主力下買單 %d 股，價格 %.2f，剩餘現金 %.2f 元", volume, price, account.getAvailableFunds()));
    }

    /**
     * 拉抬操作
     *
     * @param volume 拉抬量
     */
    public void liftStock(int volume) {
        double price = stock.getPrice();
        double cost = price * volume;

        // 創建買單（大量買入以推高股價），將主力帳戶作為交易者傳遞進去
        Order buyOrder = new Order("buy", price, volume, this, true, false);
        orderBook.submitBuyOrder(buyOrder, price);

        //System.out.println(String.format("主力進行拉抬，買入 %d 股，價格 %.2f，剩餘現金 %.2f 元", volume, price, account.getAvailableFunds()));
    }

    /**
     * 市價賣出根據市場條件計算賣出量
     *
     * @return 賣出量
     */
    private int calculateSellVolume() {
        int accumulatedStocks = getAccumulatedStocks(); // 當前主力持有的股票量
        if (accumulatedStocks <= 0) {
            return 0; // 如果無持股，返回0
        }

        // 設置賣出量比例 (例如，賣出 10% 到 30% 的持股)
        double sellPercentage = 0.1 + (0.2 * random.nextDouble()); // 隨機取10%到30%之間
        int sellVolume = (int) (accumulatedStocks * sellPercentage);

        // 確保賣出量至少為1，且不超過當前持股量
        sellVolume = Math.max(1, Math.min(sellVolume, accumulatedStocks));

        //System.out.println("計算的市價賣出量為: " + sellVolume);
        return sellVolume;
    }

    /**
     * 返回交易紀錄
     *
     * @return 交易紀錄字串
     */
    public String getTradeLog() {
        return tradeLog.toString();
    }

    /**
     * 獲取主力股票數量
     *
     * @return 主力持股量
     */
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    /**
     * 獲取主力現金
     *
     * @return 主力現金
     */
    public double getCash() {
        return account.getAvailableFunds();
    }

    /**
     * 獲取主力目標價格
     *
     * @return 目標價格
     */
    public double getTargetPrice() {
        return targetPrice;
    }

    /**
     * 獲取主力成本價格
     *
     * @return 平均成本價格
     */
    public double getAverageCostPrice() {
        return averageCostPrice;
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

    /**
     * 均值回歸 - 計算賣出量
     *
     * @param priceDifferenceRatio 價格差異比例
     * @return 均值回歸賣出量
     */
    private int calculateRevertSellVolume(double priceDifferenceRatio) {
        return (int) (getAccumulatedStocks() * priceDifferenceRatio * (0.5 + random.nextDouble() * 0.5));
    }

    /**
     * 均值回歸 - 計算買入量
     *
     * @param priceDifferenceRatio 價格差異比例
     * @return 均值回歸買入量
     */
    private int calculateRevertBuyVolume(double priceDifferenceRatio) {
        double availableFunds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        int maxAffordable = (int) (availableFunds / currentPrice);
        return (int) (maxAffordable * -priceDifferenceRatio * (0.5 + random.nextDouble() * 0.5));
    }

    /**
     * 價值投資 - 計算買入量
     *
     * @return 價值投資買入量
     */
    private int calculateValueBuyVolume() {
        double availableFunds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        return (int) (availableFunds / currentPrice * 0.5); // 資金的 50% 投入
    }

    /**
     * 風險管控 - 計算風險係數
     *
     * @return 風險係數
     */
    private double getRiskFactor() {
        double totalAssets = account.getAvailableFunds() + stock.getPrice() * getAccumulatedStocks();
        double risk = 1 - (account.getAvailableFunds() / totalAssets);
        return Math.max(0, Math.min(risk, 1)); // 保證在 [0, 1] 區間內
    }
}
