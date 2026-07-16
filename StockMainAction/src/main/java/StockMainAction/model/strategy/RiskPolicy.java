package StockMainAction.model.strategy;

import StockMainAction.model.account.AccountSnapshot;

@FunctionalInterface
public interface RiskPolicy {
    RiskDecision assess(TradingSignal signal, OrderIntent intent, AccountSnapshot account);
}
