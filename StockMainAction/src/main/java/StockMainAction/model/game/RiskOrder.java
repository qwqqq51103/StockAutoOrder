package StockMainAction.model.game;

public final class RiskOrder {
    private final String id;
    private final int quantity;
    private final double stopLossPrice;
    private final double takeProfitPrice;
    private final double trailingPercent;
    private final boolean oco;
    private double highestPrice;
    private boolean active = true;
    private String triggerReason = "";

    public RiskOrder(String id, int quantity, double entryPrice, double stopLossPct,
            double takeProfitPct, double trailingPercent, boolean oco) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (!Double.isFinite(entryPrice) || entryPrice <= 0) {
            throw new IllegalArgumentException("entryPrice must be positive");
        }
        this.id = id;
        this.quantity = quantity;
        this.stopLossPrice = stopLossPct > 0 ? entryPrice * (1.0 - stopLossPct / 100.0) : 0.0;
        this.takeProfitPrice = takeProfitPct > 0 ? entryPrice * (1.0 + takeProfitPct / 100.0) : 0.0;
        this.trailingPercent = Math.max(0.0, trailingPercent);
        this.oco = oco;
        this.highestPrice = entryPrice;
    }

    Trigger check(double currentPrice, int holdings) {
        if (!active || holdings <= 0) return null;
        highestPrice = Math.max(highestPrice, currentPrice);
        String reason = null;
        if (stopLossPrice > 0 && currentPrice <= stopLossPrice) {
            reason = "停損";
        }
        if (reason == null && trailingPercent > 0) {
            double trailingStop = highestPrice * (1.0 - trailingPercent / 100.0);
            if (currentPrice <= trailingStop) {
                reason = "移動停損";
            }
        }
        if (reason == null && takeProfitPrice > 0 && currentPrice >= takeProfitPrice) {
            reason = "停利";
        }
        if (reason == null) return null;
        active = false;
        triggerReason = reason;
        int sellQuantity = Math.min(quantity, holdings);
        return new Trigger(id, reason, sellQuantity, currentPrice);
    }

    public void cancel() {
        active = false;
        triggerReason = "手動取消";
    }

    public String getId() { return id; }
    public int getQuantity() { return quantity; }
    public double getStopLossPrice() { return stopLossPrice; }
    public double getTakeProfitPrice() { return takeProfitPrice; }
    public double getTrailingPercent() { return trailingPercent; }
    public double getHighestPrice() { return highestPrice; }
    public boolean isOco() { return oco; }
    public boolean isActive() { return active; }
    public String getTriggerReason() { return triggerReason; }

    public String displayText() {
        String state = active ? "啟用" : "關閉";
        return String.format("%s %s：%d股 停損 %.2f / 停利 %.2f / 移動 %.1f%%",
                state, id, quantity, stopLossPrice, takeProfitPrice, trailingPercent);
    }

    public record Trigger(String id, String reason, int quantity, double price) { }
}
