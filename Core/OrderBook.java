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
import java.util.HashMap;
import java.util.Map;

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

    // 新增屬性
    private MatchingMode matchingMode = MatchingMode.STANDARD; // 默認使用標準模式
    private double randomModeChangeProbability = 0.0; // 隨機模式切換概率
    private final Map<Order, Long> orderTimestamps = new HashMap<>(); // 訂單時間戳記錄
    private double liquidityFactor = 1.0; // 流動性因子
    private double depthImpactFactor = 0.2; // 深度影響因子

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

    /**
     * 提交買單 (限價) - 修正版，避免重複添加訂單
     *
     * @param order 買單
     * @param currentPrice 市場當前參考價格
     */
    public void submitBuyOrder(Order order, double currentPrice) {
        // 移除這行以避免重複添加訂單
        // buyOrders.add(order);

        // 記錄訂單時間戳
        orderTimestamps.put(order, System.currentTimeMillis());

        // 基本檢查
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

        // 3. 插入買單列表 (由高到低)
        synchronized (buyOrders) {
            // 先檢查是否有相同價格和交易者的訂單可以合併
            boolean merged = false;
            for (Order existingOrder : buyOrders) {
                if (existingOrder.getPrice() == order.getPrice()
                        && existingOrder.getTrader() == order.getTrader()) {
                    // 找到相同訂單，合併數量
                    existingOrder.setVolume(existingOrder.getVolume() + order.getVolume());
                    System.out.println("合併相同價格的買單，總數量：" + existingOrder.getVolume());
                    merged = true;
                    break;
                }
            }

            // 如果沒有合併，則按價格排序插入新訂單
            if (!merged) {
                int index = 0;
                while (index < buyOrders.size() && buyOrders.get(index).getPrice() > order.getPrice()) {
                    index++;
                }
                buyOrders.add(index, order);
            }
        }

        // 通知訂單更新
        notifyListeners();
    }

    /**
     * 提交賣單 (限價) - 修正版，避免重複添加訂單
     *
     * @param order 賣單
     * @param currentPrice 市場當前參考價格
     */
    public void submitSellOrder(Order order, double currentPrice) {
        // 移除這行以避免重複添加訂單
        // sellOrders.add(order);

        // 記錄訂單時間戳
        orderTimestamps.put(order, System.currentTimeMillis());

        // 基本檢查
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
            // 先檢查是否有相同價格和交易者的訂單可以合併
            boolean merged = false;
            for (Order existingOrder : sellOrders) {
                if (existingOrder.getPrice() == order.getPrice()
                        && existingOrder.getTrader() == order.getTrader()) {
                    // 找到相同訂單，合併數量
                    existingOrder.setVolume(existingOrder.getVolume() + order.getVolume());
                    System.out.println("合併相同價格的賣單，總數量：" + existingOrder.getVolume());
                    merged = true;
                    break;
                }
            }

            // 如果沒有合併，則按價格排序插入新訂單
            if (!merged) {
                int index = 0;
                while (index < sellOrders.size() && sellOrders.get(index).getPrice() < order.getPrice()) {
                    index++;
                }
                sellOrders.add(index, order);
            }
        }

        // 通知訂單更新
        notifyListeners();
    }

    /**
     * 提交FOK買單 (Fill or Kill)
     *
     * @return 是否成功提交
     */
    public boolean submitFokBuyOrder(double price, int volume, Trader trader) {
        // 檢查是否有足夠賣單以完全滿足此買單
        int availableSellVolume = getAvailableSellVolume(price);
        if (availableSellVolume < volume) {
            // 無法完全滿足，取消訂單
            System.out.println("FOK買單無法完全滿足，已取消");
            return false;
        }

        // 可以完全滿足，創建並提交訂單
        Order fokBuyOrder = Order.createFokBuyOrder(price, volume, trader);
        buyOrders.add(fokBuyOrder);
        orderTimestamps.put(fokBuyOrder, System.currentTimeMillis());
        notifyListeners();
        return true;
    }

    /**
     * 提交FOK賣單
     *
     * @return 是否成功提交
     */
    public boolean submitFokSellOrder(double price, int volume, Trader trader) {
        // 檢查是否有足夠買單以完全滿足此賣單
        int availableBuyVolume = getAvailableBuyVolume(price);
        if (availableBuyVolume < volume) {
            // 無法完全滿足，取消訂單
            System.out.println("FOK賣單無法完全滿足，已取消");
            return false;
        }

        // 可以完全滿足，創建並提交訂單
        Order fokSellOrder = Order.createFokSellOrder(price, volume, trader);
        sellOrders.add(fokSellOrder);
        orderTimestamps.put(fokSellOrder, System.currentTimeMillis());
        notifyListeners();
        return true;
    }

    // ================== 撮合/匹配核心 ==================
    /**
     * 處理訂單撮合 - 增強版
     *
     * @param stock 股票實例 (用來更新最新股價)
     */
    public void processOrders(Stock stock) {
        // (a) 準備異常日誌文件
        File logFile = new File(System.getProperty("user.home") + "/Desktop/MarketAnomalies.log");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            // (b) 先處理FOK訂單
            handleFokOrders();

            // (c) 先清理無效訂單並排序 - 優先考慮市價單
            buyOrders = buyOrders.stream()
                    .filter(o -> o.getVolume() > 0 && (o.isMarketOrder() || o.getPrice() > 0))
                    .sorted((o1, o2) -> {
                        // 市價單優先
                        if (o1.isMarketOrder() && !o2.isMarketOrder()) {
                            return -1;
                        }
                        if (!o1.isMarketOrder() && o2.isMarketOrder()) {
                            return 1;
                        }
                        // 價格優先
                        if (!o1.isMarketOrder() && !o2.isMarketOrder()) {
                            int priceCompare = Double.compare(o2.getPrice(), o1.getPrice());
                            // 如果模式是價格時間優先，則同價格情況下考慮時間
                            if (priceCompare == 0 && matchingMode == MatchingMode.PRICE_TIME) {
                                long t1 = orderTimestamps.getOrDefault(o1, Long.MAX_VALUE);
                                long t2 = orderTimestamps.getOrDefault(o2, Long.MAX_VALUE);
                                return Long.compare(t1, t2);
                            }
                            return priceCompare;
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());

            sellOrders = sellOrders.stream()
                    .filter(o -> o.getVolume() > 0 && (o.isMarketOrder() || o.getPrice() > 0))
                    .sorted((o1, o2) -> {
                        // 市價單優先
                        if (o1.isMarketOrder() && !o2.isMarketOrder()) {
                            return -1;
                        }
                        if (!o1.isMarketOrder() && o2.isMarketOrder()) {
                            return 1;
                        }
                        // 價格優先
                        if (!o1.isMarketOrder() && !o2.isMarketOrder()) {
                            int priceCompare = Double.compare(o1.getPrice(), o2.getPrice());
                            // 如果模式是價格時間優先，則同價格情況下考慮時間
                            if (priceCompare == 0 && matchingMode == MatchingMode.PRICE_TIME) {
                                long t1 = orderTimestamps.getOrDefault(o1, Long.MAX_VALUE);
                                long t2 = orderTimestamps.getOrDefault(o2, Long.MAX_VALUE);
                                return Long.compare(t1, t2);
                            }
                            return priceCompare;
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());

            // (d) 開始撮合
            boolean transactionOccurred = true;
            int maxRounds = 10; // 限制最大撮合次數，防止無限循環
            int currentRound = 0;

            while (transactionOccurred && currentRound < maxRounds) {
                transactionOccurred = false;
                currentRound++;

                // 如果此輪沒有新成交，跳出循環
                if (buyOrders.isEmpty() || sellOrders.isEmpty()) {
                    break;
                }

                // 處理正常買賣訂單撮合
                int i = 0;
                while (i < buyOrders.size() && i < sellOrders.size()) {
                    Order buyOrder = buyOrders.get(i);
                    Order sellOrder = sellOrders.get(i);

                    // 自我交易檢查
                    if (buyOrder.getTrader() == sellOrder.getTrader()) {
                        // 記錄自我交易異常
                        String msg = String.format("[%s] 自我撮合異常: 買單 %s, 賣單 %s",
                                getCurrentTimestamp(), buyOrder, sellOrder);
                        writer.write(msg);
                        writer.newLine();
                        System.err.println(msg);

                        // 根據撮合模式決定如何處理自我交易
                        if (matchingMode == MatchingMode.STANDARD) {
                            // 標準模式下跳過自我撮合
                            i++;
                            continue;
                        }
                        // 其他模式可以允許自我交易 (真實市場中交易所可能允許)
                    }

                    // 檢查是否可以撮合
                    if (canExecuteOrder(buyOrder, sellOrder)) {
                        // 執行撮合
                        int txVolume = executeTransaction(buyOrder, sellOrder, stock, writer);
                        if (txVolume > 0) {
                            transactionOccurred = true;
                            // 更新圖表
                            simulation.updateVolumeChart(txVolume);
                            // 每次匹配後重新通知
                            notifyListeners();

                            // 撮合成功後重新獲取訂單排序，所以重置index
                            i = 0;
                        } else {
                            i++;
                        }
                    } else {
                        i++;
                    }
                }
            }

            // (e) 撮合完成後，更新 UI
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
     * 判斷是否可以執行訂單 - 增強版 考慮不同的撮合模式和訂單類型
     */
    private boolean canExecuteOrder(Order buyOrder, Order sellOrder) {
        // 如果有任一訂單是市價單，直接撮合
        if (buyOrder.isMarketOrder() || sellOrder.isMarketOrder()) {
            return true;
        }

        // 標準價格比較：買價 >= 賣價則可成交
        boolean standardMatch = buyOrder.getPrice() >= sellOrder.getPrice();

        // 基於當前撮合模式的特殊判斷
        switch (matchingMode) {
            case STANDARD:
                return standardMatch;

            case PRICE_TIME:
                // 標準匹配還需進一步檢查訂單優先級
                if (standardMatch) {
                    // 此處完全相同價格的訂單會考慮時間優先
                    return true;
                }
                return false;

            case VOLUME_WEIGHTED:
                // 在標準匹配的基礎上，大單有更高優先級
                if (standardMatch) {
                    return true;
                }
                // 如果是大單，允許小幅價差
                int maxVolume = Math.max(buyOrder.getVolume(), sellOrder.getVolume());
                if (maxVolume > 1000) {
                    double priceDiff = (buyOrder.getPrice() - sellOrder.getPrice()) / sellOrder.getPrice();
                    return priceDiff > -0.01; // 允許1%價差
                }
                return false;

            case MARKET_PRESSURE:
                // 根據買賣單總量比例，動態調整撮合標準
                double buyPressure = calculateBuyPressure();
                // 買方壓力大時，即使買價稍低也可能成交
                if (buyPressure > 1.5) {
                    double priceDiff = (buyOrder.getPrice() - sellOrder.getPrice()) / sellOrder.getPrice();
                    return priceDiff > -0.02; // 允許2%價差
                } // 賣方壓力大時，要求更高買價
                else if (buyPressure < 0.7) {
                    return buyOrder.getPrice() >= sellOrder.getPrice() * 1.01; // 要求額外1%溢價
                }
                return standardMatch;

            case RANDOM:
                // 標準匹配基礎上增加隨機因素
                if (standardMatch) {
                    return true;
                }
                // 有10%機率即使不完全匹配也成交 (模擬市場噪聲)
                Random random = new Random();
                double priceDiff = (buyOrder.getPrice() - sellOrder.getPrice()) / sellOrder.getPrice();
                return priceDiff > -0.01 && random.nextDouble() < 0.1;

            default:
                return standardMatch;
        }
    }

    /**
     * 根據撮合模式計算成交價格
     */
    private double calculateMatchPrice(Order buyOrder, Order sellOrder, int volume) {
        double buyPrice = buyOrder.getPrice();
        double sellPrice = sellOrder.getPrice();

        // 市價單處理
        if (buyOrder.isMarketOrder()) {
            return sellPrice; // 市價買，以賣價成交
        } else if (sellOrder.isMarketOrder()) {
            return buyPrice; // 市價賣，以買價成交
        }

        // 根據不同撮合模式計算價格
        switch (matchingMode) {
            case STANDARD:
                // 標準模式：中間價
                return (buyPrice + sellPrice) / 2.0;

            case PRICE_TIME:
                // 價格時間優先：先到先得優勢
                Long buyTime = orderTimestamps.getOrDefault(buyOrder, System.currentTimeMillis());
                Long sellTime = orderTimestamps.getOrDefault(sellOrder, System.currentTimeMillis());

                if (buyTime < sellTime) {
                    // 買單先到，偏向買方價格 (60/40分)
                    return buyPrice * 0.6 + sellPrice * 0.4;
                } else {
                    // 賣單先到，偏向賣方價格 (40/60分)
                    return buyPrice * 0.4 + sellPrice * 0.6;
                }

            case VOLUME_WEIGHTED:
                // 成交量加權定價 - 大單有議價能力
                if (buyOrder.getVolume() > sellOrder.getVolume() * 2) {
                    // 買方量大，偏向買方價格
                    return buyPrice * 0.7 + sellPrice * 0.3;
                } else if (sellOrder.getVolume() > buyOrder.getVolume() * 2) {
                    // 賣方量大，偏向賣方價格
                    return buyPrice * 0.3 + sellPrice * 0.7;
                } else {
                    // 量相近，按比例加權
                    double buyWeight = (double) buyOrder.getVolume() / (buyOrder.getVolume() + sellOrder.getVolume());
                    return buyPrice * (1 - buyWeight) + sellPrice * buyWeight;
                }

            case MARKET_PRESSURE:
                // 市場壓力模式：考慮買賣壓力
                double buyPressure = calculateBuyPressure();
                if (buyPressure > 1.5) {
                    // 買方壓力大，賣方有優勢
                    return sellPrice * 0.8 + buyPrice * 0.2;
                } else if (buyPressure < 0.7) {
                    // 賣方壓力大，買方有優勢
                    return sellPrice * 0.2 + buyPrice * 0.8;
                } else {
                    // 壓力平衡，適度偏向近期趨勢
                    double recentTrend = 0.0;
                    try {
                        recentTrend = simulation.getMarketAnalyzer().getRecentPriceTrend();
                    } catch (Exception e) {
                        // 如果無法獲取趨勢，使用默認值
                    }

                    if (recentTrend > 0.01) {
                        // 上漲趨勢，偏向賣方
                        return sellPrice * 0.6 + buyPrice * 0.4;
                    } else if (recentTrend < -0.01) {
                        // 下跌趨勢，偏向買方
                        return sellPrice * 0.4 + buyPrice * 0.6;
                    } else {
                        // 盤整，中間價
                        return (buyPrice + sellPrice) / 2.0;
                    }
                }

            case RANDOM:
                // 隨機模式：增加隨機性但仍在買賣價之間
                Random random = new Random();
                double randomFactor = random.nextDouble(); // 0-1之間
                // 額外增加少許波動
                double extraNoise = (random.nextDouble() - 0.5) * 0.02 * sellPrice;
                double basePrice = sellPrice * (1 - randomFactor) + buyPrice * randomFactor;
                return basePrice + extraNoise;

            default:
                return (buyPrice + sellPrice) / 2.0;
        }
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

        // 2. 可能隨機切換撮合模式
        if (Math.random() < randomModeChangeProbability) {
            matchingMode = MatchingMode.getRandom();
            System.out.println("撮合模式隨機切換到: " + matchingMode);
        }

        // 3. 計算可成交量 - 考慮流動性因素
        int theoreticalMax = Math.min(buyOrder.getVolume(), sellOrder.getVolume());
        // 根據流動性調整成交量
        int adjustedMax = (int) (theoreticalMax * liquidityFactor);

        // 最小成交量不變
        int maxTransactionVolume = Math.max(MIN_PER_TRANSACTION - 1,
                adjustedMax / DIV_FACTOR);
        int txVolume = Math.min(adjustedMax, maxTransactionVolume);

        // 市價單優先考慮最大成交
        if (buyOrder.isMarketOrder() || sellOrder.isMarketOrder()) {
            txVolume = Math.min(buyOrder.getVolume(), sellOrder.getVolume());
        }

        // 4. 根據撮合模式決定成交價
        double finalPrice = calculateMatchPrice(buyOrder, sellOrder, txVolume);
        finalPrice = adjustPriceToUnit(finalPrice);

        // 5. 更新股價
        stock.setPrice(finalPrice);

        // 6. 扣減雙方剩餘量
        buyOrder.setVolume(buyOrder.getVolume() - txVolume);
        sellOrder.setVolume(sellOrder.getVolume() - txVolume);

        // 7. 若剩餘量 0，移除訂單並清理時間戳
        if (buyOrder.getVolume() == 0) {
            buyOrders.remove(buyOrder);
            orderTimestamps.remove(buyOrder);
        }
        if (sellOrder.getVolume() == 0) {
            sellOrders.remove(sellOrder);
            orderTimestamps.remove(sellOrder);
        }

        // 8. 記錄交易
        Transaction transaction = new Transaction(
                buyOrder.getTrader().getTraderType(),
                sellOrder.getTrader().getTraderType(),
                finalPrice, txVolume, matchingMode.toString() // 新增匹配模式記錄
        );
        writer.write(transaction.toString());
        writer.newLine();

        // 9. 傳遞給 MarketAnalyzer
        simulation.getMarketAnalyzer().addTransaction(finalPrice, txVolume);

        // 10. 更新買方/賣方的帳戶
        updateTraderStatus(buyOrder, sellOrder, txVolume, finalPrice);

        // 11. 印出詳細日誌，包括撮合模式
        System.out.printf("交易完成 [%s模式]：成交量 %d，成交價格 %.2f%n",
                matchingMode, txVolume, finalPrice);

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

    /**
     * 更新撮合模式
     *
     * @param mode 新的撮合模式
     */
    public void setMatchingMode(MatchingMode mode) {
        this.matchingMode = mode;
        System.out.println("撮合模式已更改為: " + mode);
    }

    /**
     * 獲取當前撮合模式
     */
    public MatchingMode getMatchingMode() {
        return this.matchingMode;
    }

    /**
     * 設置是否使用隨機切換模式
     *
     * @param useRandom 是否隨機切換
     * @param probability 切換概率 (0-1)
     */
    public void setRandomModeSwitching(boolean useRandom, double probability) {
        if (useRandom) {
            this.randomModeChangeProbability = Math.max(0, Math.min(1, probability));
            System.out.println("已啟用隨機撮合模式切換，概率: " + this.randomModeChangeProbability);
        } else {
            this.randomModeChangeProbability = 0;
            System.out.println("已禁用隨機撮合模式切換");
        }
    }

    /**
     * 設置流動性係數
     *
     * @param factor 流動性係數 (0.5-2.0)
     */
    public void setLiquidityFactor(double factor) {
        this.liquidityFactor = Math.max(0.5, Math.min(2.0, factor));
        System.out.println("流動性係數更新為: " + this.liquidityFactor);
    }

    /**
     * 計算買方壓力 (買單總量/賣單總量)
     *
     * @return 買賣壓力比值
     */
    private double calculateBuyPressure() {
        int totalBuyVolume = buyOrders.stream().mapToInt(Order::getVolume).sum();
        int totalSellVolume = sellOrders.stream().mapToInt(Order::getVolume).sum();

        if (totalSellVolume == 0) {
            return 5.0; // 防止除零
        }
        return (double) totalBuyVolume / totalSellVolume;
    }

    // ============== 市價單實作 (略做調整) ==============
    /**
     * 處理FOK訂單
     */
    private void handleFokOrders() {
        // 處理FOK買單
        List<Order> fokBuyOrders = buyOrders.stream()
                .filter(Order::isFillOrKill)
                .collect(Collectors.toList());

        for (Order fokOrder : fokBuyOrders) {
            int availableSellVolume = getAvailableSellVolume(fokOrder.getPrice());
            if (availableSellVolume < fokOrder.getVolume()) {
                // 無法完全滿足，從訂單簿中移除
                buyOrders.remove(fokOrder);
                orderTimestamps.remove(fokOrder);
                System.out.println("移除無法完全滿足的FOK買單: " + fokOrder);
            }
        }

        // 處理FOK賣單
        List<Order> fokSellOrders = sellOrders.stream()
                .filter(Order::isFillOrKill)
                .collect(Collectors.toList());

        for (Order fokOrder : fokSellOrders) {
            int availableBuyVolume = getAvailableBuyVolume(fokOrder.getPrice());
            if (availableBuyVolume < fokOrder.getVolume()) {
                // 無法完全滿足，從訂單簿中移除
                sellOrders.remove(fokOrder);
                orderTimestamps.remove(fokOrder);
                System.out.println("移除無法完全滿足的FOK賣單: " + fokOrder);
            }
        }
    }

    /**
     * 市價買入方法 - 保持既有邏輯但考慮新的撮合模式
     */
    public void marketBuy(Trader trader, int quantity) {
        // 現有的直接撮合邏輯保持不變
        double remainingFunds = trader.getAccount().getAvailableFunds();
        int remainingQuantity = quantity;
        double totalTxValue = 0.0; // 累加這次市價單的成交值
        int totalTxVolume = 0;

        // 檢查可用資金是否足夠最低估計
        if (remainingFunds <= 0) {
            System.out.println("市價買入失敗：資金不足");
            return;
        }

        // 或者也可考慮使用新的訂單創建方法
        // Order marketBuyOrder = Order.createMarketBuyOrder(quantity, trader);
        // buyOrders.add(marketBuyOrder);
        // orderTimestamps.put(marketBuyOrder, System.currentTimeMillis());
        // 然後在下一次處理訂單時由撮合引擎處理
        // 但這會改變現有的即時撮合邏輯
        // 繼續使用現有的即時撮合邏輯
        ListIterator<Order> it = sellOrders.listIterator();
        while (it.hasNext() && remainingQuantity > 0) {
            Order sellOrder = it.next();
            double sellPx = sellOrder.getPrice();
            int chunk = Math.min(sellOrder.getVolume(), remainingQuantity);
            double cost = chunk * sellPx;

            // 若自成交 -> 略過，或根據撮合模式決定是否允許自成交
            if (sellOrder.getTrader() == trader) {
                // 如果在特定撮合模式下允許自成交，則移除此檢查
                if (matchingMode != MatchingMode.STANDARD) {
                    // 在非標準模式下允許自成交
                } else {
                    continue; // 標準模式跳過自成交
                }
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
            double avgPrice = totalTxValue / totalTxVolume;
            simulation.getMarketAnalyzer().addTransaction(avgPrice, totalTxVolume);

            // 對於市價單，也向 MarketAnalyzer 添加新價格點
            simulation.getMarketAnalyzer().addPrice(avgPrice);

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
     * 市價賣出 - 保持既有邏輯但考慮新的撮合模式
     */
    public void marketSell(Trader trader, int quantity) {
        int remainingQty = quantity;
        double totalTxValue = 0.0;
        int totalTxVolume = 0;

        // 檢查持股是否足夠
        if (trader.getAccount().getStockInventory() < quantity) {
            System.out.println("市價賣出失敗：持股不足");
            return;
        }

        // 或者也可考慮使用新的訂單創建方法
        // Order marketSellOrder = Order.createMarketSellOrder(quantity, trader);
        // sellOrders.add(marketSellOrder);
        // orderTimestamps.put(marketSellOrder, System.currentTimeMillis());
        // 然後在下一次處理訂單時由撮合引擎處理
        // 但這會改變現有的即時撮合邏輯
        // 繼續使用現有的即時撮合邏輯
        ListIterator<Order> it = buyOrders.listIterator();
        while (it.hasNext() && remainingQty > 0) {
            Order buyOrder = it.next();
            double buyPx = buyOrder.getPrice();
            int chunk = Math.min(buyOrder.getVolume(), remainingQty);

            // 自成交檢查，或根據撮合模式決定是否允許自成交
            if (buyOrder.getTrader() == trader) {
                // 如果在特定撮合模式下允許自成交，則移除此檢查
                if (matchingMode != MatchingMode.STANDARD) {
                    // 在非標準模式下允許自成交
                } else {
                    continue; // 標準模式跳過自成交
                }
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
            double avgPrice = totalTxValue / totalTxVolume;
            simulation.getMarketAnalyzer().addTransaction(avgPrice, totalTxVolume);

            // 對於市價單，也向 MarketAnalyzer 添加新價格點
            simulation.getMarketAnalyzer().addPrice(avgPrice);

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

    /**
     * 獲取前N個買單 - 考慮所有訂單類型
     */
    public List<Order> getTopBuyOrders(int count) {
        return buyOrders.stream()
                .sorted((o1, o2) -> {
                    // 市價單優先
                    if (o1.isMarketOrder() && !o2.isMarketOrder()) {
                        return -1;
                    }
                    if (!o1.isMarketOrder() && o2.isMarketOrder()) {
                        return 1;
                    }
                    // 價格優先（買單降序）
                    return Double.compare(o2.getPrice(), o1.getPrice());
                })
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * 獲取前N個賣單 - 考慮所有訂單類型
     */
    public List<Order> getTopSellOrders(int count) {
        return sellOrders.stream()
                .sorted((o1, o2) -> {
                    // 市價單優先
                    if (o1.isMarketOrder() && !o2.isMarketOrder()) {
                        return -1;
                    }
                    if (!o1.isMarketOrder() && o2.isMarketOrder()) {
                        return 1;
                    }
                    // 價格優先（賣單升序）
                    return Double.compare(o1.getPrice(), o2.getPrice());
                })
                .limit(count)
                .collect(Collectors.toList());
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
