package StockMainAction.model.core;

import StockMainAction.controller.listeners.OrderBookListener;
import StockMainAction.model.PersonalAI;
import StockMainAction.model.StockMarketModel;
import StockMainAction.model.account.AccountSnapshot;
import StockMainAction.model.user.UserAccount;
import StockMainAction.util.logging.AsyncMarketLogger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;

/**
 * Price-time-priority matching engine. The engine lock owns every order-book
 * mutation and account settlement; callbacks and Swing notifications run only
 * after committed state has been unlocked.
 */
public class OrderBook implements AutoCloseable {
    private static final AsyncMarketLogger LOGGER = new AsyncMarketLogger(1_024);

    private final ReentrantLock engineLock = new ReentrantLock(true);
    private final NavigableMap<Double, Deque<Order>> buyLevels =
            new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Double, Deque<Order>> sellLevels = new TreeMap<>();
    private final Map<String, Order> ordersById = new HashMap<>();
    private final CopyOnWriteArrayList<OrderBookListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<TradeExecutedListener> tradeListeners = new CopyOnWriteArrayList<>();
    private final StockMarketModel model;
    private final Clock clock;
    private final ExecutorService publicationExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Thread publicationThread;

    private volatile MatchingMode matchingMode = MatchingMode.TWSE_STRICT;
    private volatile double liquidityFactor = 1.0;
    private volatile double maxMarketSlippageRatio = 0.10;

    public OrderBook(StockMarketModel model) {
        this(model, Clock.systemUTC());
    }

