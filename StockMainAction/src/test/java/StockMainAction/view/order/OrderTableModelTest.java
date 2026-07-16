package StockMainAction.view.order;

import StockMainAction.model.core.OrderSide;
import StockMainAction.model.core.OrderStatus;
import StockMainAction.model.core.OrderType;
import StockMainAction.view.order.OrderBookSnapshot.OrderRow;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class OrderTableModelTest {
    @Test
    public void appliesUpdatesByStableIdWithoutReplacingSorterOrSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OrderTableModel model = new OrderTableModel();
            JTable table = new JTable(model);
            table.setAutoCreateRowSorter(true);
            model.applyRows(table, List.of(row("a", 101.0, 10), row("b", 99.0, 20)));

            RowSorter<?> sorter = table.getRowSorter();
            table.getRowSorter().toggleSortOrder(4);
            int selectedViewRow = table.convertRowIndexToView(model.indexOfOrderId("b"));
            table.setRowSelectionInterval(selectedViewRow, selectedViewRow);

            model.applyRows(table, List.of(row("b", 98.0, 15), row("c", 102.0, 30)));

            assertSame(sorter, table.getRowSorter());
            assertEquals(2, model.getRowCount());
            assertEquals("b", selectedOrderId(table, model));
            assertEquals(15, model.getValueAt(model.indexOfOrderId("b"), 3));
        });
    }

    @Test
    public void emitsRowLevelEventsForChangedSnapshot() {
        List<Integer> eventTypes = new ArrayList<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                OrderTableModel model = new OrderTableModel();
                model.applyRows(List.of(row("a", 101.0, 10), row("b", 99.0, 20)));
                model.addTableModelListener(event -> {
                    if (event.getFirstRow() != TableModelEvent.HEADER_ROW) {
                        eventTypes.add(event.getType());
                    }
                });
                model.applyRows(List.of(row("a", 101.0, 5), row("c", 100.0, 7)));
            });
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        assertEquals(List.of(TableModelEvent.DELETE, TableModelEvent.UPDATE,
                TableModelEvent.INSERT), eventTypes);
    }

    private static String selectedOrderId(JTable table, OrderTableModel model) {
        int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
        return model.getOrderIdAt(modelRow);
    }

    private static OrderRow row(String id, double price, int volume) {
        return new OrderRow(id, "PERSONAL", OrderSide.BUY, OrderType.LIMIT,
                OrderStatus.OPEN, price, volume, 1_000L, id.charAt(0));
    }
}
