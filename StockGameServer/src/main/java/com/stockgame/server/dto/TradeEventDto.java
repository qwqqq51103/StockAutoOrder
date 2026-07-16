package com.stockgame.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 成交逐筆推送（WebSocket /topic/market/trades） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeEventDto {
    private Long          transactionId;
    private double        price;
    private int           quantity;
    private double        amount;
    private boolean       buyerInitiated;
    private LocalDateTime executedAt;
}
