package StockMainAction.view.swing;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Coalesces frequent cross-thread UI requests into one EDT update. */
public final class SwingUpdateCoalescer implements AutoCloseable {
    private final Runnable update;
    private final Timer timer;
    private final AtomicBoolean dispatchPending = new AtomicBoolean();
    private volatile boolean closed;

    public SwingUpdateCoalescer(int delayMillis, Runnable update) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative");
        }
        this.update = Objects.requireNonNull(update, "update");
        this.timer = new Timer(delayMillis, event -> runUpdate());
        this.timer.setRepeats(false);
    }

    public void request() {
        if (closed) return;
        if (SwingUtilities.isEventDispatchThread()) {
            armTimer();
            return;
        }
        if (dispatchPending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                dispatchPending.set(false);
                armTimer();
            });
        }
    }

    public void flush() {
        if (closed) return;
        if (SwingUtilities.isEventDispatchThread()) {
            timer.stop();
            runUpdate();
        } else {
            SwingUtilities.invokeLater(this::flush);
        }
    }

    private void armTimer() {
        if (!closed) timer.restart();
    }

    private void runUpdate() {
        if (!closed) update.run();
    }

    @Override
    public void close() {
        closed = true;
        if (SwingUtilities.isEventDispatchThread()) timer.stop();
        else SwingUtilities.invokeLater(timer::stop);
    }
}
