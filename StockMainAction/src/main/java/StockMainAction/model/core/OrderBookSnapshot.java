package StockMainAction.model.core;

import java.util.List;

public record OrderBookSnapshot(List<OrderSnapshot> buys, List<OrderSnapshot> sells) {
    public OrderBookSnapshot {
        buys = List.copyOf(buys);
        sells = List.copyOf(sells);
    }
}
