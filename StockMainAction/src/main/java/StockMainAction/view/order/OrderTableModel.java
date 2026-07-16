package StockMainAction.view.order;

import StockMainAction.model.core.OrderSide;
import StockMainAction.model.core.OrderStatus;
import StockMainAction.model.core.OrderType;
import StockMainAction.view.order.OrderBookSnapshot.OrderRow;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/** Table model that applies order changes incrementally by stable order ID. */
public final class OrderTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
        "訂單編號", "交易者", "類型", "數量", "價格", "時間", "狀態", "操作"
    };
    private final List<OrderRow> rows = new ArrayList<>();
    private final Map<String, Integer> rowById = new HashMap<>();
    private final DateTimeFormatter timeFormat;

    public OrderTableModel() {
        this(new SimpleDateFormat("HH:mm:ss"));
    }

    public OrderTableModel(SimpleDateFormat format) {
        this.timeFormat = DateTimeFormatter.ofPattern(format.toPattern())
                .withZone(format.getTimeZone().toZoneId());
    }

    public void applyRows(JTable table, List<OrderRow> nextRows) {
        if (table.getModel() != this) {
            throw new IllegalArgumentException("JTable is not backed by this model");
        }
        List<String> selectedIds = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < rows.size()) {
                selectedIds.add(rows.get(modelRow).id());
            }
        }

        applyRows(nextRows);

        table.clearSelection();
        for (String id : selectedIds) {
            int modelRow = indexOfOrderId(id);
            if (modelRow >= 0) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) table.addRowSelectionInterval(viewRow, viewRow);
            }
        }
    }

    public void applyRows(List<OrderRow> nextRows) {
        requireEdt();
        Set<String> nextIds = new HashSet<>();
        for (OrderRow row : nextRows) {
            if (!nextIds.add(row.id())) {
                throw new IllegalArgumentException("Duplicate order ID: " + row.id());
            }
        }

        for (int index = rows.size() - 1; index >= 0; index--) {
            if (!nextIds.contains(rows.get(index).id())) {
                rows.remove(index);
                fireTableRowsDeleted(index, index);
            }
        }
        rebuildIndex();

        for (int target = 0; target < nextRows.size(); target++) {
            OrderRow next = nextRows.get(target);
            Integer current = rowById.get(next.id());
            if (current == null) {
                rows.add(target, next);
                rebuildIndex();
                fireTableRowsInserted(target, target);
            } else if (current != target) {
                rows.remove(current.intValue());
                fireTableRowsDeleted(current, current);
                rows.add(target, next);
                rebuildIndex();
                fireTableRowsInserted(target, target);
            } else if (!next.equals(rows.get(target))) {
                rows.set(target, next);
                fireTableRowsUpdated(target, target);
            }
        }
    }

    public int indexOfOrderId(String orderId) {
        return rowById.getOrDefault(orderId, -1);
    }

    public int findRowById(String orderId) {
        return indexOfOrderId(orderId);
    }

    public String getOrderIdAt(int modelRow) {
        return rows.get(modelRow).id();
    }

    public String getOrderId(int modelRow) {
        return getOrderIdAt(modelRow);
    }

    public OrderRow getOrderAt(int modelRow) {
        return rows.get(modelRow);
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override
    public boolean isCellEditable(int row, int column) {
        return column == 7 && rows.get(row).isCancellable();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 3 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        OrderRow order = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> order.id();
            case 1 -> traderDisplayName(order.traderType());
            case 2 -> order.side() == OrderSide.BUY ? "買入" : "賣出";
            case 3 -> order.remainingVolume();
            case 4 -> order.type() == OrderType.MARKET ? "市價"
                    : String.format(java.util.Locale.ROOT, "%.2f", order.price());
            case 5 -> timeFormat.format(Instant.ofEpochMilli(order.timestamp()));
            case 6 -> statusDisplayName(order.status());
            case 7 -> order.isCancellable() ? "取消" : "已處理";
            default -> throw new IndexOutOfBoundsException("column=" + columnIndex);
        };
    }

    private void rebuildIndex() {
        rowById.clear();
        for (int i = 0; i < rows.size(); i++) rowById.put(rows.get(i).id(), i);
    }

    private static String traderDisplayName(String traderType) {
        return switch (traderType) {
            case "RETAIL_INVESTOR" -> "散戶";
            case "MAIN_FORCE" -> "主力";
            case "PERSONAL" -> "個人戶";
            case "MarketBehavior" -> "市場單";
            default -> traderType;
        };
    }

    private static String statusDisplayName(OrderStatus status) {
        return switch (status) {
            case NEW, OPEN -> "待成交";
            case PARTIALLY_FILLED -> "部分成交";
            case FILLED -> "已成交";
            case CANCELLED -> "已取消";
            case REJECTED -> "已拒絕";
        };
    }

    private static void requireEdt() {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("OrderTableModel must be updated on the EDT");
        }
    }
}
