package StockMainAction.model.strategy;

import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Trader;
import java.util.Objects;

public final class StrategyPipeline {
    private static final StrategyPipeline STANDARD =
            new StrategyPipeline(new AccountRiskPolicy(), new IntentExecutor());

    private final RiskPolicy riskPolicy;
    private final IntentExecutor executor;

    public StrategyPipeline(RiskPolicy riskPolicy, IntentExecutor executor) {
        this.riskPolicy = Objects.requireNonNull(riskPolicy, "riskPolicy");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static StrategyPipeline standard() { return STANDARD; }

    public StrategyExecutionResult execute(
            TradingSignal signal, OrderIntent intent, Trader trader, OrderBook book) {
        RiskDecision decision = riskPolicy.assess(signal, intent, trader.getAccount().snapshot());
        if (!decision.approved()) {
            return new StrategyExecutionResult(false, null, null, decision.rejectionReason());
        }
        return executor.execute(decision.intent(), trader, book);
    }
}
