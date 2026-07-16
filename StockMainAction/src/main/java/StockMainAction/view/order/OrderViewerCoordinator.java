package StockMainAction.view.order;

import StockMainAction.controller.listeners.OrderBookListener;
import StockMainAction.model.core.OrderBook;
import StockMainAction.view.swing.SwingUpdateCoalescer;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Coordinates order-book notifications and the visible window's fallback refresh. */
public final class OrderViewerCoordinator implements OrderBookListener, AutoCloseable {
    static final int FALLBACK_DELAY_MILLIS = 5_000;

    interface SnapshotSource {
        OrderBookSnapshot snapshot();
        void addListener(OrderBookListener listener);
        void removeListener(OrderBookListener listener);
    }

    private final SnapshotSource source;
    private final Consumer<OrderBookSnapshot> snapshotConsumer;
    private final SwingUpdateCoalescer coalescer;
    private final Timer fallbackTimer;
    private boolean active;
    private boolean autoRefreshEnabled = true;
    private boolean closed;

    public OrderViewerCoordinator(OrderBook orderBook,
            Consumer<OrderBookSnapshot> snapshotConsumer) {
        this(new SnapshotSource() {
            @Override public OrderBookSnapshot snapshot() {
                return OrderBookSnapshot.capture(orderBook);
            }
            @Override public void addListener(OrderBookListener listener) {
                orderBook.addOrderBookListener(listener);
            }
            @Override public void removeListener(OrderBookListener listener) {
                orderBook.removeOrderBookListener(listener);
            }
        }, snapshotConsumer, 120, FALLBACK_DELAY_MILLIS);
    }

    OrderViewerCoordinator(SnapshotSource source, Consumer<OrderBookSnapshot> snapshotConsumer,
            int coalesceDelayMillis, int fallbackDelayMillis) {
        this.source = Objects.requireNonNull(source, "source");
        this.snapshotConsumer = Objects.requireNonNull(snapshotConsumer, "snapshotConsumer");
        this.coalescer = new SwingUpdateCoalescer(coalesceDelayMillis, this::performRefresh);
        this.fallbackTimer = new Timer(fallbackDelayMillis, event -> requestRefresh());
        this.fallbackTimer.setRepeats(true);
        source.addListener(this);
    }

    public void setWindowActive(boolean active) {
        runOnEdt(() -> {
            if (closed || this.active == active) return;
            this.active = active;
            updateTimerState();
            if (active) {
                refreshNow();
            }
        });
    }

    public void setAutoRefreshEnabled(boolean enabled) {
        runOnEdt(() -> {
            if (closed) return;
            autoRefreshEnabled = enabled;
            updateTimerState();
        });
    }

    public void refreshNow() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshNow);
            return;
        }
        if (!closed) coalescer.flush();
    }

    public void requestRefresh() {
        if (closed) return;
        if (!active) return;
        coalescer.request();
    }

    @Override public void onOrderBookUpdated() { requestRefresh(); }

    private void performRefresh() {
        if (closed) return;
        snapshotConsumer.accept(source.snapshot());
    }

    private void updateTimerState() {
        if (!closed && active && autoRefreshEnabled) fallbackTimer.start();
        else fallbackTimer.stop();
    }

    boolean isFallbackTimerRunning() { return fallbackTimer.isRunning(); }

    @Override
    public void close() {
        runOnEdt(() -> {
            if (closed) return;
            closed = true;
            fallbackTimer.stop();
            coalescer.close();
            source.removeListener(this);
        });
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
