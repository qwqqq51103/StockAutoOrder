package StockMainAction.model.strategy;

public record RiskDecision(boolean approved, OrderIntent intent, String rejectionReason) {
    public static RiskDecision approve(OrderIntent intent) {
        return new RiskDecision(true, intent, null);
    }

    public static RiskDecision reject(OrderIntent intent, String reason) {
        return new RiskDecision(false, intent, reason);
    }
}
