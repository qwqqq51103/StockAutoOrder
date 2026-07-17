package StockMainAction.view.transaction;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.Trader;
import StockMainAction.model.core.Transaction;
import StockMainAction.model.user.UserAccount;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransactionHistoryPanelTest {
    @Test
    public void deDuplicatesInitialBatchAndIncrementalEventsById() throws Exception {
        onEdt(() -> {
            TransactionHistoryPanel panel = new TransactionHistoryPanel(10, 10);
            Transaction first = TransactionModelsTest.transaction("same", 10.0, 1);
            Transaction second = TransactionModelsTest.transaction("second", 11.0, 2);

            assertEquals(2, panel.addTransactions(List.of(first, second, first)));
            assertFalse(panel.addTransaction(first));
            assertEquals(2, panel.getTransactions().size());
            assertEquals(2, panel.getTableModel(TransactionHistoryPanel.View.ALL).getRowCount());
            panel.dispose();
        });
    }

    @Test
    public void allSixTablesAndDerivedModelsAreBounded() throws Exception {
        onEdt(() -> {
            TransactionHistoryPanel panel = new TransactionHistoryPanel(3, 2);
            for (int index = 0; index < 8; index++) {
                panel.addTransaction(index % 2 == 0
                        ? marketBuy("market-" + index, index + 10)
                        : TransactionModelsTest.transaction("limit-" + index, index + 10, 1));
            }

            for (TransactionHistoryPanel.View view : TransactionHistoryPanel.View.values()) {
                assertTrue(panel.getTableModel(view).getRowCount() <= 3);
            }
            assertEquals(3, panel.getTableModel(TransactionHistoryPanel.View.ALL).getRowCount());
            assertEquals(3, panel.getTableModel(TransactionHistoryPanel.View.PERSONAL).getRowCount());
            assertEquals(3, panel.getStatisticsModel().size());
            assertEquals(2, panel.getChartModel().size());
            assertEquals(3, panel.getTransactions().size());
            panel.dispose();
        });
    }

    @Test
    public void refreshTimerPausesAndDisposeIsIdempotent() throws Exception {
        AtomicReference<TransactionHistoryPanel> reference = new AtomicReference<>();
        onEdt(() -> {
            TransactionHistoryPanel panel = new TransactionHistoryPanel(3, 3);
            reference.set(panel);
            assertFalse(panel.isRefreshTimerRunning());
            panel.setRefreshActive(true);
            assertTrue(panel.isRefreshTimerRunning());
            panel.setRefreshActive(false);
            assertFalse(panel.isRefreshTimerRunning());
            panel.dispose();
            panel.dispose();
            panel.setRefreshActive(true);
            assertFalse(panel.isRefreshTimerRunning());
        });
        assertFalse(reference.get().isRefreshTimerRunning());
    }

    @Test
    public void rejectsVeryOldReplayAfterVisibleRowsHaveBeenEvicted() throws Exception {
        onEdt(() -> {
            TransactionHistoryPanel panel = new TransactionHistoryPanel(3, 2);
            Transaction first = TransactionModelsTest.transaction("original", 10.0, 1);
            assertTrue(panel.addTransaction(first));
            for (int index = 0; index < 4_200; index++) {
                panel.addTransaction(TransactionModelsTest.transaction("later-" + index, 11.0, 1));
            }
            assertFalse(panel.addTransaction(first));
            assertEquals(3, panel.getTransactions().size());
            panel.dispose();
        });
    }

    @Test
    public void newestTradeStaysOnTopAndBuySellColorsAreUnambiguous() throws Exception {
        onEdt(() -> {
            TransactionHistoryPanel panel = new TransactionHistoryPanel(10, 10);
            panel.addTransaction(new Transaction(
                    "older-sell", null, null, 99.0, 1, 1L));
            panel.addTransaction(marketBuy("newer-buy", 100.0));

            JTable table = panel.getTable(TransactionHistoryPanel.View.ALL);
            assertEquals("newer-buy", table.getValueAt(0, 1));
            assertEquals("買方主動", table.getValueAt(0, 2));

            Component direction = table.prepareRenderer(table.getCellRenderer(0, 2), 0, 2);
            assertEquals(TransactionCellRenderer.BUY_COLOR, direction.getBackground());
            Component buyer = table.prepareRenderer(table.getCellRenderer(0, 7), 0, 7);
            assertEquals(TransactionCellRenderer.BUY_COLOR, buyer.getForeground());
            Component seller = table.prepareRenderer(table.getCellRenderer(0, 8), 0, 8);
            assertEquals(TransactionCellRenderer.SELL_COLOR, seller.getForeground());
            panel.dispose();
        });
    }

    private static Transaction marketBuy(String id, double price) {
        Trader buyer = new TestTrader("PERSONAL");
        Trader seller = new TestTrader("MAIN_FORCE");
        Order buyOrder = Order.createMarketBuyOrder(1, buyer);
        Order sellOrder = Order.createLimitSellOrder(price, 1, seller);
        return new Transaction(id, buyOrder, sellOrder, price, 1, System.currentTimeMillis());
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static final class TestTrader implements Trader {
        private final String type;
        private final UserAccount account = new UserAccount(100_000, 100);

        private TestTrader(String type) {
            this.type = type;
        }

        @Override
        public UserAccount getAccount() {
            return account;
        }

        @Override
        public String getTraderType() {
            return type;
        }

        @Override
        public void updateAfterTransaction(String side, int volume, double price) {
        }

        @Override
        public void updateAverageCostPrice(String side, int volume, double price) {
        }
    }
}
