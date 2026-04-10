package com.stockgame.server.controller;

import com.stockgame.server.dto.CandleDto;
import com.stockgame.server.dto.MarketQuoteDto;
import com.stockgame.server.dto.OrderBookDto;
import com.stockgame.server.dto.TradeEventDto;
import com.stockgame.server.entity.StockTransaction;
import com.stockgame.server.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 市場行情 API（部分公開，不需登入）：
 *   GET /api/market/quote     — 即時報價快照
 *   GET /api/market/orderbook — 訂單簿（前 10 檔）
 *   GET /api/market/trades    — 最近 200 筆成交
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @GetMapping("/quote")
    public ResponseEntity<MarketQuoteDto> getQuote() {
        return ResponseEntity.ok(marketService.getQuote());
    }

    @GetMapping("/orderbook")
    public ResponseEntity<OrderBookDto> getOrderBook() {
        return ResponseEntity.ok(marketService.getOrderBook());
    }

    @GetMapping("/candles")
    public ResponseEntity<List<CandleDto>> getCandles() {
        return ResponseEntity.ok(marketService.getCandles());
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeEventDto>> getRecentTrades() {
        List<TradeEventDto> list = marketService.getRecentTrades().stream()
                .map(tx -> TradeEventDto.builder()
                        .transactionId(tx.getId())
                        .price(tx.getPrice())
                        .quantity(tx.getQuantity())
                        .amount(tx.getTotalAmount())
                        .buyerInitiated(tx.isBuyerInitiated())
                        .executedAt(tx.getExecutedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
