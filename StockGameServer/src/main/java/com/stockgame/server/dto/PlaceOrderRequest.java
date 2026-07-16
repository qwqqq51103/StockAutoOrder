package com.stockgame.server.dto;

import com.stockgame.server.entity.StockOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlaceOrderRequest {

    @NotNull
    private StockOrder.Side side;       // BUY / SELL

    @NotNull
    private StockOrder.OrderType type;  // LIMIT / MARKET / FOK

    /** 限價委託價格（市價單填 0 即可） */
    private double price;

    @NotNull @Min(1)
    private Integer quantity;
}
