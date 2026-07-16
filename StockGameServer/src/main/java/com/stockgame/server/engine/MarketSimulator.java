package com.stockgame.server.engine;

import com.stockgame.server.entity.StockOrder;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketSimulator {

    private final ServerMatchingEngine matchingEngine;
    private final ServerOrderBook orderBook;

    @Value("${game.ai-retail-count:10}")
    private int retailCount;

    @Value("${game.ai-noise-count:5}")
    private int noiseCount;

    @Value("${game.initial-price:100.0}")
    private double initialPrice;

    private double mainForceCash = 20_000_000.0;
    private int mainForceStock = 100_000;
    private double mainForceAvgCost;
    private int mainForcePhase = 0;

    private final double[] retailCash = new double[50];
    private final int[] retailStocks = new int[50];

    private int tickCount = 0;

    private final Random rng = new Random();

    public void init(double lastPrice) {
        mainForceAvgCost = lastPrice;
        for (int i = 0; i < retailCash.length; i++) {
            retailCash[i] = 500_000 + rng.nextInt(500_000);
            retailStocks[i] = rng.nextInt(5000);
        }
        log.info("AI market initialized at price {}", lastPrice);
    }

    public int getRetailCount() {
        return retailCount;
    }

    public int getNoiseCount() {
        return noiseCount;
    }

    public void setRetailCount(int retailCount) {
        this.retailCount = Math.max(0, Math.min(retailCount, retailCash.length));
    }

    public void setNoiseCount(int noiseCount) {
        this.noiseCount = Math.max(0, Math.min(noiseCount, 20));
    }

    public void runAiTick() {
        tickCount++;
        double lastPrice = orderBook.getLastPrice();
        if (lastPrice <= 0) {
            lastPrice = initialPrice;
        }

        runMainForceStrategy(lastPrice);

        if (tickCount % 2 == 0) {
            runRetailStrategies(lastPrice);
        }

        runNoiseTraders(lastPrice);
    }

    private void runMainForceStrategy(double price) {
        if (tickCount % 30 == 0) {
            mainForcePhase = (mainForcePhase + 1) % 3;
        }

        switch (mainForcePhase) {
            case 0 -> mainForceAccumulate(price);
            case 1 -> mainForcePump(price);
            case 2 -> mainForceDump(price);
            default -> {
            }
        }
    }

    private void mainForceAccumulate(double price) {
        if (mainForceCash < price * 500) {
            return;
        }
        int qty = rng.nextInt(500) + 100;
        double bidPrice = orderBook.adjustToTick(price * (0.998 + rng.nextDouble() * 0.002));
        placeAiOrder(StockOrder.Side.BUY, StockOrder.OrderType.LIMIT, bidPrice, qty);
    }

    private void mainForcePump(double price) {
        if (rng.nextDouble() < 0.3 && mainForceCash > price * 200) {
            int qty = rng.nextInt(300) + 100;
            placeAiOrder(StockOrder.Side.BUY, StockOrder.OrderType.MARKET, 0, qty);
        }
    }

    private void mainForceDump(double price) {
        if (mainForceStock > 0 && rng.nextDouble() < 0.4) {
            int qty = Math.min(mainForceStock, rng.nextInt(500) + 100);
            double askPrice = orderBook.adjustToTick(price * (1.001 + rng.nextDouble() * 0.003));
            placeAiOrder(StockOrder.Side.SELL, StockOrder.OrderType.LIMIT, askPrice, qty);
        }
    }

    private void runRetailStrategies(double price) {
        int count = Math.min(retailCount, retailCash.length);
        for (int i = 0; i < count; i++) {
            if (rng.nextDouble() > 0.15) {
                continue;
            }

            boolean isBuy = rng.nextDouble() < 0.5;
            int qty = rng.nextInt(100) + 10;

            if (isBuy && retailCash[i] >= price * qty) {
                double bid = orderBook.adjustToTick(price * (0.997 + rng.nextDouble() * 0.006));
                placeAiOrder(StockOrder.Side.BUY, StockOrder.OrderType.LIMIT, bid, qty);
                retailCash[i] -= bid * qty;
                retailStocks[i] += qty;
            } else if (!isBuy && retailStocks[i] >= qty) {
                double ask = orderBook.adjustToTick(price * (0.997 + rng.nextDouble() * 0.006));
                placeAiOrder(StockOrder.Side.SELL, StockOrder.OrderType.LIMIT, ask, qty);
                retailStocks[i] -= qty;
                retailCash[i] += ask * qty;
            }
        }
    }

    private void runNoiseTraders(double price) {
        int count = Math.min(noiseCount, 5);
        for (int i = 0; i < count; i++) {
            if (rng.nextDouble() > 0.3) {
                continue;
            }
            int qty = rng.nextInt(50) + 5;
            StockOrder.Side side = rng.nextBoolean() ? StockOrder.Side.BUY : StockOrder.Side.SELL;
            placeAiOrder(side, StockOrder.OrderType.MARKET, price, qty);
        }
    }

    private void placeAiOrder(StockOrder.Side side, StockOrder.OrderType type, double price, int qty) {
        try {
            StockOrder order = StockOrder.builder()
                    .user(null)
                    .side(side)
                    .type(type)
                    .price(price)
                    .quantity(qty)
                    .build();
            matchingEngine.submitAiOrder(order);
        } catch (Exception e) {
            log.debug("AI order rejected: {}", e.getMessage());
        }
    }
}
