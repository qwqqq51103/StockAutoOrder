package AIStrategies;

import UserManagement.UserAccount;
import Core.Order;
import Core.Trader;
import Core.OrderBook;
import Core.Stock;
import StockMainAction.StockMarketSimulation;
import java.util.Deque;
import java.util.LinkedList;
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

    private double buyThreshold = 0.95;  // 設定新的買入門檻，例如低於 SMA 的 1%
    private double sellThreshold = 3.5; // 設定新的賣出門檻，例如高於 SMA 的 2%

    /* ====== 動態掛單價權重（主力版，可自行調整） ====== */
    private double smaWeight = 0.40;   // 與均線偏離
    private double rsiWeight = 0.25;   // RSI 超買/超賣
    private double volatilityWeight = 0.35;   // 波動度
    private double maxOffsetRatio = 0.10;   // 最多 ±10 %

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
     * 更新交易者在交易後的帳戶狀態 (限價單)
     *
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            // 限價單買入：增加股數
            account.incrementStocks(volume);

            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();

            // 更新目標價
            calculateTargetPrice();

            System.out.println(String.format("【限價買入後更新】主力買入 %d 股，成交價 %.2f，更新後平均成本價 %.2f",
                    volume, price, averageCostPrice));

        } else if ("sell".equals(type)) {
            // 限價單賣出：增加現金
            account.incrementFunds(transactionAmount);

            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }

            System.out.println(String.format("【限價賣出後更新】主力賣出 %d 股，成交價 %.2f，更新後持股 %d 股",
                    volume, price, getAccumulatedStocks()));
        }

        // 更新界面上的標籤
        simulation.updateLabels();
    }

    /**
     * 更新交易者在交易後的帳戶狀態 (市價單)
     *
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    public void updateAverageCostPrice(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();

            // 更新目標價
            calculateTargetPrice();

            System.out.println(String.format("【市價買入後更新】主力買入 %d 股，成交價 %.2f，更新後平均成本價 %.2f",
                    volume, price, averageCostPrice));

        } else if ("sell".equals(type)) {
            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }

            System.out.println(String.format("【市價賣出後更新】主力賣出 %d 股，成交價 %.2f，更新後持股 %d 股",
                    volume, price, getAccumulatedStocks()));
        }

        // 更新界面上的標籤
        simulation.updateLabels();
    }

    /**
     * 主力行為：根據市場狀況做出交易決策
     */
    public void makeDecision() {
        double currentPrice = stock.getPrice();
        double availableFunds = account.getAvailableFunds();
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double rsi = simulation.getMarketAnalyzer().getRSI();
        double volatility = simulation.getMarketAnalyzer().calculateVolatility();
        double riskFactor = getRiskFactor(); // 計算主力的風險係數

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;
            // 限制價格差在 [-0.5, 0.5] 之間
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));

            double actionProbability = random.nextDouble();

            // 1. 動量交易策略（追漲買入）
            if (actionProbability < 0.1 && currentPrice > sma && volatility > 0.02) {
                int momentumVolume = calculateMomentumVolume(volatility);
                // 僅呼叫吸籌操作，不印成功與否，交由功能函數判斷
                吸籌操作(momentumVolume);

                // 2. 均值回歸策略
            } else if (actionProbability < 0.15 && Math.abs(priceDifferenceRatio) > 0.1) {
                if (priceDifferenceRatio > 0) {
                    // 股價高於均線一定幅度 -> 逢高賣出
                    int revertSellVolume = calculateRevertSellVolume(priceDifferenceRatio);
                    賣出操作(revertSellVolume);
                } else {
                    // 股價低於均線一定幅度 -> 逢低買入
                    int revertBuyVolume = calculateRevertBuyVolume(priceDifferenceRatio);
                    吸籌操作(revertBuyVolume);
                }

                // 3. 價值投資策略（低估買入）
            } else if (currentPrice < sma * 0.9 && actionProbability < 0.2) {
                int valueBuyVolume = calculateValueBuyVolume();
                吸籌操作(valueBuyVolume);

                // 4. 風險控制策略（風險過高時暫停操作）
            } else if (riskFactor > 10) {
                System.out.println(String.format("【風險管控】風險係數 %.2f 過高，主力暫停操作。", riskFactor));
                return;

                // 5. 市價買入（追蹤市場流動性）
            } else if (actionProbability < 0.25 && availableFunds > currentPrice) {
                int buyQuantity = calculateLiftVolume();
                拉抬操作(buyQuantity);

                // 6. 市價賣出（減少風險）
            } else if (actionProbability < 0.3 && getAccumulatedStocks() > 0) {
                int sellQuantity = calculateSellVolume();
                市價賣出操作(sellQuantity);

                // 7. 高風險行為：洗盤
            } else if (actionProbability < 0.35 && getAccumulatedStocks() > 0) {
                int washVolume = calculateWashVolume(volatility);
                洗盤操作(washVolume);

                // 8. 隨機取消掛單
            } else if (actionProbability < 0.4) {
                if (!orderBook.getBuyOrders().isEmpty()) {
                    Order orderToCancel = orderBook.getBuyOrders().get(0);
                    //orderBook.cancelOrder(orderToCancel.getId());
                    System.out.println(String.format("【隨機取消掛單】取消買單 ID: %s，數量: %d 股",
                            orderToCancel.getId(), orderToCancel.getVolume()));
                }
            }
        } else {
            System.out.println("【決策提示】尚無法計算 SMA，主力正在觀望中。");
        }
    }

    /**
     * 吸籌操作：實際嘗試掛買單
     *
     * @param volume 想要買入的股數
     * @return 實際成功買入的股數 (0 表示失敗)
     */
    public int 吸籌操作(int volume) {
        double limitPrice = computeBuyLimitPrice(stock.getPrice(),
                simulation.getMarketAnalyzer().getSMA(),
                simulation.getMarketAnalyzer().getRSI(),
                simulation.getMarketAnalyzer().getVolatility());

        int availableVolume = orderBook.getAvailableSellVolume(limitPrice);
        if (availableVolume < volume) {
            return 0;
        }
        if (account.getAvailableFunds() < limitPrice * volume) {
            return 0;
        }

        Order buyOrder = new Order("buy", limitPrice, volume, this, false, false);
        orderBook.submitBuyOrder(buyOrder, limitPrice);

        System.out.printf("【吸籌】掛買單 %d 股 @ %.2f%n", volume, limitPrice);
        return volume;
    }

    /**
     * 賣出操作：實際嘗試掛賣單
     *
     * @param volume 想要賣出的股數
     * @return 實際成功賣出的股數 (0 表示失敗)
     */
    public int 賣出操作(int volume) {
        double limitPrice = computeSellLimitPrice(stock.getPrice(),
                simulation.getMarketAnalyzer().getSMA(),
                simulation.getMarketAnalyzer().getRSI(),
                simulation.getMarketAnalyzer().getVolatility());

        int availableBuy = orderBook.getAvailableBuyVolume(limitPrice);
        if (availableBuy < volume) {
            return 0;
        }
        if (getAccumulatedStocks() < volume) {
            return 0;
        }

        Order sellOrder = new Order("sell", limitPrice, volume, this, false, false);
        orderBook.submitSellOrder(sellOrder, limitPrice);

        System.out.printf("【賣出】掛賣單 %d 股 @ %.2f%n", volume, limitPrice);
        return volume;
    }

    /**
     * 洗盤操作：實際嘗試大量賣出
     *
     * @param volume 想要用於洗盤的賣出量
     * @return 實際成功洗盤的股數 (0 表示失敗)
     */
    public int 洗盤操作(int volume) {
        double price = stock.getPrice();
        if (getAccumulatedStocks() < volume) {
//            System.out.println(String.format("【洗盤操作】失敗：主力持股不足，欲洗 %d 股，但僅有 %d 股",
//                    volume, getAccumulatedStocks()));
            return 0;
        }

        Order sellOrder = new Order("sell", price, volume, this, false, false);
        orderBook.submitSellOrder(sellOrder, price);

        System.out.println(String.format("【洗盤操作】成功：一次賣出 %d 股，價格 %.2f，用於壓低股價",
                volume, price));

        return volume;
    }

    /**
     * 拉抬操作：主力市價買入（盡力推高股價）
     *
     * @param volume 想要拉抬的股數
     * @return 實際成功拉抬的股數 (0 表示失敗)
     */
    public int 拉抬操作(int volume) {
        double price = stock.getPrice();
        if (account.getAvailableFunds() < price * volume) {
//            System.out.println(String.format("【拉抬操作】失敗：主力可用資金不足，買 %d 股需要 %.2f 元，僅剩 %.2f 元",
//                    volume, price * volume, account.getAvailableFunds()));
            return 0;
        }

        // 成功掛單 (市價單以 marketBuy 實現, 或這裡直接下買單)
        orderBook.marketBuy(this, volume);

        System.out.println(String.format("【拉抬操作】成功：市價買入 %d 股，預計成本上限 %.2f",
                volume, price * volume));

        return volume;
    }

    /**
     * 市價賣出操作
     *
     * @param volume 想要賣出的股數
     * @return 實際成功賣出的股數
     */
    public int 市價賣出操作(int volume) {
        if (getAccumulatedStocks() < volume) {
//            System.out.println(String.format("【市價賣出】失敗：主力持股不足，欲賣 %d 股，但僅有 %d 股",
//                    volume, getAccumulatedStocks()));
            return 0;
        }

        // 成功市價賣出
        orderBook.marketSell(this, volume);

        System.out.println(String.format("【市價賣出】成功：預計賣出 %d 股 (最終成交量視訂單簿而定)",
                volume));
        return volume;
    }

    // ==== 各種輔助計算函數 ====
    /**
     * 主力買單理想掛價（低接）
     */
    private double computeBuyLimitPrice(double currentPrice, double sma,
            double rsi, double volatility) {

        double smaOffset = (sma == 0) ? 0 : -(currentPrice - sma) / sma; // 低於均線 → 正值
        double rsiOffset = Double.isNaN(rsi) ? 0 : (50.0 - rsi) / 100.0; // RSI 30 → +0.20
        double volOffset = -volatility * 0.6;                            // 波動大 → 掛更低

        double totalOffset = smaOffset * smaWeight
                + rsiOffset * rsiWeight
                + volOffset * volatilityWeight;

        totalOffset = Math.max(-maxOffsetRatio, Math.min(totalOffset, maxOffsetRatio));
        return orderBook.adjustPriceToUnit(currentPrice * (1.0 + totalOffset));
    }

    /**
     * 主力賣單理想掛價（逢高）
     */
    private double computeSellLimitPrice(double currentPrice, double sma,
            double rsi, double volatility) {

        double smaOffset = (sma == 0) ? 0 : (currentPrice - sma) / sma; // 高於均線 → 正值
        double rsiOffset = Double.isNaN(rsi) ? 0 : (rsi - 50.0) / 100.0; // RSI 70 → +0.20
        double volOffset = volatility * 0.6;                            // 波動大 → 掛更高

        double totalOffset = smaOffset * smaWeight
                + rsiOffset * rsiWeight
                + volOffset * volatilityWeight;

        totalOffset = Math.max(-maxOffsetRatio, Math.min(totalOffset, maxOffsetRatio));
        return orderBook.adjustPriceToUnit(currentPrice * (1.0 + totalOffset));
    }

    /**
     * 計算目標價 (平均成本 + 預期利潤 + 波動性因子)
     *
     * @return 目標價
     */
    public double calculateTargetPrice() {
        targetPrice = averageCostPrice * (1 + EXPECTED_PROFIT_MARGIN + VOLATILITY_FACTOR * volatility);
        return targetPrice;
    }

    /**
     * 動量交易 - 計算欲買入量
     *
     * @param volatility 波動性
     * @return 動量交易量
     */
    public int calculateMomentumVolume(double volatility) {
        int volume = (int) (500 * volatility * (0.8 + random.nextDouble() * 0.4));
        System.out.println(String.format("【計算動量買入量】預估可買 %d 股 (波動性 %.3f)", volume, volatility));
        return volume;
    }

    /**
     * 均值回歸 - 計算賣出量 (priceDifferenceRatio > 0)
     *
     * @param priceDifferenceRatio 價格差異比例
     * @return 建議賣出量
     */
    public int calculateRevertSellVolume(double priceDifferenceRatio) {
        int volume = (int) (getAccumulatedStocks() * priceDifferenceRatio * (0.5 + random.nextDouble() * 0.5));
        volume = Math.min(volume, getAccumulatedStocks()); // 不可超過持股數
        System.out.println(String.format("【計算均值回歸賣出量】建議賣出 %d 股 (價格差異比例 %.2f)", volume, priceDifferenceRatio));
        return volume;
    }

    /**
     * 均值回歸 - 計算買入量 (priceDifferenceRatio < 0)
     *
     * @param priceDifferenceRatio 價格差異比例
     * @return 建議買入量
     */
    public int calculateRevertBuyVolume(double priceDifferenceRatio) {
        double funds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        int maxAffordable = (int) (funds / currentPrice);
        int volume = (int) (maxAffordable * -priceDifferenceRatio * (0.5 + random.nextDouble() * 0.5));
        System.out.println(String.format("【計算均值回歸買入量】建議買入 %d 股 (可用資金 %.2f, 價格差異 %.2f)",
                volume, funds, priceDifferenceRatio));
        return volume;
    }

    /**
     * 價值投資 - 計算買入量 (股價 < 0.9 * SMA) @ret
     *
     *
     * urn 買入量
     */
    public int calculateValueBuyVolume() {
        double funds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        int volume = (int) (funds / currentPrice * 0.5); // 資金的 50% 投入
        System.out.println(String.format("【計算價值買入量】建議買入 %d 股 (資金 %.2f, 價格 %.2f)",
                volume, funds, currentPrice));
        return volume;
    }

    /**
     * 根據波動性計算洗盤量
     *
     * @param volatility 波動性
     * @return 洗盤量
     */
    public int calculateWashVolume(double volatility) {
        return Math.max((int) (volatility * 1000), 50); // 最少 50 股
    }

    /**
     * 根據市場狀況計算拉抬量
     *
     * @return 拉抬量
     */
    public int calculateLiftVolume() {
        return random.nextInt(500) + 1; // 1~500 股
    }

    /**
     * 市價賣出計算賣出量 (10%~30% 持股)
     *
     * @return 賣出量
     */
    public int calculateSellVolume() {
        int hold = getAccumulatedStocks();
        if (hold <= 0) {
            return 0;
        }
        double ratio = 0.1 + 0.2 * random.nextDouble();
        int vol = (int) (hold * ratio);
        vol = Math.max(1, vol);
        System.out.println(String.format("【計算市價賣出量】預計賣出 %d 股 (目前持股 %d)", vol, hold));
        return vol;
    }

    /**
     * 取得當前持股數量
     *
     * @return 主力持股量
     */
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    /**
     * 取得主力現金餘額
     *
     * @return 可用資金
     */
    public double getCash() {
        return account.getAvailableFunds();
    }

    /**
     * 取得主力目標價格
     */
    public double getTargetPrice() {
        return targetPrice;
    }

    /**
     * 取得主力平均成本
     */
    public double getAverageCostPrice() {
        return averageCostPrice;
    }

    /**
     * 返回交易紀錄 (如需)
     */
    public String getTradeLog() {
        return tradeLog.toString();
    }

    /**
     * 計算風險係數
     */
    private double getRiskFactor() {
        double totalAssets = account.getAvailableFunds() + stock.getPrice() * getAccumulatedStocks();
        if (totalAssets <= 0) {
            return 0.0;
        }
        double risk = 1 - (account.getAvailableFunds() / totalAssets);
        return Math.max(0, Math.min(risk, 1));
    }
}
