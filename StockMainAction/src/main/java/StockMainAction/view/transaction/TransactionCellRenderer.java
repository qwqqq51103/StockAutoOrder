package StockMainAction.view.transaction;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/** Applies consistent buy/sell semantics to every transaction table. */
final class TransactionCellRenderer extends DefaultTableCellRenderer {
    static final Color BUY_COLOR = new Color(27, 94, 32);
    static final Color SELL_COLOR = new Color(183, 28, 28);
    static final Color BUY_TINT = new Color(232, 245, 233);
    static final Color SELL_TINT = new Color(255, 235, 238);

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private final DecimalFormat numberFormat = new DecimalFormat("#,##0.##");

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean selected, boolean focused, int row, int column) {
        int modelColumn = table.convertColumnIndexToModel(column);
        Object directionValue = table.getValueAt(row,
                table.convertColumnIndexToView(2));
        boolean buyerInitiated = "買方主動".equals(directionValue);

        Object displayValue = value;
        if (modelColumn == 0 && value instanceof Date) {
            displayValue = timeFormat.format((Date) value);
        } else if (value instanceof Number) {
            displayValue = numberFormat.format((Number) value);
        }

        super.getTableCellRendererComponent(
                table, displayValue, selected, focused, row, column);
        setBorder(noFocusBorder);
        setFont(table.getFont().deriveFont(Font.PLAIN));
        setHorizontalAlignment(modelColumn >= 4 && modelColumn <= 6
                ? SwingConstants.RIGHT : SwingConstants.LEFT);

        if (!selected) {
            setBackground(buyerInitiated ? BUY_TINT : SELL_TINT);
            setForeground(table.getForeground());
        }

        if (modelColumn == 2) {
            setBackground(buyerInitiated ? BUY_COLOR : SELL_COLOR);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD));
            setHorizontalAlignment(SwingConstants.CENTER);
        } else if (!selected && (modelColumn == 4 || modelColumn == 7 || modelColumn == 8)) {
            boolean buySemantic = modelColumn == 7
                    || (modelColumn == 4 && buyerInitiated);
            setForeground(buySemantic ? BUY_COLOR : SELL_COLOR);
            setFont(getFont().deriveFont(Font.BOLD));
        }
        return this;
    }
}
