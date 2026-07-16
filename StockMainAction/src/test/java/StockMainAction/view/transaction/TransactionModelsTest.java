package StockMainAction.view.transaction;

import StockMainAction.model.core.Transaction;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.TableModelEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TransactionModelsTest {
    @Test
    public void tableModelEvictsOldestRowsIncrementally() {
        TransactionTableModel model = new TransactionTableModel(2);
        List<Integer> eventTypes = new ArrayList<>();
        model.addTableModelListener(event -> eventTypes.add(event.getType()));

        Transaction first = transaction("one", 10.0, 1);
        model.addTransaction(first);
        assertFalse(model.addTransaction(first));
        model.addTransaction(transaction("two", 20.0, 2));
        model.addTransaction(transaction("three", 30.0, 3));

        assertEquals(2, model.getRowCount());
        assertEquals("two", model.getValueAt(0, 1));
        assertEquals("three", model.getValueAt(1, 1));
        assertFalse(model.addTransaction(first));
        assertEquals(List.of(TableModelEvent.INSERT, TableModelEvent.INSERT,
                TableModelEvent.DELETE, TableModelEvent.INSERT), eventTypes);
    }

    @Test
    public void statisticsAndChartModelsRemainBounded() {
        TransactionStatisticsModel statistics = new TransactionStatisticsModel(2);
        TransactionChartModel chart = new TransactionChartModel(2);

        Transaction first = transaction("one", 10.0, 1);
        for (Transaction transaction : new Transaction[] {
                first,
                transaction("two", 20.0, 2),
                transaction("three", 30.0, 3)}) {
            statistics.addTransaction(transaction);
            chart.addTransaction(transaction);
        }

        TransactionStatisticsModel.Statistics snapshot = statistics.snapshot();
        assertEquals(2, snapshot.transactionCount());
        assertEquals(5, snapshot.totalVolume());
        assertEquals(130.0, snapshot.totalAmount(), 0.001);
        assertEquals(26.0, snapshot.averagePrice(), 0.001);
        assertEquals(2, chart.size());
        assertEquals("two", chart.snapshot().get(0).getId());
        assertFalse(statistics.addTransaction(first));
        assertFalse(chart.addTransaction(first));
    }

    static Transaction transaction(String id, double price, int volume) {
        return new Transaction(id, null, null, price, volume, System.currentTimeMillis());
    }
}
