package StockMainAction.view.order;

import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.OrderSide;
import StockMainAction.model.core.OrderStatus;
import StockMainAction.model.core.OrderType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable, UI-facing snapshot of the active orders in an order book. */
public record OrderBookSnapshot(List<OrderRow> buys, List<OrderRow> sells) {
    public OrderBookSnapshot {
        buys = List.copyOf(buys);
        sells = List.copyOf(sells);
    }

    public List<OrderRow> allOrders() {
        return java.util.stream.Stream.concat(buys.stream(), sells.stream()).toList();
    }

    public List<OrderRow> personalOrders() {
        return allOrders().stream().filter(OrderRow::isPersonal).toList();
    }

    public static OrderBookSnapshot capture(OrderBook orderBook) {
        StockMainAction.model.core.OrderBookSnapshot coreSnapshot = orderBook.snapshot();
        Map<String, Long> timestamps = new HashMap<>();
        orderBook.getBuyOrders().forEach(order -> timestamps.put(order.getId(), order.getTimestamp()));
        orderBook.getSellOrders().forEach(order -> timestamps.put(order.getId(), order.getTimestamp()));
        long capturedAt = System.currentTimeMillis();
        return fromCore(coreSnapshot, timestamps, capturedAt);
    }

    static OrderBookSnapshot fromCore(StockMainAction.model.core.OrderBookSnapshot snapshot,
            Map<String, Long> timestamps, long capturedAtMillis) {
        List<OrderRow> buys = snapshot.buys().stream()
                .map(order -> OrderRow.from(order,
                        timestamps.getOrDefault(order.id(), capturedAtMillis)))
                .toList();
        List<OrderRow> sells = snapshot.sells().stream()
                .map(order -> OrderRow.from(order,
                        timestamps.getOrDefault(order.id(), capturedAtMillis)))
                .toList();
        return new OrderBookSnapshot(buys, sells);
    }

    public record OrderRow(String id, String traderType, OrderSide side, OrderType type,
            OrderStatus status, double price, int remainingVolume, long timestamp,
            long sequence) {
        private static OrderRow from(StockMainAction.model.core.OrderSnapshot order,
                long timestamp) {
            return new OrderRow(order.id(), order.traderType(), order.side(), order.type(),
                    order.status(), order.price(), order.remainingVolume(), timestamp,
                    order.sequence());
        }

        public boolean isPersonal() {
            return "PERSONAL".equalsIgnoreCase(traderType);
        }

        public boolean isCancellable() {
            return status == OrderStatus.NEW || status == OrderStatus.OPEN
                    || status == OrderStatus.PARTIALLY_FILLED;
        }
    }
}
