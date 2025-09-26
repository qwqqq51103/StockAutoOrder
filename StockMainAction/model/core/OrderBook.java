package StockMainAction.model.core;

import StockMainAction.model.PersonalAI;
import StockMainAction.model.core.Stock;
import StockMainAction.controller.listeners.OrderBookListener;
import StockMainAction.StockMarketSimulation;
import StockMainAction.model.StockMarketModel;
import StockMainAction.model.user.UserAccount;
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
import StockMainAction.util.logging.MarketLogger;
import StockMainAction.util.logging.LogicAudit;
import java.util.Collections;

/**
 * 訂單簿類別，管理買賣訂單的提交和撮合（改良後的版本）。
 */
public class OrderBook {

    private List<Order> buyOrders;   // 買單列表 (由高價到低價)
    private List<Order> sellOrders;  // 賣單列表 (由低價到高價)
    private StockMarketSimulation simulation;
    private StockMarketModel model;

    // Listener list
    private List<OrderBookListener> listeners;

    private static final MarketLogger logger = MarketLogger.getInstance();

    // ============= 參數可自行調整 =================
    /**
     * 當前版本只保留撮合「buyPrice >= sellPrice」即可成交，不再使用此常數。
     */
    final double MAX_PRICE_DIFF_RATIO = 0.25;

