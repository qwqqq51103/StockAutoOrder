package StockMainAction.view.transaction;

import StockMainAction.model.core.Transaction;
import StockMainAction.model.core.Trader;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;

/** Complete transaction history surface backed by bounded incremental models. */
public final class TransactionHistoryPanel extends JPanel {
    public enum View {
        ALL, BUY, SELL, PERSONAL, MARKET, LIMIT
    }

    public static final int DEFAULT_MAX_ROWS = 2_000;
    public static final int DEFAULT_MAX_CHART_POINTS = 500;
    private static final int REFRESH_DELAY_MS = 250;

    private final int maxTransactions;
    private final Map<View, TransactionTableModel> tableModels = new EnumMap<>(View.class);
    private final TransactionStatisticsModel statisticsModel;
    private final TransactionChartModel chartModel;
    private final Deque<Transaction> transactions = new ArrayDeque<>();
    private final Set<String> seenTransactionIds = new HashSet<>();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final Timer refreshTimer;
    private final TransactionChartPanel chartPanel;
    private final JLabel countLabel = new JLabel();
    private final JLabel volumeLabel = new JLabel();
    private final JLabel amountLabel = new JLabel();
    private final JLabel averagePriceLabel = new JLabel();
    private final JLabel orderTypeLabel = new JLabel();
    private final JLabel lastUpdateLabel = new JLabel();
    private final DecimalFormat integerFormat = new DecimalFormat("#,##0");
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private boolean derivedViewsDirty = true;

    public TransactionHistoryPanel() {
        this(DEFAULT_MAX_ROWS, DEFAULT_MAX_CHART_POINTS);
    }

    public TransactionHistoryPanel(int maxTransactions, int maxChartPoints) {
        if (maxTransactions <= 0) {
            throw new IllegalArgumentException("maxTransactions must be positive");
        }
        this.maxTransactions = maxTransactions;
        this.statisticsModel = new TransactionStatisticsModel(maxTransactions);
        this.chartModel = new TransactionChartModel(maxChartPoints);
        for (View view : View.values()) {
            tableModels.put(view, new TransactionTableModel(maxTransactions));
        }

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(new Color(245, 245, 245));
        add(createSummaryPanel(), BorderLayout.NORTH);

        chartPanel = new TransactionChartPanel(chartModel);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createTableTabs(), chartPanel);
        splitPane.setResizeWeight(0.72);
        splitPane.setContinuousLayout(true);
        add(splitPane, BorderLayout.CENTER);

