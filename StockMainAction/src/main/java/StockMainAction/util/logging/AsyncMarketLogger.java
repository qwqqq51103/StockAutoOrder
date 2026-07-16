package StockMainAction.util.logging;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, daemon-backed logging that never blocks or fails a caller. */
public final class AsyncMarketLogger {
    private final BlockingQueue<Entry> queue;
    private final AtomicLong droppedCount = new AtomicLong();
    private final MarketLogger delegate;

    public AsyncMarketLogger(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        queue = new ArrayBlockingQueue<>(capacity);
        delegate = MarketLogger.getInstance();
        Thread worker = new Thread(this::drain, "market-log-writer");
        worker.setDaemon(true);
        worker.start();
    }

    public void warn(String message, String category) {
        if (!queue.offer(new Entry(message, category))) droppedCount.incrementAndGet();
    }

    public long droppedCount() {
        return droppedCount.get();
    }

    private void drain() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Entry entry = queue.take();
                try {
                    delegate.warn(entry.message(), entry.category());
                } catch (RuntimeException ex) {
                    droppedCount.incrementAndGet();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record Entry(String message, String category) { }
}
