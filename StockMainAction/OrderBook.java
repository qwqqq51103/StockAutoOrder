package StockMainAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

/**
 * 訂單簿類別，管理買賣訂單的提交和撮合
 */
public class OrderBook {

    private List<Order> buyOrders;
    private List<Order> sellOrders;
    private StockMarketSimulation simulation;

    final double MAX_PRICE_DIFF_RATIO = 0.5;

    private double accumulatedMarketTransactionValue = 0.0;
    private int accumulatedMarketTransactionVolume = 0;

    // 設置最大允許的波動範圍
    final double MAX_ALLOWED_CHANGE = 0.05; // 允許每次成交後的價格波動不超過 5%

    // 構造函數
    public OrderBook(StockMarketSimulation simulation) {
        this.buyOrders = new ArrayList<>();
        this.sellOrders = new ArrayList<>();
        this.simulation = simulation;
    }

    /**
     * 股價變動規則
     * @param price 當前價格
     * @return 調整後的價格
     */
    public double adjustPriceToUnit(double price) {
        if (price < 100) {
            // 當價格小於 100 時，每單位為 0.1
            return Math.round(price * 10) / 10.0;
        } else {
            // 當價格大於或等於 100 時，每單位為 0.5
            return Math.round(price * 2) / 2.0;
        }
    }

    /**
     * 計算價格範圍
     * @param currentPrice 當前價格
     * @param percentage 百分比範圍
     * @return 價格範圍的陣列 [下限, 上限]
     */
    public double[] calculatePriceRange(double currentPrice, double percentage) {
        double lowerLimit = currentPrice * (1 - percentage);
        double upperLimit = currentPrice * (1 + percentage);
        return new double[]{lowerLimit, upperLimit};
    }

    /**
     * 提交買單
     * @param order 買單訂單
     * @param currentPrice 當前價格
     */
    public void submitBuyOrder(Order order, double currentPrice) {
        Trader trader = order.getTrader();
        UserAccount account = trader.getAccount();
        double totalCost = order.getPrice() * order.getVolume();

        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        // 檢查並凍結資金，若資金不足則拒絕掛單
        if (!account.freezeFunds(totalCost)) {
            System.out.println("資金不足，無法掛買單");
            return;
        }

        // 調整訂單價格到最近的價格單位
        double adjustedPrice = adjustPriceToUnit(order.getPrice());

        // 計算價格範圍並限制在範圍內
        double[] priceRange = calculatePriceRange(currentPrice, 0.05); // 設定範圍為±5%
        adjustedPrice = Math.min(Math.max(adjustedPrice, priceRange[0]), priceRange[1]);

        // 更新訂單的價格為調整後的價格
        order.setPrice(adjustedPrice);

        // 將訂單放入買單列表，並按價格排序
        int index = 0;
        while (index < buyOrders.size() && buyOrders.get(index).getPrice() > order.getPrice()) {
            index++;
        }

        if (index < buyOrders.size() && buyOrders.get(index).getPrice() == order.getPrice()) {
            Order existingOrder = buyOrders.get(index);
            existingOrder.setVolume(existingOrder.getVolume() + order.getVolume());
        } else {
            buyOrders.add(index, order);
        }

        simulation.updateOrderBookDisplay();
    }

    /**
     * 提交賣單
     * @param order 賣單訂單
     * @param currentPrice 當前價格
     */
    public void submitSellOrder(Order order, double currentPrice) {
        Trader trader = order.getTrader();
        UserAccount account = trader.getAccount();

        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        // 檢查並凍結股票數量，若持股不足則拒絕掛賣單
        if (!account.freezeStocks(order.getVolume())) {
            System.out.println("持股不足，無法掛賣單");
            return;
        }

        // 調整訂單價格到最近的價格單位
        double adjustedPrice = adjustPriceToUnit(order.getPrice());

        // 計算價格範圍並限制在範圍內
        double[] priceRange = calculatePriceRange(currentPrice, 0.05); // 設定範圍為±5%
        adjustedPrice = Math.min(Math.max(adjustedPrice, priceRange[0]), priceRange[1]);

        // 更新訂單的價格為調整後的價格
        order.setPrice(adjustedPrice);

        // 將訂單放入賣單列表，並按價格排序
        int index = 0;
        while (index < sellOrders.size() && sellOrders.get(index).getPrice() < order.getPrice()) {
            index++;
        }

        if (index < sellOrders.size() && sellOrders.get(index).getPrice() == order.getPrice()) {
            Order existingOrder = sellOrders.get(index);
            existingOrder.setVolume(existingOrder.getVolume() + order.getVolume());
        } else {
            sellOrders.add(index, order);
        }

        simulation.updateOrderBookDisplay();
    }

