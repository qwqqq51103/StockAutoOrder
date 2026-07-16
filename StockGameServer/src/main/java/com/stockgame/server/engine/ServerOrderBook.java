package com.stockgame.server.engine;

import com.stockgame.server.dto.OrderBookDto;
import com.stockgame.server.entity.StockOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServerOrderBook {

    private final ConcurrentSkipListMap<Double, List<StockOrder>> buyLevels =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private final ConcurrentSkipListMap<Double, List<StockOrder>> sellLevels =
            new ConcurrentSkipListMap<>();

    private volatile double lastPrice = 0.0;
    private volatile double openPrice = 0.0;
    private volatile double highPrice = 0.0;
    private volatile double lowPrice = Double.MAX_VALUE;
    private final AtomicInteger totalVolume = new AtomicInteger(0);
    private final AtomicLong buyVolume = new AtomicLong(0);
    private final AtomicLong sellVolume = new AtomicLong(0);
    private volatile double totalAmount = 0.0;

    private static final double LIMIT_RATIO = 0.10;

    public synchronized void addBuyOrder(StockOrder order) {
        buyLevels.computeIfAbsent(priceKey(order), k -> new ArrayList<>()).add(order);
    }

    public synchronized void addSellOrder(StockOrder order) {
        sellLevels.computeIfAbsent(priceKey(order), k -> new ArrayList<>()).add(order);
    }

    public synchronized void removeOrder(StockOrder order) {
        if (order.getSide() == StockOrder.Side.BUY) {
            removeFromLevel(buyLevels, priceKey(order), order);
        } else {
            removeFromLevel(sellLevels, priceKey(order), order);
        }
    }

    public synchronized OptionalDouble findMarketBuyLimitPrice(int quantity) {
        int remaining = quantity;
        double highestPrice = 0.0;

        for (Map.Entry<Double, List<StockOrder>> entry : sellLevels.entrySet()) {
            for (StockOrder order : entry.getValue()) {
                if (!order.isActive()) {
                    continue;
                }
                double askPrice = effectiveAskPrice(order, entry.getKey());
                if (askPrice <= 0) {
                    continue;
                }
                highestPrice = Math.max(highestPrice, askPrice);
                remaining -= order.getRemainingQuantity();
                if (remaining <= 0) {
                    return OptionalDouble.of(highestPrice);
                }
            }
        }

        return OptionalDouble.empty();
    }

    public synchronized boolean canFullyFillLimitBuy(double limitPrice, int quantity) {
        int remaining = quantity;

        for (Map.Entry<Double, List<StockOrder>> entry : sellLevels.entrySet()) {
            for (StockOrder order : entry.getValue()) {
                if (!order.isActive()) {
                    continue;
                }
                double askPrice = effectiveAskPrice(order, entry.getKey());
                if (askPrice > limitPrice) {
                    return false;
                }
                remaining -= order.getRemainingQuantity();
                if (remaining <= 0) {
                    return true;
                }
            }
        }

        return false;
    }

    public synchronized boolean canFullyFillLimitSell(double limitPrice, int quantity) {
        int remaining = quantity;

        for (Map.Entry<Double, List<StockOrder>> entry : buyLevels.entrySet()) {
            for (StockOrder order : entry.getValue()) {
                if (!order.isActive()) {
                    continue;
                }
                double bidPrice = effectiveBidPrice(order, entry.getKey());
                if (bidPrice < limitPrice) {
                    return false;
                }
                remaining -= order.getRemainingQuantity();
                if (remaining <= 0) {
                    return true;
                }
            }
        }

        return false;
    }

    public synchronized MatchResult matchOrders() {
        List<MatchEvent> events = new ArrayList<>();
        List<StockOrder> expiredOrders = new ArrayList<>();

        while (!buyLevels.isEmpty() && !sellLevels.isEmpty()) {
            Map.Entry<Double, List<StockOrder>> bestBidEntry = buyLevels.firstEntry();
            Map.Entry<Double, List<StockOrder>> bestAskEntry = sellLevels.firstEntry();
            StockOrder buyOrder = firstActive(bestBidEntry.getValue());
            StockOrder sellOrder = firstActive(bestAskEntry.getValue());

            if (buyOrder == null) {
                buyLevels.remove(bestBidEntry.getKey());
                continue;
            }
            if (sellOrder == null) {
                sellLevels.remove(bestAskEntry.getKey());
                continue;
            }

            if (!canCross(buyOrder, sellOrder, bestBidEntry.getKey(), bestAskEntry.getKey())) {
                if (buyOrder.isMarketOrder()) {
                    expireOrder(buyLevels, bestBidEntry.getKey(), buyOrder, expiredOrders);
                    continue;
                }
                if (sellOrder.isMarketOrder()) {
                    expireOrder(sellLevels, bestAskEntry.getKey(), sellOrder, expiredOrders);
                    continue;
                }
                break;
            }

            int qty = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());
            if (qty <= 0) {
                break;
            }

            double matchPrice = resolveMatchPrice(buyOrder, sellOrder, bestBidEntry.getKey(), bestAskEntry.getKey());
            boolean buyerInitiated = resolveBuyerInitiated(buyOrder, sellOrder);
            buyOrder.setFilledQuantity(buyOrder.getFilledQuantity() + qty);
            sellOrder.setFilledQuantity(sellOrder.getFilledQuantity() + qty);

            if (buyOrder.isFullyFilled()) {
                buyOrder.setStatus(StockOrder.Status.FILLED);
                removeFromLevel(buyLevels, bestBidEntry.getKey(), buyOrder);
            } else {
                buyOrder.setStatus(StockOrder.Status.PARTIAL);
            }

            if (sellOrder.isFullyFilled()) {
                sellOrder.setStatus(StockOrder.Status.FILLED);
                removeFromLevel(sellLevels, bestAskEntry.getKey(), sellOrder);
            } else {
                sellOrder.setStatus(StockOrder.Status.PARTIAL);
            }

            updateStats(matchPrice, qty, buyerInitiated);
            events.add(new MatchEvent(buyOrder, sellOrder, matchPrice, qty, buyerInitiated));
            log.debug("Matched {} @ {}", qty, matchPrice);
        }

        expireRestingMarketOrders(buyLevels, expiredOrders);
        expireRestingMarketOrders(sellLevels, expiredOrders);
        return new MatchResult(events, expiredOrders);
    }

    public synchronized Optional<StockOrder> getBestBid() {
        return buyLevels.isEmpty() ? Optional.empty() : Optional.ofNullable(firstActive(buyLevels.firstEntry().getValue()));
    }

    public synchronized Optional<StockOrder> getBestAsk() {
        return sellLevels.isEmpty() ? Optional.empty() : Optional.ofNullable(firstActive(sellLevels.firstEntry().getValue()));
    }

    public synchronized double getBestBidPrice() {
        if (buyLevels.isEmpty()) {
            return 0.0;
        }
        Map.Entry<Double, List<StockOrder>> entry = buyLevels.firstEntry();
        StockOrder order = firstActive(entry.getValue());
        return order == null ? 0.0 : displayPrice(order, entry.getKey());
    }

    public synchronized double getBestAskPrice() {
        if (sellLevels.isEmpty()) {
            return 0.0;
        }
        Map.Entry<Double, List<StockOrder>> entry = sellLevels.firstEntry();
        StockOrder order = firstActive(entry.getValue());
        return order == null ? 0.0 : displayPrice(order, entry.getKey());
    }

    public synchronized OrderBookDto toDto() {
        List<OrderBookDto.Level> bids = buyLevels.entrySet().stream()
                .limit(10)
                .map(e -> new OrderBookDto.Level(
                        displayPrice(firstActive(e.getValue()), e.getKey()),
                        e.getValue().stream().filter(StockOrder::isActive).mapToInt(StockOrder::getRemainingQuantity).sum(),
                        (int) e.getValue().stream().filter(StockOrder::isActive).count()))
                .filter(level -> level.getOrderCount() > 0)
                .collect(Collectors.toList());

        List<OrderBookDto.Level> asks = sellLevels.entrySet().stream()
                .limit(10)
                .map(e -> new OrderBookDto.Level(
                        displayPrice(firstActive(e.getValue()), e.getKey()),
                        e.getValue().stream().filter(StockOrder::isActive).mapToInt(StockOrder::getRemainingQuantity).sum(),
                        (int) e.getValue().stream().filter(StockOrder::isActive).count()))
                .filter(level -> level.getOrderCount() > 0)
                .collect(Collectors.toList());

        return new OrderBookDto(bids, asks);
    }

    private boolean canCross(StockOrder buyOrder, StockOrder sellOrder, double bidLevelPrice, double askLevelPrice) {
        double bidPrice = effectiveBidPrice(buyOrder, bidLevelPrice);
        double askPrice = effectiveAskPrice(sellOrder, askLevelPrice);

        if (buyOrder.isMarketOrder() && buyOrder.getPrice() > 0 && askPrice > buyOrder.getPrice()) {
            return false;
        }
        if (sellOrder.isMarketOrder() && sellOrder.getPrice() > 0 && bidPrice < sellOrder.getPrice()) {
            return false;
        }

        return buyOrder.isMarketOrder() || sellOrder.isMarketOrder() || bidPrice >= askPrice;
    }

    private double resolveMatchPrice(StockOrder buyOrder, StockOrder sellOrder, double bidLevelPrice, double askLevelPrice) {
        if (buyOrder.isMarketOrder() && sellOrder.isMarketOrder()) {
            if (lastPrice > 0) {
                return lastPrice;
            }
            double buyFallback = buyOrder.getPrice() > 0 ? buyOrder.getPrice() : 0.0;
            double sellFallback = sellOrder.getPrice() > 0 ? sellOrder.getPrice() : 0.0;
            return Math.max(buyFallback, sellFallback);
        }
        if (buyOrder.isMarketOrder()) {
            return effectiveAskPrice(sellOrder, askLevelPrice);
        }
        if (sellOrder.isMarketOrder()) {
            return effectiveBidPrice(buyOrder, bidLevelPrice);
        }
        return sellOrder.getPrice();
    }

    private boolean resolveBuyerInitiated(StockOrder buyOrder, StockOrder sellOrder) {
        if (buyOrder.isMarketOrder()) {
            return true;
        }
        if (sellOrder.isMarketOrder()) {
            return false;
        }
        if (buyOrder.getCreatedAt() == null || sellOrder.getCreatedAt() == null) {
            return true;
        }
        return buyOrder.getCreatedAt().isAfter(sellOrder.getCreatedAt());
    }

    private double priceKey(StockOrder order) {
        if (!order.isMarketOrder()) {
            return order.getPrice();
        }
        return order.getSide() == StockOrder.Side.BUY ? Double.MAX_VALUE : 0.0;
    }

    private double effectiveBidPrice(StockOrder order, double levelPrice) {
        if (!order.isMarketOrder()) {
            return levelPrice;
        }
        if (order.getPrice() > 0) {
            return order.getPrice();
        }
        return Double.MAX_VALUE;
    }

    private double effectiveAskPrice(StockOrder order, double levelPrice) {
        if (!order.isMarketOrder()) {
            return levelPrice;
        }
        if (order.getPrice() > 0) {
            return order.getPrice();
        }
        return lastPrice > 0 ? lastPrice : 0.0;
    }

    private double displayPrice(StockOrder order, double levelPrice) {
        if (order == null) {
            return levelPrice == Double.MAX_VALUE ? 0.0 : levelPrice;
        }
        if (!order.isMarketOrder()) {
            return levelPrice;
        }
        return order.getPrice() > 0 ? order.getPrice() : 0.0;
    }

    private StockOrder firstActive(List<StockOrder> orders) {
        return orders.stream()
                .filter(StockOrder::isActive)
                .findFirst()
                .orElse(null);
    }

    private void removeFromLevel(Map<Double, List<StockOrder>> levels, double price, StockOrder order) {
        List<StockOrder> level = levels.get(price);
        if (level == null) {
            return;
        }
        level.removeIf(existing -> sameOrder(existing, order));
        if (level.isEmpty()) {
            levels.remove(price);
        }
    }

    private boolean sameOrder(StockOrder left, StockOrder right) {
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private void expireOrder(
            Map<Double, List<StockOrder>> levels,
            double price,
            StockOrder order,
            List<StockOrder> expiredOrders
    ) {
        if (order.isActive()) {
            order.setStatus(StockOrder.Status.CANCELLED);
            expiredOrders.add(order);
        }
        removeFromLevel(levels, price, order);
    }

    private void expireRestingMarketOrders(
            ConcurrentSkipListMap<Double, List<StockOrder>> levels,
            List<StockOrder> expiredOrders
    ) {
        Iterator<Map.Entry<Double, List<StockOrder>>> levelIterator = levels.entrySet().iterator();
        while (levelIterator.hasNext()) {
            Map.Entry<Double, List<StockOrder>> entry = levelIterator.next();
            Iterator<StockOrder> orderIterator = entry.getValue().iterator();
            while (orderIterator.hasNext()) {
                StockOrder order = orderIterator.next();
                if (order.isMarketOrder() && order.isActive()) {
                    order.setStatus(StockOrder.Status.CANCELLED);
                    expiredOrders.add(order);
                    orderIterator.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                levelIterator.remove();
            }
        }
    }

    private void updateStats(double price, int qty, boolean buyerInitiated) {
        if (openPrice == 0.0) {
            openPrice = price;
        }
        lastPrice = price;
        highPrice = Math.max(highPrice, price);
        lowPrice = Math.min(lowPrice, price);
        totalVolume.addAndGet(qty);
        totalAmount += price * qty;
        if (buyerInitiated) {
            buyVolume.addAndGet(qty);
        } else {
            sellVolume.addAndGet(qty);
        }
    }

    public void setLastPrice(double price) {
        if (openPrice == 0.0) {
            openPrice = price;
        }
        lastPrice = price;
        highPrice = Math.max(highPrice, price);
        lowPrice = Math.min(lowPrice == Double.MAX_VALUE ? price : lowPrice, price);
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public double getLowPrice() {
        return lowPrice == Double.MAX_VALUE ? 0.0 : lowPrice;
    }

    public int getTotalVolume() {
        return totalVolume.get();
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public long getBuyVolume() {
        return buyVolume.get();
    }

    public long getSellVolume() {
        return sellVolume.get();
    }

    public double getUpperLimit(double prevClose) {
        return Math.round(prevClose * (1 + LIMIT_RATIO) * 10.0) / 10.0;
    }

    public double getLowerLimit(double prevClose) {
        return Math.round(prevClose * (1 - LIMIT_RATIO) * 10.0) / 10.0;
    }

    public double adjustToTick(double price) {
        return Math.round(price * 100.0) / 100.0;
    }

    public record MatchResult(
            List<MatchEvent> events,
            List<StockOrder> expiredOrders
    ) {}

    public record MatchEvent(
            StockOrder buyOrder,
            StockOrder sellOrder,
            double price,
            int quantity,
            boolean buyerInitiated
    ) {}
}