    /**
     * 單次撮合量的限制參數。
     */
    private static final int MAX_PER_TRANSACTION = 5000; // 單筆撮合上限，避免一次吃掉太多深度
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
    public OrderBook(StockMarketModel model) {
        this.buyOrders = Collections.synchronizedList(new ArrayList<>());
        this.sellOrders = Collections.synchronizedList(new ArrayList<>());
        this.model = model;
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
        try {
            // 記錄訂單時間戳
            orderTimestamps.put(order, System.currentTimeMillis());

            // 基本檢查
            if (order == null) {
                logger.error("嘗試提交空訂單", "ORDER_SUBMIT");
                return;
            }
            if (order.getTrader() == null) {
                logger.error("嘗試提交的訂單缺少交易者", "ORDER_SUBMIT");
                return;
            }
            UserAccount account = order.getTrader().getAccount();
            if (account == null) {
                logger.error("嘗試提交的訂單交易者帳戶為空", "ORDER_SUBMIT");
                return;
            }

            // 1. 檢查資金
            double totalCost = order.getPrice() * order.getVolume();
            if (!account.freezeFunds(totalCost)) {
                logger.warn(String.format(
                        "資金不足，無法掛買單：需要 %.2f，可用資金不足",
                        totalCost
                ), "ORDER_SUBMIT");
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

                        logger.info(String.format(
                                "合併相同價格的買單：交易者=%s, 價格=%.2f, 合併後數量=%d",
                                existingOrder.getTrader().getTraderType(),
                                existingOrder.getPrice(),
                                existingOrder.getVolume()
                        ), "ORDER_SUBMIT");

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

                    logger.info(String.format(
                            "新增買單：交易者=%s, 價格=%.2f, 數量=%d",
                            order.getTrader().getTraderType(),
                            order.getPrice(),
                            order.getVolume()
                    ), "ORDER_SUBMIT");
                }
            }

            // 通知訂單更新
            notifyListeners();

        } catch (Exception e) {
            logger.error("提交買單過程中發生異常：" + e.getMessage(), "ORDER_SUBMIT");
        }
    }

    /**
     * 提交賣單 (限價) - 修正版，避免重複添加訂單
     *
     * @param order 賣單
     * @param currentPrice 市場當前參考價格
     */
    public void submitSellOrder(Order order, double currentPrice) {
        try {
            // 記錄訂單時間戳
            orderTimestamps.put(order, System.currentTimeMillis());

            // 基本檢查
            if (order == null) {
                logger.error("嘗試提交空訂單", "ORDER_SUBMIT");
                return;
            }
            if (order.getTrader() == null) {
                logger.error("嘗試提交的訂單缺少交易者", "ORDER_SUBMIT");
                return;
            }
            UserAccount account = order.getTrader().getAccount();
            if (account == null) {
                logger.error("嘗試提交的訂單交易者帳戶為空", "ORDER_SUBMIT");
                return;
            }

            // 1. 檢查持股
            if (!account.freezeStocks(order.getVolume())) {
                logger.warn(String.format(
                        "持股不足，無法掛賣單：需要 %d，可用持股不足",
                        order.getVolume()
                ), "ORDER_SUBMIT");
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

                        logger.info(String.format(
                                "合併相同價格的賣單：交易者=%s, 價格=%.2f, 合併後數量=%d",
                                existingOrder.getTrader().getTraderType(),
                                existingOrder.getPrice(),
                                existingOrder.getVolume()
                        ), "ORDER_SUBMIT");

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

                    logger.info(String.format(
                            "新增賣單：交易者=%s, 價格=%.2f, 數量=%d",
                            order.getTrader().getTraderType(),
                            order.getPrice(),
                            order.getVolume()
                    ), "ORDER_SUBMIT");
                }
            }

            // 通知訂單更新
            notifyListeners();

        } catch (Exception e) {
            logger.error("提交賣單過程中發生異常：" + e.getMessage(), "ORDER_SUBMIT");
        }
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
            System.out.println("FOK買單無法完全滿足，已取消");
            return false;
        }

        // 直接入簿並立即觸發撮合，實現「立即成交」語義
        Order fokBuyOrder = Order.createFokBuyOrder(price, volume, trader);
        buyOrders.add(0, fokBuyOrder);
        orderTimestamps.put(fokBuyOrder, System.currentTimeMillis());
        notifyListeners();
        // 立即處理撮合
        if (model != null && model.getStock() != null) {
            processOrders(model.getStock());
        }
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
            System.out.println("FOK賣單無法完全滿足，已取消");
            return false;
        }

        // 直接入簿並立即觸發撮合
        Order fokSellOrder = Order.createFokSellOrder(price, volume, trader);
        sellOrders.add(0, fokSellOrder);
        orderTimestamps.put(fokSellOrder, System.currentTimeMillis());
        notifyListeners();
        if (model != null && model.getStock() != null) {
            processOrders(model.getStock());
        }
        return true;
    }

    // ================== 撮合/匹配核心 ==================
    /**
     * 處理訂單撮合 - 增強版
     *
     * @param stock 股票實例 (用來更新最新股價)
     */
    public void processOrders(Stock stock) {
        logger.info("開始處理訂單撮合", "ORDER_PROCESSING");
        logger.debug("處理訂單：使用模式=" + matchingMode, "ORDER_BOOK");
        LogicAudit.info("ORDER_MATCH", "start | mode=" + matchingMode);

        // 準備異常日誌文件
        File logFile = new File(System.getProperty("user.home") + "/Desktop/MarketAnomalies.log");
        try ( BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            // 處理FOK訂單
            try {
                handleFokOrders();
                logger.info("成功處理FOK訂單", "ORDER_PROCESSING");
                LogicAudit.info("ORDER_MATCH", "FOK handled");
            } catch (Exception e) {
                logger.error("處理FOK訂單時發生異常：" + e.getMessage(), "ORDER_PROCESSING");
            }

            // 清理並排序訂單
            int initialBuyOrdersCount = buyOrders.size();
            int initialSellOrdersCount = sellOrders.size();

            buyOrders = buyOrders.stream()
                    .filter(o -> o.getVolume() > 0 && (o.isMarketOrder() || o.getPrice() > 0))
                    .sorted((o1, o2) -> {
                        // 市價單和價格排序邏輯
                        if (o1.isMarketOrder() && !o2.isMarketOrder()) {
                            return -1;
                        }
                        if (!o1.isMarketOrder() && o2.isMarketOrder()) {
                            return 1;
                        }

                        if (!o1.isMarketOrder() && !o2.isMarketOrder()) {
                            int priceCompare = Double.compare(o2.getPrice(), o1.getPrice());
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
                        // 市價單和價格排序邏輯
                        if (o1.isMarketOrder() && !o2.isMarketOrder()) {
                            return -1;
                        }
                        if (!o1.isMarketOrder() && o2.isMarketOrder()) {
                            return 1;
                        }

                        if (!o1.isMarketOrder() && !o2.isMarketOrder()) {
                            int priceCompare = Double.compare(o1.getPrice(), o2.getPrice());
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

            logger.info(String.format(
                    "訂單篩選與排序完成：買單從 %d 到 %d，賣單從 %d 到 %d",
                    initialBuyOrdersCount, buyOrders.size(),
                    initialSellOrdersCount, sellOrders.size()
            ), "ORDER_PROCESSING");
            LogicAudit.info("ORDER_MATCH", String.format(
                    "books | buys=%d sells=%d", buyOrders.size(), sellOrders.size()));

            // 開始撮合
            boolean transactionOccurred = true;
            int maxRounds = 10;
            int currentRound = 0;
            int totalTransactionVolume = 0;

            while (transactionOccurred && currentRound < maxRounds) {
                transactionOccurred = false;
                currentRound++;

                logger.info(String.format(
                        "開始第 %d 輪撮合，買單數量：%d，賣單數量：%d",
                        currentRound, buyOrders.size(), sellOrders.size()
                ), "ORDER_PROCESSING");
                LogicAudit.info("ORDER_MATCH", String.format("round=%d", currentRound));

                if (buyOrders.isEmpty() || sellOrders.isEmpty()) {
                    logger.info("買單或賣單為空，中止撮合", "ORDER_PROCESSING");
                    break;
                }

                // 標準撮合：以買一與賣一進行配對
                while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
                    Order buyOrder = buyOrders.get(0);
                    Order sellOrder = sellOrders.get(0);

                    // 自我交易檢查
//                    if (buyOrder.getTrader() == sellOrder.getTrader()) {
//                        String msg = String.format(
//                                "自我撮合異常：買單 %s, 賣單 %s",
//                                buyOrder, sellOrder
//                        );
//                        logger.warn(msg, "ORDER_PROCESSING_ANOMALY");
//
//                        writer.write(msg);
//                        writer.newLine();
//
//                        if (matchingMode == MatchingMode.STANDARD) {
//                            i++;
//                            continue;
//                        }
//                    }
                    // 檢查是否可以撮合
                    if (canExecuteOrder(buyOrder, sellOrder)) {
                        int txVolume = executeTransaction(buyOrder, sellOrder, stock, writer);
                        if (txVolume > 0) {
                            transactionOccurred = true;
                            totalTransactionVolume += txVolume;

                            logger.info(String.format(
                                    "成功撮合：買單交易者=%s, 賣單交易者=%s, 成交量=%d",
                                    buyOrder.getTrader().getTraderType(),
                                    sellOrder.getTrader().getTraderType(),
                                    txVolume
                            ), "ORDER_PROCESSING");

                            // 稽核：價格跳躍與量能
                            LogicAudit.checkPriceJump("ORDER_TX", stock.getPreviousPrice(), stock.getPrice(), 0.05);
                            LogicAudit.checkTransaction("ORDER_TX", stock.getPrice(), txVolume, "engine");

                            // 更新 UI
                            if (model != null) {
                                model.updateVolumeChart(txVolume);
                            } else {
                                System.out.println("警告：無法更新 UI，updateVolumeChart 為 null");
                            }
                            notifyListeners();

                            // 成交後重新以買一/賣一繼續判斷
                            continue;
                        } else {
                            // 無實際成交，嘗試退出以避免死循環
                            break;
                        }
                    } else {
                        // 當前買一、賣一無法成交，結束當前輪
                        break;
                    }
                }
            }

            // 撮合結束日誌
            logger.info(String.format(
                    "訂單撮合結束：總輪數 %d，總成交量 %d",
                    currentRound, totalTransactionVolume
            ), "ORDER_PROCESSING");

            // 更新 UI，替換原有的 simulation 調用
            if (model != null) {
                model.updateLabels();
                model.updateOrderBookDisplay();
            } else {
                System.out.println("警告：無法更新 UI，updateLabels、updateOrderBookDisplay 為 null");
            }

        } catch (IOException e) {
            logger.error("無法寫入異常日誌：" + e.getMessage(), "ORDER_PROCESSING");
        } catch (Exception e) {
            logger.error("訂單撮合過程中發生未預期的異常：" + e.getMessage(), "ORDER_PROCESSING");
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
                        recentTrend = model.getMarketAnalyzer().getRecentPriceTrend();
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

    private int executeTransaction(Order buyOrder, Order sellOrder, Stock stock, BufferedWriter writer) throws IOException {
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
        // 根據流動性調整最大可成交量
        int adjustedMax = (int) Math.max(1, Math.floor(theoreticalMax * liquidityFactor));
        // 分批上限：取(按分母切分)與(硬性上限)的較小值
        int perSliceByDiv = Math.max(1, adjustedMax / DIV_FACTOR);
        int maxTransactionVolume = Math.min(MAX_PER_TRANSACTION, perSliceByDiv);
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

        // 🆕 記錄成交前的剩餘量（用於成交記錄）
        int buyOrderRemainingVolume = buyOrder.getVolume() - txVolume;
        int sellOrderRemainingVolume = sellOrder.getVolume() - txVolume;

        // 6. 扣減雙方剩餘量
        buyOrder.setVolume(buyOrder.getVolume() - txVolume);
        sellOrder.setVolume(sellOrder.getVolume() - txVolume);

        // 7. 若剩餘量 0,移除訂單並清理時間戳
        if (buyOrder.getVolume() == 0) {
            buyOrders.remove(buyOrder);
            orderTimestamps.remove(buyOrder);
        }
        if (sellOrder.getVolume() == 0) {
            sellOrders.remove(sellOrder);
            orderTimestamps.remove(sellOrder);
        }

        // 8. 記錄交易到檔案（使用簡單格式）
        // 這裡不要使用 Transaction 類，直接寫入字串
        String transactionRecord = String.format("%s,%s,%.2f,%d,%s",
                buyOrder.getTrader().getTraderType(),
                sellOrder.getTrader().getTraderType(),
                finalPrice,
                txVolume,
                matchingMode.toString()
        );
        writer.write(transactionRecord);
        writer.newLine();

        // 🆕 9. 創建詳細的成交記錄並添加到模型
        if (model != null) {
            // 生成唯一的成交編號
            String transactionId = String.format("TX%d_%04d",
                    System.currentTimeMillis(),
                    (int) (Math.random() * 10000));

            // 創建詳細的成交記錄（使用新的建構函數）
            Transaction detailedTransaction = new Transaction(
                    transactionId,
                    buyOrder,
                    sellOrder,
                    finalPrice,
                    txVolume,
                    System.currentTimeMillis()
            );

            // 設置額外信息
            detailedTransaction.setBuyOrderRemainingVolume(buyOrderRemainingVolume);
            detailedTransaction.setSellOrderRemainingVolume(sellOrderRemainingVolume);
            detailedTransaction.setMatchingMode(matchingMode.toString());

            // 判斷是買方還是賣方主動
            boolean isBuyerInitiated = buyOrder.getTimestamp() > sellOrder.getTimestamp();
            detailedTransaction.setBuyerInitiated(isBuyerInitiated);

            // 添加到模型的成交記錄中
            model.addTransaction(detailedTransaction);
        }

        // 10. 傳遞給 MarketAnalyzer
        model.getMarketAnalyzer().addTransaction(finalPrice, txVolume);

        // 11. 更新買方/賣方的帳戶（統一用 updateAfterTransaction 維護持股/資金）
        if (buyOrder != null && buyOrder.getTrader() != null) {
            buyOrder.getTrader().updateAfterTransaction("buy", txVolume, finalPrice);
        }
        if (sellOrder != null && sellOrder.getTrader() != null) {
            try {
                sellOrder.getTrader().getAccount().consumeFrozenStocks(txVolume);
            } catch (Exception ignore) {}
            sellOrder.getTrader().updateAfterTransaction("sell", txVolume, finalPrice);
        }

        // 12. 印出詳細日誌,包括撮合模式
        System.out.printf("交易完成 [%s模式]:成交量 %d,成交價格 %.2f%n",
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
     * 設置撮合模式
     */
    public void setMatchingMode(MatchingMode mode) {
        if (mode == null) {
            logger.warn("嘗試設置 null 撮合模式，使用默認模式", "ORDER_BOOK");
            this.matchingMode = MatchingMode.PRICE_TIME;
        } else {
            logger.info("設置撮合模式：從 " + this.matchingMode + " 變更為 " + mode, "ORDER_BOOK");
            this.matchingMode = mode;
        }
    }

    /**
     * 獲取當前撮合模式
     */
    public MatchingMode getMatchingMode() {
        return matchingMode;
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
        this.liquidityFactor = factor;
        logger.info("設置流動性因子：" + factor, "ORDER_BOOK");
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

// 在 OrderBook.java 中替換市價單方法
    /**
     * 市價買入方法 - 增強版，集成到現有Transaction系統
     */
    public void marketBuy(Trader trader, int quantity) {
        // 創建市價單交易記錄
        String transactionId = String.format("MKT_%d_%04d",
                System.currentTimeMillis(),
                (int) (Math.random() * 10000));

        double currentPrice = model.getStock().getPrice();
        Transaction transaction = new Transaction(
                transactionId,
                trader.getTraderType(),
                "MARKET_BUY",
                quantity,
                currentPrice, // 預估價格
                currentPrice // 交易前價格
        );

        logger.info(String.format(
                "市價買入開始：交易者=%s, 數量=%d, 可用資金=%.2f, 交易ID=%s",
                trader.getTraderType(), quantity, trader.getAccount().getAvailableFunds(),
                transaction.getId()
        ), "MARKET_BUY");

        double remainingFunds = trader.getAccount().getAvailableFunds();
        int remainingQuantity = quantity;
        String failureReason = null;

        // 價格保護帶參數（避免市價單跨過巨大價差造成異常成交）
        // 允許在參考價上下10%內撮合；可視需要外部化成配置
        final double maxSlippageRatio = 0.10;

        // 參考價：優先使用中間價（有買一與賣一時），否則使用最後成交價
        double referencePrice = model.getStock().getPrice();
        try {
            if (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
                double bestBid = buyOrders.get(0).getPrice();
                double bestAsk = sellOrders.get(0).getPrice();
                // 僅當買一 < 賣一時使用中間價
                if (bestBid > 0 && bestAsk > 0 && bestBid <= bestAsk) {
                    referencePrice = (bestBid + bestAsk) / 2.0;
                }
            }
        } catch (Exception ignore) { }
        double allowedMaxPrice = referencePrice * (1.0 + maxSlippageRatio);

        // 檢查可用資金
        if (remainingFunds <= 0) {
            failureReason = "資金不足";
            transaction.completeMarketOrderTransaction(currentPrice, 0, failureReason);

            // 僅在有實際成交時才加入交易記錄（避免UI誤判為成交）
            if (transaction.getActualVolume() > 0) {
                model.addTransaction(transaction);
            }

            logger.warn(String.format(
                    "市價買入失敗：交易者=%s, 原因=%s, 交易ID=%s",
                    trader.getTraderType(), failureReason, transaction.getId()
            ), "MARKET_BUY");
            return;
        }

        try {
            ListIterator<Order> it = sellOrders.listIterator();
            int depthLevel = 1; // 訂單簿深度層級

            while (it.hasNext() && remainingQuantity > 0 && remainingFunds > 0) {
                Order sellOrder = it.next();
                double sellPx = sellOrder.getPrice();
                int availableVolume = sellOrder.getVolume();
                int chunk = Math.min(availableVolume, remainingQuantity);
                double cost = chunk * sellPx;

                // 價格保護：市價買不吃超過參考價+保護帶的掛單
                if (sellPx > allowedMaxPrice) {
                    failureReason = String.format(
                            "價格超出市價單保護帶：賣價=%.2f，高於允許上限=%.2f（ref=%.2f, +%.0f%%）",
                            sellPx, allowedMaxPrice, referencePrice, maxSlippageRatio * 100);
                    logger.warn(failureReason + ", 停止後續撮合，保護剩餘市價買單不被高價吃掉", "MARKET_BUY");
                    break; // 之後價位只會更高，直接停止
                }

                // 自成交檢查
                if (sellOrder.getTrader() == trader) {
                    logger.debug(String.format(
                            "市價買入跳過自成交：交易者=%s, 賣單價格=%.2f, 數量=%d, 深度=%d",
                            trader.getTraderType(), sellPx, chunk, depthLevel
                    ), "MARKET_BUY");
                    depthLevel++;
                    continue;
                }

                // 資金檢查
                if (remainingFunds < cost) {
                    // 部分資金不足，計算能買多少
                    int affordableQuantity = (int) Math.floor(remainingFunds / sellPx);
                    if (affordableQuantity > 0) {
                        chunk = affordableQuantity;
                        cost = chunk * sellPx;
                    } else {
                        failureReason = remainingQuantity == quantity
                                ? "資金完全不足" : "剩餘資金不足";
                        break;
                    }
                }

                // 執行交易
                try {
                    // 更新買方帳戶與成本（由交易者實作處理平均成本與資金變化）
                    trader.updateAverageCostPrice("buy", chunk, sellPx);
                    remainingFunds -= cost;
                    remainingQuantity -= chunk;

                    // 先消耗賣方凍結庫存，再更新賣方帳戶
                    try {
                        sellOrder.getTrader().getAccount().consumeFrozenStocks(chunk);
                    } catch (Exception ignore) {}
                    // 更新賣方帳戶
                    sellOrder.getTrader().updateAfterTransaction("sell", chunk, sellPx);

                    // 記錄填單到Transaction
                    transaction.addFillRecord(sellPx, chunk,
                            sellOrder.getTrader().getTraderType(), depthLevel);

                    logger.info(String.format(
                            "市價買入成交：買入 %d 股@%.2f，成本=%.2f，深度=%d，對手=%s，交易ID=%s",
                            chunk, sellPx, cost, depthLevel,
                            sellOrder.getTrader().getTraderType(),
                            transaction.getId()
                    ), "MARKET_BUY");

                    // 更新賣單數量
                    sellOrder.setVolume(sellOrder.getVolume() - chunk);
                    if (sellOrder.getVolume() <= 0) {
                        it.remove();
                        orderTimestamps.remove(sellOrder);
                        logger.debug("賣單已全部成交，從列表中移除", "MARKET_BUY");
                    }

                } catch (Exception e) {
                    logger.error(String.format(
                            "市價買入執行交易異常：交易者=%s, 錯誤=%s, 交易ID=%s",
                            trader.getTraderType(), e.getMessage(), transaction.getId()
                    ), "MARKET_BUY");
                    failureReason = "交易執行異常: " + e.getMessage();
                    break;
                }

                depthLevel++;
            }

            // 完成交易記錄
            double postTradePrice = model.getStock().getPrice();
            transaction.completeMarketOrderTransaction(postTradePrice, depthLevel - 1, failureReason);

            // 設置兼容性屬性（用於現有的UI顯示）
            if (transaction.getActualVolume() > 0) {
                // 創建虛擬的買賣訂單用於兼容現有系統
                Order virtualBuyOrder = new Order("buy", transaction.getAveragePrice(),
                        transaction.getActualVolume(), trader, false, true, false);
                transaction.setBuyOrder(virtualBuyOrder);

                // 設置買方發起
                transaction.setBuyerInitiated(true);
            }

            // 更新市場分析器和UI
            if (transaction.getActualVolume() > 0) {
                model.getMarketAnalyzer().addTransaction(
                        transaction.getAveragePrice(),
                        transaction.getActualVolume()
                );
                model.getMarketAnalyzer().addPrice(transaction.getAveragePrice());

                // 更新股價為最後成交價
                if (!transaction.getFillRecords().isEmpty()) {
                    List<Transaction.FillRecord> fills = transaction.getFillRecords();
                    double lastPrice = fills.get(fills.size() - 1).getPrice();
                    model.getStock().setPrice(lastPrice);
                }

                // 通知UI更新
                if (model != null) {
                    model.updateVolumeChart(transaction.getActualVolume());
                    model.updateLabels();
                    model.updateOrderBookDisplay();
                }

                logger.info(String.format(
                        "市價買入完成：交易ID=%s, 成交=%d/%d股, 均價=%.2f, 滑價=%.2f%%, 執行時間=%dms",
                        transaction.getId(),
                        transaction.getActualVolume(),
                        transaction.getRequestedVolume(),
                        transaction.getAveragePrice(),
                        transaction.getSlippagePercentage(),
                        transaction.getExecutionTimeMs()
                ), "MARKET_BUY");
            } else {
                logger.warn(String.format(
                        "市價買入無成交：交易ID=%s, 原因=%s",
                        transaction.getId(),
                        failureReason != null ? failureReason : "無可用賣單"
                ), "MARKET_BUY");
            }

            // 僅在有實際成交時才加入交易記錄
            if (transaction.getActualVolume() > 0) {
                model.addTransaction(transaction);
            }
            notifyListeners();

        } catch (Exception e) {
            logger.error(String.format(
                    "市價買入整體異常：交易者=%s, 交易ID=%s, 錯誤=%s",
                    trader.getTraderType(), transaction.getId(), e.getMessage()
            ), "MARKET_BUY");

            transaction.completeMarketOrderTransaction(currentPrice, 0, "系統異常: " + e.getMessage());
        }
    }

    /**
     * 市價賣出方法 - 增強版，集成到現有Transaction系統
     */
    public void marketSell(Trader trader, int quantity) {
        // 創建市價單交易記錄
        String transactionId = String.format("MKT_%d_%04d",
                System.currentTimeMillis(),
                (int) (Math.random() * 10000));

        double currentPrice = model.getStock().getPrice();
        Transaction transaction = new Transaction(
                transactionId,
                trader.getTraderType(),
                "MARKET_SELL",
                quantity,
                currentPrice, // 預估價格
                currentPrice // 交易前價格
        );

        logger.info(String.format(
                "市價賣出開始：交易者=%s, 數量=%d, 可用持股=%d, 交易ID=%s",
                trader.getTraderType(), quantity, trader.getAccount().getStockInventory(),
                transaction.getId()
        ), "MARKET_SELL");

        int remainingQty = quantity;
        String failureReason = null;

        // 價格保護帶參數
        final double maxSlippageRatio = 0.10;
        double referencePrice = model.getStock().getPrice();
        try {
            if (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
                double bestBid = buyOrders.get(0).getPrice();
                double bestAsk = sellOrders.get(0).getPrice();
                if (bestBid > 0 && bestAsk > 0 && bestBid <= bestAsk) {
                    referencePrice = (bestBid + bestAsk) / 2.0;
                }
            }
        } catch (Exception ignore) { }
        double allowedMinPrice = referencePrice * (1.0 - maxSlippageRatio);

        // 檢查持股
        if (trader.getAccount().getStockInventory() < quantity) {
            failureReason = String.format("持股不足，當前持股=%d, 賣出需求=%d",
                    trader.getAccount().getStockInventory(), quantity);
            transaction.completeMarketOrderTransaction(currentPrice, 0, failureReason);

            // 僅在有實際成交時才加入交易記錄
            if (transaction.getActualVolume() > 0) {
                model.addTransaction(transaction);
            }

            logger.warn(String.format(
                    "市價賣出失敗：交易者=%s, 原因=%s, 交易ID=%s",
                    trader.getTraderType(), failureReason, transaction.getId()
            ), "MARKET_SELL");
            return;
        }

        try {
            ListIterator<Order> it = buyOrders.listIterator();
            int depthLevel = 1; // 訂單簿深度層級

            while (it.hasNext() && remainingQty > 0) {
                Order buyOrder = it.next();
                double buyPx = buyOrder.getPrice();
                int availableVolume = buyOrder.getVolume();
                int chunk = Math.min(availableVolume, remainingQty);

                // 價格保護：市價賣不賣到低於參考價-保護帶的掛單
                if (buyPx < allowedMinPrice) {
                    failureReason = String.format(
                            "價格超出市價單保護帶：買價=%.2f，低於允許下限=%.2f（ref=%.2f, -%.0f%%）",
                            buyPx, allowedMinPrice, referencePrice, maxSlippageRatio * 100);
                    logger.warn(failureReason + ", 停止後續撮合，保護剩餘市價賣單不被低價賣出", "MARKET_SELL");
                    break; // 之後價位只會更低，直接停止
                }

                // 自成交檢查
                if (buyOrder.getTrader() == trader) {
                    logger.debug(String.format(
                            "市價賣出跳過自成交：交易者=%s, 買單價格=%.2f, 數量=%d, 深度=%d",
                            trader.getTraderType(), buyPx, chunk, depthLevel
                    ), "MARKET_SELL");
                    depthLevel++;
                    continue;
                }

                // 檢查賣方持股（動態檢查）
                if (trader.getAccount().getStockInventory() < chunk) {
                    int actualAvailable = trader.getAccount().getStockInventory();
                    if (actualAvailable > 0) {
                        chunk = actualAvailable;
                    } else {
                        failureReason = "持股已用盡";
                        break;
                    }
                }

                // 執行交易
                try {
                    double revenue = buyPx * chunk;

                    // 更新賣方帳戶與賣出處理（由交易者實作）
                    trader.updateAverageCostPrice("sell", chunk, buyPx);
                    remainingQty -= chunk;

                    // 更新買方帳戶
                    buyOrder.getTrader().updateAfterTransaction("buy", chunk, buyPx);

                    // 記錄填單到Transaction
                    transaction.addFillRecord(buyPx, chunk,
                            buyOrder.getTrader().getTraderType(), depthLevel);

                    logger.info(String.format(
                            "市價賣出成交：賣出 %d 股@%.2f，收入=%.2f，深度=%d，對手=%s，交易ID=%s",
                            chunk, buyPx, revenue, depthLevel,
                            buyOrder.getTrader().getTraderType(),
                            transaction.getId()
                    ), "MARKET_SELL");

                    // 更新買單數量
                    buyOrder.setVolume(buyOrder.getVolume() - chunk);
                    if (buyOrder.getVolume() <= 0) {
                        it.remove();
                        orderTimestamps.remove(buyOrder);
                        logger.debug("買單已全部成交，從列表中移除", "MARKET_SELL");
                    }

                } catch (Exception e) {
                    logger.error(String.format(
                            "市價賣出執行交易異常：交易者=%s, 錯誤=%s, 交易ID=%s",
                            trader.getTraderType(), e.getMessage(), transaction.getId()
                    ), "MARKET_SELL");
                    failureReason = "交易執行異常: " + e.getMessage();
                    break;
                }

                depthLevel++;
            }

            // 完成交易記錄
            double postTradePrice = model.getStock().getPrice();
            transaction.completeMarketOrderTransaction(postTradePrice, depthLevel - 1, failureReason);

            // 設置兼容性屬性（用於現有的UI顯示）
            if (transaction.getActualVolume() > 0) {
                // 創建虛擬的買賣訂單用於兼容現有系統
                Order virtualSellOrder = new Order("sell", transaction.getAveragePrice(),
                        transaction.getActualVolume(), trader, false, true, false);
                transaction.setSellOrder(virtualSellOrder);

                // 設置賣方發起
                transaction.setBuyerInitiated(false);
            }

            // 更新市場分析器和UI
            if (transaction.getActualVolume() > 0) {
                model.getMarketAnalyzer().addTransaction(
                        transaction.getAveragePrice(),
                        transaction.getActualVolume()
                );
                model.getMarketAnalyzer().addPrice(transaction.getAveragePrice());

                // 更新股價為最後成交價
                if (!transaction.getFillRecords().isEmpty()) {
                    List<Transaction.FillRecord> fills = transaction.getFillRecords();
                    double lastPrice = fills.get(fills.size() - 1).getPrice();
                    model.getStock().setPrice(lastPrice);
                }

                // 通知UI更新
                if (model != null) {
                    model.updateVolumeChart(transaction.getActualVolume());
                    model.updateLabels();
                    model.updateOrderBookDisplay();
                }

                logger.info(String.format(
                        "市價賣出完成：交易ID=%s, 成交=%d/%d股, 均價=%.2f, 滑價=%.2f%%, 執行時間=%dms",
                        transaction.getId(),
                        transaction.getActualVolume(),
                        transaction.getRequestedVolume(),
                        transaction.getAveragePrice(),
                        transaction.getSlippagePercentage(),
                        transaction.getExecutionTimeMs()
                ), "MARKET_SELL");
            } else {
                logger.warn(String.format(
                        "市價賣出無成交：交易ID=%s, 原因=%s",
                        transaction.getId(),
                        failureReason != null ? failureReason : "無可用買單"
                ), "MARKET_SELL");
            }

            // 僅在有實際成交時才加入交易記錄
            if (transaction.getActualVolume() > 0) {
                model.addTransaction(transaction);
            }
            notifyListeners();

        } catch (Exception e) {
            logger.error(String.format(
                    "市價賣出整體異常：交易者=%s, 交易ID=%s, 錯誤=%s",
                    trader.getTraderType(), transaction.getId(), e.getMessage()
            ), "MARKET_SELL");

            transaction.completeMarketOrderTransaction(currentPrice, 0, "系統異常: " + e.getMessage());
            if (transaction.getActualVolume() > 0) {
                model.addTransaction(transaction);
            }
        }
    }

    // ============== 取消訂單 / 其他功能 ==============
    /**
     * 取消訂單
     */
    public boolean cancelOrder(String orderId) {
        Order canceled = null;
        boolean success = false;

        try {
            // 檢查買單
            canceled = buyOrders.stream()
                    .filter(o -> o.getId().equals(orderId))
                    .findFirst().orElse(null);

            if (canceled != null) {
                buyOrders.remove(canceled);
                double refund = canceled.getPrice() * canceled.getVolume();
                canceled.getTrader().getAccount().incrementFunds(refund);
                success = true;

                logger.info(String.format(
                        "取消買單：訂單ID=%s, 交易者=%s, 退還資金=%.2f",
                        orderId, canceled.getTrader().getTraderType(), refund
                ), "ORDER_CANCEL");

                if (canceled.getTrader() instanceof PersonalAI) {
                    ((PersonalAI) canceled.getTrader()).onOrderCancelled(canceled);
                }

            } else {
                // 檢查賣單
                canceled = sellOrders.stream()
                        .filter(o -> o.getId().equals(orderId))
                        .findFirst().orElse(null);

                if (canceled != null) {
                    sellOrders.remove(canceled);
                    // 撤單應解凍凍結庫存回可用
                    try {
                        canceled.getTrader().getAccount().unfreezeStocks(canceled.getVolume());
                    } catch (Exception ex) {
                        canceled.getTrader().getAccount().incrementStocks(canceled.getVolume());
                    }
                    success = true;

                    logger.info(String.format(
                            "取消賣單：訂單ID=%s, 交易者=%s, 退還股票數量=%d",
                            orderId, canceled.getTrader().getTraderType(), canceled.getVolume()
                    ), "ORDER_CANCEL");

                    if (canceled.getTrader() instanceof PersonalAI) {
                        ((PersonalAI) canceled.getTrader()).onOrderCancelled(canceled);
                    }
                } else {
                    logger.warn(String.format(
                            "取消訂單失敗：找不到訂單ID=%s",
                            orderId
                    ), "ORDER_CANCEL");
                }
            }

            SwingUtilities.invokeLater(() -> model.updateOrderBookDisplay());
            notifyListeners();

        } catch (Exception e) {
            logger.error(String.format(
                    "取消訂單異常：訂單ID=%s, 錯誤=%s",
                    orderId, e.getMessage()
            ), "ORDER_CANCEL");
        }

        return success;
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
