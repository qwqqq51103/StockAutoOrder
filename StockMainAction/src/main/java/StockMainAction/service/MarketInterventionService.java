package StockMainAction.service;

import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Trader;
import StockMainAction.util.logging.MarketLogger;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Executes bounded, sequential market interventions outside the Swing EDT. */
public final class MarketInterventionService implements AutoCloseable {
    private static final int MAX_PENDING_INTERVENTIONS = 4;
    private static final MarketLogger LOGGER = MarketLogger.getInstance();

    private final StockMarketModel model;
    private final ThreadPoolExecutor executor;

    public MarketInterventionService(StockMarketModel model) {
        this.model = Objects.requireNonNull(model, "model");
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_INTERVENTIONS), task -> {
                    Thread thread = new Thread(task, "market-intervention");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public CompletableFuture<Result> execute(Request request) {
        Objects.requireNonNull(request, "request");
        try {
            return CompletableFuture.supplyAsync(() -> run(request), executor);
        } catch (RejectedExecutionException ex) {
            String message = executor.isShutdown()
                    ? "市場介入服務已關閉" : "市場介入工作已滿，請等待目前工作完成";
            return CompletableFuture.failedFuture(new IllegalStateException(
                    message, ex));
        }
    }

    private Result run(Request request) {
        OrderBook orderBook = model.getOrderBook();
        if (orderBook == null || model.getStock() == null) {
            throw new IllegalStateException("模型/訂單簿未初始化");
        }
        Trader actor = request.useMainForce() ? model.getMainForce() : model.getUserInvestor();
        if (actor == null) {
            throw new IllegalStateException("找不到交易者(主力/個人戶)");
        }

        if (request.enableLiquidity()) {
            seedLiquidity(actor, request);
            pause(50);
        }
        if (request.enableCounterWallSelfTrade()) {
            seedCounterWall(actor, request);
            pause(50);
        }

        int remaining = request.totalQuantity();
        int chunkMinimum = Math.max(1, request.totalQuantity() / request.slices());
        for (int i = 0; i < request.slices() && remaining > 0; i++) {
            requireNotInterrupted();
            int chunk = i == request.slices() - 1
                    ? remaining : Math.min(remaining, chunkMinimum);
            if (request.pump()) {
                orderBook.marketBuy(actor, chunk);
            } else {
                orderBook.marketSell(actor, chunk);
            }
            remaining -= chunk;
            pause(30);
        }
        return new Result(request.totalQuantity() - remaining, request.useMainForce());
    }

    private void seedLiquidity(Trader actor, Request request) {
        try {
            OrderBook orderBook = model.getOrderBook();
            double last = model.getStock().getPrice();
            if (last <= 0) return;
            double slip = orderBook.getMaxMarketSlippageRatio();
            double minPrice = orderBook.adjustPriceToUnit(last * (1.0 - slip));
            double maxPrice = orderBook.adjustPriceToUnit(last * (1.0 + slip));
            int depthTotal = Math.max(0, (int) Math.round(request.totalQuantity() * 0.30));
            if (depthTotal <= 0) return;
            double span = request.depthSpanRatio() <= 0 ? 0.01 : request.depthSpanRatio();

            if (request.pump()) {
                placeBuyLayers(actor, depthTotal, request.depthLevels(),
                        last * 0.999, last * (1.0 - span), minPrice, maxPrice, orderBook);
            } else {
                int sellPart = Math.max(1, (int) Math.round(depthTotal * 0.70));
                int buyPart = Math.max(0, depthTotal - sellPart);
                placeSellLayers(actor, sellPart, request.depthLevels(),
                        last * 1.001, last * (1.0 + span), minPrice, maxPrice, orderBook);
                if (buyPart > 0) {
                    Trader footing = request.useOtherTraderFooting()
                            ? chooseFootingTrader(actor) : actor;
                    placeBuyLayers(footing, buyPart, Math.max(1, request.depthLevels() / 2),
                            last * (1.0 - Math.min(0.02, slip * 0.6)),
                            last * (1.0 - Math.min(span + 0.02, slip * 0.95)),
                            minPrice, maxPrice, orderBook);
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("建立市場深度掛單失敗：" + ex.getMessage(), "MARKET_INTERVENTION");
        }
    }

    private void seedCounterWall(Trader actor, Request request) {
        try {
            OrderBook orderBook = model.getOrderBook();
            double last = model.getStock().getPrice();
            if (last <= 0) return;
            double slip = orderBook.getMaxMarketSlippageRatio();
            double minPrice = orderBook.adjustPriceToUnit(last * (1.0 - slip));
            double maxPrice = orderBook.adjustPriceToUnit(last * (1.0 + slip));
            double span = request.depthSpanRatio() <= 0 ? 0.01 : request.depthSpanRatio();
            int counterTotal = Math.max(0, (int) Math.round(request.totalQuantity() * 0.20));
            if (counterTotal <= 0) return;

            if (request.pump()) {
                placeSellLayers(actor, counterTotal, request.depthLevels(),
                        last * 1.0005, last * (1.0 + Math.min(span, slip * 0.8)),
                        minPrice, maxPrice, orderBook);
            } else {
                placeBuyLayers(actor, counterTotal, request.depthLevels(),
                        last * 0.9995, last * (1.0 - Math.min(span, slip * 0.8)),
                        minPrice, maxPrice, orderBook);
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("建立自成交對手牆失敗：" + ex.getMessage(), "MARKET_INTERVENTION");
        }
    }

    private Trader chooseFootingTrader(Trader actor) {
        Trader main = model.getMainForce();
        Trader user = model.getUserInvestor();
        if (actor == main && user != null) return user;
        if (actor == user && main != null) return main;
        return actor;
    }

    private static void placeBuyLayers(Trader actor, int total, int levels, double high,
            double low, double minPrice, double maxPrice, OrderBook orderBook) {
        if (actor == null || actor.getAccount() == null || total <= 0) return;
        double highBuy = clamp(orderBook.adjustPriceToUnit(high), minPrice, maxPrice);
        double lowBuy = clamp(orderBook.adjustPriceToUnit(low), minPrice, maxPrice);
        if (lowBuy > highBuy) {
            double swap = lowBuy; lowBuy = highBuy; highBuy = swap;
        }
        int layerCount = Math.max(1, levels);
        int perLayer = Math.max(1, total / layerCount);
        for (int i = 0; i < layerCount; i++) {
            int quantity = i == layerCount - 1 ? total - perLayer * (layerCount - 1) : perLayer;
            if (quantity <= 0) break;
            double ratio = layerCount == 1 ? 0.0 : i / (double) (layerCount - 1);
            double price = clamp(orderBook.adjustPriceToUnit(highBuy + (lowBuy - highBuy) * ratio),
                    minPrice, maxPrice);
            int affordable = (int) Math.floor(actor.getAccount().getAvailableFunds()
                    / Math.max(0.01, price));
            quantity = Math.min(quantity, affordable);
            if (quantity <= 0) break;
            orderBook.submitBuyOrder(Order.createLimitBuyOrder(price, quantity, actor), price);
        }
    }

    private static void placeSellLayers(Trader actor, int total, int levels, double low,
            double high, double minPrice, double maxPrice, OrderBook orderBook) {
        if (actor == null || actor.getAccount() == null || total <= 0) return;
        double lowSell = clamp(orderBook.adjustPriceToUnit(low), minPrice, maxPrice);
        double highSell = clamp(orderBook.adjustPriceToUnit(high), minPrice, maxPrice);
        if (lowSell > highSell) {
            double swap = lowSell; lowSell = highSell; highSell = swap;
        }
        int layerCount = Math.max(1, levels);
        int perLayer = Math.max(1, total / layerCount);
        for (int i = 0; i < layerCount; i++) {
            int quantity = i == layerCount - 1 ? total - perLayer * (layerCount - 1) : perLayer;
            quantity = Math.min(quantity, actor.getAccount().getStockInventory());
            if (quantity <= 0) break;
            double ratio = layerCount == 1 ? 0.0 : i / (double) (layerCount - 1);
            double price = clamp(orderBook.adjustPriceToUnit(lowSell + (highSell - lowSell) * ratio),
                    minPrice, maxPrice);
            orderBook.submitSellOrder(Order.createLimitSellOrder(price, quantity, actor), price);
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void pause(long milliseconds) {
        requireNotInterrupted();
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("市場介入已取消", ex);
        }
    }

    private static void requireNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("市場介入已取消");
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record Request(boolean pump, int totalQuantity, int slices, boolean useMainForce,
            boolean enableLiquidity, int depthLevels, double depthSpanRatio,
            boolean enableCounterWallSelfTrade, boolean useOtherTraderFooting) {
        public Request {
            if (totalQuantity <= 0) throw new IllegalArgumentException("總量必須大於 0");
            slices = Math.max(1, Math.min(500, slices));
            depthLevels = Math.max(1, Math.min(30, depthLevels));
            depthSpanRatio = Math.max(0.0, Math.min(0.15, depthSpanRatio));
        }
    }

    public record Result(int attemptedQuantity, boolean usedMainForce) { }
}
