package StockMainAction.view.swing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SwingUpdateCoalescerTest {
    @Test
    public void coalescesFrequentRequestsOnEdt() throws Exception {
        AtomicInteger updates = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);
        SwingUpdateCoalescer coalescer = new SwingUpdateCoalescer(30, () -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            updates.incrementAndGet();
            completed.countDown();
        });

        for (int i = 0; i < 100; i++) coalescer.request();

        assertTrue(completed.await(2, TimeUnit.SECONDS));
        assertEquals(1, updates.get());
        coalescer.close();
    }

    @Test
    public void closeDropsPendingUpdate() throws Exception {
        AtomicInteger updates = new AtomicInteger();
        SwingUpdateCoalescer coalescer = new SwingUpdateCoalescer(100, updates::incrementAndGet);
        SwingUtilities.invokeAndWait(() -> {
            coalescer.request();
            coalescer.close();
        });
        Thread.sleep(150);

        assertEquals(0, updates.get());
    }
}
