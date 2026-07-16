package StockMainAction.model.core;

import java.util.List;

/** Immutable pre-commit plan. Mutable orders never escape the matching package. */
final class ExecutionPlan {
    private final OrderSide aggressorSide;
    private final OrderType orderType;
    private final int requestedQuantity;
    private final List<Fill> fills;
    private final String rejectionReason;

    ExecutionPlan(OrderSide aggressorSide, OrderType orderType, int requestedQuantity,
            List<Fill> fills, String rejectionReason) {
        this.aggressorSide = aggressorSide;
        this.orderType = orderType;
        this.requestedQuantity = requestedQuantity;
        this.fills = List.copyOf(fills);
        this.rejectionReason = rejectionReason;
    }

    OrderSide aggressorSide() { return aggressorSide; }
    OrderType orderType() { return orderType; }
    int requestedQuantity() { return requestedQuantity; }
    List<Fill> fills() { return fills; }
    String rejectionReason() { return rejectionReason; }
    int plannedQuantity() { return fills.stream().mapToInt(Fill::quantity).sum(); }
    boolean isComplete() { return rejectionReason == null && plannedQuantity() == requestedQuantity; }

    record Fill(Order restingOrder, int quantity, double executionPrice) {
        Fill {
            if (restingOrder == null || quantity <= 0
                    || !Double.isFinite(executionPrice) || executionPrice <= 0) {
                throw new IllegalArgumentException("Invalid execution plan fill");
            }
        }
    }
}
