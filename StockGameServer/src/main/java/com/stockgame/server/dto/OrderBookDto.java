package com.stockgame.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 訂單簿快照（前 10 檔買賣） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookDto {

    private List<Level> bids;   // 買方，價高在前
    private List<Level> asks;   // 賣方，價低在前

    @Data
    @AllArgsConstructor
    public static class Level {
        private double price;
        private int    quantity;
        private int    orderCount;
    }
}
