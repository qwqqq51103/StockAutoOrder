package com.stockgame.server.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserAccountDto {
    private String username;
    private String role;
    private boolean enabled;
    private double availableCash;
    private double totalAssets;
    private int totalStocks;
    private double realizedPnl;
    private double unrealizedPnl;
    private double returnRate;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
