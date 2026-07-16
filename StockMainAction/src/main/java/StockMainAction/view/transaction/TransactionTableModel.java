package StockMainAction.view.transaction;

import StockMainAction.model.core.Transaction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Incremental table model with a fixed row budget. */
public final class TransactionTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
        "成交時間", "交易 ID", "方向", "類型", "價格", "數量", "成交額",
        "買方", "賣方", "成交率", "滑價"
    };

    private final int maxRows;
    private final List<Transaction> rows = new ArrayList<>();
    private final BoundedTransactionIds seenTransactionIds;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    public TransactionTableModel(int maxRows) {
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        this.maxRows = maxRows;
        this.seenTransactionIds = new BoundedTransactionIds(maxRows);
    }

    public boolean addTransaction(Transaction transaction) {
        if (!seenTransactionIds.add(transaction)) {
            return false;
        }
        if (rows.size() == maxRows) {
            rows.remove(0);
            fireTableRowsDeleted(0, 0);
        }
        rows.add(transaction);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        return true;
    }

    public int addTransactions(Iterable<Transaction> transactions) {
        if (transactions == null) return 0;
        int added = 0;
        for (Transaction transaction : transactions) {
            if (addTransaction(transaction)) added++;
        }
        return added;
    }

    public void clear() {
        int previousSize = rows.size();
        rows.clear();
        seenTransactionIds.clear();
        if (previousSize > 0) {
            fireTableRowsDeleted(0, previousSize - 1);
        }
    }

    public List<Transaction> getTransactions() {
        return List.copyOf(rows);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 4, 6, 9, 10 -> Double.class;
            case 5 -> Integer.class;
            default -> String.class;
        };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Transaction transaction = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> timeFormat.format(new Date(transaction.getTimestamp()));
            case 1 -> transaction.getId();
            case 2 -> isBuyerInitiated(transaction) ? "買方主動" : "賣方主動";
            case 3 -> transaction.getTransactionType().getDisplayName();
            case 4 -> transaction.getPrice();
            case 5 -> transaction.getVolume();
            case 6 -> transaction.getTotalValue();
            case 7 -> traderType(transaction.getBuyer());
            case 8 -> traderType(transaction.getSeller());
            case 9 -> transaction.getFillRate();
            case 10 -> transaction.getSlippagePercentage();
            default -> throw new IndexOutOfBoundsException("columnIndex=" + columnIndex);
        };
    }

    private static String traderType(StockMainAction.model.core.Trader trader) {
        return trader == null ? "未知" : trader.getTraderType();
    }

    private static boolean isBuyerInitiated(Transaction transaction) {
        return transaction.isBuyerInitiated()
                || "MARKET_BUY".equals(transaction.getOrderType())
                || "FOK_BUY".equals(transaction.getOrderType());
    }
}
