package StockMainAction.model.strategy;

import StockMainAction.model.core.ExecutionResult;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.OrderSide;
import StockMainAction.model.core.OrderSubmissionResult;
import StockMainAction.model.core.Trader;

/** The only strategy-layer component that turns an approved intent into a command. */
public final class IntentExecutor {
    public StrategyExecutionResult execute(OrderIntent intent, Trader trader, OrderBook book) {
        return switch (intent.type()) {
            case MARKET -> immediate(intent.side() == OrderSide.BUY
                    ? book.marketBuy(trader, intent.quantity())
                    : book.marketSell(trader, intent.quantity()));
            case FOK -> immediate(intent.side() == OrderSide.BUY
                    ? book.submitFokBuyOrderResult(intent.price(), intent.quantity(), trader)
                    : book.submitFokSellOrderResult(intent.price(), intent.quantity(), trader));
            case LIMIT -> {
                Order order = intent.side() == OrderSide.BUY
                        ? Order.createLimitBuyOrder(intent.price(), intent.quantity(), trader)
                        : Order.createLimitSellOrder(intent.price(), intent.quantity(), trader);
                OrderSubmissionResult submission = intent.side() == OrderSide.BUY
                        ? book.submitBuyOrderResult(order, intent.price())
                        : book.submitSellOrderResult(order, intent.price());
                yield new StrategyExecutionResult(submission.accepted(), submission, null,
                        submission.failureReason());
            }
        };
    }

    private static StrategyExecutionResult immediate(ExecutionResult result) {
        return new StrategyExecutionResult(result.filledVolume() > 0, null, result,
                result.failureReason());
    }
}
