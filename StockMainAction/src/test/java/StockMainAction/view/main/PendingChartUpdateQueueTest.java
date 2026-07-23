package StockMainAction.view.main;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class PendingChartUpdateQueueTest {
    @Test
    public void enqueueAndDrainPreservesOrderAndClearsQueue() {
        PendingChartUpdateQueue<String> queue = new PendingChartUpdateQueue<>(10);

        queue.enqueue("a", null);
        queue.enqueue("b", null);

        assertEquals(2, queue.size());
        assertEquals(List.of("a", "b"), queue.drain());
        assertEquals(0, queue.size());
    }

    @Test
    public void enqueueInvokesBackpressureFlushWhenFull() {
        PendingChartUpdateQueue<String> queue = new PendingChartUpdateQueue<>(1);
        AtomicInteger flushCount = new AtomicInteger();

        queue.enqueue("a", null);
        queue.enqueue("b", () -> {
            flushCount.incrementAndGet();
            queue.drain();
        });

        assertEquals(1, flushCount.get());
        assertEquals(List.of("b"), queue.drain());
    }

    @Test
    public void clearEmptiesQueue() {
        PendingChartUpdateQueue<Integer> queue = new PendingChartUpdateQueue<>(2);
        queue.enqueue(1, null);

        queue.clear();

        assertEquals(0, queue.size());
    }
}
