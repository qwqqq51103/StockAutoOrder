package Core;

import AIStrategies.PersonalAI;
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
 * 訂單簿類別，管理買賣訂單的提交和撮合（改良後的版本）。
 */
public class OrderBook {

    private List<Order> buyOrders;   // 買單列表 (由高價到低價)
    private List<Order> sellOrders;  // 賣單列表 (由低價到高價)
    private StockMarketSimulation simulation;

    // Listener list
    private List<OrderBookListener> listeners;

    // ============= 參數可自行調整 =================
    /**
     * 當前版本只保留撮合「buyPrice >= sellPrice」即可成交，不再使用此常數。
     */
    final double MAX_PRICE_DIFF_RATIO = 0.25;

    /**
     * 單次撮合量的限制參數。
     */
    private static final int MIN_PER_TRANSACTION = 100; // 避免一次吃掉太多深度
    private static final int DIV_FACTOR = 30;            // 分批撮合的分母

    /**
     * 構造函數
     *
     * @param simulation 模擬實例
     */
    public OrderBook(StockMarketSimulation simulation) {
        this.buyOrders = new ArrayList<>();
        this.sellOrders = new ArrayList<>();
        this.simulation = simulation;
        this.listeners = new ArrayList<>();
    }

    // ================== 工具函式 ==================
    /**
     * 按照交易所規則(或自訂)對價格做跳階
     * <p>
     * 小於 100 時，每單位 0.1；大於等於 100 時，每單位 0.5。
     */
    public double adjustPriceToUnit(double price) {
        if (price < 100) {
            return Math.round(price * 10) / 10.0;   // 取到小數第一位
        } else {
            return Math.round(price * 2) / 2.0;     // 取到 0.5
        }
    }

    /**
     * 以當前股價為中心計算上下界
     * <p>
     * 本版本預設不會強制使用，可自行決定要不要 clamp。
     */
    public double[] calculatePriceRange(double currentPrice, double percentage) {
        double lowerLimit = currentPrice * (1 - percentage);
        double upperLimit = currentPrice * (1 + percentage);
        return new double[]{lowerLimit, upperLimit};
    }

    // ================== 提交訂單 ==================
    /**
     * 提交買單 (限價)
     *
     * @param order 買單
     * @param currentPrice 市場當前參考價格
     */
    public void submitBuyOrder(Order order, double currentPrice) {
        if (order == null) {
            System.out.println("Error: Order is null.");
            return;
        }
        if (order.getTrader() == null) {
            System.out.println("Error: Trader is null.");
            return;
        }
        UserAccount account = order.getTrader().getAccount();
        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        // 1. 檢查資金
        double totalCost = order.getPrice() * order.getVolume();
        if (!account.freezeFunds(totalCost)) {
            System.out.println("資金不足，無法掛買單。");
            return;
        }

        // 2. 調整價格到 tick 大小
        double adjustedPrice = adjustPriceToUnit(order.getPrice());
        order.setPrice(adjustedPrice);

        /*
         * 若你還想限制不能超過 ±5%，可以這樣做：
         * double[] range = calculatePriceRange(currentPrice, 0.05);
         * adjustedPrice = Math.min(Math.max(adjustedPrice, range[0]), range[1]);
         * order.setPrice(adjustedPrice);
         */
        // 3. 插入買單列表 (由高到低)
        synchronized (buyOrders) {
            int index = 0;
            while (index < buyOrders.size() && buyOrders.get(index).getPrice() > order.getPrice()) {
                index++;
            }
            // 若已有相同交易者 & 相同價格，可以考慮合併
            if (index < buyOrders.size()) {
                Order existingOrder = buyOrders.get(index);
                if (existingOrder.getPrice() == order.getPrice()
                        && existingOrder.getTrader() == order.getTrader()) {
                    existingOrder.setVolume(existingOrder.getVolume() + order.getVolume());
                } else {
                    buyOrders.add(index, order);
                }
            } else {
                buyOrders.add(order);
            }
        }

        notifyListeners();
    }