    /**
     * 處理訂單撮合
     * @param stock 股票實例
     */
    public void processOrders(Stock stock) {
        double totalTransactionValue = accumulatedMarketTransactionValue;
        int totalTransactionVolume = accumulatedMarketTransactionVolume;

        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order buyOrder = buyOrders.get(0);
            Order sellOrder = sellOrders.get(0);

            // 僅處理限價單或跳過自我匹配
            if (buyOrder.getTrader() == sellOrder.getTrader()) {
                System.out.println("跳過自我匹配，買單：" + buyOrder + "，賣單：" + sellOrder);
                buyOrders.remove(buyOrder); // 或直接更新
                continue;
            }

            if (canExecuteOrder(buyOrder, sellOrder)) {
                int transactionVolume = executeTransaction(buyOrder, sellOrder, stock);

                // 成交價格設為賣方價格
                double transactionPrice = sellOrder.getPrice();

                // 累加成交價值和成交量，用於計算加權平均價格
                totalTransactionValue += transactionPrice * transactionVolume;
                totalTransactionVolume += transactionVolume;

                // 更新交易者的帳戶
                updateTraderStatus(buyOrder, sellOrder, transactionVolume, transactionPrice);

                // 更新成交量圖表
                simulation.updateVolumeChart(transactionVolume);
            } else {
                break;
            }
        }

