package com.stockgame.server.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountInfoDto {
    private String username;
    private String marketSymbol;
    private double availableCash;
    private double frozenCash;
    private double totalCash;
    private int availableStocks;
    private int frozenStocks;
    private int totalStocks;
    private double avgCostPrice;
    private double realizedPnl;
    private double unrealizedPnl;
    private double totalPnl;
    private double totalAssets;
    private double initialCash;
    private double returnRate;
    private List<AccountPositionDto> positions;
}
