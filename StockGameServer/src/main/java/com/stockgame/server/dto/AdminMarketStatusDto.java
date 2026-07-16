package com.stockgame.server.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminMarketStatusDto {
    private boolean marketOpen;
    private String symbol;
    private double lastPrice;
    private double bestBid;
    private double bestAsk;
    private int totalVolume;
    private double totalAmount;
    private long buyVolume;
    private long sellVolume;
    private long tickIntervalMs;
    private int aiRetailCount;
    private int aiNoiseCount;
    private long accountCount;
}
