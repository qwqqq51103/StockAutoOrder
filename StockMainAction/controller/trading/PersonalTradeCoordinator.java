package StockMainAction.controller.trading;

import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.ExecutionResult;
import StockMainAction.model.core.OrderSubmissionResult;
import java.util.Objects;

/** Converts UI trade commands into stable, presentation-neutral outcomes. */
public final class PersonalTradeCoordinator {
    private final StockMarketModel model;

    public PersonalTradeCoordinator(StockMarketModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    public TradeOutcome marketBuy(int quantity) {
        return immediate(model.executeMarketBuyResult(quantity));
    }

    public TradeOutcome marketSell(int quantity) {
        return immediate(model.executeMarketSellResult(quantity));
    }

    public TradeOutcome limitBuy(int quantity, double price) {
        return submitted(quantity, price, model.executeLimitBuyResult(quantity, price));
    }

    public TradeOutcome limitSell(int quantity, double price) {
        return submitted(quantity, price, model.executeLimitSellResult(quantity, price));
    }

    private static TradeOutcome immediate(ExecutionResult result) {
        TradeOutcomeStatus status = result.isFilled() ? TradeOutcomeStatus.FILLED
                : result.isPartiallyFilled() ? TradeOutcomeStatus.PARTIALLY_FILLED
                : TradeOutcomeStatus.REJECTED;
        return new TradeOutcome(status, result.requestedVolume(), result.filledVolume(),
                result.averagePrice(), result.totalValue(), null, result.failureReason());
    }

    private static TradeOutcome submitted(
            int quantity, double price, OrderSubmissionResult result) {
        return new TradeOutcome(result.accepted() ? TradeOutcomeStatus.SUBMITTED : TradeOutcomeStatus.REJECTED,
                quantity, 0, price, price * quantity, result.orderId(), result.failureReason());
    }
}
