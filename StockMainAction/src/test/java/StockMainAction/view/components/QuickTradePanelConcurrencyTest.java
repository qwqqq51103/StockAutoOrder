package StockMainAction.view.components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import StockMainAction.model.core.QuickTradeConfig;
import StockMainAction.model.core.QuickTradeConfig.PriceStrategy;
import StockMainAction.model.core.QuickTradeConfig.QuickTradeType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class QuickTradePanelConcurrencyTest {
    @Test
    public void repeatedAutoExecuteRunsOnceOffEdtInsteadOfDroppingAcceptedCommands() throws Exception {
        QuickTradePanel panel = new QuickTradePanel();
        QuickTradeConfig config = new QuickTradeConfig("auto", QuickTradeType.FIXED_QUANTITY,
                PriceStrategy.MARKET, true);
        config.setAutoExecute(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean listenerWasOnEdt = new AtomicBoolean(true);
        panel.setListener(new ListenerAdapter() {
            @Override public void onQuickTradeExecute(QuickTradeConfig ignored) {
                calls.incrementAndGet();
                listenerWasOnEdt.set(SwingUtilities.isEventDispatchThread());
                entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
        });
        SwingUtilities.invokeAndWait(() -> panel.loadQuickTradeConfigs(List.of(config)));

        Method execute = QuickTradePanel.class.getDeclaredMethod("executeQuickTrade", int.class);
        execute.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            invoke(execute, panel, 0);
            invoke(execute, panel, 0);
        });

        entered.await(2, TimeUnit.SECONDS);
        assertEquals(1, calls.get());
        assertFalse(listenerWasOnEdt.get());
        release.countDown();
        panel.close();
    }

    private static void invoke(Method method, Object target, Object... arguments) {
        try { method.invoke(target, arguments); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private abstract static class ListenerAdapter implements QuickTradePanel.QuickTradePanelListener {
        @Override public void onConfigureQuickTrade() { }
        @Override public void onPreviewQuickTrade(QuickTradeConfig config) { }
        @Override public void onPumpPrice(int totalQty, int slices, boolean useMainForce,
                boolean enableLiquidity, int depthLevels, double depthSpanPct,
                boolean enableCounterWallSelfTrade, boolean useOtherTraderFooting) { }
        @Override public void onDumpPrice(int totalQty, int slices, boolean useMainForce,
                boolean enableLiquidity, int depthLevels, double depthSpanPct,
                boolean enableCounterWallSelfTrade, boolean useOtherTraderFooting) { }
    }
}
