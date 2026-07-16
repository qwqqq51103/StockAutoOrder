package StockMainAction.model.strategy;

public record TradingSignal(SignalAction action, double confidence, String reason) {
    public TradingSignal {
        if (action == null) throw new IllegalArgumentException("action is required");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        reason = reason == null ? "" : reason;
    }
}
