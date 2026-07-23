package StockMainAction.model.game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RiskOrderManager {
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final List<RiskOrder> orders = new ArrayList<>();

    public synchronized RiskOrder addBracketOrder(double currentPrice, int holdings,
            int requestedQuantity, double stopLossPct, double takeProfitPct,
            double trailingPct, boolean oco) {
        if (holdings <= 0) throw new IllegalArgumentException("目前沒有可保護持股");
        int quantity = Math.min(Math.max(1, requestedQuantity), holdings);
        RiskOrder order = new RiskOrder("RISK-" + nextId.getAndIncrement(), quantity,
                currentPrice, stopLossPct, takeProfitPct, trailingPct, oco);
        orders.add(order);
        return order;
    }

    public synchronized List<RiskOrder.Trigger> evaluate(double currentPrice, int holdings) {
        List<RiskOrder.Trigger> triggers = new ArrayList<>();
        for (RiskOrder order : orders) {
            RiskOrder.Trigger trigger = order.check(currentPrice, holdings);
            if (trigger != null && trigger.quantity() > 0) {
                triggers.add(trigger);
                holdings -= trigger.quantity();
            }
        }
        return triggers;
    }

    public synchronized void cancelAll() {
        for (RiskOrder order : orders) {
            order.cancel();
        }
    }

    public synchronized List<RiskOrder> snapshot() {
        return new ArrayList<>(orders);
    }
}