        refreshTimer = new Timer(REFRESH_DELAY_MS, event -> refreshDerivedViews());
        refreshTimer.setCoalesce(true);
        refreshDerivedViews();
    }

    public boolean addTransaction(Transaction transaction) {
        if (disposed.get() || !isValid(transaction)
                || !seenTransactionIds.add(transaction.getId())) {
            return false;
        }
        if (transactions.size() == maxTransactions) {
            transactions.removeFirst();
        }
        transactions.addLast(transaction);

        tableModels.get(View.ALL).addTransaction(transaction);
        tableModels.get(isBuyerInitiated(transaction) ? View.BUY : View.SELL)
                .addTransaction(transaction);
        tableModels.get(transaction.isMarketOrder() ? View.MARKET : View.LIMIT)
                .addTransaction(transaction);
        if (isPersonal(transaction)) {
            tableModels.get(View.PERSONAL).addTransaction(transaction);
        }
        statisticsModel.addTransaction(transaction);
        chartModel.addTransaction(transaction);
        derivedViewsDirty = true;
        return true;
    }

    public int addTransactions(List<Transaction> batch) {
        if (batch == null || batch.isEmpty() || disposed.get()) {
            return 0;
        }
        int added = 0;
        for (Transaction transaction : batch) {
            if (addTransaction(transaction)) {
                added++;
            }
        }
        return added;
    }

    public void clear() {
        transactions.clear();
        seenTransactionIds.clear();
        for (TransactionTableModel tableModel : tableModels.values()) {
            tableModel.clear();
        }
        statisticsModel.clear();
        chartModel.clear();
        derivedViewsDirty = true;
        refreshDerivedViews();
    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public TransactionTableModel getTableModel(View view) {
        return tableModels.get(view);
    }

    public TransactionStatisticsModel getStatisticsModel() {
        return statisticsModel;
    }

    public TransactionChartModel getChartModel() {
        return chartModel;
    }

    public void setRefreshActive(boolean active) {
        if (active && !disposed.get()) {
            refreshDerivedViews();
            if (!refreshTimer.isRunning()) {
                refreshTimer.start();
            }
        } else {
            refreshTimer.stop();
        }
    }

    public boolean isRefreshTimerRunning() {
        return refreshTimer.isRunning();
    }

    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            refreshTimer.stop();
        }
    }

    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 6, 8, 0));
        panel.setBackground(new Color(48, 63, 159));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        for (JLabel label : List.of(countLabel, volumeLabel, amountLabel, averagePriceLabel,
                orderTypeLabel, lastUpdateLabel)) {
            label.setForeground(Color.WHITE);
            label.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(label);
        }
        return panel;
    }

    private JTabbedPane createTableTabs() {
        JTabbedPane tabs = new JTabbedPane();
        addTableTab(tabs, "全部成交", View.ALL);
        addTableTab(tabs, "買方主動", View.BUY);
        addTableTab(tabs, "賣方主動", View.SELL);
        addTableTab(tabs, "我的成交", View.PERSONAL);
        addTableTab(tabs, "市價單", View.MARKET);
        addTableTab(tabs, "限價單", View.LIMIT);
        return tabs;
    }

    private void addTableTab(JTabbedPane tabs, String title, View view) {
        JTable table = new JTable(tableModels.get(view));
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(25);
        table.setShowVerticalLines(false);
        DefaultTableCellRenderer numberRenderer = new DefaultTableCellRenderer();
        numberRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        table.setDefaultRenderer(Double.class, numberRenderer);
        table.setDefaultRenderer(Integer.class, numberRenderer);
        tabs.addTab(title, new JScrollPane(table));
    }

    private void refreshDerivedViews() {
        if (!derivedViewsDirty) {
            return;
        }
        TransactionStatisticsModel.Statistics statistics = statisticsModel.snapshot();
        countLabel.setText("成交筆數  " + integerFormat.format(statistics.transactionCount()));
        volumeLabel.setText("成交量  " + integerFormat.format(statistics.totalVolume()));
        amountLabel.setText("成交額  " + priceFormat.format(statistics.totalAmount()));
        averagePriceLabel.setText("均價  " + priceFormat.format(statistics.averagePrice()));
        orderTypeLabel.setText("市價 / 限價  " + statistics.marketOrderCount()
                + " / " + statistics.limitOrderCount());
        lastUpdateLabel.setText("更新  " + timeFormat.format(new Date()));
        chartPanel.repaint();
        derivedViewsDirty = false;
    }

    private static boolean isValid(Transaction transaction) {
        return transaction != null
                && transaction.getId() != null
                && !transaction.getId().isBlank()
                && Double.isFinite(transaction.getPrice())
                && transaction.getPrice() > 0
                && transaction.getVolume() > 0;
    }

    private static boolean isPersonal(Transaction transaction) {
        return isPersonal(transaction.getBuyer())
                || isPersonal(transaction.getSeller())
                || "PERSONAL".equals(transaction.getInitiatingTraderType());
    }

    private static boolean isPersonal(Trader trader) {
        return trader != null && "PERSONAL".equals(trader.getTraderType());
    }

    private static boolean isBuyerInitiated(Transaction transaction) {
        return transaction.isBuyerInitiated()
                || "MARKET_BUY".equals(transaction.getOrderType())
                || "FOK_BUY".equals(transaction.getOrderType());
    }

    private static final class TransactionChartPanel extends JPanel {
        private final TransactionChartModel model;

        private TransactionChartPanel(TransactionChartModel model) {
            this.model = model;
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createTitledBorder("成交價格走勢"));
            setPreferredSize(new Dimension(380, 300));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            List<Transaction> points = model.snapshot();
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if (points.isEmpty()) {
                    g2.setColor(Color.GRAY);
                    g2.drawString("暫無成交資料", Math.max(12, getWidth() / 2 - 40), getHeight() / 2);
                    return;
                }

                int left = 44;
                int right = Math.max(left + 1, getWidth() - 18);
                int top = 28;
                int bottom = Math.max(top + 1, getHeight() - 36);
                double min = points.stream().mapToDouble(Transaction::getPrice).min().orElse(0);
                double max = points.stream().mapToDouble(Transaction::getPrice).max().orElse(min);
                double range = Math.max(0.01, max - min);

                g2.setColor(new Color(210, 210, 210));
                g2.drawLine(left, bottom, right, bottom);
                g2.drawLine(left, top, left, bottom);
                g2.setColor(new Color(33, 150, 243));
                g2.setStroke(new BasicStroke(1.5f));
                int previousX = left;
                int previousY = priceY(points.get(0).getPrice(), min, range, top, bottom);
                for (int index = 1; index < points.size(); index++) {
                    int x = left + index * (right - left) / Math.max(1, points.size() - 1);
                    int y = priceY(points.get(index).getPrice(), min, range, top, bottom);
                    g2.drawLine(previousX, previousY, x, y);
                    previousX = x;
                    previousY = y;
                }
                g2.setColor(Color.DARK_GRAY);
                g2.drawString(String.format("%.2f", max), 4, top + 5);
                g2.drawString(String.format("%.2f", min), 4, bottom);
            } finally {
                g2.dispose();
            }
        }

        private static int priceY(double price, double min, double range, int top, int bottom) {
            return bottom - (int) Math.round((price - min) / range * (bottom - top));
        }
    }
}
