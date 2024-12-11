package Core;

import Core.Stock;
import OrderManagement.OrderBookListener;
import StockMainAction.StockMarketSimulation;
import UserManagement.UserAccount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;

/**
 * 訂單簿類別，管理買賣訂單的提交和撮合
 */
public class OrderBook {

    private List<Order> buyOrders;
    private List<Order> sellOrders;
    private StockMarketSimulation simulation;

    final double MAX_PRICE_DIFF_RATIO = 0.5;

    // 設置最大允許的波動範圍
    final double MAX_ALLOWED_CHANGE = 0.05; // 允許每次成交後的價格波動不超過 5%

    // Listener list
    private List<OrderBookListener> listeners;

    // 構造函數
    public OrderBook(StockMarketSimulation simulation) {
        this.buyOrders = new ArrayList<>();
        this.sellOrders = new ArrayList<>();
        this.simulation = simulation;
        this.listeners = new ArrayList<>();
    }

    /**
     * 股價變動規則
     *
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
     *
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
     *
     * @param order 買單訂單
     * @param currentPrice 當前價格
     */
    public void submitBuyOrder(Order order, double currentPrice) {
        if (order == null) {
            System.out.println("Error: Order is null.");
            return;
        }

        Trader trader = order.getTrader();
        if (trader == null) {
            System.out.println("Error: Trader is null.");
            return;
        }

        UserAccount account = trader.getAccount();
        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        double totalCost = order.getPrice() * order.getVolume();

        // 檢查並凍結資金，若資金不足則拒絕掛單
        if (!account.freezeFunds(totalCost)) {
            System.out.println("資金不足，無法掛買單。");
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
            System.out.println("合併現有買單，新的買單數量: " + existingOrder.getVolume());
        } else {
            buyOrders.add(index, order);
            System.out.println("新增買單，價格: " + adjustedPrice + "，數量: " + order.getVolume());
        }

        // 通知監聽者
        notifyListeners();
    }

    /**
     * 提交賣單
     *
     * @param order 賣單訂單
     * @param currentPrice 當前價格
     */
    public void submitSellOrder(Order order, double currentPrice) {
        if (order == null) {
            System.out.println("Error: Order is null.");
            return;
        }

        Trader trader = order.getTrader();
        if (trader == null) {
            System.out.println("Error: Trader is null.");
            return;
        }

        UserAccount account = trader.getAccount();
        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        // 檢查並凍結股票數量，若持股不足則拒絕掛賣單
        if (!account.freezeStocks(order.getVolume())) {
            System.out.println("持股不足，無法掛賣單。");
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
            System.out.println("合併現有賣單，新的賣單數量: " + existingOrder.getVolume());
        } else {
            sellOrders.add(index, order);
            System.out.println("新增賣單，價格: " + adjustedPrice + "，數量: " + order.getVolume());
        }

        // 通知監聽者
        notifyListeners();
    }

    /**
     * 處理訂單撮合
     *
     * @param stock 股票實例
     */
    public void processOrders(Stock stock) {
        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order buyOrder = buyOrders.get(0);
            Order sellOrder = sellOrders.get(0);

            // 僅處理限價單或跳過自我匹配
            if (buyOrder.getTrader() == sellOrder.getTrader()) {
                System.out.println("跳過自我匹配，買單：" + buyOrder + "，賣單：" + sellOrder);
//                buyOrders.remove(buyOrder); // 或直接更新
//                continue;
            }

            if (canExecuteOrder(buyOrder, sellOrder)) {
                int transactionVolume = executeTransaction(buyOrder, sellOrder, stock);

                // 成交價格設為賣方價格
                double transactionPrice = sellOrder.getPrice();

                // 將交易數據傳遞給 MarketAnalyzer
                simulation.getMarketAnalyzer().addTransaction(transactionPrice, transactionVolume);

                // 更新交易者的帳戶
                updateTraderStatus(buyOrder, sellOrder, transactionVolume, transactionPrice);

                // 更新成交量圖表
                simulation.updateVolumeChart(transactionVolume);

                // 通知監聽者
                notifyListeners();
            } else {
                break;
            }
        }

