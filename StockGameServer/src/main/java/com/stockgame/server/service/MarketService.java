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
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketService {

    private final ServerOrderBook orderBook;
    private final ServerMatchingEngine matchingEngine;
    private final MarketSimulator simulator;
    private final StockTransactionRepository txRepository;
    private final MarketBroadcastService broadcast;
    private final CandleService candleService;

    @Value("${game.stock-symbol:DEMO}")
    private String symbol;

    @Value("${game.initial-price:100.0}")
    private double initialPrice;

    @Value("${game.tick-interval-ms:1000}")
    private long tickIntervalMs;

    private volatile boolean marketOpen = true;
    private double prevClosePrice;

    @PostConstruct
    public void init() {
        Double dbLastPrice = txRepository.findLatestPrice();
        prevClosePrice = dbLastPrice != null ? dbLastPrice : initialPrice;
        orderBook.setLastPrice(prevClosePrice);
        simulator.init(prevClosePrice);
        log.info("Market service initialized at price {}", prevClosePrice);
    }

    @Scheduled(fixedDelayString = "${game.tick-interval-ms:1000}")
    public void marketTick() {
        if (!marketOpen) {
            return;
        }

        try {
            runTickCycle(true);
        } catch (Exception e) {
            log.warn("Scheduled market tick failed: {}", e.getMessage(), e);
        }
    }

    public MarketQuoteDto getQuote() {
        return buildQuote();
    }

    public OrderBookDto getOrderBook() {
        return orderBook.toDto();
    }

    public List<CandleDto> getCandles() {
        return candleService.getHistory();
    }

    public List<StockTransaction> getRecentTrades() {
        return txRepository.findTop200ByOrderByExecutedAtDesc();
    }

    public double getLastPrice() {
        double price = orderBook.getLastPrice();
        return price > 0 ? price : prevClosePrice;
    }

    public long getTickIntervalMs() {
        return tickIntervalMs;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isMarketOpen() {
        return marketOpen;
    }

    public void setMarketOpen(boolean open) {
        this.marketOpen = open;
        broadcast.broadcastAnnouncement(open ? "Market opened by admin." : "Market paused by admin.");
        broadcastMarketSnapshot();
    }

    public void runAdminTick() {
        runTickCycle(true);
    }

    public void broadcastMarketSnapshot() {
        CandleDto latestCandle = candleService.getLatestCandle();
        if (latestCandle != null) {
            broadcast.broadcastCandle(latestCandle);
        }
        broadcast.broadcastQuote(buildQuote());
        broadcast.broadcastOrderBook(orderBook.toDto());
    }

    private MarketQuoteDto buildQuote() {
        double last = orderBook.getLastPrice() > 0 ? orderBook.getLastPrice() : prevClosePrice;
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
                .bestBidQty(orderBook.getBestBid().map(order -> order.getRemainingQuantity()).orElse(0))
                .bestAskQty(orderBook.getBestAsk().map(order -> order.getRemainingQuantity()).orElse(0))
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

    private void runTickCycle(boolean runSimulator) {
        if (runSimulator) {
            simulator.runAiTick();
        }

        List<StockTransaction> newTrades = matchingEngine.runMatchingCycle();

        if (!newTrades.isEmpty()) {
            newTrades.forEach(tx -> broadcast.broadcastTrade(toTradeDto(tx)));
        }

        long tickBuyVol = newTrades.stream()
                .filter(StockTransaction::isBuyerInitiated)
                .mapToLong(StockTransaction::getQuantity)
                .sum();
        long tickSellVol = newTrades.stream()
                .filter(tx -> !tx.isBuyerInitiated())
                .mapToLong(StockTransaction::getQuantity)
                .sum();
        double currentPrice = orderBook.getLastPrice() > 0 ? orderBook.getLastPrice() : prevClosePrice;
        candleService.recordTick(currentPrice, tickBuyVol + tickSellVol, tickBuyVol, tickSellVol);

        broadcastMarketSnapshot();
    }
}
