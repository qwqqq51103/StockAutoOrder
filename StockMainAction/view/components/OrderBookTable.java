package StockMainAction.view.components;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 訂單簿表格 - AbstractTableModel + RowSorter + 批次新增與上限
 */
public class OrderBookTable {

    // [UI] 表格列名
    private static final String[] COLUMNS = {
            "買單數量", "買單價格", "買單類型", "賣單價格", "賣單數量", "賣單類型"
    };

    // [PERF] 最大顯示列數（資料行數上限，不含標題行）
    private static final int MAX_ROWS = 200;

    // [UI]
    private final JTable table;
    private final JScrollPane scrollPane;

    // [PERF] 自訂資料模型（更快的批次插入）
    private final OrderBookTableModel model;

    public OrderBookTable() {
        this.model = new OrderBookTableModel();
        this.table = new JTable(model);

        // [UI] 行高、視覺
        table.setRowHeight(26); // [UI] rowHeight=26
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        // [UX] 自動排序器
        table.setAutoCreateRowSorter(true);
        TableRowSorter<OrderBookTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        // [UI] 對齊與顏色（注意數量欄是 Integer 類型，需要綁定 Integer 渲染器）
        EnhancedCellRenderer renderer = new EnhancedCellRenderer();
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(String.class, renderer);
        table.setDefaultRenderer(Integer.class, renderer);

        this.scrollPane = new JScrollPane(table);
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    /**
     * [UX] 與既有呼叫相容：以二維陣列整批設定資料
     * - 內部會進行批次更新，避免多次 fire 造成卡頓
     * - 自動裁切到上限
     */
    public void updateData(Object[][] newData) {
        if (newData == null) {
            model.setData(new ArrayList<>());
            return;
        }
        List<Object[]> rows = new ArrayList<>(newData.length);
        for (Object[] r : newData) {
            // 防守：每列固定 6 欄
            Object[] row = new Object[6];
            int len = Math.min(6, r.length);
            System.arraycopy(r, 0, row, 0, len);
            rows.add(row);
        }
        model.setData(rows);
    }

    /**
     * [PERF] 批次新增列（提供更快插入與上限裁切）
     */
    public void addRowsBatch(List<Object[]> rowsToAdd) {
        if (rowsToAdd == null || rowsToAdd.isEmpty()) return;
        model.addRowsBatch(rowsToAdd);
    }

    /**
     * [PERF] 清空
     */
    public void clear() {
        model.setData(new ArrayList<>());
    }

    // [PERF][UX] 計算目前顯示的買/賣量總和（跳過前兩行標題）
    public int[] getBuySellSums() {
        int buy = 0, sell = 0;
        try {
            for (int i = 2; i < model.getRowCount(); i++) {
                buy += safeInt(model.getValueAt(i, 0));
                sell += safeInt(model.getValueAt(i, 4));
            }
        } catch (Exception ignore) {}
        return new int[]{buy, sell};
    }

    private static int safeInt(Object v) {
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    // ===================== Model =====================

    private static class OrderBookTableModel extends AbstractTableModel {
        private final List<Object[]> rows = new ArrayList<>();
        private int maxBuyVolume = 1;  // 用於0欄位的最大值
        private int maxSellVolume = 1; // 用於4欄位的最大值

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
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0:
                case 4:
                    return Integer.class; // 數量
                default:
                    return String.class;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) return null;
            Object[] row = rows.get(rowIndex);
            return (columnIndex >= 0 && columnIndex < row.length) ? row[columnIndex] : null;
        }

        // [PERF] 批次設定完整資料（一次 fireTableDataChanged）
        public void setData(List<Object[]> newData) {
            int size = Math.min(MAX_ROWS, newData.size());
            rows.clear();
            if (size > 0) {
                rows.addAll(newData.subList(0, size));
            }
            recomputeMax();
            fireTableDataChanged();
        }

        // [PERF] 批次新增（僅一次 fire）
        public void addRowsBatch(List<Object[]> toAdd) {
            if (toAdd == null || toAdd.isEmpty()) return;

            int before = rows.size();
            // 先加入
            rows.addAll(toAdd);

            // 上限裁切（保留最新 MAX_ROWS）
            if (rows.size() > MAX_ROWS) {
                int from = Math.max(0, rows.size() - MAX_ROWS);
                List<Object[]> latest = new ArrayList<>(rows.subList(from, rows.size()));
                rows.clear();
                rows.addAll(latest);
                recomputeMax();
                fireTableDataChanged(); // 範圍不易計算，以全量重繪
            } else {
                int added = rows.size() - before;
                if (added > 0) {
                    recomputeMax();
                    fireTableRowsInserted(before, rows.size() - 1);
                }
            }
        }

        private void recomputeMax() {
            int mb = 1, ms = 1;
            for (Object[] r : rows) {
                try {
                    if (r[0] != null) mb = Math.max(mb, Integer.parseInt(String.valueOf(r[0])));
                } catch (Exception ignore) {}
                try {
                    if (r[4] != null) ms = Math.max(ms, Integer.parseInt(String.valueOf(r[4])));
                } catch (Exception ignore) {}
            }
            maxBuyVolume = Math.max(1, mb);
            maxSellVolume = Math.max(1, ms);
        }

        public int getMaxBuyVolume() { return maxBuyVolume; }
        public int getMaxSellVolume() { return maxSellVolume; }
    }

