package com.stockgame.server.engine;

import com.stockgame.server.dto.OrderBookDto;
import com.stockgame.server.entity.StockOrder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 線程安全的記憶體訂單簿。
 *
 * 買方：ConcurrentSkipListMap（降序，買一在最前）
 * 賣方：ConcurrentSkipListMap（升序，賣一在最前）
 *
 * 移植自桌面版 OrderBook.java；移除 Swing UI 回呼，改為回傳事件供 MatchingEngine 處理。
 */
@Slf4j
public class ServerOrderBook {

    // 買方：價格降序（最高買價優先）
    private final ConcurrentSkipListMap<Double, List<StockOrder>> buyLevels =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    // 賣方：價格升序（最低賣價優先）
    private final ConcurrentSkipListMap<Double, List<StockOrder>> sellLevels =
            new ConcurrentSkipListMap<>();

    // 今日統計
    private volatile double lastPrice    = 0.0;
    private volatile double openPrice    = 0.0;
    private volatile double highPrice    = 0.0;
    private volatile double lowPrice     = Double.MAX_VALUE;
    private final AtomicInteger totalVolume  = new AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicLong buyVolume  = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong sellVolume = new java.util.concurrent.atomic.AtomicLong(0);
    private volatile double totalAmount  = 0.0;

    // 台股漲跌停幅度（10%）
    private static final double LIMIT_RATIO = 0.10;

    // ── 委託送入 ─────────────────────────────────────────────────────────────

    public synchronized void addBuyOrder(StockOrder order) {
        buyLevels.computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(order);
    }

    public synchronized void addSellOrder(StockOrder order) {
        sellLevels.computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(order);
    }

    public synchronized void removeOrder(StockOrder order) {
        if (order.getSide() == StockOrder.Side.BUY) {
            removeFromLevel(buyLevels, order.getPrice(), order);
        } else {
            removeFromLevel(sellLevels, order.getPrice(), order);
        }
    }

    private void removeFromLevel(Map<Double, List<StockOrder>> levels, double price, StockOrder order) {
        List<StockOrder> level = levels.get(price);
        if (level != null) {
            level.remove(order);
            if (level.isEmpty()) levels.remove(price);
        }
    }

    // ── 撮合觸發：找到可成交的買賣對 ──────────────────────────────────────

    /**
     * 嘗試撮合一輪。回傳所有這輪產生的成交事件。
     */
    public synchronized List<MatchEvent> matchOrders() {
        List<MatchEvent> events = new ArrayList<>();

        while (!buyLevels.isEmpty() && !sellLevels.isEmpty()) {
            Map.Entry<Double, List<StockOrder>> bestBidEntry = buyLevels.firstEntry();
            Map.Entry<Double, List<StockOrder>> bestAskEntry = sellLevels.firstEntry();

            double bestBid = bestBidEntry.getKey();
            double bestAsk = bestAskEntry.getKey();

            // 台股撮合規則：買一 >= 賣一 才能成交
            if (bestBid < bestAsk) break;

            StockOrder buyOrder  = bestBidEntry.getValue().get(0);
            StockOrder sellOrder = bestAskEntry.getValue().get(0);

            // 成交價：以先掛單者為主（時間優先）；簡化為以賣價為成交價
            double matchPrice = sellOrder.isMarketOrder() ? bestBid :
                                buyOrder.isMarketOrder()  ? bestAsk : bestAsk;

            int qty = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());
            if (qty <= 0) break;

            // 更新委託剩餘量
            buyOrder.setFilledQuantity(buyOrder.getFilledQuantity() + qty);
            sellOrder.setFilledQuantity(sellOrder.getFilledQuantity() + qty);

            boolean buyFilled  = buyOrder.isFullyFilled();
            boolean sellFilled = sellOrder.isFullyFilled();

            if (buyFilled) {
                buyOrder.setStatus(StockOrder.Status.FILLED);
                removeFromLevel(buyLevels, buyOrder.getPrice(), buyOrder);
            } else {
                buyOrder.setStatus(StockOrder.Status.PARTIAL);
            }

            if (sellFilled) {
                sellOrder.setStatus(StockOrder.Status.FILLED);
                removeFromLevel(sellLevels, sellOrder.getPrice(), sellOrder);
            } else {
                sellOrder.setStatus(StockOrder.Status.PARTIAL);
            }

            // 判斷主動方（市價單為主動，限價對沖時買方為主動）
            boolean buyerInitiated = buyOrder.isMarketOrder() || !sellOrder.isMarketOrder();

            // 更新行情統計
            updateStats(matchPrice, qty, buyerInitiated);

            events.add(new MatchEvent(buyOrder, sellOrder, matchPrice, qty, buyerInitiated));
            log.debug("撮合成交：{} 股 @ {}", qty, matchPrice);
        }

