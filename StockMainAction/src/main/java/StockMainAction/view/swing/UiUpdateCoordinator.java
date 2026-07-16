package StockMainAction.view.swing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Coalesces keyed UI work into one bounded EDT batch. */
public final class UiUpdateCoordinator implements AutoCloseable {
    private final Object lock = new Object();
    private final Map<String, Runnable> pending = new LinkedHashMap<>();
    private final Timer timer;
    private final AtomicBoolean dispatchPending = new AtomicBoolean();
    private volatile boolean closed;

    public UiUpdateCoordinator(int delayMillis) {
        if (delayMillis < 0) throw new IllegalArgumentException("delayMillis must be >= 0");
        timer = new Timer(delayMillis, event -> flushOnEdt());
        timer.setRepeats(false);
    }

    public void submit(String key, Runnable update) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(update, "update");
        synchronized (lock) {
            if (closed) return;
            pending.put(key, update);
        }
        armTimer();
    }

    public int pendingCount() {
        synchronized (lock) {
            return pending.size();
        }
    }

    public void flush() {
        runOnEdt(this::flushOnEdt);
    }

    private void flushOnEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::flushOnEdt);
            return;
        }
        Map<String, Runnable> batch;
        synchronized (lock) {
            if (closed || pending.isEmpty()) return;
            batch = new LinkedHashMap<>(pending);
            pending.clear();
        }
        batch.values().forEach(Runnable::run);
    }

    private void armTimer() {
        if (closed) return;
        if (SwingUtilities.isEventDispatchThread()) {
            timer.restart();
            return;
        }
        if (dispatchPending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                dispatchPending.set(false);
                if (!closed) timer.restart();
            });
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            pending.clear();
        }
        runOnEdt(timer::stop);
    }

    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }
}
