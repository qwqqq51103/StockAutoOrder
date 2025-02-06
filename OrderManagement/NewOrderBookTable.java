package OrderManagement;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 用於顯示訂單簿的新的表格類別
 */
public class NewOrderBookTable {

    private JTable table;
    private JScrollPane scrollPane;
    private DefaultTableModel tableModel;

    /**
     * 構造函數
     *
     * @param data 初始數據
     * @param columnNames 列名
     * @param useProgressBars 是否在數量列使用進度條
     * @param colorPriceByType 是否根據類型設置價格列的顏色
     */
    public NewOrderBookTable(Object[][] data, String[] columnNames, boolean useProgressBars, boolean colorPriceByType) {
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
            table.getColumnModel().getColumn(i).setCellRenderer(new CustomTableCellRenderer(useProgressBars, colorPriceByType));
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
     * 自定義渲染器，用於顯示數量的柱狀圖，並根據訂單類型設置價格列的顏色
     */
    class CustomTableCellRenderer extends DefaultTableCellRenderer {

        private boolean useProgressBars;
        private boolean colorPriceByType;

        public CustomTableCellRenderer(boolean useProgressBars, boolean colorPriceByType) {
            this.useProgressBars = useProgressBars;
            this.colorPriceByType = colorPriceByType;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {
            if (value == null) {
                // 避免因為 null 值導致的崩潰，顯示預設值
                setText("-");
                return this;
            }

            // 處理數量的柱狀圖顯示
            if (useProgressBars && column == 3) { // 數量列
                int volume;
                try {
                    volume = Integer.parseInt(value.toString());
                } catch (NumberFormatException e) {
                    volume = 0; // 如果轉型失敗，將預設量設為 0
                }

                JProgressBar progressBar = new JProgressBar(0, 5000); // 假設最大量是10000，根據實際需求調整
                progressBar.setValue(volume);
                progressBar.setString(String.valueOf(volume)); // 顯示實際數量
                progressBar.setStringPainted(true); // 顯示數字

                // 根據買賣單類型調整顏色
                if (table.getValueAt(row, 2).toString().equalsIgnoreCase("Buy")) { // 假設第 2 列是類型列
                    progressBar.setForeground(Color.RED); // 買單顯示紅色
                } else if (table.getValueAt(row, 2).toString().equalsIgnoreCase("Sell")) { // 賣單
                    progressBar.setForeground(Color.GREEN); // 賣單顯示綠色
                } else {
                    progressBar.setForeground(Color.GRAY); // 如果類型不明，顯示灰色
                }

                return progressBar;
            }

            // 處理訂單類型的文字顯示
            if (column == 2) { // 訂單類型列
                JLabel label = new JLabel(value.toString());
                label.setOpaque(true); // 設置不透明，允許背景顏色生效
                label.setFont(label.getFont().deriveFont(Font.BOLD, 14f)); // 設定為粗體，字體大小為 14

                // 根據類型設置字體顏色和背景
                if (value.toString().equalsIgnoreCase("Buy")) {
                    label.setForeground(Color.RED); // 買單顯示紅色字
                    label.setBackground(new Color(255, 200, 200)); // 背景為淡紅色
                } else if (value.toString().equalsIgnoreCase("Sell")) {
                    label.setForeground(Color.GREEN); // 賣單顯示綠色字
                    label.setBackground(new Color(200, 255, 200)); // 背景為淡綠色
                } else {
                    label.setForeground(Color.BLACK); // 預設黑色字
                    label.setBackground(Color.LIGHT_GRAY); // 預設灰色背景
                }

                label.setHorizontalAlignment(SwingConstants.CENTER); // 文字居中
                return label;
            }

            // 處理價格列的顏色設置
            if (colorPriceByType && column == 4) { // 價格列
                JLabel label = new JLabel(value.toString()); // 創建顯示價格的 JLabel
                label.setOpaque(true); // 設置不透明，允許背景顏色生效
                label.setFont(label.getFont().deriveFont(Font.BOLD, 14f)); // 設置字體為粗體，字體大小為 14

                // 獲取當前行的「類型」值（Buy/Sell）
                String type = table.getModel().getValueAt(row, 2).toString();
                if ("Buy".equalsIgnoreCase(type)) {
                    label.setForeground(Color.RED); // 設置紅色字體
                    label.setBackground(new Color(255, 200, 200)); // 設置淡紅色背景
                } else if ("Sell".equalsIgnoreCase(type)) {
                    label.setForeground(new Color(0, 128, 0)); // 設置深綠色字體
                    label.setBackground(new Color(200, 255, 200)); // 設置淡綠色背景
                } else {
                    label.setForeground(Color.BLACK); // 預設黑色字體
                    label.setBackground(Color.LIGHT_GRAY); // 預設灰色背景
                }

                label.setHorizontalAlignment(SwingConstants.CENTER); // 價格文字居中顯示
                return label; // 返回設置好的 JLabel
            } else {
                // 其餘列保持默認顏色
                setForeground(Color.BLACK);
                setBackground(Color.WHITE);
            }

            // 使用默認的渲染器來獲取單元格組件
            Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // 右對齊
            if (cell instanceof JLabel) {
                ((JLabel) cell).setHorizontalAlignment(JLabel.RIGHT);
            }

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
