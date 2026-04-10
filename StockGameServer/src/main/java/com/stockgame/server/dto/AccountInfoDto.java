package com.stockgame.server.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountInfoDto {
    private String username;
    private double availableCash;
    private double frozenCash;
    private double totalCash;
    private int    availableStocks;
    private int    frozenStocks;
    private int    totalStocks;
    private double avgCostPrice;
    private double realizedPnl;
    private double unrealizedPnl;   // (currentPrice - avgCost) * totalStocks
    private double totalPnl;
    private double totalAssets;     // cash + stocks * currentPrice
    private double initialCash;     // 初始資金
    private double returnRate;      // 報酬率 %
}