        // 最後更新加權平均價格
        if (totalTransactionVolume > 0) {
            updateStockPrice(stock, totalTransactionValue, totalTransactionVolume);
        }
    }

    /**
     * 判斷是否可以執行訂單
     * @param buyOrder 買單
     * @param sellOrder 賣單
     * @return 是否可以執行
     */
    private boolean canExecuteOrder(Order buyOrder, Order sellOrder) {
        double priceDifference = buyOrder.getPrice() - sellOrder.getPrice();
        double allowableDifference = sellOrder.getPrice() * MAX_PRICE_DIFF_RATIO;
        return buyOrder.getPrice() >= sellOrder.getPrice() && priceDifference <= allowableDifference;
    }

    /**
     * 執行交易
     * @param buyOrder 買單
     * @param sellOrder 賣單
     * @param stock 股票實例
     * @return 成交量
     */
    private int executeTransaction(Order buyOrder, Order sellOrder, Stock stock) {
        // 檢查 Stock 是否為 null
        if (stock == null) {
            System.out.println("Error: Stock is null. Unable to update stock price.");
            return 0; // 若 stock 為 null，則停止執行交易
        }

        // 檢查交易者帳戶是否為 null
        if (buyOrder.getTraderAccount() == null || sellOrder.getTraderAccount() == null) {
            System.out.println("Error: Trader account is null for one of the orders.");
            return 0;  // 若發現問題，可以返回 0 或進行其他處理
        }

        int transactionVolume = Math.min(buyOrder.getVolume(), sellOrder.getVolume());

        // 更新訂單的剩餘數量
        buyOrder.setVolume(buyOrder.getVolume() - transactionVolume);
        sellOrder.setVolume(sellOrder.getVolume() - transactionVolume);

        // 更新股價
        stock.setPrice(sellOrder.getPrice());

        // 移除已完成的訂單
        if (buyOrder.getVolume() == 0) {
            buyOrders.remove(buyOrder);
            // System.out.println("買單已完成，從列表中移除。");
        }
        if (sellOrder.getVolume() == 0) {
            sellOrders.remove(sellOrder);
            // System.out.println("賣單已完成，從列表中移除。");
        }

        simulation.updateOrderBookDisplay();
        return transactionVolume;
    }

    /**
     * 更新加權股價
     * @param stock 股票實例
     * @param totalTransactionValue 總成交價值
     * @param totalTransactionVolume 總成交量
     */
    private void updateStockPrice(Stock stock, double totalTransactionValue, int totalTransactionVolume) {
        double finalWeightedPrice = totalTransactionValue / totalTransactionVolume;
        stock.setPrice(finalWeightedPrice);
        checkPriceChange(stock);
        //System.out.println("加權平均股價更新為: " + finalWeightedPrice);
    }

    /**
     * 根據前一個股價資訊來判斷漲跌
     * @param stock 股票實例
     */
    private void checkPriceChange(Stock stock) {
        double currentPrice = stock.getPrice();
        double previousPrice = stock.getPreviousPrice();
        if (currentPrice > previousPrice) {
            //System.out.println("股價上升至 " + currentPrice);
        } else if (currentPrice < previousPrice) {
            //System.out.println("股價下跌至 " + currentPrice);
        } else {
            //System.out.println("股價保持不變 " + currentPrice);
        }
    }

    /**
     * 更新所屬的交易者資訊
     * @param buyOrder 買單
     * @param sellOrder 賣單
     * @param transactionVolume 成交量
     * @param transactionPrice 成交價格
     */
    private void updateTraderStatus(Order buyOrder, Order sellOrder, int transactionVolume, double transactionPrice) {
        buyOrder.getTrader().updateAfterTransaction("buy", transactionVolume, transactionPrice);
        sellOrder.getTrader().updateAfterTransaction("sell", transactionVolume, transactionPrice);
    }

    /**
     * 將市價訂單的成交值和成交量累加
     * @param transactionValue 成交價值
     * @param transactionVolume 成交量
     */
    public void addMarketTransactionData(double transactionValue, int transactionVolume) {
        accumulatedMarketTransactionValue += transactionValue;
        accumulatedMarketTransactionVolume += transactionVolume;
    }

    /**
     * 獲取當前的買賣訂單列表（可用於視覺化訂單簿）
     * @return 買單列表
     */
    public List<Order> getBuyOrders() {
        return buyOrders;
    }

    /**
     * 獲取當前的賣單訂單列表（可用於視覺化訂單簿）
     * @return 賣單列表
     */
    public List<Order> getSellOrders() {
        return sellOrders;
    }

    /**
     * 獲取頂部N個買單
     * @param n 數量
     * @return 頂部N個買單
     */
    public List<Order> getTopBuyOrders(int n) {
        return buyOrders.stream()
                .sorted(Comparator.comparing(Order::getPrice).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * 獲取頂部N個賣單
     * @param n 數量
     * @return 頂部N個賣單
     */
    public List<Order> getTopSellOrders(int n) {
        return sellOrders.stream()
                .sorted(Comparator.comparing(Order::getPrice))
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * 獲取可用的平均賣出交易量
     * @param price 價格
     * @return 可用賣出交易量
     */
    public int getAvailableSellVolume(double price) {
        return sellOrders.stream()
                .filter(order -> order.getPrice() <= price)
                .mapToInt(Order::getVolume)
                .sum();
    }

    /**
     * 獲取可用的平均買入交易量
     * @param price 價格
     * @return 可用買入交易量
     */
    public int getAvailableBuyVolume(double price) {
        return buyOrders.stream()
                .filter(order -> order.getPrice() >= price)
                .mapToInt(Order::getVolume)
                .sum();
    }

    /**
     * 市價買入
     * @param trader 交易者，可以是主力策略或散戶投資者
     * @param quantity 購買數量
     */
    public void marketBuy(Trader trader, int quantity) {
        double remainingFunds = trader.getAccount().getAvailableFunds();
        int remainingQuantity = quantity;
        double marketTotalTransactionValue = 0.0;
        int marketTotalTransactionVolume = 0;

        ListIterator<Order> iterator = sellOrders.listIterator();
        while (iterator.hasNext() && remainingQuantity > 0) {
            Order sellOrder = iterator.next();
            double transactionPrice = sellOrder.getPrice();
            int transactionVolume = Math.min(sellOrder.getVolume(), remainingQuantity);
            double transactionCost = transactionPrice * transactionVolume;

            // 避免自成交
            if (sellOrder.getTrader() == trader) {
                continue;
            }

            if (remainingFunds >= transactionCost) {
                // 執行交易
                remainingFunds -= transactionCost;
                remainingQuantity -= transactionVolume;

                // 更新交易者（買方）的帳戶
                trader.getAccount().incrementStocks(transactionVolume);
                trader.getAccount().decrementFunds(transactionCost);

                System.out.println(trader.getTraderType() + " 市價買進，價格: " + transactionPrice + "，數量: " + transactionVolume);

                // 更新賣方（限價訂單交易者）的帳戶
                sellOrder.getTrader().updateAfterTransaction("sell", transactionVolume, transactionCost);

                // 累加市價單的成交值和成交量
                marketTotalTransactionValue += transactionPrice * transactionVolume;
                marketTotalTransactionVolume += transactionVolume;

                // 更新或移除賣單
                if (sellOrder.getVolume() == transactionVolume) {
                    iterator.remove();
                } else {
                    sellOrder.setVolume(sellOrder.getVolume() - transactionVolume);
                }
            } else {
                break; // 資金不足
            }
        }

        if (marketTotalTransactionVolume > 0) {
            addMarketTransactionData(marketTotalTransactionValue, marketTotalTransactionVolume);
        }

        simulation.updateLabels();
        simulation.updateVolumeChart(marketTotalTransactionVolume);
        simulation.updateOrderBookDisplay();
    }

    /**
     * 市價賣出
     * @param trader 交易者，可以是主力策略或散戶投資者
     * @param quantity 賣出數量
     */
    public void marketSell(Trader trader, int quantity) {
        int remainingQuantity = quantity;
        double marketTotalTransactionValue = 0.0;
        int marketTotalTransactionVolume = 0;

        ListIterator<Order> iterator = buyOrders.listIterator();
        while (iterator.hasNext() && remainingQuantity > 0) {
            Order buyOrder = iterator.next();
            double transactionPrice = buyOrder.getPrice();
            int transactionVolume = Math.min(buyOrder.getVolume(), remainingQuantity);
            double transactionRevenue = transactionPrice * transactionVolume;

            // 避免自成交
            if (buyOrder.getTrader() == trader) {
                continue;
            }

            // 確認持股量足夠
            if (trader.getAccount().getStockInventory() >= transactionVolume) {
                // 執行交易
                trader.getAccount().decrementStocks(transactionVolume);
                remainingQuantity -= transactionVolume;
                trader.getAccount().incrementFunds(transactionRevenue);

                System.out.println(trader.getTraderType() + " 市價賣出，價格: " + transactionPrice + "，數量: " + transactionVolume);

                // 更新買方（限價訂單交易者）的帳戶
                buyOrder.getTrader().updateAfterTransaction("buy", transactionVolume, transactionRevenue);

                // 累加市價單的成交值和成交量
                marketTotalTransactionValue += transactionPrice * transactionVolume;
                marketTotalTransactionVolume += transactionVolume;

                // 更新或移除買單
                if (buyOrder.getVolume() == transactionVolume) {
                    iterator.remove();
                } else {
                    buyOrder.setVolume(buyOrder.getVolume() - transactionVolume);
                }
            } else {
                break; // 持股不足
            }
        }

        if (marketTotalTransactionVolume > 0) {
            addMarketTransactionData(marketTotalTransactionValue, marketTotalTransactionVolume);
        }

        simulation.updateLabels();
        simulation.updateVolumeChart(marketTotalTransactionVolume);
        simulation.updateOrderBookDisplay();
    }
}
