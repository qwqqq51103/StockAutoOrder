package StockMainAction.controller.trading;

public record TradeOutcome(
        TradeOutcomeStatus status,
        int requestedQuantity,
        int filledQuantity,
        double averagePrice,
        double totalValue,
        String orderId,
        String reason) {

    public boolean successful() { return status != TradeOutcomeStatus.REJECTED; }
}
