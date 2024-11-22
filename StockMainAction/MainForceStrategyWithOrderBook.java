package StockMainAction;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
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
//                System.out.println("風險過高，主力觀望，暫停操作。");1
                return;

                // 5. 市價買入（追蹤市場流動性）
            } else if (actionProbability < 0.25 && availableFunds > currentPrice) {
                int buyQuantity = calculateLiftVolume();
                marketBuy(buyQuantity);
//                System.out.println("市價買入：買入 " + buyQuantity + " 股。");

                // 6. 市價賣出（減少風險）
            } else if (actionProbability < 0.3 && getAccumulatedStocks() > 0) {
                int sellQuantity = calculateSellVolume();
                marketSell(sellQuantity);
//                System.out.println("市價賣出：賣出 " + sellQuantity + " 股。");

                // 7. 高風險行為：洗盤
            } else if (actionProbability < 0.35 && getAccumulatedStocks() > 0) {
                int washVolume = calculateWashVolume(volatility);
                simulateWashTrading(washVolume);
//                System.out.println("洗盤：模擬交易 " + washVolume + " 股。");

                // 8. 隨機取消掛單
            } else if (actionProbability < 0.4) {
                if (!orderBook.getBuyOrders().isEmpty()) {
                    Order orderToCancel = orderBook.getBuyOrders().get(0);
                    cancelOrder(orderToCancel.getId());
//                    System.out.println("取消掛單，單號：" + orderToCancel.getId());1
                }
            }

        } else {
//            System.out.println("主力正在收集趨勢數據，無法計算 SMA。\n");1
        }
    }

    //市價買入
    public void marketBuy(int quantity) {
        double remainingFunds = account.getAvailableFunds();
        int remainingQuantity = quantity;
        double marketTotalTransactionValue = 0.0;
        int marketTotalTransactionVolume = 0;

        // 使用 ListIterator 來安全地進行修改
        ListIterator<Order> iterator = orderBook.getSellOrders().listIterator();
        while (iterator.hasNext() && remainingQuantity > 0) {
            Order sellOrder = iterator.next();
            double transactionPrice = sellOrder.getPrice();
            int transactionVolume = Math.min(sellOrder.getVolume(), remainingQuantity);
            double transactionCost = transactionPrice * transactionVolume;

            // 自成交檢查，避免買到自己的限價賣單
            if (sellOrder.getTrader() == this) {
//                System.out.println("主力 跳過與自己的訂單匹配，賣單資訊：" + sellOrder);
                continue;
            }

            if (remainingFunds >= transactionCost) {
                remainingFunds -= transactionCost;
                remainingQuantity -= transactionVolume;

                // 更新主力的持股量和資金
                account.incrementStocks(transactionVolume);
                account.decrementFunds(transactionCost);

                System.out.println("主力 市價買進，價格: " + transactionPrice + "，數量: " + transactionVolume);

                // 更新散戶的帳戶（賣單交易者）
                if (sellOrder.getTrader() instanceof RetailInvestorAI) {
                    RetailInvestorAI seller = (RetailInvestorAI) sellOrder.getTrader();
                    seller.getAccount().decrementStocks(transactionVolume);
                    seller.getAccount().incrementFunds(transactionCost);
                    System.out.println("更新散戶帳戶，增加資金：" + transactionCost + "，扣減股票：" + transactionVolume);
                }

                // 累加市價單的成交值和成交量
                marketTotalTransactionValue += transactionPrice * transactionVolume;
                marketTotalTransactionVolume += transactionVolume;

                // 如果賣單已全部成交，移除該賣單
                if (sellOrder.getVolume() == transactionVolume) {
                    iterator.remove(); // 使用 iterator 的 remove 方法來移除
                } else {
                    sellOrder.setVolume(sellOrder.getVolume() - transactionVolume);
                }
            } else {
//                System.out.println("主力現金不足，無法完成市價買進剩餘數量");
                break;
            }
        }

        if (marketTotalTransactionVolume > 0) {
            orderBook.addMarketTransactionData(marketTotalTransactionValue, marketTotalTransactionVolume);
        }

        simulation.updateLabels();
        simulation.updateVolumeChart(marketTotalTransactionVolume);
        simulation.updateOrderBookDisplay();
    }

    //市價賣出方法
    public void marketSell(int quantity) {
        double remainingQuantity = quantity;
        double marketTotalTransactionValue = 0.0;
        int marketTotalTransactionVolume = 0;

        // 使用 ListIterator 安全地遍歷買單列表
        ListIterator<Order> iterator = orderBook.getBuyOrders().listIterator();

        while (iterator.hasNext() && remainingQuantity > 0) {
            Order buyOrder = iterator.next();
            double transactionPrice = buyOrder.getPrice();
            int transactionVolume = Math.min(buyOrder.getVolume(), (int) remainingQuantity);

            // 檢查是否與自己交易（避免自我匹配）
            if (buyOrder.getTrader() == this) {
//                System.out.println("主力 跳過與自己的訂單匹配，買單資訊：" + buyOrder);
                continue;
            }

            double transactionRevenue = transactionPrice * transactionVolume;

            // 確認持股量足夠進行賣出
            if (account.getStockInventory() >= transactionVolume) {
                // 減少持股量和剩餘賣出數量
                account.decrementStocks(transactionVolume);
                remainingQuantity -= transactionVolume;

                // 增加資金
                account.incrementFunds(transactionRevenue);

                System.out.println("主力 市價賣出，價格: " + transactionPrice + "，數量: " + transactionVolume + "，匹配的買單資訊：" + buyOrder);
                
                // 累加市價單的成交值和成交量
                marketTotalTransactionValue += transactionPrice * transactionVolume;
                marketTotalTransactionVolume += transactionVolume;

                // 如果買單已全部成交，移除該買單
                if (buyOrder.getVolume() == transactionVolume) {
                    iterator.remove();
                } else {
                    // 否則更新買單的剩餘數量
                    buyOrder.setVolume(buyOrder.getVolume() - transactionVolume);
                }
            } else {
//                System.out.println("主力 持股不足，無法完成市價賣出剩餘數量");
                break;
            }
        }

        // 更新累計的成交值和成交量
        if (marketTotalTransactionVolume > 0) {
            orderBook.addMarketTransactionData(marketTotalTransactionValue, marketTotalTransactionVolume);
        }

        // 更新用戶界面
        simulation.updateLabels();
        simulation.updateVolumeChart(marketTotalTransactionVolume);
        simulation.updateOrderBookDisplay();
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
//            System.out.println("已取消買單：");
//            System.out.println("訂單ID：" + orderId);
//            System.out.println("股票數量：" + canceledOrder.getVolume());
//            System.out.println("單價：" + canceledOrder.getPrice());
//            System.out.println("已退還資金：" + refundAmount);
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
//                System.out.println("已取消賣單：");
//                System.out.println("訂單ID：" + orderId);
//                System.out.println("股票數量：" + canceledOrder.getVolume());
//                System.out.println("單價：" + canceledOrder.getPrice());
//                System.out.println("已退還股票數量：" + canceledOrder.getVolume());
            } else {
//                System.out.println("訂單ID " + orderId + " 未找到，無法取消。");
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
        return random.nextInt(500) + 1; // 隨機決定拉抬量，100 到 600 股之間
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
//            System.out.println(String.format("主力下賣單 %d 股，價格 %.2f，當前持股 %d 股", volume, price, getAccumulatedStocks()));
        } else {
//            System.out.println("主力持股不足，無法賣出\n");
        }
    }

    // 洗盤操作
    public void simulateWashTrading(int volume) {
        if (getAccumulatedStocks() >= volume) {
            double price = stock.getPrice();
            // 創建賣單（大量賣出以壓低股價），將主力帳戶作為交易者傳遞進去
            Order sellOrder = new Order("sell", price, volume, "MainForce", this, account, false, false);
            orderBook.submitSellOrder(sellOrder, price);

//            System.out.println(String.format("主力進行洗盤，賣出 %d 股，價格 %.2f", volume, price));
        } else {
//            System.out.println("主力持股不足，無法進行洗盤\n");
        }
    }

    // 吸籌操作並更新平均成本價格
    public void accumulateStock(int volume) {

        double price = stock.getPrice();
        double cost = price * volume;

        // 檢查市場中是否有足夠的賣單
        int availableVolume = orderBook.getAvailableSellVolume(price); // 假設此方法返回賣單的總量
        if (availableVolume < volume) {
//            System.out.println("市場賣單不足，無法完成吸籌操作");
            return;
        }

        // 創建並提交買單
        Order buyOrder = new Order("buy", price, volume, "MainForce", this, account, false, false);
        orderBook.submitBuyOrder(buyOrder, price);
//      System.out.println(String.format("主力下買單 %d 股，價格 %.2f，剩餘現金 %.2f 元", volume, price, account.getAvailableFunds()));
    }

    // 拉抬操作
    public void liftStock(int volume) {
        double price = stock.getPrice();
        double cost = price * volume;

        // 創建買單（大量買入以推高股價），將主力帳戶作為交易者傳遞進去
        Order buyOrder = new Order("buy", price, volume, "MainForce", this, account, true, false);
        orderBook.submitBuyOrder(buyOrder, price);

//        System.out.println(String.format("主力進行拉抬，買入 %d 股，價格 %.2f，剩餘現金 %.2f 元", volume, price, account.getAvailableFunds()));
    }

    // 在訂單成交時更新現金和持股量
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if (type.equals("buy")) {
            //限價單買入 不扣資金 增加股數
            account.incrementStocks(volume);

            // 更新平均成本價
            double totalInvestment = averageCostPrice * (getAccumulatedStocks() - volume) + transactionAmount;
            averageCostPrice = totalInvestment / getAccumulatedStocks();

            // 更新目標價
            calculateTargetPrice();

        } else if (type.equals("sell")) {
            //現價賣出 不扣股數 增加現金
            account.incrementFunds(transactionAmount);

            // 若持股為零，重置平均成本價
            if (getAccumulatedStocks() == 0) {
                averageCostPrice = 0.0;
            }
        }

        // 更新界面上的標籤
        simulation.updateLabels();
    }

    // 市價賣出根據市場條件計算賣出量
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

        System.out.println("計算的市價賣出量為: " + sellVolume);
        return sellVolume;
    }

    // 返回交易紀錄
    public String getTradeLog() {
        return tradeLog.toString();
    }

    //獲取主力股票數量
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    //獲取主力現金
    public double getCash() {
        return account.getAvailableFunds();
    }

    //獲取帳戶
    public UserAccount getAccount() {
        return account;
    }

    //獲取主力目標價格
    public double getTargetPrice() {
        return targetPrice;
    }

    //獲取主力成本價格
    public double getAverageCostPrice() {
        return averageCostPrice;
    }

    //動量交易
    private int calculateMomentumVolume(double volatility) {
        return (int) (500 * volatility * (0.8 + random.nextDouble() * 0.4));
    }

    //均值回歸
    private int calculateRevertSellVolume(double priceDifferenceRatio) {
        return (int) (getAccumulatedStocks() * priceDifferenceRatio * (0.5 + random.nextDouble() * 0.5));
    }

    //均值回歸
    private int calculateRevertBuyVolume(double priceDifferenceRatio) {
        double availableFunds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        int maxAffordable = (int) (availableFunds / currentPrice);
        return (int) (maxAffordable * -priceDifferenceRatio * (0.5 + random.nextDouble() * 0.5));
    }

    //價值投資
    private int calculateValueBuyVolume() {
        double availableFunds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        return (int) (availableFunds / currentPrice * 0.5); // 資金的 50% 投入
    }

    //風險管控
    private double getRiskFactor() {
        double totalAssets = account.getAvailableFunds() + stock.getPrice() * getAccumulatedStocks();
        double risk = 1 - (account.getAvailableFunds() / totalAssets);
        return Math.max(0, Math.min(risk, 1)); // 保證在 [0, 1] 區間內
    }

}
