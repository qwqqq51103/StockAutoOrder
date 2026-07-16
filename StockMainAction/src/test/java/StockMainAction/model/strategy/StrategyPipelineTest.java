package StockMainAction.model.strategy;

import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.OrderSide;
import StockMainAction.model.core.Trader;
import StockMainAction.model.user.UserAccount;
import org.junit.Test;

import static org.junit.Assert.*;

public class StrategyPipelineTest {
    @Test
    public void riskRejectionDoesNotMutateAccountOrBook() {
        OrderBook book = new OrderBook(null);
        TestTrader trader = new TestTrader(10, 0);
        OrderIntent intent = OrderIntent.limit(OrderSide.BUY, 2, 10, "test");

        StrategyExecutionResult result = StrategyPipeline.standard().execute(
                new TradingSignal(SignalAction.BUY, 1, "test"), intent, trader, book);

        assertFalse(result.accepted());
        assertEquals("insufficient funds", result.failureReason());
        assertEquals(1_000, trader.getAccount().snapshot().availableCashCents());
        assertTrue(book.snapshot().buys().isEmpty());
    }

    @Test
    public void approvedIntentCreatesReservedLimitOrder() {
        OrderBook book = new OrderBook(null);
        TestTrader trader = new TestTrader(100, 0);
        OrderIntent intent = OrderIntent.limit(OrderSide.BUY, 2, 10, "test");

        StrategyExecutionResult result = StrategyPipeline.standard().execute(
                new TradingSignal(SignalAction.BUY, 0.8, "test"), intent, trader, book);

        assertTrue(result.accepted());
        assertTrue(result.submission().accepted());
        assertEquals(2_000, trader.getAccount().snapshot().frozenCashCents());
        assertEquals(1, book.snapshot().buys().size());
    }

    private static final class TestTrader implements Trader {
        private final UserAccount account;
        private TestTrader(double cash, int stocks) { account = new UserAccount(cash, stocks); }
        @Override public UserAccount getAccount() { return account; }
        @Override public String getTraderType() { return "test"; }
        @Override public void updateAfterTransaction(String side, int volume, double price) { }
        @Override public void updateAverageCostPrice(String side, int volume, double price) { }
    }
}