    // ===================== Renderer =====================

    // [UI] 增強渲染：數值對齊、買賣區塊底色、類型著色、交錯行
    private static class EnhancedCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // [UI] 量柱顯示：買單數量/賣單數量使用進度條呈現比例
            try {
                OrderBookTableModel m = (OrderBookTableModel) table.getModel();
                if ((column == 0 || column == 4) && value != null && String.valueOf(value).length() > 0) {
                    int v = Integer.parseInt(String.valueOf(value));
                    int max = Math.max(1, (column == 0) ? m.getMaxBuyVolume() : m.getMaxSellVolume());
                    // 保留輕量繪製，但調整外觀為「進度條樣式」
                    JPanel bar = new JPanel() {
                        protected void paintComponent(Graphics g) {
                            super.paintComponent(g);
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            int w = getWidth(); int h = getHeight();
                            int arc = 10; int pad = 2; int y = pad; int barH = Math.max(6, h - pad*2);
                            double ratio = Math.min(1.0, Math.max(0.0, v * 1.0 / max));
                            int innerW = w - pad*2;
                            int bw = (int) Math.round(innerW * ratio);
                            // 極小但非零的量，仍給最小可見寬度（避免看起來像沒有條）
                            if (v > 0 && bw == 0) bw = Math.min(3, innerW);
                            Color fg = (column == 0) ? new Color(67,160,71) : new Color(239,83,80);
                            Color track = new Color(230, 230, 230);
                            // track
                            g2.setColor(track);
                            g2.fillRoundRect(pad, y, w - pad*2, barH, arc, arc);
                            // fill（漸層）
                            if (bw > 0) {
                                GradientPaint gp = new GradientPaint(0, y, fg.brighter(), 0, y + barH, fg.darker());
                                g2.setPaint(gp);
                                g2.fillRoundRect(pad, y, bw, barH, arc, arc);
                            }
                            // 邊框
                            g2.setColor(new Color(0,0,0,60));
                            g2.drawRoundRect(pad, y, w - pad*2, barH, arc, arc);
                            // 文字置中顯示（水平置中在整個儲存格，垂直置中於條中）
                            String s = String.valueOf(v);
                            java.awt.FontMetrics fm = g2.getFontMetrics();
                            int sw = fm.stringWidth(s);
                            int sxCenter = pad + (w - pad*2 - sw) / 2;
                            int ty = y + (barH + fm.getAscent() - fm.getDescent()) / 2;
                            // 依置中文字是否落在填滿區決定字色
                            boolean textInsideFill = (sxCenter >= pad) && (sxCenter + sw <= pad + bw);
                            g2.setColor(textInsideFill ? Color.WHITE : new Color(33,33,33));
                            g2.drawString(s, sxCenter, ty);
                            g2.dispose();
                        }
                    };
                    bar.setOpaque(false);
                    return bar;
                }
            } catch (Exception ignore) {}