    /**
     * 提交賣單 (限價)
     *
     * @param order 賣單
     * @param currentPrice 市場當前參考價格
     */
    public void submitSellOrder(Order order, double currentPrice) {
        if (order == null) {
            System.out.println("Error: Order is null.");
            return;
        }
        if (order.getTrader() == null) {
            System.out.println("Error: Trader is null.");
            return;
        }
        UserAccount account = order.getTrader().getAccount();
        if (account == null) {
            System.out.println("Error: Trader account is null.");
            return;
        }

        // 1. 檢查持股
        if (!account.freezeStocks(order.getVolume())) {
            System.out.println("持股不足，無法掛賣單。");
            return;
        }

        // 2. 調整價格到 tick 大小
        double adjustedPrice = adjustPriceToUnit(order.getPrice());
        order.setPrice(adjustedPrice);

        // 3. 插入賣單列表 (由低到高)
        synchronized (sellOrders) {
            int index = 0;
            while (index < sellOrders.size() && sellOrders.get(index).getPrice() < order.getPrice()) {
                index++;
            }
            if (index < sellOrders.size()) {
                Order existingOrder = sellOrders.get(index);
                if (existingOrder.getPrice() == order.getPrice()
                        && existingOrder.getTrader() == order.getTrader()) {
                    existingOrder.setVolume(existingOrder.getVolume() + order.getVolume());
                } else {
                    sellOrders.add(index, order);
                }
            } else {
                sellOrders.add(order);
            }
        }

        notifyListeners();
    }

