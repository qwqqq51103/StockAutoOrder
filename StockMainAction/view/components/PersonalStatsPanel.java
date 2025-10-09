// === 3. 個人統計UI面板 ===
// 位置: view/components/PersonalStatsPanel.java
package StockMainAction.view.components;

import StockMainAction.model.core.PersonalStatistics;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.ExecutorService; // [PERF]
import java.util.concurrent.Executors; // [PERF]

/**
 * 個人統計面板組件
 */
public class PersonalStatsPanel extends JPanel {

    // 統計顯示標籤
    private JLabel totalProfitLabel;
    private JLabel todayProfitLabel;
    private JLabel portfolioValueLabel;
    private JLabel returnRateLabel;
    private JLabel winRateLabel;
    private JLabel totalTradesLabel;
    private JLabel maxDrawdownLabel;
    private JLabel avgProfitLabel;
    private JLabel maxProfitLabel;
    private JLabel maxLossLabel;

    // 詳細信息區域
    private JTextArea detailsArea;
    private JList<PersonalStatistics.TradeRecord> tradeHistoryList;
    private DefaultListModel<PersonalStatistics.TradeRecord> historyListModel;
    private JComboBox<PersonalStatistics.StatsPeriod> periodComboBox;

    // 控制按鈕
    private JButton refreshButton;
    private JButton resetButton;
    private JButton exportButton;

    // [PERF] 背景執行緒
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 事件監聽器接口
    public interface PersonalStatsPanelListener {

        void onRefreshStats();

        void onResetStats();

        void onExportStats();
    }

    private PersonalStatsPanelListener listener;

