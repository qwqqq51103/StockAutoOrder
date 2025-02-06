package OrderManagement;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class OrderBookTable {

    private JTable table;
    private JScrollPane scrollPane;
    private DefaultTableModel tableModel;

    public OrderBookTable(Object[][] data, String[] columnNames) {
        // 使用 DefaultTableModel 來管理表格數據
        tableModel = new DefaultTableModel(data, columnNames) {
            // 防止用戶編輯表格
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);

        // 設置自定義渲染器
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(new CustomTableCellRenderer());
        }

        // 設置表格的一些基本屬性
        table.setFillsViewportHeight(true);
        table.setRowHeight(25); // 根據需要調整行高

        scrollPane = new JScrollPane(table);
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    /**
     * 自定義渲染器，用於顯示買量和賣量的柱狀圖，並以顏色區分買價和賣價
     */
    class CustomTableCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {
            if (value == null) {
                // 避免因為 null 值導致的崩潰，顯示預設值
                setText("-");
                return this;
            }

            // 處理買量和賣量的柱狀圖顯示
            if (column == 0 || column == 3) { // 假設 0 是買量，3 是賣量
                int volume;
                try {
                    volume = Integer.parseInt(value.toString());
                } catch (NumberFormatException e) {
                    volume = 0; // 如果轉型失敗，將預設量設為 0
                }

                JProgressBar progressBar = new JProgressBar(0, 1000); // 假設最大量是1000，根據實際需求調整
                progressBar.setValue(volume);
                progressBar.setString(String.valueOf(volume)); // 顯示實際數量
                progressBar.setStringPainted(true); // 顯示數字

                if (column == 0) {
                    progressBar.setForeground(Color.RED); // 買量顯示紅色
                } else if (column == 3) {
                    progressBar.setForeground(Color.GREEN); // 賣量顯示綠色
                }

                return progressBar;
            }

            // 其餘列的標準渲染
            Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // 確保值不是 null，避免 NullPointerException
            if (value != null) {
                // 買價顯示紅色
                if (column == 1) {
                    cell.setForeground(Color.RED);
                } else if (column == 2) {
                    cell.setForeground(Color.GREEN); // 賣價顯示綠色
                } else {
                    cell.setForeground(Color.BLACK);
                }
            }

            // 右對齊
            ((JLabel) cell).setHorizontalAlignment(JLabel.RIGHT);
            return cell;
        }
    }

    /**
     * 更新表格數據
     *
     * @param newData 新的表格數據
     */
    public void updateData(Object[][] newData) {
        // 清空現有數據
        tableModel.setRowCount(0);

        // 添加新數據
        for (Object[] rowData : newData) {
            tableModel.addRow(rowData);
        }

        // 觸發表格更新
        tableModel.fireTableDataChanged();
    }
}