    // ================== 撮合/匹配核心 ==================
    /**
     * 處理訂單撮合
     *
     * @param stock 股票實例 (用來更新最新股價)
     */
    public void processOrders(Stock stock) {
        // (a) 準備異常日誌文件
        File logFile = new File(System.getProperty("user.home") + "/Desktop/MarketAnomalies.log");
        try ( BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {

            // (b) 先清理無效訂單(價格 <=0 或 量<=0)，再排序
            buyOrders = buyOrders.stream()
                    .filter(o -> o.getVolume() > 0 && o.getPrice() > 0)
                    .sorted((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice())) // 買單降序
                    .collect(Collectors.toList());

            sellOrders = sellOrders.stream()
                    .filter(o -> o.getVolume() > 0 && o.getPrice() > 0)
                    .sorted(Comparator.comparingDouble(Order::getPrice)) // 賣單升序
                    .collect(Collectors.toList());

            // (c) 開始撮合
            while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
                Order buyOrder = buyOrders.get(0);  // 最高買
                Order sellOrder = sellOrders.get(0); // 最低賣

                // 自我交易檢查
                if (buyOrder.getTrader() == sellOrder.getTrader()) {
                    // 可視需求把自我成交視為異常; 這裡僅做示範
                    String msg = String.format("[%s] 自我撮合異常: 買單 %s, 賣單 %s",
                            getCurrentTimestamp(), buyOrder, sellOrder);
                    writer.write(msg);
                    writer.newLine();
                    System.err.println(msg);

                    // 如果要跳過
//                    buyOrders.remove(0);
//                    sellOrders.remove(0);
                    // continue;
                }

                // canExecuteOrder: 若 買價 >= 賣價 則可成交
                if (canExecuteOrder(buyOrder, sellOrder)) {
                    // 執行撮合
                    int txVolume = executeTransaction(buyOrder, sellOrder, stock, writer);
                    // 更新圖表
                    simulation.updateVolumeChart(txVolume);
                    // 每次匹配後重新通知
                    notifyListeners();
                } else {
                    // 如果買方價格仍小於賣方，停止撮合
                    break;
                }
            }

            // (d) 撮合完成後，更新 UI
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
     * 判斷是否可以執行訂單
     * <p>
     * 改為：只要 買單價格 >= 賣單價格 即可成交 (不再使用 MAX_PRICE_DIFF_RATIO)。
     */
    private boolean canExecuteOrder(Order buyOrder, Order sellOrder) {
        return buyOrder.getPrice() >= sellOrder.getPrice();
    }

    /**
     * 執行一次撮合成交，並決定成交量、成交價，更新訂單與股票。
     */
    private int executeTransaction(Order buyOrder, Order sellOrder, Stock stock,
            BufferedWriter writer) throws IOException {
        // 1. 基本檢查
        if (!validateTransaction(buyOrder, sellOrder, stock)) {
            return 0;
        }

        // 2. 計算可成交量
        int theoreticalMax = Math.min(buyOrder.getVolume(), sellOrder.getVolume());
        // 避免一次成交太多：以三者最小值
        int maxTransactionVolume = Math.max(MIN_PER_TRANSACTION - 1, theoreticalMax / DIV_FACTOR);
        int txVolume = Math.min(theoreticalMax, maxTransactionVolume);

        // 3. 決定成交價 - 取中間價 (或你可改為 randomBetween(buy,sell))
        double finalPrice = (buyOrder.getPrice() + sellOrder.getPrice()) / 2.0;
        finalPrice = adjustPriceToUnit(finalPrice);

        // 4. 更新股價
        stock.setPrice(finalPrice);

        // 5. 扣減雙方剩餘量
        buyOrder.setVolume(buyOrder.getVolume() - txVolume);
        sellOrder.setVolume(sellOrder.getVolume() - txVolume);

        // 6. 若剩餘量 0，移除訂單
        if (buyOrder.getVolume() == 0) {
            buyOrders.remove(buyOrder);
        }
        if (sellOrder.getVolume() == 0) {
            sellOrders.remove(sellOrder);
        }

        // 7. 記錄交易
        Transaction transaction = new Transaction(
                buyOrder.getTrader().getTraderType(),
                sellOrder.getTrader().getTraderType(),
                finalPrice, txVolume
        );
        writer.write(transaction.toString());
        writer.newLine();

        // 8. 傳遞給 MarketAnalyzer
        simulation.getMarketAnalyzer().addTransaction(finalPrice, txVolume);

        // 9. 更新買方/賣方的帳戶
        updateTraderStatus(buyOrder, sellOrder, txVolume, finalPrice);

        // 10. 印出日誌
        System.out.printf("交易完成：成交量 %d，成交價格 %.2f%n", txVolume, finalPrice);

        return txVolume;
    }

    /**
     * 校驗交易條件 (股票、交易者帳戶是否為 null 等)
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
     * 更新交易者的帳戶狀態
     */
    private void updateTraderStatus(Order buyOrder, Order sellOrder, int volume, double price) {
        buyOrder.getTrader().updateAfterTransaction("buy", volume, price);
        sellOrder.getTrader().updateAfterTransaction("sell", volume, price);
    }

    // ============== 市價單實作 (略做調整) ==============
    /**
     * 市價買入
     * <p>
     * 直接吃當前賣單列表(從低到高), 直到買完或資金不足。
     */
    public void marketBuy(Trader trader, int quantity) {
        double remainingFunds = trader.getAccount().getAvailableFunds();
        int remainingQuantity = quantity;
        double totalTxValue = 0.0; // 累加這次市價單的成交值
        int totalTxVolume = 0;

        ListIterator<Order> it = sellOrders.listIterator();
        while (it.hasNext() && remainingQuantity > 0) {
            Order sellOrder = it.next();
            double sellPx = sellOrder.getPrice();
            int chunk = Math.min(sellOrder.getVolume(), remainingQuantity);
            double cost = chunk * sellPx;

            // 若自成交 -> 略過
            if (sellOrder.getTrader() == trader) {
                continue;
            }

            if (remainingFunds >= cost) {
                // 更新買方
                trader.getAccount().decrementFunds(cost);
                trader.getAccount().incrementStocks(chunk);
                remainingFunds -= cost;
                remainingQuantity -= chunk;

                // 更新賣方
                sellOrder.getTrader().updateAfterTransaction("sell", chunk, sellPx);

                totalTxValue += cost;
                totalTxVolume += chunk;

                // 更新賣單
                sellOrder.setVolume(sellOrder.getVolume() - chunk);
                if (sellOrder.getVolume() <= 0) {
                    it.remove();
                }
            } else {
                // 資金不足
                break;
            }
        }

        // 更新 MarketAnalyzer
        if (totalTxVolume > 0) {
            simulation.getMarketAnalyzer()
                    .addTransaction(totalTxValue / totalTxVolume, totalTxVolume);

            // 更新成交量圖
            final int finalVolume = totalTxVolume;
            SwingUtilities.invokeLater(() -> {
                simulation.updateLabels();
                simulation.updateVolumeChart(finalVolume);
                simulation.updateOrderBookDisplay();
            });
        }

        notifyListeners();
    }

    /**
     * 市價賣出
     * <p>
     * 直接吃當前買單列表(從高到低), 直到賣完或股數不足。
     */
    public void marketSell(Trader trader, int quantity) {
        int remainingQty = quantity;
        double totalTxValue = 0.0;
        int totalTxVolume = 0;

        ListIterator<Order> it = buyOrders.listIterator();
        while (it.hasNext() && remainingQty > 0) {
            Order buyOrder = it.next();
            double buyPx = buyOrder.getPrice();
            int chunk = Math.min(buyOrder.getVolume(), remainingQty);

            // 自成交檢查
            if (buyOrder.getTrader() == trader) {
                continue;
            }

            // 檢查賣方持股
            if (trader.getAccount().getStockInventory() >= chunk) {
                double revenue = buyPx * chunk;

                // 更新賣方
                trader.getAccount().decrementStocks(chunk);
                trader.getAccount().incrementFunds(revenue);
                remainingQty -= chunk;

                // 更新買方
                buyOrder.getTrader().updateAfterTransaction("buy", chunk, buyPx);

                totalTxValue += revenue;
                totalTxVolume += chunk;

                // 更新買單
                buyOrder.setVolume(buyOrder.getVolume() - chunk);
                if (buyOrder.getVolume() <= 0) {
                    it.remove();
                }
            } else {
                // 賣方持股不足
                break;
            }
        }

        if (totalTxVolume > 0) {
            simulation.getMarketAnalyzer()
                    .addTransaction(totalTxValue / totalTxVolume, totalTxVolume);

            final int finalVolume = totalTxVolume;
            SwingUtilities.invokeLater(() -> {
                simulation.updateLabels();
                simulation.updateVolumeChart(finalVolume);
                simulation.updateOrderBookDisplay();
            });
        }

        notifyListeners();
    }

    // ============== 取消訂單 / 其他功能 ==============
    /**
     * 取消掛單
     *
     * @param orderId 訂單ID
     */
    public void cancelOrder(String orderId) {
        // 先檢查買單
        Order canceled = buyOrders.stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst().orElse(null);

        if (canceled != null) {
            buyOrders.remove(canceled);
            double refund = canceled.getPrice() * canceled.getVolume();
            canceled.getTrader().getAccount().incrementFunds(refund);

            if (canceled.getTrader() instanceof PersonalAI) {
                ((PersonalAI) canceled.getTrader()).onOrderCancelled(canceled);
            }

            System.out.println("已取消買單：");
            System.out.println("訂單ID: " + orderId);
            System.out.println("數量: " + canceled.getVolume());
            System.out.println("單價: " + canceled.getPrice());
            System.out.println("退還資金: " + refund);
        } else {
            // 檢查賣單
            canceled = sellOrders.stream()
                    .filter(o -> o.getId().equals(orderId))
                    .findFirst().orElse(null);
            if (canceled != null) {
                sellOrders.remove(canceled);
                canceled.getTrader().getAccount().incrementStocks(canceled.getVolume());

                if (canceled.getTrader() instanceof PersonalAI) {
                    ((PersonalAI) canceled.getTrader()).onOrderCancelled(canceled);
                }

                System.out.println("已取消賣單：");
                System.out.println("訂單ID: " + orderId);
                System.out.println("數量: " + canceled.getVolume());
                System.out.println("單價: " + canceled.getPrice());
                System.out.println("退還股票: " + canceled.getVolume());
            } else {
                System.out.println("找不到該訂單ID: " + orderId + "，無法取消。");
            }
        }

        SwingUtilities.invokeLater(() -> simulation.updateOrderBookDisplay());
        notifyListeners();
    }

    /**
     * 添加 OrderBookListener
     */
    public void addOrderBookListener(OrderBookListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除 OrderBookListener
     */
    public void removeOrderBookListener(OrderBookListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知所有監聽者
     */
    private void notifyListeners() {
        for (OrderBookListener l : listeners) {
            l.onOrderBookUpdated();
        }
    }

    /**
     * 計算市場平均成交量 - 用於成交量異常檢測 (非關鍵)
     */
    private int calculateAverageVolume() {
        // 這裡僅以賣單總量 / 賣單筆數 為平均量，可自行調整
        return sellOrders.isEmpty() ? 1
                : sellOrders.stream().mapToInt(Order::getVolume).sum() / sellOrders.size();
    }

    /**
     * 檢測價格閃崩 (若需要，可在 executeTransaction(...) 裡呼叫)
     */
    private boolean detectPriceAnomaly(double prevPrice, double currentPrice) {
        double ratio = Math.abs(currentPrice - prevPrice) / prevPrice;
        double limit = 0.05; // ±5%
        return ratio > limit;
    }

    /**
     * 檢測成交量異常 (若需要)
     */
    private boolean detectVolumeAnomaly(int txVolume) {
        int avg = calculateAverageVolume();
        double multiplier = 3.0;
        return txVolume > avg * multiplier || txVolume < avg * 0.1;
    }

    /**
     * 取得現在時戳
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ============= 取得買/賣單列表給外部使用 =============
    public List<Order> getBuyOrders() {
        return new ArrayList<>(buyOrders);
    }

    public List<Order> getSellOrders() {
        return new ArrayList<>(sellOrders);
    }

    public List<Order> getTopBuyOrders(int n) {
        return buyOrders.stream()
                .sorted((a, b) -> Double.compare(b.getPrice(), a.getPrice()))
                .limit(n).collect(Collectors.toList());
    }

    public List<Order> getTopSellOrders(int n) {
        return sellOrders.stream()
                .sorted(Comparator.comparingDouble(Order::getPrice))
                .limit(n).collect(Collectors.toList());
    }

    public List<Order> getBuyOrdersByTraderType(String type) {
        if (type == null || type.isEmpty()) {
            return new ArrayList<>();
        }
        List<Order> snapshot;
        synchronized (buyOrders) {
            snapshot = new ArrayList<>(buyOrders);
        }
        return snapshot.stream()
                .filter(o -> o != null && o.getTrader() != null
                && type.equalsIgnoreCase(o.getTrader().getTraderType()))
                .collect(Collectors.toList());
    }

    public List<Order> getSellOrdersByTraderType(String type) {
        if (type == null || type.isEmpty()) {
            return new ArrayList<>();
        }
        List<Order> snapshot;
        synchronized (sellOrders) {
            snapshot = new ArrayList<>(sellOrders);
        }
        return snapshot.stream()
                .filter(o -> o != null && o.getTrader() != null
                && type.equalsIgnoreCase(o.getTrader().getTraderType()))
                .collect(Collectors.toList());
    }

    public int getAvailableBuyVolume(double price) {
        return buyOrders.stream()
                .filter(order -> order.getPrice() >= price)
                .mapToInt(Order::getVolume)
                .sum();
    }

    public int getAvailableSellVolume(double price) {
        return sellOrders.stream()
                .filter(order -> order.getPrice() <= price)
                .mapToInt(Order::getVolume)
                .sum();
    }
}