    public OrderBook(StockMarketModel model, Clock clock) {
        this.model = model;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.publicationExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "order-book-publication");
            thread.setDaemon(true);
            publicationThread = thread;
            return thread;
        });
    }

    public double adjustPriceToUnit(double price) {
        if (!Double.isFinite(price) || price <= 0) {
            return 0.0;
        }
        double tick = getTickSize(price);
        return Math.round(Math.round(price / tick) * tick * 100.0) / 100.0;
    }

    public double[] calculatePriceRange(double currentPrice, double percentage) {
        if (!Double.isFinite(currentPrice) || currentPrice <= 0
                || !Double.isFinite(percentage) || percentage < 0) {
            throw new IllegalArgumentException("Invalid price range input");
        }
        return new double[]{currentPrice * (1 - percentage), currentPrice * (1 + percentage)};
    }

    public void submitBuyOrder(Order order, double currentPrice) {
        submitBuyOrderResult(order, currentPrice);
    }

    public void submitSellOrder(Order order, double currentPrice) {
        submitSellOrderResult(order, currentPrice);
    }

    public OrderSubmissionResult submitBuyOrderResult(Order order, double currentPrice) {
        return submitLimitOrder(order, OrderSide.BUY);
    }

    public OrderSubmissionResult submitSellOrderResult(Order order, double currentPrice) {
        return submitLimitOrder(order, OrderSide.SELL);
    }

    private OrderSubmissionResult submitLimitOrder(Order order, OrderSide expectedSide) {
        ensureOpen();
        boolean accepted = false;
        boolean reservationAcquired = false;
        boolean addedToBook = false;
        String failureReason = null;
        engineLock.lock();
        try {
            ensureOpen();
            requireLimitOrder(order, expectedSide);
            double adjusted = adjustPriceToUnit(order.getPrice());
            if (adjusted <= 0) {
                order.markRejected();
                failureReason = "invalid adjusted price";
            } else {
                order.setPrice(adjusted);
                UserAccount account = order.getTraderAccount();
                reservationAcquired = expectedSide == OrderSide.BUY
                        ? account.freezeFunds(adjusted * order.getVolume())
                        : account.freezeStocks(order.getVolume());
                if (!reservationAcquired) {
                    order.markRejected();
                    failureReason = expectedSide == OrderSide.BUY
                            ? "insufficient funds" : "insufficient stocks";
                } else {
                    addOrderLocked(order);
                    addedToBook = true;
                    order.markOpen();
                    accepted = true;
                }
            }
        } catch (RuntimeException ex) {
            if (addedToBook) {
                removeOrderLocked(order);
            }
            if (reservationAcquired) {
                releaseReservation(order, expectedSide);
            }
            accepted = false;
            if (order != null) {
                order.markRejected();
            }
            failureReason = ex.getMessage();
            safeLog("Order rejected: " + ex.getMessage(), "ORDER_SUBMIT");
        } finally {
            engineLock.unlock();
        }
        if (accepted) {
            notifyBookChanged();
        }
        return new OrderSubmissionResult(order == null ? null : order.getId(), accepted, failureReason);
    }

    public boolean submitFokBuyOrder(double price, int volume, Trader trader) {
        return submitFokBuyOrderResult(price, volume, trader).isFilled();
    }

    public boolean submitFokSellOrder(double price, int volume, Trader trader) {
        return submitFokSellOrderResult(price, volume, trader).isFilled();
    }

    public ExecutionResult submitFokBuyOrderResult(double price, int volume, Trader trader) {
        ensureOpen();
        return executeFok(OrderSide.BUY, price, volume, trader);
    }

    public ExecutionResult submitFokSellOrderResult(double price, int volume, Trader trader) {
        ensureOpen();
        return executeFok(OrderSide.SELL, price, volume, trader);
    }

    private ExecutionResult executeFok(OrderSide side, double price, int volume, Trader trader) {
        validateImmediateOrder(price, volume, trader);
        double limitPrice = adjustPriceToUnit(price);
        List<CommittedTrade> trades = new ArrayList<>();
        CompletableFuture<Void> publication = CompletableFuture.completedFuture(null);
        boolean committed = false;
        long totalCents = 0;
        String failureReason = null;
        engineLock.lock();
        try {
            ensureOpen();
            ExecutionPlan plan = buildFokPlanLocked(side, limitPrice, volume);
            if (!plan.isComplete()) {
                failureReason = plan.rejectionReason();
            } else if (!counterpartyReservationsCoverPlan(plan)) {
                failureReason = "counterparty reservation invariant failed";
            } else {
                long plannedTotalCents = 0;
                for (ExecutionPlan.Fill fill : plan.fills()) {
                    plannedTotalCents = Math.addExact(plannedTotalCents,
                            Math.multiplyExact(toCents(fill.executionPrice()), fill.quantity()));
                }
                UserAccount initiator = trader.getAccount();
                boolean reserved = side == OrderSide.BUY
                        ? initiator.freezeFunds(limitPrice * volume)
                        : initiator.freezeStocks(volume);
                if (!reserved) {
                    failureReason = side == OrderSide.BUY
                            ? "insufficient funds" : "insufficient stocks";
                } else {
                    List<PlannedCommit> commits = new ArrayList<>(plan.fills().size());
                    List<UserAccount.TradeSettlement> settlements = new ArrayList<>(plan.fills().size());
                    for (ExecutionPlan.Fill fill : plan.fills()) {
                        Order resting = fill.restingOrder();
                        int quantity = fill.quantity();
                        double executionPrice = fill.executionPrice();
                        Order synthetic = side == OrderSide.BUY
                                ? Order.createFokBuyOrder(limitPrice, quantity, trader)
                                : Order.createFokSellOrder(limitPrice, quantity, trader);
                        Order buy = side == OrderSide.BUY ? synthetic : resting;
                        Order sell = side == OrderSide.SELL ? synthetic : resting;
                        commits.add(new PlannedCommit(buy, sell, executionPrice, quantity,
                                side == OrderSide.BUY, OrderType.FOK));
                        settlements.add(new UserAccount.TradeSettlement(
                                buy.getTraderAccount(), sell.getTraderAccount(),
                                buy.getPrice() * quantity, executionPrice * quantity,
                                quantity, true, true));
                    }
                    try {
                        UserAccount.settleTrades(settlements);
                        totalCents = plannedTotalCents;
                        committed = true;
                    } catch (RuntimeException ex) {
                        releaseImmediateReservation(initiator, side, limitPrice, volume);
                        failureReason = "FOK settlement failed: " + ex.getMessage();
                        safeLog(failureReason, "ORDER_INVARIANT");
                    }
                    if (committed) {
                        for (PlannedCommit commit : commits) {
                            trades.add(applyBookFillLocked(commit.buy(), commit.sell(),
                                    commit.price(), commit.volume(), commit.buyerInitiated(),
                                    commit.type(), commit.price(), null));
                        }
                        publication = enqueueCommittedTradesLocked(trades);
                    }
                }
            }
        } finally {
            engineLock.unlock();
        }
        if (committed) {
            awaitPublication(publication);
            notifyBookChanged();
        }
        return executionResult(volume, committed ? volume : 0,
                committed ? totalCents : 0, failureReason);
    }

    public void processOrders(Stock stock) {
        ensureOpen();
        List<CommittedTrade> trades = new ArrayList<>();
        CompletableFuture<Void> publication = CompletableFuture.completedFuture(null);
        engineLock.lock();
        try {
            ensureOpen();
            while (!buyLevels.isEmpty() && !sellLevels.isEmpty()) {
                Order buy = firstOrder(buyLevels);
                Order sell = firstOrder(sellLevels);
                if (buy == null || sell == null || buy.getPrice() < sell.getPrice()) {
                    break;
                }
                int quantity = Math.min(buy.getVolume(), sell.getVolume());
                double price = adjustPriceToUnit(
                        buy.getSequence() < sell.getSequence() ? buy.getPrice() : sell.getPrice());
                if (!reservationsCover(buy, sell, quantity)) {
                    safeLog("Settlement reservation invariant failed", "ORDER_INVARIANT");
                    break;
                }

                trades.add(commitFillLocked(buy, sell, price, quantity,
                        true, true, buy.getSequence() > sell.getSequence(),
                        OrderType.LIMIT, price, stock));
            }
            publication = enqueueCommittedTradesLocked(trades);
        } finally {
            engineLock.unlock();
        }
        if (!trades.isEmpty()) {
            awaitPublication(publication);
            notifyBookChanged();
        }
    }

    public ExecutionResult marketBuy(Trader trader, int quantity) {
        ensureOpen();
        validateMarketRequest(trader, quantity);
        List<CommittedTrade> trades = new ArrayList<>();
        CompletableFuture<Void> publication = CompletableFuture.completedFuture(null);
        int filled = 0;
        long totalCents = 0;
        String reason = null;
        engineLock.lock();
        try {
            ensureOpen();
            double reference = modelStockPriceOrBest(false);
            double maximumPrice = reference * (1 + maxMarketSlippageRatio);
            while (filled < quantity && !sellLevels.isEmpty()) {
                Order sell = firstOrder(sellLevels);
                if (sell.getPrice() > maximumPrice) {
                    reason = "slippage limit reached";
                    break;
                }
                int desired = Math.min(quantity - filled, sell.getVolume());
                long priceCents = toCents(sell.getPrice());
                long availableCents = trader.getAccount().snapshot().availableCashCents();
                int affordable = (int) Math.min(Integer.MAX_VALUE, availableCents / priceCents);
                int fill = Math.min(desired, affordable);
                if (fill <= 0) {
                    reason = "insufficient funds";
                    break;
                }
                if (sell.getTraderAccount().snapshot().frozenStocks() < fill) {
                    reason = "counterparty reservation invariant failed";
                    break;
                }
                Order marketBuy = Order.createMarketBuyOrder(fill, trader);
                trades.add(commitFillLocked(marketBuy, sell, sell.getPrice(), fill,
                        false, true, true, OrderType.MARKET, reference, null));
                filled += fill;
                totalCents = Math.addExact(totalCents, Math.multiplyExact(priceCents, fill));
            }
            if (filled < quantity && reason == null) {
                reason = "insufficient sell liquidity";
            }
            publication = enqueueCommittedTradesLocked(trades);
        } finally {
            engineLock.unlock();
        }
        awaitPublication(publication);
        if (!trades.isEmpty()) {
            notifyBookChanged();
        }
        return executionResult(quantity, filled, totalCents, reason);
    }

    public ExecutionResult marketSell(Trader trader, int quantity) {
        ensureOpen();
        validateMarketRequest(trader, quantity);
        if (trader.getAccount().snapshot().availableStocks() < quantity) {
            return new ExecutionResult(quantity, 0, 0, 0, "insufficient stocks");
        }
        List<CommittedTrade> trades = new ArrayList<>();
        CompletableFuture<Void> publication = CompletableFuture.completedFuture(null);
        int filled = 0;
        long totalCents = 0;
        String reason = null;
        engineLock.lock();
        try {
            ensureOpen();
            double reference = modelStockPriceOrBest(true);
            double minimumPrice = reference * (1 - maxMarketSlippageRatio);
            while (filled < quantity && !buyLevels.isEmpty()) {
                Order buy = firstOrder(buyLevels);
                if (buy.getPrice() < minimumPrice) {
                    reason = "slippage limit reached";
                    break;
                }
                int fill = Math.min(quantity - filled, buy.getVolume());
                if (buy.getTraderAccount().snapshot().frozenCashCents()
                        < Math.multiplyExact(toCents(buy.getPrice()), fill)) {
                    reason = "counterparty reservation invariant failed";
                    break;
                }
                Order marketSell = Order.createMarketSellOrder(fill, trader);
                trades.add(commitFillLocked(buy, marketSell, buy.getPrice(), fill,
                        true, false, false, OrderType.MARKET, reference, null));
                filled += fill;
                totalCents = Math.addExact(totalCents,
                        Math.multiplyExact(toCents(buy.getPrice()), fill));
            }
            if (filled < quantity && reason == null) {
                reason = "insufficient buy liquidity";
            }
            publication = enqueueCommittedTradesLocked(trades);
        } finally {
            engineLock.unlock();
        }
        awaitPublication(publication);
        if (!trades.isEmpty()) {
            notifyBookChanged();
        }
        return executionResult(quantity, filled, totalCents, reason);
    }

    public boolean cancelOrder(String orderId) {
        ensureOpen();
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        Order cancelled;
        engineLock.lock();
        try {
            ensureOpen();
            cancelled = ordersById.get(orderId);
            if (cancelled == null) {
                return false;
            }
            removeOrderLocked(cancelled);
            boolean released = cancelled.getSide() == OrderSide.BUY
                    ? cancelled.getTraderAccount().unfreezeFunds(
                            cancelled.getPrice() * cancelled.getVolume())
                    : releaseStocks(cancelled.getTraderAccount(), cancelled.getVolume());
            if (!released) {
                addOrderLocked(cancelled);
                safeLog("Cancellation release invariant failed for " + orderId, "ORDER_INVARIANT");
                return false;
            }
            cancelled.markCancelled();
        } finally {
            engineLock.unlock();
        }
        if (cancelled.getTrader() instanceof PersonalAI personalAI) {
            try {
                personalAI.onOrderCancelled(cancelled);
            } catch (RuntimeException ex) {
                safeLog("Cancellation callback failed: " + ex.getMessage(), "ORDER_CALLBACK");
            }
        }
        notifyBookChanged();
        return true;
    }

    public void addOrderBookListener(OrderBookListener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeOrderBookListener(OrderBookListener listener) {
        listeners.remove(listener);
    }

    public void addTradeExecutedListener(TradeExecutedListener listener) {
        if (listener != null) tradeListeners.addIfAbsent(listener);
    }

    public void removeTradeExecutedListener(TradeExecutedListener listener) {
        tradeListeners.remove(listener);
    }

    public List<Order> getBuyOrders() { return snapshotOrders(buyLevels); }
    public List<Order> getSellOrders() { return snapshotOrders(sellLevels); }

    public List<Order> getTopBuyOrders(int count) {
        return getBuyOrders().stream().limit(validCount(count)).collect(Collectors.toList());
    }

    public List<Order> getTopSellOrders(int count) {
        return getSellOrders().stream().limit(validCount(count)).collect(Collectors.toList());
    }

    public List<Order> getBuyOrdersByTraderType(String type) {
        return filterByTraderType(getBuyOrders(), type);
    }

    public List<Order> getSellOrdersByTraderType(String type) {
        return filterByTraderType(getSellOrders(), type);
    }

    public int getAvailableBuyVolume(double price) {
        return sumVolume(buyLevels, order -> order.getPrice() >= price);
    }

    public int getAvailableSellVolume(double price) {
        return sumVolume(sellLevels, order -> order.getPrice() <= price);
    }

    public int getBuyVolumeAtPrice(double targetPrice, double tolerance) {
        return sumVolume(buyLevels, order -> Math.abs(order.getPrice() - targetPrice) <= tolerance);
    }

    public int getSellVolumeAtPrice(double targetPrice, double tolerance) {
        return sumVolume(sellLevels, order -> Math.abs(order.getPrice() - targetPrice) <= tolerance);
    }

    public OrderBookSnapshot snapshot() {
        engineLock.lock();
        try {
            return new OrderBookSnapshot(toOrderSnapshotsLocked(buyLevels),
                    toOrderSnapshotsLocked(sellLevels));
        } finally {
            engineLock.unlock();
        }
    }

    public void setMatchingMode(MatchingMode mode) {
        if (mode == null) throw new IllegalArgumentException("Matching mode is required");
        this.matchingMode = mode;
    }

    public MatchingMode getMatchingMode() { return matchingMode; }

    /** Compatibility adapter; matching mode randomization remains disabled. */
    public void setRandomModeSwitching(boolean useRandom, double probability) { }

    public void setLiquidityFactor(double factor) {
        if (!Double.isFinite(factor) || factor <= 0) {
            throw new IllegalArgumentException("Liquidity factor must be positive");
        }
        liquidityFactor = factor;
    }

    public double getMaxMarketSlippageRatio() { return maxMarketSlippageRatio; }

    public void setMaxMarketSlippageRatio(double ratio) {
        if (!Double.isFinite(ratio)) throw new IllegalArgumentException("Invalid slippage ratio");
        maxMarketSlippageRatio = Math.max(0, Math.min(0.5, ratio));
    }

    public double getTickSize(double price) {
        if (!Double.isFinite(price) || price <= 0) return 0.01;
        if (price < 10) return 0.01;
        if (price < 50) return 0.05;
        if (price < 100) return 0.10;
        if (price < 500) return 0.50;
        if (price < 1000) return 1.00;
        return 5.00;
    }

    public double[][] generateFiveLevelPrices(double currentPrice) {
        double[] buys = new double[5];
        double[] sells = new double[5];
        double buy = currentPrice;
        double sell = currentPrice;
        for (int i = 0; i < 5; i++) {
            buys[i] = adjustPriceToUnit(Math.max(0.01, buy));
            sells[i] = adjustPriceToUnit(sell);
            buy = Math.max(0.01, buy - getTickSize(buy));
            sell += getTickSize(sell);
        }
        return new double[][]{buys, sells};
    }

    public double getCurrentStockPrice() {
        if (model != null && model.getStock() != null) return model.getStock().getPrice();
        return 10.0;
    }

    private ExecutionPlan buildFokPlanLocked(OrderSide side, double limitPrice, int volume) {
        NavigableMap<Double, Deque<Order>> levels = side == OrderSide.BUY ? sellLevels : buyLevels;
        List<ExecutionPlan.Fill> fills = new ArrayList<>();
        int remaining = volume;
        for (Map.Entry<Double, Deque<Order>> level : levels.entrySet()) {
            boolean eligible = side == OrderSide.BUY
                    ? level.getKey() <= limitPrice : level.getKey() >= limitPrice;
            if (!eligible) break;
            for (Order order : level.getValue()) {
                int fill = Math.min(remaining, order.getVolume());
                if (fill > 0) fills.add(new ExecutionPlan.Fill(order, fill, order.getPrice()));
                remaining -= fill;
                if (remaining == 0) {
                    return new ExecutionPlan(side, OrderType.FOK, volume, fills, null);
                }
            }
        }
        return new ExecutionPlan(side, OrderType.FOK, volume, fills,
                "insufficient eligible liquidity");
    }

    private boolean counterpartyReservationsCoverPlan(ExecutionPlan plan) {
        Map<UserAccount, Long> funds = new HashMap<>();
        Map<UserAccount, Integer> stocks = new HashMap<>();
        for (ExecutionPlan.Fill fill : plan.fills()) {
            UserAccount account = fill.restingOrder().getTraderAccount();
            if (plan.aggressorSide() == OrderSide.BUY) {
                stocks.merge(account, fill.quantity(), Math::addExact);
            } else {
                long amount = Math.multiplyExact(toCents(fill.restingOrder().getPrice()), fill.quantity());
                funds.merge(account, amount, Math::addExact);
            }
        }
        return funds.entrySet().stream().allMatch(e -> e.getKey().snapshot().frozenCashCents() >= e.getValue())
                && stocks.entrySet().stream().allMatch(e -> e.getKey().snapshot().frozenStocks() >= e.getValue());
    }

    private boolean reservationsCover(Order buy, Order sell, int quantity) {
        long required = Math.multiplyExact(toCents(buy.getPrice()), quantity);
        return buy.getTraderAccount().snapshot().frozenCashCents() >= required
                && sell.getTraderAccount().snapshot().frozenStocks() >= quantity;
    }

    private CommittedTrade commitFillLocked(Order buy, Order sell, double executionPrice,
            int quantity, boolean buyerUsesReservation, boolean sellerUsesReservation,
            boolean buyerInitiated, OrderType type, double referencePrice, Stock stock) {
        double value = executionPrice * quantity;
        UserAccount.settleTrade(buy.getTraderAccount(), sell.getTraderAccount(),
                buyerUsesReservation ? buy.getPrice() * quantity : 0,
                value, quantity, buyerUsesReservation, sellerUsesReservation);

        return applyBookFillLocked(buy, sell, executionPrice, quantity,
                buyerInitiated, type, referencePrice, stock);
    }

    private CommittedTrade applyBookFillLocked(Order buy, Order sell, double executionPrice,
            int quantity, boolean buyerInitiated, OrderType type, double referencePrice, Stock stock) {
        if (ordersById.containsKey(buy.getId())) reduceOrderLocked(buy, quantity);
        if (ordersById.containsKey(sell.getId())) reduceOrderLocked(sell, quantity);
        if (stock == null) updateStockPriceLocked(executionPrice);
        else stock.setPrice(executionPrice);
        return new CommittedTrade(buy, sell, executionPrice, quantity, buyerInitiated, type, referencePrice);
    }

    private static void releaseImmediateReservation(UserAccount account, OrderSide side,
            double limitPrice, int volume) {
        if (side == OrderSide.BUY) {
            if (!account.unfreezeFunds(limitPrice * volume)) {
                throw new IllegalStateException("failed to release rejected FOK funds");
            }
        } else {
            account.unfreezeStocks(volume);
        }
    }

    private void addOrderLocked(Order order) {
        NavigableMap<Double, Deque<Order>> levels =
                order.getSide() == OrderSide.BUY ? buyLevels : sellLevels;
        Deque<Order> queue = levels.computeIfAbsent(order.getPrice(), ignored -> new ArrayDeque<>());
        if (queue.isEmpty() || queue.peekLast().getSequence() < order.getSequence()) {
            queue.addLast(order);
        } else {
            List<Order> sorted = new ArrayList<>(queue);
            sorted.add(order);
            sorted.sort(Comparator.comparingLong(Order::getSequence));
            queue.clear();
            queue.addAll(sorted);
        }
        ordersById.put(order.getId(), order);
    }

    private void reduceOrderLocked(Order order, int quantity) {
        order.setVolume(order.getVolume() - quantity);
        if (order.getVolume() == 0) removeOrderLocked(order);
    }

    private void removeOrderLocked(Order order) {
        NavigableMap<Double, Deque<Order>> levels =
                order.getSide() == OrderSide.BUY ? buyLevels : sellLevels;
        Deque<Order> queue = levels.get(order.getPrice());
        if (queue != null) {
            queue.remove(order);
            if (queue.isEmpty()) levels.remove(order.getPrice());
        }
        ordersById.remove(order.getId());
    }

    private static Order firstOrder(NavigableMap<Double, Deque<Order>> levels) {
        return levels.isEmpty() ? null : levels.firstEntry().getValue().peekFirst();
    }

    private List<Order> snapshotOrders(NavigableMap<Double, Deque<Order>> levels) {
        engineLock.lock();
        try {
            List<Order> result = new ArrayList<>();
            levels.values().forEach(queue -> queue.forEach(order -> result.add(order.detachedCopy())));
            return List.copyOf(result);
        } finally {
            engineLock.unlock();
        }
    }

    private int sumVolume(NavigableMap<Double, Deque<Order>> levels, Predicate<Order> filter) {
        engineLock.lock();
        try {
            return levels.values().stream().flatMap(Deque::stream)
                    .filter(filter).mapToInt(Order::getVolume).sum();
        } finally {
            engineLock.unlock();
        }
    }

    private static List<Order> filterByTraderType(List<Order> orders, String type) {
        if (type == null || type.isBlank()) return List.of();
        return orders.stream().filter(order -> type.equalsIgnoreCase(order.getTrader().getTraderType()))
                .collect(Collectors.toUnmodifiableList());
    }

    private static int validCount(int count) {
        if (count < 0) throw new IllegalArgumentException("Count must not be negative");
        return count;
    }

    private List<OrderSnapshot> toOrderSnapshotsLocked(NavigableMap<Double, Deque<Order>> levels) {
        List<OrderSnapshot> result = new ArrayList<>();
        levels.values().forEach(queue -> queue.forEach(order -> result.add(new OrderSnapshot(
                order.getId(), order.getSide(), order.getOrderType(), order.getStatus(),
                order.getPrice(), order.getOriginalVolume(), order.getVolume(),
                order.getSequence(), order.getTrader().getTraderType()))));
        return List.copyOf(result);
    }

    private CompletableFuture<Void> enqueueCommittedTradesLocked(List<CommittedTrade> trades) {
        if (trades.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CommittedTrade> committed = List.copyOf(trades);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            publicationExecutor.execute(() -> {
                engineLock.lock();
                engineLock.unlock();
                try {
                    publishCommittedTrades(committed);
                    completion.complete(null);
                } catch (RuntimeException ex) {
                    completion.completeExceptionally(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            completion.completeExceptionally(ex);
        }
        return completion;
    }

    private void awaitPublication(CompletableFuture<Void> publication) {
        if (Thread.currentThread() == publicationThread) {
            return;
        }
        try {
            publication.join();
        } catch (RuntimeException ex) {
            safeLog("Trade publication failed: " + ex.getMessage(), "ORDER_CALLBACK");
        }
    }

    private void publishCommittedTrades(List<CommittedTrade> trades) {
        for (CommittedTrade trade : trades) {
            notifyTrader(trade.buy().getTrader(), "buy", trade.volume(), trade.price(), trade.type());
            notifyTrader(trade.sell().getTrader(), "sell", trade.volume(), trade.price(), trade.type());
            TradeExecuted event = new TradeExecuted(UUID.randomUUID().toString(),
                    trade.buy().getId(), trade.sell().getId(),
                    trade.buy().getTrader().getTraderType(), trade.sell().getTrader().getTraderType(),
                    trade.price(), trade.volume(), trade.buyerInitiated(),
                    trade.type(), clock.millis());
            recordTransaction(trade, event.id());
            for (TradeExecutedListener listener : tradeListeners) {
                try { listener.onTradeExecuted(event); }
                catch (RuntimeException ex) { safeLog("Trade listener failed: " + ex.getMessage(), "ORDER_CALLBACK"); }
            }
        }
    }

    private void releaseReservation(Order order, OrderSide side) {
        UserAccount account = order.getTraderAccount();
        if (side == OrderSide.BUY) {
            if (!account.unfreezeFunds(order.getPrice() * order.getVolume())) {
                throw new IllegalStateException("failed to roll back reserved funds");
            }
        } else {
            account.unfreezeStocks(order.getVolume());
        }
    }

    private void notifyTrader(Trader trader, String side, int volume, double price, OrderType type) {
        try {
            if (type == OrderType.MARKET) trader.updateAverageCostPrice(side, volume, price);
            else trader.updateAfterTransaction(side, volume, price);
        } catch (RuntimeException ex) {
            safeLog("Trader callback failed: " + ex.getMessage(), "ORDER_CALLBACK");
        }
    }

    private void recordTransaction(CommittedTrade trade, String id) {
        if (model == null) return;
        try {
            Transaction transaction;
            if (trade.type() == OrderType.MARKET) {
                String orderType = trade.buyerInitiated() ? "MARKET_BUY" : "MARKET_SELL";
                String initiator = trade.buyerInitiated()
                        ? trade.buy().getTrader().getTraderType()
                        : trade.sell().getTrader().getTraderType();
                String counterparty = trade.buyerInitiated()
                        ? trade.sell().getTrader().getTraderType()
                        : trade.buy().getTrader().getTraderType();
                double estimatedPrice = Double.isFinite(trade.referencePrice()) && trade.referencePrice() > 0
                        ? trade.referencePrice()
                        : trade.price();
                transaction = new Transaction(id, initiator, orderType,
                        trade.volume(), estimatedPrice, estimatedPrice, clock);
                transaction.addFillRecord(trade.price(), trade.volume(), counterparty, 1);
                transaction.completeMarketOrderTransaction(trade.price(), 1, null);
            } else {
                transaction = new Transaction(id, trade.buy(), trade.sell(),
                        trade.price(), trade.volume(), clock.millis(), clock);
            }
            transaction.setMatchingMode(matchingMode.toString());
            transaction.setBuyerInitiated(trade.buyerInitiated());
            model.addTransaction(transaction);
            if (model.getMarketAnalyzer() != null) {
                model.getMarketAnalyzer().addTransaction(trade.price(), trade.volume());
                model.getMarketAnalyzer().addPrice(trade.price());
            }
            model.updateVolumeChart(trade.volume());
        } catch (RuntimeException ex) {
            safeLog("Post-trade recording failed: " + ex.getMessage(), "ORDER_RECORDING");
        }
    }

    private void notifyBookChanged() {
        if (listeners.isEmpty() && model == null) {
            return;
        }
        Runnable notification = () -> {
            for (OrderBookListener listener : listeners) {
                try { listener.onOrderBookUpdated(); }
                catch (RuntimeException ex) { safeLog("Book listener failed: " + ex.getMessage(), "ORDER_CALLBACK"); }
            }
            if (model != null) {
                try {
                    model.updateLabels();
                    model.updateOrderBookDisplay();
                } catch (RuntimeException ex) {
                    safeLog("Model UI notification failed: " + ex.getMessage(), "ORDER_CALLBACK");
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) notification.run();
        else SwingUtilities.invokeLater(notification);
    }

    private void updateStockPriceLocked(double price) {
        if (model != null && model.getStock() != null) model.getStock().setPrice(price);
    }

    private double modelStockPriceOrBest(boolean buySide) {
        if (model != null && model.getStock() != null && model.getStock().getPrice() > 0) {
            return model.getStock().getPrice();
        }
        Order best = firstOrder(buySide ? buyLevels : sellLevels);
        return best == null ? 10.0 : best.getPrice();
    }

    private static void requireLimitOrder(Order order, OrderSide expectedSide) {
        if (order == null || order.getSide() != expectedSide || order.getOrderType() != OrderType.LIMIT) {
            throw new IllegalArgumentException("Expected a matching-side limit order");
        }
    }

    private static void validateImmediateOrder(double price, int volume, Trader trader) {
        if (!Double.isFinite(price) || price <= 0 || volume <= 0
                || trader == null || trader.getAccount() == null) {
            throw new IllegalArgumentException("Invalid immediate order");
        }
    }

    private static void validateMarketRequest(Trader trader, int quantity) {
        if (trader == null || trader.getAccount() == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid market order");
        }
    }

    private static boolean releaseStocks(UserAccount account, int quantity) {
        try {
            account.unfreezeStocks(quantity);
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    private static ExecutionResult executionResult(
            int requested, int filled, long totalCents, String failureReason) {
        double total = totalCents / 100.0;
        double average = filled == 0 ? 0 : total / filled;
        return new ExecutionResult(requested, filled, average, total,
                filled == requested ? null : failureReason);
    }

    private static long toCents(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact();
    }

    private static void safeLog(String message, String category) {
        LOGGER.warn(message, category);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("OrderBook is closed");
        }
    }

    @Override
    public void close() {
        engineLock.lock();
        try {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
        } finally {
            engineLock.unlock();
        }
        publicationExecutor.shutdown();
        try {
            if (!publicationExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                publicationExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            publicationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record CommittedTrade(Order buy, Order sell, double price, int volume,
            boolean buyerInitiated, OrderType type, double referencePrice) { }
    private record PlannedCommit(Order buy, Order sell, double price, int volume,
            boolean buyerInitiated, OrderType type) { }
}
