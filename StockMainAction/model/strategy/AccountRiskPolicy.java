package StockMainAction.model.strategy;

import StockMainAction.model.account.AccountSnapshot;
import StockMainAction.model.core.OrderSide;
import StockMainAction.model.core.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Side-effect-free balance and position gate. */
public final class AccountRiskPolicy implements RiskPolicy {
    @Override
    public RiskDecision assess(TradingSignal signal, OrderIntent intent, AccountSnapshot account) {
        if (signal.action() == SignalAction.HOLD) return RiskDecision.reject(intent, "hold signal");
        if ((signal.action() == SignalAction.BUY) != (intent.side() == OrderSide.BUY)) {
            return RiskDecision.reject(intent, "signal and intent sides differ");
        }
        if (intent.side() == OrderSide.SELL && account.availableStocks() < intent.quantity()) {
            return RiskDecision.reject(intent, "insufficient stocks");
        }
        if (intent.side() == OrderSide.BUY) {
            if (account.availableCashCents() <= 0) {
                return RiskDecision.reject(intent, "insufficient funds");
            }
            if (intent.type() != OrderType.MARKET) {
                long unitPrice = BigDecimal.valueOf(intent.price()).movePointRight(2)
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
                long required = Math.multiplyExact(unitPrice, intent.quantity());
                if (account.availableCashCents() < required) {
                    return RiskDecision.reject(intent, "insufficient funds");
                }
            }
        }
        return RiskDecision.approve(intent);
    }
}
