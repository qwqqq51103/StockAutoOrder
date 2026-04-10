package com.stockgame.server.service;

import com.stockgame.server.dto.CandleDto;
import com.stockgame.server.dto.MarketQuoteDto;
import com.stockgame.server.dto.OrderBookDto;
import com.stockgame.server.dto.TradeEventDto;
import com.stockgame.server.engine.MarketSimulator;
import com.stockgame.server.engine.ServerMatchingEngine;
import com.stockgame.server.engine.ServerOrderBook;
import com.stockgame.server.entity.StockTransaction;
import com.stockgame.server.repository.StockTransactionRepository;
import com.stockgame.server.websocket.MarketBroadcastService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 市場主循環服務。
 *
 * 每個 tick（預設 1 秒）執行：
 *   1. AI 決策 → 送出委託
 *   2. 撮合引擎 → 產生成交
 *   3. WebSocket 廣播行情快照、訂單簿、逐筆成交
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketService {

    private final ServerOrderBook            orderBook;
    private final ServerMatchingEngine       matchingEngine;
    private final MarketSimulator            simulator;
    private final StockTransactionRepository txRepository;
    private final MarketBroadcastService     broadcast;
    private final CandleService              candleService;

    @Value("${game.stock-symbol:DEMO}")
    private String symbol;

    @Value("${game.initial-price:100.0}")
    private double initialPrice;

    private volatile boolean marketOpen = true;
    private double prevClosePrice;

    @PostConstruct
    public void init() {
        // 嘗試從 DB 取最後成交價作為開盤參考價
        Double dbLastPrice = txRepository.findLatestPrice();
        prevClosePrice = dbLastPrice != null ? dbLastPrice : initialPrice;
        orderBook.setLastPrice(prevClosePrice);
        simulator.init(prevClosePrice);
        log.info("市場初始化完成，參考價：{}", prevClosePrice);
    }

    /**
     * 主市場 tick（每秒執行一次）。
     * fixedDelay 確保每輪結束後才開始計時，避免 tick 積壓。
     */
    @Scheduled(fixedDelayString = "${game.tick-interval-ms:1000}")
    public void marketTick() {
        if (!marketOpen) return;

        try {
            // 1. AI 下單
            simulator.runAiTick();

            // 2. 撮合
            List<StockTransaction> newTrades = matchingEngine.runMatchingCycle();

            // 3. 廣播逐筆成交
            if (!newTrades.isEmpty()) {
                newTrades.forEach(tx -> broadcast.broadcastTrade(toTradeDto(tx)));
            }

            // 4. K 線累積（每 tick 記錄一次）
            long tickBuyVol  = newTrades.stream().filter(StockTransaction::isBuyerInitiated)
                                        .mapToLong(StockTransaction::getQuantity).sum();
            long tickSellVol = newTrades.stream().filter(t -> !t.isBuyerInitiated())
                                        .mapToLong(StockTransaction::getQuantity).sum();
            double currentPrice = orderBook.getLastPrice() > 0 ? orderBook.getLastPrice() : prevClosePrice;
            candleService.recordTick(currentPrice, tickBuyVol + tickSellVol, tickBuyVol, tickSellVol);

            // 5. 廣播最新 K 線（讓前端即時更新）
            CandleDto latestCandle = candleService.getLatestCandle();
            if (latestCandle != null) {
                broadcast.broadcastCandle(latestCandle);
            }

            broadcast.broadcastQuote(buildQuote());
            broadcast.broadcastOrderBook(orderBook.toDto());

        } catch (Exception e) {
            log.warn("市場 tick 發生例外：{}", e.getMessage(), e);
        }
    }

    // ── 行情查詢 ─────────────────────────────────────────────────────────────

    public MarketQuoteDto        getQuote()        { return buildQuote(); }
    public OrderBookDto          getOrderBook()    { return orderBook.toDto(); }
    public List<CandleDto>       getCandles()      { return candleService.getHistory(); }

    public List<StockTransaction> getRecentTrades() {
        return txRepository.findTop200ByOrderByExecutedAtDesc();
    }

    public double getLastPrice() {
        double p = orderBook.getLastPrice();
        return p > 0 ? p : prevClosePrice;
    }

    public boolean isMarketOpen() { return marketOpen; }
    public void setMarketOpen(boolean open) { marketOpen = open; }

    // ── DTO 建構 ──────────────────────────────────────────────────────────────

    private MarketQuoteDto buildQuote() {
        double last   = orderBook.getLastPrice() > 0 ? orderBook.getLastPrice() : prevClosePrice;
        double change = last - prevClosePrice;
        double changePct = prevClosePrice > 0 ? change / prevClosePrice * 100 : 0;

        return MarketQuoteDto.builder()
                .symbol(symbol)
                .lastPrice(last)
                .openPrice(orderBook.getOpenPrice() > 0 ? orderBook.getOpenPrice() : prevClosePrice)
                .highPrice(orderBook.getHighPrice())
                .lowPrice(orderBook.getLowPrice())
                .totalVolume(orderBook.getTotalVolume())
                .totalAmount(orderBook.getTotalAmount())
                .change(change)
                .changePct(changePct)
                .bestBid(orderBook.getBestBidPrice())
                .bestAsk(orderBook.getBestAskPrice())
                .bestBidQty(orderBook.getBestBid().map(o -> o.getRemainingQuantity()).orElse(0))
                .bestAskQty(orderBook.getBestAsk().map(o -> o.getRemainingQuantity()).orElse(0))
                .buyVolume(orderBook.getBuyVolume())
                .sellVolume(orderBook.getSellVolume())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private TradeEventDto toTradeDto(StockTransaction tx) {
        return TradeEventDto.builder()
                .transactionId(tx.getId())
                .price(tx.getPrice())
                .quantity(tx.getQuantity())
                .amount(tx.getTotalAmount())
                .buyerInitiated(tx.isBuyerInitiated())
                .executedAt(tx.getExecutedAt())
                .build();
    }
}
