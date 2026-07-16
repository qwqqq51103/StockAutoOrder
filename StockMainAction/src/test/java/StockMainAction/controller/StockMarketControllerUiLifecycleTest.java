package StockMainAction.controller;

import static org.junit.Assert.assertEquals;
import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.Transaction;
import StockMainAction.view.ControlView;
import StockMainAction.view.MainView;
import StockMainAction.view.OrderViewer;
import StockMainAction.view.TransactionHistoryViewer;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import javax.swing.SwingUtilities;
import org.junit.Assume;
import org.junit.Test;

public class StockMarketControllerUiLifecycleTest {
    @Test
    public void transactionHistoryReadsAndClearsPendingUpdatesConsistently() throws Exception {
        Assume.assumeFalse("Desktop UI is unavailable", GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            TransactionHistoryViewer viewer = new TransactionHistoryViewer(null);
            try {
                viewer.addTransaction(new Transaction("pending", null, null, 100.0, 2, 1L));
                assertEquals(1, viewer.getTransactionHistory().size());

                viewer.addTransaction(new Transaction("clear-me", null, null, 101.0, 3, 2L));
                viewer.clearTransactionHistory();
                assertEquals(0, viewer.getTransactionHistory().size());
            } finally {
                viewer.dispose();
            }
        });
    }

    @Test
    public void startupWindowReuseAndShutdownCompleteOnDesktop() throws Exception {
        Assume.assumeFalse("Desktop UI is unavailable", GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            StockMarketModel model = new StockMarketModel(42L, java.time.Clock.systemUTC());
            MainView mainView = new MainView();
            ControlView controlView = new ControlView();
            StockMarketController controller = new StockMarketController(model, mainView, controlView);
            try {
                mainView.setVisible(true);
                controlView.setVisible(true);
                controller.startSimulation();
                controlView.getViewOrdersButton().doClick();
                controlView.getViewOrdersButton().doClick();
                controlView.getTransactionHistoryButton().doClick();
                controlView.getTransactionHistoryButton().doClick();
                assertEquals(1, countDisplayableWindows(OrderViewer.class));
                assertEquals(1, countDisplayableWindows(TransactionHistoryViewer.class));
            } finally {
                controller.close();
                mainView.dispose();
                for (Window window : Window.getWindows()) {
                    if (window.isDisplayable()) window.dispose();
                }
            }
        });
    }

    private static long countDisplayableWindows(Class<? extends Window> type) {
        return java.util.Arrays.stream(Window.getWindows())
                .filter(type::isInstance)
                .filter(Window::isDisplayable)
                .count();
    }
}
