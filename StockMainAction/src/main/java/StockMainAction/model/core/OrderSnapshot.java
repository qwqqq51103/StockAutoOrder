package StockMainAction.model.core;

public record OrderSnapshot(
        String id,
        OrderSide side,
        OrderType type,
        OrderStatus status,
        double price,
        int originalVolume,
        int remainingVolume,
        long sequence,
        String traderType) { }
