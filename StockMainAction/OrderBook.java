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

    private double accumulatedMarketTransactionValue = 0.0;
    private int accumulatedMarketTransactionVolume = 0;

    // 設置最大允許的波動範圍
    final double MAX_ALLOWED_CHANGE = 0.05; // 允許每次成交後的價格波動不超過 5%

    //構造函數
    public OrderBook(StockMarketSimulation simulation, UserAccount account) {
        this.buyOrders = new ArrayList<>();
        this.sellOrders = new ArrayList<>();
        this.simulation = simulation;
        this.account = account; // 正確賦值給 account
    }

    //股價變動規則
    public double adjustPriceToUnit(double price) {
        if (price < 100) {
            // 當價格小於 100 時，每單位為 0.1
            return Math.round(price * 10) / 10.0;
        } else {
            // 當價格大於或等於 100 時，每單位為 0.5
            return Math.round(price * 2) / 2.0;
        }
    }

    //
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

        // 檢查並凍結資金，若資金不足則拒絕掛單
        if (!account.freezeFunds(totalCost)) {
//            System.out.println("資金不足，無法掛買單");
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

    //提交賣單
    public void submitSellOrder(Order order, double currentPrice) {
        UserAccount account = order.getTraderAccount();

        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        // 檢查並凍結股票數量，若持股不足則拒絕掛單
        if (!account.freezeStocks(order.getVolume())) {
//            System.out.println("持股不足，無法掛賣單");
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

    // 處理訂單撮合
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

                // 更新主力現金和持股量
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

    //可執行訂單
    private boolean canExecuteOrder(Order buyOrder, Order sellOrder) {
        double priceDifference = buyOrder.getPrice() - sellOrder.getPrice();
        double allowableDifference = sellOrder.getPrice() * MAX_PRICE_DIFF_RATIO;
        return buyOrder.getPrice() >= sellOrder.getPrice() && priceDifference <= allowableDifference;
    }

    //
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

        simulation.updateOrderBookDisplay();
        return transactionVolume;
    }

    //更新加權股價
    private void updateStockPrice(Stock stock, double totalTransactionValue, int totalTransactionVolume) {
        double finalWeightedPrice = totalTransactionValue / totalTransactionVolume;
        stock.setPrice(finalWeightedPrice);
        checkPriceChange(stock);
        //System.out.println("加權平均股價更新為: " + finalWeightedPrice);
    }

    //根據前一個股價資訊來判斷漲跌
    private void checkPriceChange(Stock stock) {
        double currentPrice = stock.getPrice();
        double previousPrice = stock.getPreviousPrice();
        if (currentPrice > previousPrice) {
//            System.out.println("股價上升至 " + currentPrice);
        } else if (currentPrice < previousPrice) {
//            System.out.println("股價下跌至 " + currentPrice);
        } else {
//            System.out.println("股價保持不變 " + currentPrice);
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
        } else {
            account.incrementStocks(transactionVolume);
        }
    }

    //
    public void addMarketTransactionData(double transactionValue, int transactionVolume) {
        accumulatedMarketTransactionValue += transactionValue;
        accumulatedMarketTransactionVolume += transactionVolume;
    }

    // 獲取當前的買賣訂單列表（可用於視覺化訂單簿）
    public List<Order> getBuyOrders() {
        return buyOrders;
    }

    // 獲取當前的賣單訂單列表（可用於視覺化訂單簿）
    public List<Order> getSellOrders() {
        return sellOrders;
    }

    //
    public List<Order> getTopBuyOrders(int n) {
        return buyOrders.stream()
                .sorted(Comparator.comparing(Order::getPrice).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    //
    public List<Order> getTopSellOrders(int n) {
        return sellOrders.stream()
                .sorted(Comparator.comparing(Order::getPrice))
                .limit(n)
                .collect(Collectors.toList());
    }

    //獲取平均賣出交易量
    public int getAvailableSellVolume(double price) {
        return sellOrders.stream()
                .filter(order -> order.getPrice() <= price)
                .mapToInt(Order::getVolume)
                .sum();
    }

    //獲取平均買出交易量
    public int getAvailableBuyVolume(double price) {
        return buyOrders.stream()
                .filter(order -> order.getPrice() >= price)
                .mapToInt(Order::getVolume)
                .sum();
    }
}
