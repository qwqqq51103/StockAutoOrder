package com.stockgame.server.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameAccountTest {

    @Test
    void buyFillRefundsPriceImprovementFromFrozenCash() {
        GameAccount account = GameAccount.builder()
                .availableCash(1_000.0)
                .availableStocks(0)
                .build();

        account.freezeCash(1_000.0);
        account.onBuyFilled(100, 8.0, 10.0);

        assertThat(account.getFrozenCash()).isZero();
        assertThat(account.getAvailableCash()).isEqualTo(200.0);
        assertThat(account.getAvailableStocks()).isEqualTo(100);
        assertThat(account.getAvgCostPrice()).isEqualTo(8.0);
    }

    @Test
    void averageCostIncludesFrozenStocks() {
        GameAccount account = GameAccount.builder()
                .availableCash(1_000.0)
                .availableStocks(100)
                .avgCostPrice(10.0)
                .build();

        account.freezeStocks(50);
        account.freezeCash(600.0);
        account.onBuyFilled(50, 12.0, 12.0);

        assertThat(account.getTotalStocks()).isEqualTo(150);
        assertThat(account.getAvgCostPrice()).isEqualTo(10.666666666666666);
    }
}
