package StockMainAction.model.core;

import StockMainAction.model.user.UserAccount;
import org.junit.Test;

import static org.junit.Assert.*;

public class OrderValidationTest {
    private final Trader trader = new TestTrader();

    @Test
    public void validOrdersExposeTypedAndLegacyProperties() {
        Order order = Order.createLimitBuyOrder(100.5, 3, trader);

        assertEquals("buy", order.getType());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(OrderType.LIMIT, order.getOrderType());
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertEquals(3, order.getOriginalVolume());
        assertEquals(3, order.getVolume());

        order.setVolume(1);
        assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
        order.setVolume(0);
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    @Test
    public void invalidOrderInputsAreRejected() {
        assertIllegalArgument(() -> Order.createLimitBuyOrder(10, 0, trader));
        assertIllegalArgument(() -> Order.createLimitSellOrder(0, 1, trader));
        assertIllegalArgument(() -> Order.createLimitSellOrder(Double.NaN, 1, trader));
        assertIllegalArgument(() -> Order.createLimitSellOrder(Double.POSITIVE_INFINITY, 1, trader));
        assertIllegalArgument(() -> Order.createMarketBuyOrder(-1, trader));
        assertIllegalArgument(() -> new Order("hold", 10, 1, trader, false, false, false));
        assertIllegalArgument(() -> new Order("buy", 10, 1, trader, false, true, false));
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static final class TestTrader implements Trader {
        private final UserAccount account = new UserAccount(1_000, 10);

        @Override public UserAccount getAccount() { return account; }
        @Override public String getTraderType() { return "test"; }
        @Override public void updateAfterTransaction(String type, int volume, double price) { }
        @Override public void updateAverageCostPrice(String type, int volume, double price) { }
    }
}
