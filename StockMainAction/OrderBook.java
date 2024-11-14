package StockMainAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 訂單簿類別，管理買賣訂單的提交和撮合
 */
public class OrderBook {

    private List<Order> buyOrders;
    private List<Order> sellOrders;
    private MainForceStrategyWithOrderBook mainForceStrategy;
    private Stock stock;
    private StockMarketSimulation simulation;
    private UserAccount account;

    final double MAX_PRICE_DIFF_RATIO = 0.5;

    // 設置最大允許的波動範圍
    final double MAX_ALLOWED_CHANGE = 0.05; // 允許每次成交後的價格波動不超過 5%

    public OrderBook(StockMarketSimulation simulation, UserAccount account) {
        this.buyOrders = new ArrayList<>();
        this.sellOrders = new ArrayList<>();
        this.simulation = simulation;
        this.account = account; // 正確賦值給 account
    }

    public double adjustPriceToUnit(double price) {
        if (price < 100) {
            // 當價格小於 100 時，每單位為 0.1
            return Math.round(price * 10) / 10.0;
        } else {
            // 當價格大於或等於 100 時，每單位為 0.5
            return Math.round(price * 2) / 2.0;
        }
    }

    public double[] calculatePriceRange(double currentPrice, double percentage) {
        double lowerLimit = currentPrice * (1 - percentage);
        double upperLimit = currentPrice * (1 + percentage);
        return new double[]{lowerLimit, upperLimit};
    }

