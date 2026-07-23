package StockMainAction.view.main;

import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MainViewChartLifecycleTest {
    @Test
    public void backgroundIndicatorUpdateMutatesSeriesAndFlushesOnEdt() throws Exception {
        XYSeries first = new XYSeries("first");
        XYSeries second = new XYSeries("second");
        XYSeries third = new XYSeries("third");
        CountDownLatch flushed = new CountDownLatch(1);
        AtomicBoolean flushWasOnEdt = new AtomicBoolean(false);

        MainViewChartLifecycle.updateIndicatorTripleOnEdt(
                7,
                1.5,
                2.5,
                3.5,
                first,
                second,
                third,
                10,
                () -> {
                    flushWasOnEdt.set(SwingUtilities.isEventDispatchThread());
                    flushed.countDown();
                },
                ex -> { throw new AssertionError(ex); });

        assertTrue("indicator update did not flush", flushed.await(2, TimeUnit.SECONDS));
        flushEdt();

        assertTrue(flushWasOnEdt.get());
        assertEquals(1, first.getItemCount());
        assertEquals(7.0, first.getX(0).doubleValue(), 0.001);
        assertEquals(1.5, first.getY(0).doubleValue(), 0.001);
        assertEquals(2.5, second.getY(0).doubleValue(), 0.001);
        assertEquals(3.5, third.getY(0).doubleValue(), 0.001);
    }

    @Test
    public void invalidIndicatorSampleDoesNotWriteOrFlush() throws Exception {
        XYSeries first = new XYSeries("first");
        XYSeries second = new XYSeries("second");
        XYSeries third = new XYSeries("third");
        AtomicInteger flushCount = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();

        runOnEdtAndWait(() -> MainViewChartLifecycle.updateIndicatorTripleOnEdt(
                8,
                Double.NaN,
                2.0,
                3.0,
                first,
                second,
                third,
                10,
                flushCount::incrementAndGet,
                failure::set));

        assertEquals(0, flushCount.get());
        assertEquals(0, first.getItemCount());
        assertEquals(0, second.getItemCount());
        assertEquals(0, third.getItemCount());
        assertNull(failure.get());
    }

    @Test
    public void lifecyclePreservesMaxPointLimitOnEdt() throws Exception {
        XYSeries first = new XYSeries("first");
        XYSeries second = new XYSeries("second");
        XYSeries third = new XYSeries("third");

        runOnEdtAndWait(() -> {
            for (int i = 0; i < 5; i++) {
                MainViewChartLifecycle.updateIndicatorTripleOnEdt(
                        i,
                        i,
                        i + 10.0,
                        i + 20.0,
                        first,
                        second,
                        third,
                        3,
                        null,
                        ex -> { throw new AssertionError(ex); });
            }
        });

        assertEquals(3, first.getItemCount());
        assertEquals(2.0, first.getX(0).doubleValue(), 0.001);
        assertEquals(4.0, first.getX(2).doubleValue(), 0.001);
        assertEquals(3, second.getItemCount());
        assertEquals(3, third.getItemCount());
    }

    private static void flushEdt() throws InterruptedException, InvocationTargetException {
        runOnEdtAndWait(() -> { });
    }

    private static void runOnEdtAndWait(Runnable runnable) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeAndWait(runnable);
        }
    }
}
