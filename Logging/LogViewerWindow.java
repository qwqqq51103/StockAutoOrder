package Logging;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 日誌查看視窗，提供實時日誌顯示和過濾功能
 */
public class LogViewerWindow extends JFrame implements MarketLogger.LogListener {

    private DefaultTableModel logTableModel;
    private JTable logTable;
    private TableRowSorter<DefaultTableModel> sorter;

    private JComboBox<String> levelFilterCombo;
    private JComboBox<String> categoryFilterCombo;
    private JTextField messageFilterField;
    private JButton clearButton;
    private JButton exportButton;

    private Set<String> knownCategories = new HashSet<>();
    private Set<String> knownLevels = new HashSet<>();

    private static final int MAX_LOGS = 50000; // 最大顯示的日誌條數

    /**
     * 創建日誌查看視窗
     */
    public LogViewerWindow() {
        // 設置窗口基本屬性
        setTitle("市場模擬 - 日誌查看器");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 創建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // 創建過濾器面板
        JPanel filterPanel = createFilterPanel();
        mainPanel.add(filterPanel, BorderLayout.NORTH);

        // 創建日誌表格
        createLogTable();
        JScrollPane scrollPane = new JScrollPane(logTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 創建底部按鈕面板
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 添加面板到窗口
        add(mainPanel);

        // 註冊日誌監聽器
        MarketLogger.getInstance().addLogListener(this);

        // 窗口關閉時取消監聽
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                MarketLogger.getInstance().removeLogListener(LogViewerWindow.this);
            }
        });

        // 默認日誌級別
        levelFilterCombo.addItem("全部");
        levelFilterCombo.addItem("DEBUG");
        levelFilterCombo.addItem("INFO");
        levelFilterCombo.addItem("WARN");
        levelFilterCombo.addItem("ERROR");

        // 默認分類
        categoryFilterCombo.addItem("全部");

        // 顯示窗口
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * 創建過濾器面板
     */
    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 級別過濾器
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("級別:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.2;
        levelFilterCombo = new JComboBox<>();
        panel.add(levelFilterCombo, gbc);

        // 分類過濾器
        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("分類:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.3;
        categoryFilterCombo = new JComboBox<>();
        panel.add(categoryFilterCombo, gbc);

        // 消息過濾器
        gbc.gridx = 4;
        gbc.weightx = 0;
        panel.add(new JLabel("消息:"), gbc);

        gbc.gridx = 5;
        gbc.weightx = 0.5;
        messageFilterField = new JTextField();
        panel.add(messageFilterField, gbc);

        // 添加過濾器變更監聽器
        levelFilterCombo.addActionListener(e -> applyFilters());
        categoryFilterCombo.addActionListener(e -> applyFilters());
        messageFilterField.addActionListener(e -> applyFilters());

        return panel;
    }

    /**
     * 創建日誌表格
     */
    private void createLogTable() {
        // 定義表格列
        String[] columnNames = {"時間", "級別", "分類", "消息"};
        logTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 不可編輯
            }
        };

        logTable = new JTable(logTableModel);
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(500);

        // 添加排序和過濾器
        sorter = new TableRowSorter<>(logTableModel);
        logTable.setRowSorter(sorter);

        // 設置單元格渲染器 - 修正這裡的代碼
        DefaultLogCellRenderer renderer = new DefaultLogCellRenderer();
        for (int i = 0; i < logTable.getColumnCount(); i++) {
            logTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    /**
     * 創建底部按鈕面板
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        clearButton = new JButton("清空日誌");
        clearButton.addActionListener(e -> {
            logTableModel.setRowCount(0);
        });
        panel.add(clearButton);

        exportButton = new JButton("導出日誌");
        exportButton.addActionListener(e -> {
            exportLogs();
        });
        panel.add(exportButton);

        return panel;
    }

    /**
     * 應用過濾器
     */
    private void applyFilters() {
        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        // 級別過濾
        String selectedLevel = (String) levelFilterCombo.getSelectedItem();
        if (selectedLevel != null && !selectedLevel.equals("全部")) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(selectedLevel) + "$", 1));
        }

        // 分類過濾
        String selectedCategory = (String) categoryFilterCombo.getSelectedItem();
        if (selectedCategory != null && !selectedCategory.equals("全部")) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(selectedCategory) + "$", 2));
        }

        // 消息過濾
        String messageFilter = messageFilterField.getText().trim();
        if (!messageFilter.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(messageFilter), 3));
        }

        // 應用過濾器
        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else if (filters.size() == 1) {
            sorter.setRowFilter(filters.get(0));
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    /**
     * 導出日誌到文件
     */
    private void exportLogs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("導出日誌");
        fileChooser.setSelectedFile(new File("exported_logs.csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try ( PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // 寫入CSV標題
                writer.println("時間,級別,分類,消息");

                // 寫入日誌數據
                for (int i = 0; i < logTableModel.getRowCount(); i++) {
                    int modelRow = i;
                    if (sorter != null) {
                        int viewRow = logTable.convertRowIndexToView(i);
                        if (viewRow == -1) {
                            continue; // 跳過被過濾的行
                        }
                    }

                    StringBuilder line = new StringBuilder();
                    for (int j = 0; j < logTableModel.getColumnCount(); j++) {
                        Object value = logTableModel.getValueAt(modelRow, j);
                        if (value != null) {
                            // 處理CSV中的特殊字符
                            String text = value.toString().replace("\"", "\"\"");
                            line.append("\"").append(text).append("\"");
                        } else {
                            line.append("\"\"");
                        }

                        if (j < logTableModel.getColumnCount() - 1) {
                            line.append(",");
                        }
                    }
                    writer.println(line);
                }

                JOptionPane.showMessageDialog(this,
                        "日誌已成功導出到: " + file.getAbsolutePath(),
                        "導出成功",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "導出失敗: " + e.getMessage(),
                        "導出錯誤",
                        JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onNewLog(String timestamp, String level, String category, String message) {
        // 在EDT中更新UI
        SwingUtilities.invokeLater(() -> {
            // 添加新日誌到表格
            logTableModel.addRow(new Object[]{timestamp, level, category, message});

            // 檢查並添加新的分類
            if (!knownCategories.contains(category)) {
                knownCategories.add(category);
                categoryFilterCombo.addItem(category);
            }

            // 檢查並添加新的級別
            if (!knownLevels.contains(level)) {
                knownLevels.add(level);
            }

            // 限制日誌條數
            while (logTableModel.getRowCount() > MAX_LOGS) {
                logTableModel.removeRow(0);
            }

            // 滾動到最新日誌
            int lastRow = logTable.convertRowIndexToView(logTableModel.getRowCount() - 1);
            if (lastRow >= 0) {
                logTable.scrollRectToVisible(logTable.getCellRect(lastRow, 0, true));
            }
        });
    }

    /**
     * 自定義單元格渲染器，根據日誌級別顯示不同顏色
     */
    private class DefaultLogCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow >= 0 && modelRow < logTableModel.getRowCount()) {
                    String level = (String) logTableModel.getValueAt(modelRow, 1);

                    if ("ERROR".equals(level)) {
                        c.setBackground(new Color(255, 200, 200));
                    } else if ("WARN".equals(level)) {
                        c.setBackground(new Color(255, 255, 200));
                    } else if ("INFO".equals(level)) {
                        c.setBackground(new Color(220, 220, 255));
                    } else {
                        c.setBackground(Color.WHITE);
                    }
                }
            }

            return c;
        }
    }

    /**
     * 啟動日誌查看器
     */
    public static void main(String[] args) {
        try {
            // 設置界面外觀
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new LogViewerWindow();
        });
    }
}
