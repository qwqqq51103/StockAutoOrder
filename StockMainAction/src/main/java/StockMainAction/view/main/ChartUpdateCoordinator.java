package StockMainAction.view.main;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.jfree.chart.JFreeChart;

/** Owns chart invalidation for one window. */
public final class ChartUpdateCoordinator implements AutoCloseable {
    private final Set<JFreeChart> charts = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Timer timer;
    private final AtomicBoolean dispatchPending = new AtomicBoolean();
    private boolean dirty;
    private boolean closed;

    public ChartUpdateCoordinator(int delayMillis) {
        timer = new Timer(delayMillis, event -> flush());
        timer.setRepeats(false);
    }

    public void register(JFreeChart chart) {
        if (chart != null && !closed) charts.add(chart);
    }

    public void unregister(JFreeChart chart) {
        charts.remove(chart);
    }

    public void requestFlush() {
        if (closed) return;
        if (SwingUtilities.isEventDispatchThread()) {
            armTimer();
        } else if (dispatchPending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                dispatchPending.set(false);
                armTimer();
            });
        }
    }

    private void armTimer() {
            if (closed || charts.isEmpty()) return;
            dirty = true;
            timer.restart();
    }

    public void setDelay(int delayMillis) {
        runOnEdt(() -> {
            timer.setInitialDelay(delayMillis);
            timer.setDelay(delayMillis);
        });
    }

    public int registeredChartCount() {
        return charts.size();
    }

    private void flush() {
        if (!dirty || closed) return;
        dirty = false;
        for (JFreeChart chart : Set.copyOf(charts)) chart.fireChartChanged();
    }

    @Override
    public void close() {
        closed = true;
        dirty = false;
        charts.clear();
        runOnEdt(timer::stop);
    }

    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }
}
