package OrderManagement;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 訂單簿表格 - 增強版，支持顯示訂單類型和撮合模式
 */
public class OrderBookTable {

    private JTable table;
    private JScrollPane scrollPane;
    private DefaultTableModel tableModel;

    /**
     * 構造函數
     */
    public OrderBookTable() {
        // 定義列名，增加到 6 列，包含訂單類型
        String[] columnNames = {"買單數量", "買單價格", "買單類型", "賣單價格", "賣單數量", "賣單類型"};

        // 創建空的初始數據
        Object[][] initialData = new Object[12][6]; // 12行 6列，含標題和撮合模式行

        // 使用 DefaultTableModel 來管理表格數據
        tableModel = new DefaultTableModel(initialData, columnNames) {
            // 防止用戶編輯表格
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);

        // 設置自定義渲染器
        table.setDefaultRenderer(Object.class, new EnhancedTableCellRenderer());

        // 設置表格的一些基本屬性
        table.setFillsViewportHeight(true);
        table.setRowHeight(25); // 根據需要調整行高

        // 設置列寬
        table.getColumnModel().getColumn(0).setPreferredWidth(80); // 買單數量
        table.getColumnModel().getColumn(1).setPreferredWidth(80); // 買單價格
        table.getColumnModel().getColumn(2).setPreferredWidth(70); // 買單類型
        table.getColumnModel().getColumn(3).setPreferredWidth(80); // 賣單價格
        table.getColumnModel().getColumn(4).setPreferredWidth(80); // 賣單數量
        table.getColumnModel().getColumn(5).setPreferredWidth(70); // 賣單類型

        scrollPane = new JScrollPane(table);
    }

    /**
     * 獲取滾動面板
     */
    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    /**
     * 增強版表格渲染器，支持訂單類型和撮合模式的顯示
     */
    class EnhancedTableCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            // 先使用基本渲染組件
            Component cell = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            // 處理空值
            if (value == null || value.toString().isEmpty()) {
                setText("");
                return cell;
            }

            // 設置撮合模式行的樣式（第一行）
            if (row == 0) {
                setFont(getFont().deriveFont(Font.BOLD));
                setBackground(new Color(230, 230, 230)); // 淺灰色
                setHorizontalAlignment(JLabel.LEFT);
                return cell;
            }

            // 設置標題行的樣式（第二行）
            if (row == 1) {
                setFont(getFont().deriveFont(Font.BOLD));
                setBackground(new Color(240, 240, 240)); // 更淺的灰色
                setHorizontalAlignment(JLabel.CENTER);
                return cell;
            }

            // 數據行的樣式（從第三行開始）
            setFont(getFont().deriveFont(Font.PLAIN));

            // 處理買量和賣量的進度條顯示
            if ((column == 0 || column == 4) && !value.toString().isEmpty()) {
                try {
                    int volume = Integer.parseInt(value.toString());
                    if (volume > 0) {
                        JProgressBar progressBar = new JProgressBar(0, 1000); // 調整最大值
                        progressBar.setValue(volume);
                        progressBar.setString(String.valueOf(volume));
                        progressBar.setStringPainted(true);

                        if (column == 0) { // 買單數量
                            progressBar.setForeground(new Color(255, 100, 100)); // 紅色
                        } else { // 賣單數量
                            progressBar.setForeground(new Color(100, 255, 100)); // 綠色
                        }

                        return progressBar;
                    }
                } catch (NumberFormatException e) {
                    // 非數字值，使用普通文本顯示
                }
            }

            // 根據列設置不同的對齊方式和顏色
            if (column == 1) { // 買單價格
                setHorizontalAlignment(JLabel.RIGHT);
                setForeground(Color.RED);
            } else if (column == 3) { // 賣單價格
                setHorizontalAlignment(JLabel.RIGHT);
                setForeground(Color.GREEN);
            } else if (column == 2) { // 買單類型
                setHorizontalAlignment(JLabel.CENTER);
                setOrderTypeStyle(value.toString());
            } else if (column == 5) { // 賣單類型
                setHorizontalAlignment(JLabel.CENTER);
                setOrderTypeStyle(value.toString());
            } else {
                setHorizontalAlignment(JLabel.RIGHT);
                setForeground(Color.BLACK);
            }

            // 買單和賣單區域的背景顏色
            if (column < 3) { // 買單區域
                setBackground(new Color(253, 245, 245)); // 非常淺的紅色
            } else { // 賣單區域
                setBackground(new Color(245, 255, 245)); // 非常淺的綠色
            }

            return cell;
        }

        /**
         * 設置訂單類型的樣式
         */
        private void setOrderTypeStyle(String orderType) {
            if ("市價單".equals(orderType)) {
                setForeground(Color.BLUE);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if ("FOK單".equals(orderType)) {
                setForeground(new Color(128, 0, 128)); // 紫色
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setForeground(Color.BLACK);
            }
        }
    }

    /**
     * 更新表格數據
     *
     * @param newData 新的表格數據
     */
    public void updateData(Object[][] newData) {
        // 確保表格模型有足夠的行
        while (tableModel.getRowCount() < newData.length) {
            tableModel.addRow(new Object[tableModel.getColumnCount()]);
        }

        // 更新單元格數據
        for (int i = 0; i < newData.length; i++) {
            for (int j = 0; j < newData[i].length; j++) {
                tableModel.setValueAt(newData[i][j], i, j);
            }
        }

        // 如果有多餘的行，移除它們
        while (tableModel.getRowCount() > newData.length) {
            tableModel.removeRow(tableModel.getRowCount() - 1);
        }

        // 觸發表格更新
        tableModel.fireTableDataChanged();
    }
}