        return events;
    }

    // ── 行情查詢 ─────────────────────────────────────────────────────────────

    public synchronized Optional<StockOrder> getBestBid() {
        return buyLevels.isEmpty() ? Optional.empty() :
               Optional.of(buyLevels.firstEntry().getValue().get(0));
    }

    public synchronized Optional<StockOrder> getBestAsk() {
        return sellLevels.isEmpty() ? Optional.empty() :
               Optional.of(sellLevels.firstEntry().getValue().get(0));
    }

    public double getBestBidPrice() {
        return buyLevels.isEmpty() ? 0.0 : buyLevels.firstKey();
    }

    public double getBestAskPrice() {
        return sellLevels.isEmpty() ? 0.0 : sellLevels.firstKey();
    }

    /**
     * 轉換為 DTO（前 10 檔）
     */
    public synchronized OrderBookDto toDto() {
        List<OrderBookDto.Level> bids = buyLevels.entrySet().stream()
                .limit(10)
                .map(e -> new OrderBookDto.Level(
                        e.getKey(),
                        e.getValue().stream().mapToInt(StockOrder::getRemainingQuantity).sum(),
                        e.getValue().size()))
                .collect(Collectors.toList());

        List<OrderBookDto.Level> asks = sellLevels.entrySet().stream()
                .limit(10)
                .map(e -> new OrderBookDto.Level(
                        e.getKey(),
                        e.getValue().stream().mapToInt(StockOrder::getRemainingQuantity).sum(),
                        e.getValue().size()))
                .collect(Collectors.toList());

        return new OrderBookDto(bids, asks);
    }

    // ── 行情統計 ─────────────────────────────────────────────────────────────

    private void updateStats(double price, int qty, boolean buyerInitiated) {
        if (openPrice == 0.0) openPrice = price;
        lastPrice  = price;
        highPrice  = Math.max(highPrice, price);
        lowPrice   = Math.min(lowPrice, price);
        totalVolume.addAndGet(qty);
        totalAmount += price * qty;
        if (buyerInitiated) buyVolume.addAndGet(qty);
        else                sellVolume.addAndGet(qty);
    }

    public void setLastPrice(double price) {
        if (openPrice == 0.0) openPrice = price;
        lastPrice = price;
        highPrice = Math.max(highPrice, price);
        lowPrice  = Math.min(lowPrice == Double.MAX_VALUE ? price : lowPrice, price);
    }

    public double getLastPrice()   { return lastPrice; }
    public double getOpenPrice()   { return openPrice; }
    public double getHighPrice()   { return highPrice; }
    public double getLowPrice()    { return lowPrice == Double.MAX_VALUE ? 0.0 : lowPrice; }
    public int    getTotalVolume() { return totalVolume.get(); }
    public double getTotalAmount() { return totalAmount; }
    public long   getBuyVolume()   { return buyVolume.get(); }
    public long   getSellVolume()  { return sellVolume.get(); }

    /** 計算漲跌停價 */
    public double getUpperLimit(double prevClose) { return Math.round(prevClose * (1 + LIMIT_RATIO) * 10.0) / 10.0; }
    public double getLowerLimit(double prevClose) { return Math.round(prevClose * (1 - LIMIT_RATIO) * 10.0) / 10.0; }

    /** 調整到 tick（0.01 元為最小單位） */
    public double adjustToTick(double price) { return Math.round(price * 100.0) / 100.0; }

    // ── 成交事件 ─────────────────────────────────────────────────────────────

    /** 撮合引擎回傳的單筆成交事件 */
    public record MatchEvent(
            StockOrder buyOrder,
            StockOrder sellOrder,
            double     price,
            int        quantity,
            boolean    buyerInitiated
    ) {}
}