        // 更新加權平均價格標籤和其他相關 UI
        SwingUtilities.invokeLater(() -> {
            simulation.updateLabels();
            simulation.updateOrderBookDisplay();
        });
    }

    /**
     * 判斷是否可以執行訂單
     *
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
     *
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
     * 更新所屬的交易者資訊
     *
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
     * 獲取當前的買賣訂單列表（可用於視覺化訂單簿）
     *
     * @return 買單列表
     */
    public List<Order> getBuyOrders() {
        return new ArrayList<>(buyOrders); // 返回新列表以保護內部列表
    }

    /**
     * 獲取當前的賣單訂單列表（可用於視覺化訂單簿）
     *
     * @return 賣單列表
     */
    public List<Order> getSellOrders() {
        return new ArrayList<>(sellOrders); // 返回新列表以保護內部列表
    }

    /**
     * 獲取頂部N個買單
     *
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
     *
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
     *
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
     *
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
     *
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

                // 更新交易狀態
                trader.updateAverageCostPrice("buy", transactionVolume, transactionPrice);

                System.out.println(trader.getTraderType() + " 市價買進，價格: " + transactionPrice + "，數量: " + transactionVolume);

                // 更新賣方（限價訂單交易者）的帳戶
                sellOrder.getTrader().updateAfterTransaction("sell", transactionVolume, transactionPrice);

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
            // 將交易數據傳遞給 MarketAnalyzer
            simulation.getMarketAnalyzer().addTransaction(marketTotalTransactionValue / marketTotalTransactionVolume, marketTotalTransactionVolume);

            // 捕獲變量以用於 lambda 表達式
            final int finalVolume = marketTotalTransactionVolume;

            // 更新加權平均價格標籤和成交量圖表
            SwingUtilities.invokeLater(() -> {
                simulation.updateLabels();
                simulation.updateVolumeChart(finalVolume);
                simulation.updateOrderBookDisplay();
            });
        }

        // 捕獲需要使用的變量到 final 變量
        final int volumeDifference = quantity - remainingQuantity;

        // 更新用戶界面
        SwingUtilities.invokeLater(() -> {
            simulation.updateLabels();
            simulation.updateVolumeChart(volumeDifference);
            simulation.updateOrderBookDisplay();
        });

        // 通知監聽者
        notifyListeners();
    }

    /**
     * 市價賣出
     *
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

                // 更新交易狀態
                trader.updateAverageCostPrice("sell", transactionVolume, transactionPrice);

                System.out.println(trader.getTraderType() + " 市價賣出，價格: " + transactionPrice + "，數量: " + transactionVolume);

                // 更新買方（限價訂單交易者）的帳戶
                buyOrder.getTrader().updateAfterTransaction("buy", transactionVolume, transactionPrice);

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
            // 將交易數據傳遞給 MarketAnalyzer
            simulation.getMarketAnalyzer().addTransaction(marketTotalTransactionValue / marketTotalTransactionVolume, marketTotalTransactionVolume);

            // 捕獲變量以用於 lambda 表達式
            final int finalVolume = marketTotalTransactionVolume;

            // 更新加權平均價格標籤和成交量圖表
            SwingUtilities.invokeLater(() -> {
                simulation.updateLabels();
                simulation.updateVolumeChart(finalVolume);
                simulation.updateOrderBookDisplay();
            });
        }

        // 捕獲需要使用的變量到 final 變量
        final int volumeDifference = quantity - remainingQuantity;

        // 更新用戶界面
        SwingUtilities.invokeLater(() -> {
            simulation.updateLabels();
            simulation.updateVolumeChart(volumeDifference);
            simulation.updateOrderBookDisplay();
        });

        // 通知監聽者
        notifyListeners();
    }

    /**
     * 取消掛單
     *
     * @param orderId 訂單ID
     */
    public void cancelOrder(String orderId) {
        // 找到並移除買單
        Order canceledOrder = buyOrders.stream()
                .filter(order -> order.getId().equals(orderId))
                .findFirst()
                .orElse(null);

        if (canceledOrder != null) {
            // 從買單列表中移除
            buyOrders.remove(canceledOrder);

            // 歸還凍結的資金
            double refundAmount = canceledOrder.getPrice() * canceledOrder.getVolume();
            canceledOrder.getTrader().getAccount().incrementFunds(refundAmount);

            // 打印詳細信息
            System.out.println("已取消買單：");
            System.out.println("訂單ID：" + orderId);
            System.out.println("股票數量：" + canceledOrder.getVolume());
            System.out.println("單價：" + canceledOrder.getPrice());
            System.out.println("已退還資金：" + refundAmount);
        } else {
            // 找到並移除賣單
            canceledOrder = sellOrders.stream()
                    .filter(order -> order.getId().equals(orderId))
                    .findFirst()
                    .orElse(null);

            if (canceledOrder != null) {
                // 從賣單列表中移除
                sellOrders.remove(canceledOrder);

                // 歸還凍結的股票數量
                canceledOrder.getTrader().getAccount().incrementStocks(canceledOrder.getVolume());

                // 打印詳細信息
                System.out.println("已取消賣單：");
                System.out.println("訂單ID：" + orderId);
                System.out.println("股票數量：" + canceledOrder.getVolume());
                System.out.println("單價：" + canceledOrder.getPrice());
                System.out.println("已退還股票數量：" + canceledOrder.getVolume());
            } else {
                System.out.println("訂單ID " + orderId + " 未找到，無法取消。");
            }
        }

        // 更新 UI 顯示
        SwingUtilities.invokeLater(() -> simulation.updateOrderBookDisplay());

        // 通知監聽者
        notifyListeners();
    }

    /**
     * 添加市場成交數據（示例方法）
     *
     * @param transactionValue 成交價值
     * @param transactionVolume 成交量
     */
    public void addMarketTransactionData(double transactionValue, int transactionVolume) {
        // 這部分已被移動到 MarketAnalyzer，所以可以保留或刪除
        // 這裡僅為示例，實際實現可能涉及更多細節
        System.out.println("市場成交：總額 " + transactionValue + "，總量 " + transactionVolume);
    }

    /**
     * 獲取按交易者類型的買單列表
     *
     * @param traderType 交易者類型，如 "Retail", "Main Force", "Personal"
     * @return 符合類型的買單列表
     */
    public List<Order> getBuyOrdersByTraderType(String traderType) {
        if (traderType == null || traderType.isEmpty()) {
            return new ArrayList<>();
        }

        synchronized (buyOrders) {
            return buyOrders.stream()
                    .peek(order -> {
                        if (order == null) {
                            System.err.println("Null order found in buyOrders.");
                        } else if (order.getTrader() == null) {
                            System.err.println("Order with null trader found: " + order.getId());
                        } else if (order.getTrader().getTraderType() == null) {
                            System.err.println("Order with trader having null traderType: " + order.getId());
                        }
                    })
                    .filter(order -> order != null
                    && order.getTrader() != null
                    && order.getTrader().getTraderType() != null
                    && traderType.equalsIgnoreCase(order.getTrader().getTraderType()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 獲取按交易者類型的賣單列表
     *
     * @param traderType 交易者類型，如 "Retail", "Main Force", "Personal"
     * @return 符合類型的賣單列表
     */
    public List<Order> getSellOrdersByTraderType(String traderType) {
        if (traderType == null || traderType.isEmpty()) {
            return new ArrayList<>();
        }

        synchronized (sellOrders) {
            return sellOrders.stream()
                    .peek(order -> {
                        if (order == null) {
                            System.err.println("Null order found in sellOrders.");
                        } else if (order.getTrader() == null) {
                            System.err.println("Order with null trader found: " + order.getId());
                        } else if (order.getTrader().getTraderType() == null) {
                            System.err.println("Order with trader having null traderType: " + order.getId());
                        }
                    })
                    .filter(order -> order != null
                    && order.getTrader() != null
                    && order.getTrader().getTraderType() != null
                    && traderType.equalsIgnoreCase(order.getTrader().getTraderType()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 添加 OrderBookListener
     *
     * @param listener 監聽器
     */
    public void addOrderBookListener(OrderBookListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除 OrderBookListener
     *
     * @param listener 監聽器
     */
    public void removeOrderBookListener(OrderBookListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知所有監聽器訂單簿已更新
     */
    private void notifyListeners() {
        for (OrderBookListener listener : listeners) {
            listener.onOrderBookUpdated();
        }
    }
}
