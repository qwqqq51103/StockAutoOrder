package StockMainAction.model.core;

import StockMainAction.model.account.AccountSnapshot;
import StockMainAction.model.user.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.*;

public class OrderBookStressTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    public void deterministicHundredThousandTickSmokeTestHasNoAssetDrift() {
        OrderBook book = new OrderBook(null, FIXED_CLOCK);
        TestTrader buyer = new TestTrader("buyer", 20_000_000, 0);
        TestTrader seller = new TestTrader("seller", 0, 200_000);
        Totals before = totals(buyer, seller);
        Random random = new Random(20260716L);
        Stock stock = new Stock("T", 100, 0);

        for (int tick = 0; tick < 100_000; tick++) {
            double price = 90 + random.nextInt(41) * 0.5;
            book.submitSellOrder(Order.createLimitSellOrder(price, 1, seller), price);
            book.submitBuyOrder(Order.createLimitBuyOrder(price, 1, buyer), price);
            book.processOrders(stock);
            if (tick % 100 == 0) {
                assertNonNegative(buyer.getAccount().snapshot());
                assertNonNegative(seller.getAccount().snapshot());
                assertEquals(before, totals(buyer, seller));
            }
        }

        assertTrue(book.getBuyOrders().isEmpty());
        assertTrue(book.getSellOrders().isEmpty());
        assertEquals(0, buyer.getAccount().snapshot().frozenCashCents());
        assertEquals(0, seller.getAccount().snapshot().frozenStocks());
        assertEquals(before, totals(buyer, seller));
        book.close();
    }

    @Test
    public void concurrentSubmitCancelMatchAndSnapshotRemainConsistent() throws Exception {
        OrderBook book = new OrderBook(null, FIXED_CLOCK);
        TestTrader buyer = new TestTrader("buyer", 5_000_000, 0);
        TestTrader seller = new TestTrader("seller", 0, 50_000);
        Totals before = totals(buyer, seller);
        Stock stock = new Stock("T", 100, 0);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> submitter = pool.submit(() -> {
            await(start);
            for (int i = 0; i < 2_000; i++) {
                book.submitBuyOrder(Order.createLimitBuyOrder(100, 1, buyer), 100);
                book.submitSellOrder(Order.createLimitSellOrder(100, 1, seller), 100);
            }
        });
        Future<?> matcher = pool.submit(() -> {
            await(start);
            for (int i = 0; i < 2_000; i++) book.processOrders(stock);
        });
        Future<?> canceller = pool.submit(() -> {
            await(start);
            for (int i = 0; i < 2_000; i++) {
                List<Order> buys = book.getBuyOrders();
                if (!buys.isEmpty()) book.cancelOrder(buys.get(0).getId());
                List<Order> sells = book.getSellOrders();
                if (!sells.isEmpty()) book.cancelOrder(sells.get(0).getId());
            }
        });
        Future<?> reader = pool.submit(() -> {
            await(start);
            for (int i = 0; i < 4_000; i++) {
                OrderBookSnapshot snapshot = book.snapshot();
                snapshot.buys().forEach(order -> assertTrue(order.remainingVolume() > 0));
                snapshot.sells().forEach(order -> assertTrue(order.remainingVolume() > 0));
            }
        });

        start.countDown();
        submitter.get(30, TimeUnit.SECONDS);
        matcher.get(30, TimeUnit.SECONDS);
        canceller.get(30, TimeUnit.SECONDS);
        reader.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        book.processOrders(stock);
        book.getBuyOrders().forEach(order -> assertTrue(book.cancelOrder(order.getId())));
        book.getSellOrders().forEach(order -> assertTrue(book.cancelOrder(order.getId())));
        assertTrue(book.snapshot().buys().isEmpty());
        assertTrue(book.snapshot().sells().isEmpty());
        assertNonNegative(buyer.getAccount().snapshot());
        assertNonNegative(seller.getAccount().snapshot());
        assertEquals(0, buyer.getAccount().snapshot().frozenCashCents());
        assertEquals(0, seller.getAccount().snapshot().frozenStocks());
        assertEquals(before, totals(buyer, seller));
        book.close();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static void assertNonNegative(AccountSnapshot snapshot) {
        assertTrue(snapshot.availableCashCents() >= 0);
        assertTrue(snapshot.frozenCashCents() >= 0);
        assertTrue(snapshot.availableStocks() >= 0);
        assertTrue(snapshot.frozenStocks() >= 0);
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

        private TestTrader(String type, double cash, int stocks) {
            this.type = type;
            this.account = new UserAccount(cash, stocks);
        }

        @Override public UserAccount getAccount() { return account; }
        @Override public String getTraderType() { return type; }
        @Override public void updateAfterTransaction(String side, int volume, double price) { }
        @Override public void updateAverageCostPrice(String side, int volume, double price) { }
    }
}
