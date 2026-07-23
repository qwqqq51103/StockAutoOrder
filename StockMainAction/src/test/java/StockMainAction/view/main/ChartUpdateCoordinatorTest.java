package StockMainAction.view.main;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.event.ChartChangeEvent;
import org.jfree.chart.event.ChartChangeListener;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChartUpdateCoordinatorTest {
    @Test
    public void requestFlushFromBackgroundFiresChartChangedOnEdt() throws Exception {
        JFreeChart chart = newChart();
        ChartUpdateCoordinator coordinator = new ChartUpdateCoordinator(1);
        CountDownLatch changed = new CountDownLatch(1);
        AtomicBoolean listenerWasOnEdt = new AtomicBoolean(false);
        chart.addChangeListener(event -> {
            listenerWasOnEdt.set(SwingUtilities.isEventDispatchThread());
            changed.countDown();
        });

        coordinator.register(chart);
        coordinator.requestFlush();

        assertTrue("chart change was not fired", changed.await(2, TimeUnit.SECONDS));
        assertTrue(listenerWasOnEdt.get());
        coordinator.close();
    }

    @Test
    public void unregisterPreventsSubsequentFlushNotification() throws Exception {
        JFreeChart chart = newChart();
        ChartUpdateCoordinator coordinator = new ChartUpdateCoordinator(1);
        AtomicInteger changedCount = new AtomicInteger();
        ChartChangeListener listener = (ChartChangeEvent event) -> changedCount.incrementAndGet();
        chart.addChangeListener(listener);

        coordinator.register(chart);
        coordinator.unregister(chart);
        coordinator.requestFlush();
        drainEdt();
        Thread.sleep(30);
        drainEdt();

        assertEquals(0, changedCount.get());
        coordinator.close();
    }

    @Test
    public void closeClearsRegisteredChartsAndStopsFutureRequests() throws Exception {
        JFreeChart chart = newChart();
        ChartUpdateCoordinator coordinator = new ChartUpdateCoordinator(1);
        coordinator.register(chart);

        assertEquals(1, coordinator.registeredChartCount());
        coordinator.close();
        coordinator.requestFlush();
        drainEdt();

        assertEquals(0, coordinator.registeredChartCount());
    }

    private static JFreeChart newChart() {
        XYSeries series = new XYSeries("price");
        series.add(1, 10);
        XYSeriesCollection dataset = new XYSeriesCollection(series);
        return ChartFactory.createXYLineChart("chart", "x", "y", dataset);
    }

    private static void drainEdt() throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> { });
    }
}