    public PersonalStatsPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("個人損益統計"));

        // 創建主要內容面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 上部：統計摘要
        JPanel summaryPanel = createSummaryPanel();

        // 中部：詳細統計和交易歷史
        JPanel detailsPanel = createDetailsPanel();

        // 下部：控制按鈕
        JPanel controlPanel = createControlPanel();

        mainPanel.add(summaryPanel, BorderLayout.NORTH);
        mainPanel.add(detailsPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * 創建統計摘要面板
     */
    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("統計摘要"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 第一行
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("總損益:"), gbc);
        gbc.gridx = 1;
        totalProfitLabel = new JLabel("0.00");
        totalProfitLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        panel.add(totalProfitLabel, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("今日損益:"), gbc);
        gbc.gridx = 3;
        todayProfitLabel = new JLabel("0.00");
        todayProfitLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        panel.add(todayProfitLabel, gbc);

        // 第二行
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("投資組合價值:"), gbc);
        gbc.gridx = 1;
        portfolioValueLabel = new JLabel("0.00");
        panel.add(portfolioValueLabel, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("總回報率:"), gbc);
        gbc.gridx = 3;
        returnRateLabel = new JLabel("0.00%");
        panel.add(returnRateLabel, gbc);

        // 第三行
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("交易次數:"), gbc);
        gbc.gridx = 1;
        totalTradesLabel = new JLabel("0");
        panel.add(totalTradesLabel, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("勝率:"), gbc);
        gbc.gridx = 3;
        winRateLabel = new JLabel("0.00%");
        panel.add(winRateLabel, gbc);

        // 第四行
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("最大回撤:"), gbc);
        gbc.gridx = 1;
        maxDrawdownLabel = new JLabel("0.00%");
        panel.add(maxDrawdownLabel, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("平均每筆損益:"), gbc);
        gbc.gridx = 3;
        avgProfitLabel = new JLabel("0.00");
        panel.add(avgProfitLabel, gbc);

        return panel;
    }

    /**
     * 創建詳細信息面板
     */
    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 左側：詳細統計
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("詳細統計"));

        detailsArea = new JTextArea(8, 25);
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        detailsArea.setBackground(new Color(250, 250, 250));

        JScrollPane detailsScrollPane = new JScrollPane(detailsArea);
        leftPanel.add(detailsScrollPane, BorderLayout.CENTER);

        // 右側：交易歷史
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("交易歷史"));

        // 時間段選擇
        JPanel periodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        periodPanel.add(new JLabel("時間段:"));
        periodComboBox = new JComboBox<>(PersonalStatistics.StatsPeriod.values());
        periodComboBox.addActionListener(e -> updateTradeHistory());
        periodPanel.add(periodComboBox);

        // 交易歷史列表
        historyListModel = new DefaultListModel<>();
        tradeHistoryList = new JList<>(historyListModel);
        tradeHistoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tradeHistoryList.setCellRenderer(new TradeRecordRenderer());

        JScrollPane historyScrollPane = new JScrollPane(tradeHistoryList);
        historyScrollPane.setPreferredSize(new Dimension(350, 200));

        rightPanel.add(periodPanel, BorderLayout.NORTH);
        rightPanel.add(historyScrollPane, BorderLayout.CENTER);

        // 組合左右面板
        panel.add(leftPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * 創建控制按鈕面板
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());

        refreshButton = new JButton("刷新統計");
        resetButton = new JButton("重置統計");
        exportButton = new JButton("導出報告");

        refreshButton.addActionListener(e -> {
            if (listener != null) {
                listener.onRefreshStats();
            }
        });

        resetButton.addActionListener(e -> {
            if (listener != null) {
                int result = JOptionPane.showConfirmDialog(this,
                        "確定要重置所有統計數據嗎？此操作無法撤銷！",
                        "確認重置", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    listener.onResetStats();
                }
            }
        });

        exportButton.addActionListener(e -> {
            if (listener != null) {
                listener.onExportStats();
            }
        });

        panel.add(refreshButton);
        panel.add(resetButton);
        panel.add(exportButton);

        return panel;
    }

    /**
     * 自定義交易記錄渲染器
     */
    private class TradeRecordRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof PersonalStatistics.TradeRecord) {
                PersonalStatistics.TradeRecord record = (PersonalStatistics.TradeRecord) value;

                // 根據交易類型設置顏色
                if (!isSelected) {
                    if ("買入".equals(record.getType())) {
                        setForeground(new Color(0, 100, 0)); // 深綠色
                    } else if ("賣出".equals(record.getType())) {
                        if (record.getProfitLoss() > 0) {
                            setForeground(new Color(0, 150, 0)); // 綠色獲利
                        } else if (record.getProfitLoss() < 0) {
                            setForeground(Color.RED); // 紅色虧損
                        } else {
                            setForeground(Color.BLACK); // 黑色平手
                        }
                    }
                }
            }

            return this;
        }
    }

    /**
     * 更新統計顯示
     */
    public void updateStatistics(PersonalStatistics stats) {
        // [PERF] 將重計算/格式化移出 EDT
        executor.submit(() -> {
            final String totalProfit = String.format("%.2f", stats.getTotalProfitLoss());
            final String todayProfit = String.format("%.2f", stats.getTodayProfitLoss());
            final String pv = String.format("%.2f", stats.getCurrentPortfolioValue());
            final String rr = String.format("%.2f%%", stats.getReturnRate());
            final String wr = String.format("%.1f%%", stats.getWinRate());
            final String tt = String.valueOf(stats.getTotalTrades());
            final String mdd = String.format("%.2f%%", stats.getMaxDrawdown());
            final String avg = String.format("%.2f", stats.getAvgProfitPerTrade());
            final Color profitColor = stats.getTotalProfitLoss() >= 0 ? new Color(0, 150, 0) : Color.RED;
            final Color todayColor = stats.getTodayProfitLoss() >= 0 ? new Color(0, 150, 0) : Color.RED;

            SwingUtilities.invokeLater(() -> {
                totalProfitLabel.setText(totalProfit);
                todayProfitLabel.setText(todayProfit);
                portfolioValueLabel.setText(pv);
                returnRateLabel.setText(rr);
                winRateLabel.setText(wr);
                totalTradesLabel.setText(tt);
                maxDrawdownLabel.setText(mdd);
                avgProfitLabel.setText(avg);
                totalProfitLabel.setForeground(profitColor);
                todayProfitLabel.setForeground(todayColor);
                updateDetailsArea(stats);
                updateTradeHistory(stats);
            });
        });
    }

    /**
     * 更新詳細統計區域
     */
    private void updateDetailsArea(PersonalStatistics stats) {
        StringBuilder details = new StringBuilder();
        details.append("=== 詳細統計 ===\n");
        details.append(String.format("初始資金: %.2f\n", stats.getInitialCash()));
        details.append(String.format("當前現金: %.2f\n", stats.getCurrentCash()));
        details.append(String.format("當前持股: %d股\n", stats.getCurrentHoldings()));
        details.append(String.format("平均成本價: %.2f\n", stats.getAvgCostPrice()));
        details.append(String.format("獲利交易: %d筆\n", stats.getWinningTrades()));
        details.append(String.format("虧損交易: %d筆\n", stats.getLosingTrades()));
        details.append(String.format("單筆最大獲利: %.2f\n", stats.getMaxSingleProfit()));
        details.append(String.format("單筆最大虧損: %.2f\n", stats.getMaxSingleLoss()));

        detailsArea.setText(details.toString());
    }

    /**
     * 更新交易歷史列表
     */
    private void updateTradeHistory() {
        if (listener != null) {
            listener.onRefreshStats(); // 觸發刷新，會調用updateTradeHistory(stats)
        }
    }

    private void updateTradeHistory(PersonalStatistics stats) {
        SwingUtilities.invokeLater(() -> {
            historyListModel.clear();

            PersonalStatistics.StatsPeriod selectedPeriod
                    = (PersonalStatistics.StatsPeriod) periodComboBox.getSelectedItem();

            List<PersonalStatistics.TradeRecord> records = stats.getTradesByPeriod(selectedPeriod);

            // 按時間倒序顯示（最新的在前）
            for (int i = records.size() - 1; i >= 0; i--) {
                historyListModel.addElement(records.get(i));
            }
        });
    }

    // Setter
    public void setListener(PersonalStatsPanelListener listener) {
        this.listener = listener;
    }
}
