package com.stockgame.server.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockgame.server.entity.StockOrder;
import org.junit.jupiter.api.Test;

class ServerOrderBookTest {

    @Test
    void marketBuyCrossesSellBookAtAskPrice() {
        ServerOrderBook orderBook = new ServerOrderBook();
        StockOrder sellOrder = order(1L, StockOrder.Side.SELL, StockOrder.OrderType.LIMIT, 10.0, 100);
        StockOrder buyOrder = order(2L, StockOrder.Side.BUY, StockOrder.OrderType.MARKET, 10.0, 100);

        orderBook.addSellOrder(sellOrder);
        orderBook.addBuyOrder(buyOrder);

        ServerOrderBook.MatchResult result = orderBook.matchOrders();

        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).price()).isEqualTo(10.0);
        assertThat(result.events().get(0).quantity()).isEqualTo(100);
        assertThat(buyOrder.getStatus()).isEqualTo(StockOrder.Status.FILLED);
        assertThat(sellOrder.getStatus()).isEqualTo(StockOrder.Status.FILLED);
        assertThat(result.expiredOrders()).isEmpty();
    }

    @Test
    void marketBuyCancelsWhenAskIsAboveProtectionPrice() {
        ServerOrderBook orderBook = new ServerOrderBook();
        StockOrder sellOrder = order(1L, StockOrder.Side.SELL, StockOrder.OrderType.LIMIT, 11.0, 100);
        StockOrder buyOrder = order(2L, StockOrder.Side.BUY, StockOrder.OrderType.MARKET, 10.0, 100);

        orderBook.addSellOrder(sellOrder);
        orderBook.addBuyOrder(buyOrder);

        ServerOrderBook.MatchResult result = orderBook.matchOrders();

        assertThat(result.events()).isEmpty();
        assertThat(result.expiredOrders()).containsExactly(buyOrder);
        assertThat(buyOrder.getStatus()).isEqualTo(StockOrder.Status.CANCELLED);
        assertThat(sellOrder.getStatus()).isEqualTo(StockOrder.Status.PENDING);
    }

    private StockOrder order(Long id, StockOrder.Side side, StockOrder.OrderType type, double price, int quantity) {
        StockOrder order = StockOrder.builder()
                .side(side)
                .type(type)
                .price(price)
                .quantity(quantity)
                .build();
        order.setId(id);
        return order;
    }
}
