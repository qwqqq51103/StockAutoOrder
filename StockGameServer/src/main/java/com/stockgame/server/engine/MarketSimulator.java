package com.stockgame.server.engine;

import com.stockgame.server.entity.StockOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 市場參與者：主力、散戶、噪音交易者。
 * 移植自桌面版的 MainForceStrategyWithOrderBook、RetailInvestorAI、NoiseTraderAI。
 *
 * 每個 tick 由 MarketService 呼叫 runAiTick()，
 * AI 依市況決定是否掛單並透過 ServerMatchingEngine 送出。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketSimulator {

    private final ServerMatchingEngine matchingEngine;
    private final ServerOrderBook      orderBook;

    @Value("${game.ai-retail-count:10}")
    private int retailCount;

    @Value("${game.ai-noise-count:5}")
    private int noiseCount;

    @Value("${game.initial-price:100.0}")
    private double initialPrice;

    // 主力資金池（記憶體，不持久化）
    private double mainForceCash  = 20_000_000.0;
    private int    mainForceStock = 100_000;
    private double mainForceAvgCost;
    private int    mainForcePhase = 0;   // 0=吸籌 1=拉抬 2=出貨

    // 散戶
    private final double[] retailCash   = new double[50];
    private final int[]    retailStocks = new int[50];

    private int tickCount = 0;

    private final Random rng = new Random();

    /**
     * 初始化 AI 資產（由 MarketService 在 @PostConstruct 呼叫）
     */
    public void init(double lastPrice) {
        mainForceAvgCost = lastPrice;
        for (int i = 0; i < retailCash.length; i++) {
            retailCash[i]   = 500_000 + rng.nextInt(500_000);
            retailStocks[i] = rng.nextInt(5000);
        }
        log.info("AI 市場初始化完成，初始股價：{}", lastPrice);
    }

    /**
     * 每個 tick 執行 AI 決策。
     */
    public void runAiTick() {
        tickCount++;
        double lastPrice = orderBook.getLastPrice();
        if (lastPrice <= 0) lastPrice = initialPrice;

        // 主力策略
        runMainForceStrategy(lastPrice);

        // 散戶（每 2 tick 跑一次）
        if (tickCount % 2 == 0) {
            runRetailStrategies(lastPrice);
        }

        // 噪音交易者（每 tick 隨機吃單）
        runNoiseTraders(lastPrice);
    }

    // ── 主力策略 ──────────────────────────────────────────────────────────────

    private void runMainForceStrategy(double price) {
        // 每 30 tick 切換一次階段
        if (tickCount % 30 == 0) {
            mainForcePhase = (mainForcePhase + 1) % 3;
            log.debug("主力切換階段 → {}", mainForcePhase == 0 ? "吸籌" : mainForcePhase == 1 ? "拉抬" : "出貨");
        }

        switch (mainForcePhase) {
            case 0 -> mainForceAccumulate(price);  // 吸籌
            case 1 -> mainForcePump(price);         // 拉抬
            case 2 -> mainForceDump(price);         // 出貨
        }
    }

    private void mainForceAccumulate(double price) {
        if (mainForceCash < price * 500) return;
        int qty = rng.nextInt(500) + 100;
        double bidPrice = orderBook.adjustToTick(price * (0.998 + rng.nextDouble() * 0.002));
        placeAiOrder(StockOrder.Side.BUY, StockOrder.OrderType.LIMIT, bidPrice, qty);
    }

    private void mainForcePump(double price) {
        // 拉抬：積極市價買入
        if (rng.nextDouble() < 0.3 && mainForceCash > price * 200) {
            int qty = rng.nextInt(300) + 100;
            placeAiOrder(StockOrder.Side.BUY, StockOrder.OrderType.MARKET, 0, qty);
        }
    }

    private void mainForceDump(double price) {
        // 出貨：分批限價賣出
        if (mainForceStock > 0 && rng.nextDouble() < 0.4) {
            int qty = Math.min(mainForceStock, rng.nextInt(500) + 100);
            double askPrice = orderBook.adjustToTick(price * (1.001 + rng.nextDouble() * 0.003));
            placeAiOrder(StockOrder.Side.SELL, StockOrder.OrderType.LIMIT, askPrice, qty);
        }
    }

    // ── 散戶策略 ──────────────────────────────────────────────────────────────

    private void runRetailStrategies(double price) {
        int count = Math.min(retailCount, retailCash.length);
        for (int i = 0; i < count; i++) {
            if (rng.nextDouble() > 0.15) continue;  // 15% 機率每個散戶決策

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

    // ── 噪音交易者 ────────────────────────────────────────────────────────────

    private void runNoiseTraders(double price) {
        int count = Math.min(noiseCount, 5);
        for (int i = 0; i < count; i++) {
            if (rng.nextDouble() > 0.3) continue;
            int    qty  = rng.nextInt(50) + 5;
            StockOrder.Side side = rng.nextBoolean() ? StockOrder.Side.BUY : StockOrder.Side.SELL;
            // 噪音交易者傾向使用市價單，增加成交
            placeAiOrder(side, StockOrder.OrderType.MARKET, 0, qty);
        }
    }

    // ── 下單工具 ──────────────────────────────────────────────────────────────

    private void placeAiOrder(StockOrder.Side side, StockOrder.OrderType type, double price, int qty) {
        try {
            StockOrder order = StockOrder.builder()
                    .user(null)             // null = AI，不關聯玩家
                    .side(side)
                    .type(type)
                    .price(price)
                    .quantity(qty)
                    .build();
            matchingEngine.submitAiOrder(order);
        } catch (Exception e) {
            log.debug("AI 下單失敗（可忽略）：{}", e.getMessage());
        }
    }
}