    //提交買單
    public void submitBuyOrder(Order order, double currentPrice) {
        UserAccount account = order.getTraderAccount();
        double totalCost = order.getPrice() * order.getVolume();

        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        // 市價單設定：如果是市價單，設定價格為 Double.MAX_VALUE 以表示無價格限制
        if (order.isMarketOrder()) {
            order.setPrice(Double.MAX_VALUE); // 市價單識別
            // 市價單不需即時凍結總資金，只需確保可用資金足夠執行操作
        } else {
            // 檢查並凍結資金，若資金不足則拒絕掛單
            if (!account.freezeFunds(totalCost)) {
                System.out.println("資金不足，無法掛買單");
                return;
            }
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

    //提交賣單 
    public void submitSellOrder(Order order, double currentPrice) {
        UserAccount account = order.getTraderAccount();

        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        // 市價單設定：如果是市價單，設定價格為 Double.MIN_VALUE 以表示無價格限制
        if (order.isMarketOrder()) {
            order.setPrice(Double.MIN_VALUE); // 市價單識別
            // 市價單不需即時凍結股票數量，只需確保持股數量足夠執行操作
        } else {
            // 檢查並凍結股票數量，若持股不足則拒絕掛單
            if (!account.freezeStocks(order.getVolume())) {
                System.out.println("持股不足，無法掛賣單");
                return;
            }
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

    // 處理訂單撮合
    public void processOrders(Stock stock) {
        double totalTransactionValue = 0.0;
        int totalTransactionVolume = 0;

        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order buyOrder = buyOrders.get(0);
            Order sellOrder = sellOrders.get(0);

            // 判斷是否有市價訂單
            boolean isMarketOrder = buyOrder.getPrice() == Double.MAX_VALUE || sellOrder.getPrice() == Double.MIN_VALUE;

            if (isMarketOrder || canExecuteOrder(buyOrder, sellOrder)) {
                int transactionVolume = executeTransaction(buyOrder, sellOrder, stock);

                // 設定成交價格根據市價單或限價單的不同情況
                double transactionPrice;
                if (isMarketOrder) {
                    if (buyOrder.getPrice() == Double.MAX_VALUE && sellOrder.getPrice() != Double.MIN_VALUE) {
                        // 市價買單以賣單價格成交
                        transactionPrice = sellOrder.getPrice();
                    } else if (sellOrder.getPrice() == Double.MIN_VALUE && buyOrder.getPrice() != Double.MAX_VALUE) {
                        // 市價賣單以買單價格成交
                        transactionPrice = buyOrder.getPrice();
                    } else {
                        // 若同時存在市價買單和市價賣單，使用當前市場價格（可設定為股票的上一成交價格）
                        transactionPrice = stock.getPrice();
                    }
                } else {
                    // 限價單則以買賣雙方的價格平均值成交
                    transactionPrice = (buyOrder.getPrice() + sellOrder.getPrice()) / 2.0;
                }

                // 累加成交價值和成交量，用於計算加權平均價格
                totalTransactionValue += transactionPrice * transactionVolume;
                totalTransactionVolume += transactionVolume;

                // 更新主力現金和持股量
                updateTraderStatus(buyOrder, sellOrder, transactionVolume, transactionPrice);

                // 更新成交量圖表
                simulation.updateVolumeChart(transactionVolume);
            } else {
                // 當買賣雙方無法成交時跳出迴圈
                break;
            }
        }

        // 最後更新加權平均價格
        if (totalTransactionVolume > 0) {
            updateStockPrice(stock, totalTransactionValue, totalTransactionVolume);
        }
    }

    private boolean canExecuteOrder(Order buyOrder, Order sellOrder) {
        boolean isMarketOrder = buyOrder.getPrice() == Double.MAX_VALUE || sellOrder.getPrice() == Double.MAX_VALUE;
        double priceDifference = buyOrder.getPrice() - sellOrder.getPrice();
        double allowableDifference = sellOrder.getPrice() * MAX_PRICE_DIFF_RATIO;
        return isMarketOrder || (buyOrder.getPrice() >= sellOrder.getPrice() && priceDifference <= allowableDifference);
    }

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
//            System.out.println("買單已完成，從列表中移除。");
        }
        if (sellOrder.getVolume() == 0) {
            sellOrders.remove(sellOrder);
//            System.out.println("賣單已完成，從列表中移除。");
        }

        return transactionVolume;
    }

    private void updateStockPrice(Stock stock, double totalTransactionValue, int totalTransactionVolume) {
        double finalWeightedPrice = totalTransactionValue / totalTransactionVolume;
        stock.setPrice(finalWeightedPrice);
        checkPriceChange(stock);
        System.out.println("加權平均股價更新為: " + finalWeightedPrice);
    }

    //根據前一個股價資訊來判斷漲跌
    private void checkPriceChange(Stock stock) {
        double currentPrice = stock.getPrice();
        double previousPrice = stock.getPreviousPrice();
        if (currentPrice > previousPrice) {
            System.out.println("股價上升至 " + currentPrice);
        } else if (currentPrice < previousPrice) {
            System.out.println("股價下跌至 " + currentPrice);
        } else {
            System.out.println("股價保持不變 " + currentPrice);
        }
    }

    //更新所屬的交易者資訊  
    private void updateTraderStatus(Order buyOrder, Order sellOrder, int transactionVolume, double transactionPrice) {
        // 主力買入或賣出
        if (buyOrder.getTraderType().equals("MainForce")) {
            MainForceStrategyWithOrderBook mainForce = (MainForceStrategyWithOrderBook) buyOrder.getTrader();
            mainForce.updateAfterTransaction("buy", transactionVolume, transactionPrice);
        } else if (sellOrder.getTraderType().equals("MainForce")) {
            MainForceStrategyWithOrderBook mainForce = (MainForceStrategyWithOrderBook) sellOrder.getTrader();
            mainForce.updateAfterTransaction("sell", -transactionVolume, transactionPrice);
        } // 散戶買入或賣出
        else if (buyOrder.getTraderType().equals("RetailInvestor")) {
            RetailInvestorAI retailAI = (RetailInvestorAI) buyOrder.getTrader();
            retailAI.updateAfterTransaction("buy", transactionVolume, transactionPrice);
        } else if (sellOrder.getTraderType().equals("RetailInvestor")) {
            RetailInvestorAI retailAI = (RetailInvestorAI) sellOrder.getTrader();
            retailAI.updateAfterTransaction("sell", -transactionVolume, transactionPrice);
        } // 市價主力買入或賣出
        else if (buyOrder.getTraderType().equals("MarketMainForce")) {
            MainForceStrategyWithOrderBook marketMainForce = (MainForceStrategyWithOrderBook) buyOrder.getTrader();
            marketMainForce.updateAfterTransaction("marketBuy", transactionVolume, transactionPrice);
        } else if (sellOrder.getTraderType().equals("MarketMainForce")) {
            MainForceStrategyWithOrderBook marketMainForce = (MainForceStrategyWithOrderBook) sellOrder.getTrader();
            marketMainForce.updateAfterTransaction("marketSell", -transactionVolume, transactionPrice);
        } // 市價散戶買入或賣出
        else if (buyOrder.getTraderType().equals("MarketRetailInvestor")) {
            RetailInvestorAI marketRetailAI = (RetailInvestorAI) buyOrder.getTrader();
            marketRetailAI.updateAfterTransaction("marketBuy", transactionVolume, transactionPrice);
        } else if (sellOrder.getTraderType().equals("MarketRetailInvestor")) {
            RetailInvestorAI marketRetailAI = (RetailInvestorAI) sellOrder.getTrader();
            marketRetailAI.updateAfterTransaction("marketSell", -transactionVolume, transactionPrice);
        } // 若無特定交易者，默認增持股數
        else {
            account.incrementStocks(transactionVolume);
        }
    }

    //移除成交買賣單
    private void removeCompletedOrders(Order buyOrder, Order sellOrder) {
        if (buyOrder.getVolume() == 0) {
            buyOrders.remove(0);
            System.out.println("買單已完成，從列表中移除。");
        }
        if (sellOrder.getVolume() == 0) {
            sellOrders.remove(0);
            System.out.println("賣單已完成，從列表中移除。");
        }
    }

    // 獲取當前的買賣訂單列表（可用於視覺化訂單簿）
    public List<Order> getBuyOrders() {
        return buyOrders;
    }

    public List<Order> getSellOrders() {
        return sellOrders;
    }

    public List<Order> getTopBuyOrders(int n) {
        return buyOrders.stream()
                .sorted(Comparator.comparing(Order::getPrice).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<Order> getTopSellOrders(int n) {
        return sellOrders.stream()
                .sorted(Comparator.comparing(Order::getPrice))
                .limit(n)
                .collect(Collectors.toList());
    }

    public int getAvailableSellVolume(double price) {
        return sellOrders.stream()
                .filter(order -> order.getPrice() <= price)
                .mapToInt(Order::getVolume)
                .sum();
    }

    public int getAvailableBuyVolume(double price) {
        return buyOrders.stream()
                .filter(order -> order.getPrice() >= price)
                .mapToInt(Order::getVolume)
                .sum();
    }

    // 清除所有訂單（可選，用於重置市場）
    public void clearOrders() {
        buyOrders.clear();
        sellOrders.clear();
    }
}
