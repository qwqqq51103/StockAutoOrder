package StockMainAction.model.strategy;

import StockMainAction.model.core.OrderSide;
import StockMainAction.model.core.OrderType;

public record OrderIntent(OrderSide side, OrderType type, int quantity, double price, String reason) {
    public OrderIntent {
        if (side == null || type == null) throw new IllegalArgumentException("side and type are required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (!Double.isFinite(price)) throw new IllegalArgumentException("price must be finite");
        if (type == OrderType.MARKET && price != 0) {
            throw new IllegalArgumentException("market intent price must be zero");
        }
        if (type != OrderType.MARKET && price <= 0) {
            throw new IllegalArgumentException("priced intent requires a positive price");
        }
        reason = reason == null ? "" : reason;
    }

    public static OrderIntent market(OrderSide side, int quantity, String reason) {
        return new OrderIntent(side, OrderType.MARKET, quantity, 0, reason);
    }

    public static OrderIntent limit(OrderSide side, int quantity, double price, String reason) {
        return new OrderIntent(side, OrderType.LIMIT, quantity, price, reason);
    }

    public static OrderIntent fok(OrderSide side, int quantity, double price, String reason) {
        return new OrderIntent(side, OrderType.FOK, quantity, price, reason);
    }
}