            // [UI] 基本對齊
            if (column == 0 || column == 1 || column == 3 || column == 4) {
                setHorizontalAlignment(RIGHT);
            } else {
                setHorizontalAlignment(CENTER);
            }

            // [UI] 標題/模式行特別處理：row 0 = 模式，row 1 = 標題
            if (!isSelected && row == 0) {
                setBackground(new Color(230, 230, 230));
                setFont(getFont().deriveFont(Font.BOLD));
                setHorizontalAlignment(LEFT);
                setForeground(Color.DARK_GRAY);
                return c;
            }
            if (!isSelected && row == 1) {
                setBackground(new Color(240, 240, 240));
                setFont(getFont().deriveFont(Font.BOLD));
                setHorizontalAlignment(CENTER);
                setForeground(Color.BLACK);
                return c;
            }

            // [UI] 交錯底色、選取配色（資料行）
            if (!isSelected) {
                setBackground((row % 2 == 0) ? Color.WHITE : new Color(248, 248, 248));
            } else {
                setBackground(new Color(232, 240, 254));
            }

            // [UI] 五檔強調顏色：row 2..6 屬於 Top 5（0..4）+ 深度熱力
            int level = row - 2; // 0 為最優價
            if (!isSelected) {
                Color baseBuy = new Color(210, 245, 210);
                Color baseSell = new Color(245, 210, 210);
                double alpha = (level >= 0 && level < 5) ? (0.65 - level * 0.12) : 0.15;
                alpha = Math.max(0.0, Math.min(0.75, alpha));
                // 熱力：依數量列比例再加深
                try {
                    OrderBookTableModel m = (OrderBookTableModel) table.getModel();
                    if (column <= 2) {
                        int v = safeInt(table.getValueAt(row, 0));
                        double ratio = Math.min(1.0, v * 1.0 / Math.max(1, m.getMaxBuyVolume()));
                        alpha += 0.25 * ratio;
                        setBackground(blend(getBackground(), baseBuy, alpha));
            } else {
                        int v = safeInt(table.getValueAt(row, 4));
                        double ratio = Math.min(1.0, v * 1.0 / Math.max(1, m.getMaxSellVolume()));
                        alpha += 0.25 * ratio;
                        setBackground(blend(getBackground(), baseSell, alpha));
                    }
                } catch (Exception ex) {
                    if (column <= 2) setBackground(blend(getBackground(), baseBuy, alpha));
                    else setBackground(blend(getBackground(), baseSell, alpha));
                }
            }

            // [UI] 價格/數量欄前景色：買=綠、賣=紅
            if (column == 1) {
                setForeground(new Color(0, 128, 0));
            } else if (column == 3) {
                setForeground(new Color(200, 0, 0));
            } else if (column == 0) {
                setForeground(new Color(0, 128, 0));
            } else if (column == 4) {
                setForeground(new Color(200, 0, 0));
            } else {
                setForeground(Color.BLACK);
            }

            // [UI] 類型著色
            if (column == 2 || column == 5) {
                if (value != null) {
                    String t = String.valueOf(value);
                    if ("市價單".equals(t)) {
                        setForeground(new Color(0, 102, 204));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if ("FOK單".equals(t)) {
                        setForeground(new Color(128, 0, 128));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        // 保持既定前景（已按價格欄設置），類型以常規顯示
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                }
            }

            return c;
        }

        private static Color blend(Color base, Color overlay, double alpha) {
            double a = Math.max(0.0, Math.min(1.0, alpha));
            int r = (int) Math.round(base.getRed() * (1 - a) + overlay.getRed() * a);
            int g = (int) Math.round(base.getGreen() * (1 - a) + overlay.getGreen() * a);
            int b = (int) Math.round(base.getBlue() * (1 - a) + overlay.getBlue() * a);
            return new Color(r, g, b);
        }
    }
}
