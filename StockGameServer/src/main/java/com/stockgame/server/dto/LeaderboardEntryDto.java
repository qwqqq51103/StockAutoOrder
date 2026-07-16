package com.stockgame.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 排行榜單一條目 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDto {
    private int    rank;
    private String username;
    private double realizedPnl;
    private double availableCash;
    private int    totalStocks;
}
