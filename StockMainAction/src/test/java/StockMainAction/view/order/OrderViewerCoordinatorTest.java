package StockMainAction.view.order;

import StockMainAction.controller.listeners.OrderBookListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrderViewerCoordinatorTest {
    @Test
    public void pausesFallbackWhenHiddenOrMinimizedAndRefreshesOnResume() throws Exception {
        FakeSource source = new FakeSource();
        AtomicInteger updates = new AtomicInteger();
        OrderViewerCoordinator coordinator = new OrderViewerCoordinator(
                source, snapshot -> updates.incrementAndGet(), 0, 10_000);

        SwingUtilities.invokeAndWait(() -> {
            assertFalse(coordinator.isFallbackTimerRunning());
            coordinator.setWindowActive(true);
            assertTrue(coordinator.isFallbackTimerRunning());
            assertEquals(1, updates.get());

            coordinator.setWindowActive(false);
            assertFalse(coordinator.isFallbackTimerRunning());

            coordinator.setWindowActive(true);
            assertTrue(coordinator.isFallbackTimerRunning());
            assertEquals(2, updates.get());
            coordinator.close();
        });
    }

    @Test
    public void closeIsIdempotentAndDropsLaterNotifications() throws Exception {
        FakeSource source = new FakeSource();
        AtomicInteger updates = new AtomicInteger();
        OrderViewerCoordinator coordinator = new OrderViewerCoordinator(
                source, snapshot -> updates.incrementAndGet(), 100, 10_000);

        SwingUtilities.invokeAndWait(() -> {
            coordinator.setWindowActive(true);
            source.fireUpdate();
            coordinator.close();
            coordinator.close();
            source.fireUpdate();
        });

        assertEquals(1, source.addCount);
        assertEquals(1, source.removeCount);
        assertEquals(1, updates.get());
        assertFalse(coordinator.isFallbackTimerRunning());
    }

    private static final class FakeSource implements OrderViewerCoordinator.SnapshotSource {
        private final OrderBookSnapshot snapshot = new OrderBookSnapshot(List.of(), List.of());
        private OrderBookListener listener;
        private int addCount;
        private int removeCount;

        @Override
        public OrderBookSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public void addListener(OrderBookListener listener) {
            this.listener = listener;
            addCount++;
        }

        @Override
        public void removeListener(OrderBookListener listener) {
            if (this.listener == listener) this.listener = null;
            removeCount++;
        }

        private void fireUpdate() {
            if (listener != null) listener.onOrderBookUpdated();
        }
    }
}
