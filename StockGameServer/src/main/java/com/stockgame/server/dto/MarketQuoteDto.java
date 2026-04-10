package com.stockgame.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 行情快照（每 tick 廣播） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketQuoteDto {
    private String        symbol;
    private double        lastPrice;
    private double        openPrice;
    private double        highPrice;
    private double        lowPrice;
    private int           totalVolume;
    private double        totalAmount;
    private double        change;        // 價格變動
    private double        changePct;     // 漲跌幅 %
    private double        bestBid;       // 買一
    private double        bestAsk;       // 賣一
    private int           bestBidQty;
    private int           bestAskQty;
    private long          buyVolume;     // 累積主動買量（外盤）
    private long          sellVolume;    // 累積主動賣量（內盤）
    private LocalDateTime timestamp;
}
