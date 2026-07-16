package StockMainAction.model;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.Trader;
import StockMainAction.model.core.Transaction;
import StockMainAction.model.user.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.*;

public class StockMarketModelConcurrencyTest {
    @Test
    public void concurrentStartAndStopLeaveSimulationStopped() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        StockMarketModel model = new StockMarketModel(42L, clock);
        SwingUtilities.invokeAndWait(() -> { });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            futures.add(pool.submit(i % 2 == 0
                    ? model::startAutoPriceFluctuation
                    : model::stopAutoPriceFluctuation));
        }
        for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        model.stopAutoPriceFluctuation();
        assertFalse(model.isRunning());
        model.close();
    }

    @Test
    public void concurrentTransactionRecordingIsBoundedAndCallbacksUseEdt() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        StockMarketModel model = new StockMarketModel(42L, clock);
        TestTrader buyer = new TestTrader("buyer");
        TestTrader seller = new TestTrader("seller");
        Order buy = Order.createLimitBuyOrder(100, 1, buyer);
        Order sell = Order.createLimitSellOrder(100, 1, seller);
        CountDownLatch callbacks = new CountDownLatch(100);
        AtomicBoolean allOnEdt = new AtomicBoolean(true);
        model.addTransactionListener(transaction -> {
            allOnEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
            callbacks.countDown();
        });

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int id = i;
            futures.add(pool.submit(() -> model.addTransaction(new Transaction(
                    "T" + id, buy, sell, 100, 1, clock.millis()))));
        }
        for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(callbacks.await(10, TimeUnit.SECONDS));

        assertEquals(100, model.getTransactionHistory().size());
        assertTrue(allOnEdt.get());
        model.close();
    }

    @Test
    public void closedModelCannotRestart() {
        StockMarketModel model = new StockMarketModel(42L,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        model.close();

        try {
            model.startAutoPriceFluctuation();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("StockMarketModel is closed", expected.getMessage());
        }
        model.close();
    }

    private static final class TestTrader implements Trader {
        private final String type;
        private final UserAccount account = new UserAccount(1_000, 10);
        private TestTrader(String type) { this.type = type; }
        @Override public UserAccount getAccount() { return account; }
        @Override public String getTraderType() { return type; }
        @Override public void updateAfterTransaction(String side, int volume, double price) { }
        @Override public void updateAverageCostPrice(String side, int volume, double price) { }
    }
}
