package StockMainAction.model.core;

public record TradeExecuted(
        String id,
        String buyOrderId,
        String sellOrderId,
        String buyerType,
        String sellerType,
        double price,
        int volume,
        boolean buyerInitiated,
        OrderType orderType,
        long timestamp) { }
