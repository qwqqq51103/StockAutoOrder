package Core;

import Core.Stock;
import OrderManagement.OrderBookListener;
import StockMainAction.StockMarketSimulation;
import UserManagement.UserAccount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ConcurrentModificationException;

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

        // **確保所有對 `buyOrders` 的操作都在同步區塊內**
        synchronized (buyOrders) {
            // 按價格排序插入訂單
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
//            System.out.println("合併現有賣單，新的賣單數量: " + existingOrder.getVolume());
        } else {
            sellOrders.add(index, order);
//            System.out.println("新增賣單，價格: " + adjustedPrice + "，數量: " + order.getVolume());
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
        // 準備異常日誌文件
        File logFile = new File(System.getProperty("user.home") + "/Desktop/MarketAnomalies.log");
        try ( BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {

            // 清理並排序買單和賣單
            buyOrders = buyOrders.stream()
                    .filter(order -> order.getVolume() > 0 && order.getPrice() > 0) // 過濾有效買單
                    .sorted((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice())) // 按買價降序排序
                    .collect(Collectors.toList());

            sellOrders = sellOrders.stream()
                    .filter(order -> order.getVolume() > 0 && order.getPrice() > 0) // 過濾有效賣單
                    .sorted(Comparator.comparingDouble(Order::getPrice)) // 按賣價升序排序
                    .collect(Collectors.toList());

            // 撮合訂單
            while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
                Order buyOrder = buyOrders.get(0); // 取最高買單
                Order sellOrder = sellOrders.get(0); // 取最低賣單

                // 檢測自我匹配異常
                if (buyOrder.getTrader() == sellOrder.getTrader()) {
                    String selfMatchLog = String.format(
                            "[%s] 自我撮合異常：買單（%s），賣單（%s）",
                            getCurrentTimestamp(), buyOrder, sellOrder
                    );
                    writer.write(selfMatchLog);
                    writer.newLine();
                    System.err.println(selfMatchLog);
                    // 跳過自我撮合的訂單，但不退出
//                    buyOrders.remove(buyOrder);
//                    sellOrders.remove(sellOrder);
//                    continue;
                }

                // 檢查是否能成交
                if (canExecuteOrder(buyOrder, sellOrder)) {
                    // 計算成交量
                    int transactionVolume = executeTransaction(buyOrder, sellOrder, stock);

                    // 設定成交價格為中間價
                    double transactionPrice = (buyOrder.getPrice() + sellOrder.getPrice()) / 2;

                    // 檢測價格閃崩或異常
                    if (detectPriceAnomaly(stock.getPrice(), transactionPrice)) {
                        String priceAnomalyLog = String.format(
                                "[%s] 價格異常：成交價格 %.2f 超出允許範圍，股價 %.2f",
                                getCurrentTimestamp(), transactionPrice, stock.getPrice()
                        );
                        writer.write(priceAnomalyLog);
                        writer.newLine();
                        System.err.println(priceAnomalyLog);
//                        buyOrders.remove(buyOrder); // 跳過異常訂單
//                        sellOrders.remove(sellOrder);
//                        continue;
                    }

                    // 檢測成交量異常
                    if (detectVolumeAnomaly(transactionVolume)) {
                        String volumeAnomalyLog = String.format(
                                "[%s] 成交量異常：成交量 %d 超出允許範圍",
                                getCurrentTimestamp(), transactionVolume
                        );
                        writer.write(volumeAnomalyLog);
                        writer.newLine();
                        System.err.println(volumeAnomalyLog);
//                        buyOrders.remove(buyOrder); // 跳過異常訂單
//                        sellOrders.remove(sellOrder);
//                        continue;
                    }

                    // 記錄交易到日誌
                    Transaction transaction = new Transaction(
                            buyOrder.getTrader().getTraderType(),
                            sellOrder.getTrader().getTraderType(),
                            transactionPrice,
                            transactionVolume
                    );
                    writer.write(transaction.toString());
                    writer.newLine();

                    // 傳遞交易數據到 MarketAnalyzer
                    simulation.getMarketAnalyzer().addTransaction(transactionPrice, transactionVolume);

                    // 更新交易者的帳戶
                    updateTraderStatus(buyOrder, sellOrder, transactionVolume, transactionPrice);

                    // 更新成交量圖表
                    simulation.updateVolumeChart(transactionVolume);

                    // 通知監聽者
                    notifyListeners();
                } else {
                    // 如果買賣雙方價格不匹配，停止撮合
                    break;
                }
            }

            // 更新加權平均價格標籤和其他相關 UI
            SwingUtilities.invokeLater(() -> {
                simulation.updateLabels();
                simulation.updateOrderBookDisplay();
            });

        } catch (IOException e) {
            System.err.println("無法寫入異常日誌：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 獲取當前時間戳
     *
     * @return 當前時間的格式化字串
     */
    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }

    /**
     * 檢測價格閃崩或異常波動
     *
     * @param previousPrice 上一次成交價格
     * @param currentPrice 當前成交價格
     * @return 如果價格變動超出允許範圍，返回 true；否則返回 false
     */
    private boolean detectPriceAnomaly(double previousPrice, double currentPrice) {
        double priceChange = Math.abs(currentPrice - previousPrice) / previousPrice;
        double allowedChange = 0.05; // 設定允許的價格變動範圍為 ±5%
        return priceChange > allowedChange;
    }

    /**
     * 檢測成交量異常
     *
     * @param transactionVolume 本次成交量
     * @return 如果成交量過大或過小，返回 true；否則返回 false
     */
    private boolean detectVolumeAnomaly(int transactionVolume) {
        int averageVolume = calculateAverageVolume(); // 計算平均成交量
        double allowedMultiplier = 3.0; // 允許的最大倍數
        return transactionVolume > averageVolume * allowedMultiplier || transactionVolume < averageVolume * 0.1;
    }

    /**
     * 計算市場的平均成交量
     *
     * @return 平均成交量
     */
    private int calculateAverageVolume() {
        return sellOrders.stream().mapToInt(Order::getVolume).sum() / Math.max(1, sellOrders.size());
    }

    /**
     * 計算成交價格
     *
     * @param buyOrder 買單
     * @param sellOrder 賣單
     * @return 成交價格
     */
    private double calculateTransactionPrice(Order buyOrder, Order sellOrder) {
        // 默認邏輯：成交價格為賣方價格
        double transactionPrice = sellOrder.getPrice();

        // 自定義邏輯：取買賣雙方價格的中間值
        transactionPrice = (buyOrder.getPrice() + sellOrder.getPrice()) / 2.0;
        // 隨機成交價格（在買價與賣價之間）
        Random random = new Random();
        transactionPrice = sellOrder.getPrice() + random.nextDouble() * (buyOrder.getPrice() - sellOrder.getPrice());
        return transactionPrice;
    }

    /**
     * 判斷是否可以執行訂單
     *
     * @param buyOrder 買單
     * @param sellOrder 賣單
     * @return 是否可以執行
     */
    private boolean canExecuteOrder(Order buyOrder, Order sellOrder) {
        if (buyOrder == null || sellOrder == null) {
            return false; // 防止空指針異常
        }
        if (buyOrder.getVolume() <= 0 || sellOrder.getVolume() <= 0) {
            return false; // 無效的訂單數量
        }

        // 計算價格差距
        double priceDifference = sellOrder.getPrice() - buyOrder.getPrice();
        double allowableDifference = sellOrder.getPrice() * MAX_PRICE_DIFF_RATIO; // 設定價格差距比例

        // 支持價格容忍範圍內的撮合
        return priceDifference <= allowableDifference;
    }

    /**
     * 校驗交易條件
     */
    private boolean validateTransaction(Order buyOrder, Order sellOrder, Stock stock) {
        if (stock == null) {
            System.out.println("Error: Stock is null. Unable to update stock price.");
            return false;
        }
        if (buyOrder.getTraderAccount() == null || sellOrder.getTraderAccount() == null) {
            System.out.println("Error: Trader account is null for one of the orders.");
            return false;
        }
        return true;
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
        if (!validateTransaction(buyOrder, sellOrder, stock)) {
            return 0; // 若校驗失敗，則不執行交易
        }

        int maxTransactionVolume = Math.max(50000, Math.min(buyOrder.getVolume(), sellOrder.getVolume()) / 30); //限制每次成交總量的 10%
        int transactionVolume = Math.min(Math.min(buyOrder.getVolume(), sellOrder.getVolume()), maxTransactionVolume);

        // 更新訂單數量
        buyOrder.setVolume(buyOrder.getVolume() - transactionVolume);
        sellOrder.setVolume(sellOrder.getVolume() - transactionVolume);

        // 更新股票價格
        stock.setPrice(sellOrder.getPrice());

        // 處理完成的訂單
        if (buyOrder.getVolume() == 0) {
            buyOrders.remove(buyOrder);
            System.out.println("買單已完成，從列表中移除。");
        }
        if (sellOrder.getVolume() == 0) {
            sellOrders.remove(sellOrder);
            System.out.println("賣單已完成，從列表中移除。");
        }

        // 更新 UI
        simulation.updateOrderBookDisplay();

        // 返回成交量
        System.out.println("交易完成：成交量 " + transactionVolume + "，成交價格 " + sellOrder.getPrice());
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
                //trader.updateAverageCostPrice("buy", transactionVolume, transactionPrice);
//                System.out.println(trader.getTraderType() + " 市價買進，價格: " + transactionPrice + "，數量: " + transactionVolume);
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

        List<Order> snapshot;
        synchronized (buyOrders) {
            snapshot = new ArrayList<>(buyOrders); // 先複製一份，確保 `stream()` 在安全的快照上執行
        }

        return snapshot.stream()
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
