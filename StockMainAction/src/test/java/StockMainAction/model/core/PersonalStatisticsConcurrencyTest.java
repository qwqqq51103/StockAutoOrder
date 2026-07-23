package StockMainAction.model.core;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import StockMainAction.model.user.UserAccount;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class PersonalStatisticsConcurrencyTest {

    @Test
    public void concurrentTradeWritesAndUiReadsDoNotThrowConcurrentModification() throws Exception {
        PersonalStatistics stats = new PersonalStatistics(new UserAccount(1_000_000, 1_000), null, 1_000_000);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            await(start, failure);
            for (int i = 0; i < 2_000 && failure.get() == null; i++) {
                stats.addTradeRecord("買入", 1, 10.0 + (i % 5));
                stats.addTradeRecord("賣出", 1, 10.5 + (i % 5));
            }
        });

        executor.submit(() -> {
            await(start, failure);
            for (int i = 0; i < 2_000 && failure.get() == null; i++) {
                try {
                    stats.getPayoffRatio();
                    stats.getAverageHoldingMinutes();
                    stats.getTradesByPeriod(PersonalStatistics.StatsPeriod.ALL_TIME);
                    stats.getTradeHistory();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        });

        start.countDown();
        executor.shutdown();
        assertTrue("Concurrent stats test timed out", executor.awaitTermination(10, TimeUnit.SECONDS));
        assertNull(failure.get());
    }

    private static void await(CountDownLatch latch, AtomicReference<Throwable> failure) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, e);
        }
    }
}
