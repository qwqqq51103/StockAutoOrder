package StockMainAction.view.swing;

import static org.junit.Assert.assertEquals;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class UiUpdateCoordinatorTest {
    @Test
    public void keepsOnlyLatestUpdatePerKeyAndFlushesOnEdt() throws Exception {
        UiUpdateCoordinator coordinator = new UiUpdateCoordinator(1_000);
        AtomicInteger value = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        coordinator.submit("price", () -> { value.set(1); executions.incrementAndGet(); });
        coordinator.submit("price", () -> { value.set(2); executions.incrementAndGet(); });
        SwingUtilities.invokeAndWait(coordinator::flush);
        assertEquals(2, value.get());
        assertEquals(1, executions.get());
        assertEquals(0, coordinator.pendingCount());
        coordinator.close();
    }

    @Test
    public void closeDropsPendingWork() throws Exception {
        UiUpdateCoordinator coordinator = new UiUpdateCoordinator(1_000);
        AtomicInteger executions = new AtomicInteger();
        coordinator.submit("state", executions::incrementAndGet);
        coordinator.close();
        SwingUtilities.invokeAndWait(coordinator::flush);
        assertEquals(0, executions.get());
    }

    @Test
    public void highFrequencySubmissionRemainsBoundedAndUsesLatestValues() throws Exception {
        UiUpdateCoordinator coordinator = new UiUpdateCoordinator(1_000);
        AtomicInteger latest = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        for (int index = 0; index < 100_000; index++) {
            int value = index;
            coordinator.submit("price", () -> { latest.set(value); executions.incrementAndGet(); });
            coordinator.submit("volume", () -> executions.incrementAndGet());
        }
        assertEquals(2, coordinator.pendingCount());
        SwingUtilities.invokeAndWait(coordinator::flush);
        assertEquals(99_999, latest.get());
        assertEquals(2, executions.get());
        coordinator.close();
    }
}
