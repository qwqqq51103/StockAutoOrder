package StockMainAction.view.main;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Chart update 的 bounded pending queue 與 backpressure helper。
 */
public final class PendingChartUpdateQueue<T> {
    private final int maxPending;
    private final ArrayDeque<T> pending = new ArrayDeque<>();

    public PendingChartUpdateQueue(int maxPending) {
        this.maxPending = Math.max(1, maxPending);
    }

    public void enqueue(T update, Runnable backpressureFlush) {
        while (true) {
            synchronized (pending) {
                if (pending.size() < maxPending) {
                    pending.addLast(update);
                    return;
                }
            }
            if (backpressureFlush == null) {
                throw new IllegalStateException("Pending chart update queue is full");
            }
            backpressureFlush.run();
        }
    }

    public List<T> drain() {
        synchronized (pending) {
            List<T> batch = new ArrayList<>(pending);
            pending.clear();
            return batch;
        }
    }

    public void clear() {
        synchronized (pending) {
            pending.clear();
        }
    }

    public int size() {
        synchronized (pending) {
            return pending.size();
        }
    }
}
