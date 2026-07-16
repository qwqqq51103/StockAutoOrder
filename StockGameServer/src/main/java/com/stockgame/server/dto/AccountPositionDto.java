package com.stockgame.server.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountPositionDto {
    private String symbol;
    private int availableQuantity;
    private int frozenQuantity;
    private int totalQuantity;
    private double avgCostPrice;
    private double lastPrice;
    private double marketValue;
    private double unrealizedPnl;
    private double allocationPct;
}
