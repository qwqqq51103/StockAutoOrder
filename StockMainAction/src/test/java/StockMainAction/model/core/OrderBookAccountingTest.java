package StockMainAction.model.core;

import StockMainAction.model.account.AccountSnapshot;
import StockMainAction.model.user.UserAccount;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class OrderBookAccountingTest {
    @Test
    public void limitMatchRefundsPriceImprovementAndConservesAssets() {
        OrderBook book = new OrderBook(null);
        TestTrader buyer = new TestTrader("buyer", 2_000, 0);
        TestTrader seller = new TestTrader("seller", 0, 20);
        Totals before = totals(buyer, seller);

        book.submitSellOrder(Order.createLimitSellOrder(100, 6, seller), 100);
        book.submitBuyOrder(Order.createLimitBuyOrder(110, 10, buyer), 100);
        book.processOrders(new Stock("T", 100, 0));

        assertEquals(6, buyer.getAccount().getStockInventory());
        assertEquals(960.0, buyer.getAccount().getAvailableFunds(), 0.001);
        assertEquals(440.0, buyer.getAccount().getFrozenFunds(), 0.001);
        assertEquals(600.0, seller.getAccount().getAvailableFunds(), 0.001);
        assertEquals(14, seller.getAccount().getStockInventory());
        assertEquals(4, book.getBuyOrders().get(0).getVolume());
        assertTrue(book.getSellOrders().isEmpty());
        assertEquals(before, totals(buyer, seller));
    }

    @Test
    public void samePriceUsesFifoSequence() {
        OrderBook book = new OrderBook(null);
        TestTrader first = new TestTrader("first", 0, 1);
        TestTrader second = new TestTrader("second", 0, 1);
        TestTrader buyer = new TestTrader("buyer", 500, 0);
        List<String> sellers = new ArrayList<>();
        book.addTradeExecutedListener(event -> sellers.add(event.sellerType()));

        book.submitSellOrder(Order.createLimitSellOrder(100, 1, first), 100);
        book.submitSellOrder(Order.createLimitSellOrder(100, 1, second), 100);
        ExecutionResult result = book.marketBuy(buyer, 2);

        assertTrue(result.isFilled());
        assertEquals(List.of("first", "second"), sellers);
    }

    @Test
    public void cancellationReleasesExactRemainingReservation() {
        OrderBook book = new OrderBook(null);
        TestTrader buyer = new TestTrader("buyer", 1_000, 0);
        TestTrader seller = new TestTrader("seller", 0, 3);
        Order buy = Order.createLimitBuyOrder(100, 5, buyer);
        book.submitBuyOrder(buy, 100);
        book.submitSellOrder(Order.createLimitSellOrder(90, 3, seller), 100);
        book.processOrders(new Stock("T", 100, 0));

        assertEquals(200.0, buyer.getAccount().getFrozenFunds(), 0.001);
        assertTrue(book.cancelOrder(buy.getId()));
        assertEquals(700.0, buyer.getAccount().getAvailableFunds(), 0.001);
        assertEquals(0.0, buyer.getAccount().getFrozenFunds(), 0.001);
        assertEquals(OrderStatus.CANCELLED, buy.getStatus());
        assertFalse(book.cancelOrder(buy.getId()));
    }

    @Test
    public void queriedOrdersAreDetachedFromFutureBookMutations() {
        OrderBook book = new OrderBook(null);
        TestTrader buyer = new TestTrader("buyer", 1_000, 0);
        TestTrader seller = new TestTrader("seller", 0, 2);
        book.submitBuyOrder(Order.createLimitBuyOrder(100, 5, buyer), 100);

        Order queriedBeforeFill = book.getBuyOrders().get(0);
        book.submitSellOrder(Order.createLimitSellOrder(100, 2, seller), 100);
        book.processOrders(new Stock("T", 100, 0));

        assertEquals(5, queriedBeforeFill.getVolume());
        assertEquals(OrderStatus.OPEN, queriedBeforeFill.getStatus());
        assertEquals(3, book.getBuyOrders().get(0).getVolume());
        assertEquals(OrderStatus.PARTIALLY_FILLED, book.getBuyOrders().get(0).getStatus());
    }

    @Test
    public void marketOrderReturnsActualPartialFill() {
        OrderBook book = new OrderBook(null);
        TestTrader buyer = new TestTrader("buyer", 1_000, 0);
        TestTrader seller = new TestTrader("seller", 0, 4);
        Totals before = totals(buyer, seller);
        book.submitSellOrder(Order.createLimitSellOrder(100, 4, seller), 100);

        ExecutionResult result = book.marketBuy(buyer, 10);

        assertTrue(result.isPartiallyFilled());
        assertEquals(4, result.filledVolume());
        assertEquals(100.0, result.averagePrice(), 0.001);
        assertEquals("insufficient sell liquidity", result.failureReason());
        assertEquals(before, totals(buyer, seller));
    }

    @Test
    public void marketBuyStopsAtSlippageLimitAndReturnsActualFill() {
        OrderBook book = new OrderBook(null);
        book.setMaxMarketSlippageRatio(0.05);
        TestTrader buyer = new TestTrader("buyer", 1_000, 0);
        TestTrader nearSeller = new TestTrader("near-seller", 0, 1);
        TestTrader farSeller = new TestTrader("far-seller", 0, 2);
        Totals before = totals(buyer, nearSeller, farSeller);

        book.submitSellOrder(Order.createLimitSellOrder(100, 1, nearSeller), 100);
        book.submitSellOrder(Order.createLimitSellOrder(120, 2, farSeller), 100);

        ExecutionResult result = book.marketBuy(buyer, 3);

        assertTrue(result.isPartiallyFilled());
        assertEquals(1, result.filledVolume());
        assertEquals("slippage limit reached", result.failureReason());
        assertEquals(1, buyer.getAccount().getStockInventory());
        assertEquals(2, book.getSellOrders().get(0).getVolume());
        assertEquals(120.0, book.getSellOrders().get(0).getPrice(), 0.001);
        assertEquals(before, totals(buyer, nearSeller, farSeller));
    }

    @Test
    public void marketSellStopsAtSlippageLimitAndReturnsActualFill() {
        OrderBook book = new OrderBook(null);
        book.setMaxMarketSlippageRatio(0.05);
        TestTrader seller = new TestTrader("seller", 0, 3);
        TestTrader nearBuyer = new TestTrader("near-buyer", 500, 0);
        TestTrader farBuyer = new TestTrader("far-buyer", 500, 0);
        Totals before = totals(seller, nearBuyer, farBuyer);

        book.submitBuyOrder(Order.createLimitBuyOrder(100, 1, nearBuyer), 100);
        book.submitBuyOrder(Order.createLimitBuyOrder(80, 2, farBuyer), 100);

        ExecutionResult result = book.marketSell(seller, 3);

        assertTrue(result.isPartiallyFilled());
        assertEquals(1, result.filledVolume());
        assertEquals("slippage limit reached", result.failureReason());
        assertEquals(2, seller.getAccount().getStockInventory());
        assertEquals(2, book.getBuyOrders().get(0).getVolume());
        assertEquals(80.0, book.getBuyOrders().get(0).getPrice(), 0.001);
        assertEquals(before, totals(seller, nearBuyer, farBuyer));
    }

    @Test
    public void insufficientFokChangesNothing() {
        OrderBook book = new OrderBook(null);
        TestTrader buyer = new TestTrader("buyer", 2_000, 0);
        TestTrader seller = new TestTrader("seller", 0, 4);
        book.submitSellOrder(Order.createLimitSellOrder(100, 4, seller), 100);
        AccountSnapshot buyerBefore = buyer.getAccount().snapshot();
        AccountSnapshot sellerBefore = seller.getAccount().snapshot();
        OrderBookSnapshot bookBefore = book.snapshot();

        assertFalse(book.submitFokBuyOrder(100, 5, buyer));

        assertEquals(buyerBefore, buyer.getAccount().snapshot());
        assertEquals(sellerBefore, seller.getAccount().snapshot());
        assertEquals(bookBefore, book.snapshot());
    }

    @Test
    public void completeFokConsumesAllAndConservesAssets() {
        OrderBook book = new OrderBook(null);
        TestTrader buyer = new TestTrader("buyer", 2_000, 0);
        TestTrader sellerA = new TestTrader("seller-a", 0, 2);
        TestTrader sellerB = new TestTrader("seller-b", 0, 3);
        Totals before = totals(buyer, sellerA, sellerB);
        book.submitSellOrder(Order.createLimitSellOrder(99, 2, sellerA), 100);
        book.submitSellOrder(Order.createLimitSellOrder(100, 3, sellerB), 100);

        assertTrue(book.submitFokBuyOrder(100, 5, buyer));

        assertTrue(book.getSellOrders().isEmpty());
        assertEquals(5, buyer.getAccount().getStockInventory());
        assertEquals(1_502.0, buyer.getAccount().getAvailableFunds(), 0.001);
        assertEquals(0.0, buyer.getAccount().getFrozenFunds(), 0.001);
        assertEquals(before, totals(buyer, sellerA, sellerB));
    }

    private static Totals totals(TestTrader... traders) {
        long cash = 0;
        int stocks = 0;
        for (TestTrader trader : traders) {
            AccountSnapshot snapshot = trader.getAccount().snapshot();
            cash += snapshot.totalCashCents();
            stocks += snapshot.totalStocks();
        }
        return new Totals(cash, stocks);
    }

    private record Totals(long cashCents, int stocks) { }

    private static final class TestTrader implements Trader {
        private final String type;
        private final UserAccount account;

        private TestTrader(String type, double funds, int stocks) {
            this.type = type;
            this.account = new UserAccount(funds, stocks);
        }

        @Override public UserAccount getAccount() { return account; }
        @Override public String getTraderType() { return type; }
        @Override public void updateAfterTransaction(String side, int volume, double price) { }
        @Override public void updateAverageCostPrice(String side, int volume, double price) { }
    }
}
