// === 3. 快捷交易UI面板 ===
// 位置: view/components/QuickTradePanel.java
package StockMainAction.view.components;

import StockMainAction.model.core.QuickTradeConfig;
import StockMainAction.util.logging.MarketLogger;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 快捷交易面板組件
 */
public class QuickTradePanel extends JPanel implements AutoCloseable {

    // UI組件
    private JList<QuickTradeConfig> configList;
    private DefaultListModel<QuickTradeConfig> listModel;
    private JButton[] quickButtons;
    private JPanel quickButtonPanel; // 快捷按鈕容器（動態重建用）
    private JLabel currentPriceLabel;
    private JLabel availableFundsLabel;
    private JLabel currentHoldingsLabel;
    private JTextArea previewArea;
    private JButton executeButton;
    private JButton configButton;
    private JButton pumpButton;
    private JButton dumpButton;

    // 狀態變數
    private QuickTradeConfig selectedConfig;
    private double currentPrice = 0.0;
    private double availableFunds = 0.0;
    private int currentHoldings = 0;


    // 事件監聽器接口
    public interface QuickTradePanelListener {

        void onQuickTradeExecute(QuickTradeConfig config);

        void onConfigureQuickTrade();

        void onPreviewQuickTrade(QuickTradeConfig config);

        // 市場介入（主力/個人戶）：以「分批市價吃單」推動成交價
        void onPumpPrice(int totalQty, int slices, boolean useMainForce,
                boolean enableLiquidity, int depthLevels, double depthSpanPct,
                boolean enableCounterWallSelfTrade, boolean useOtherTraderFooting);

        void onDumpPrice(int totalQty, int slices, boolean useMainForce,
                boolean enableLiquidity, int depthLevels, double depthSpanPct,
                boolean enableCounterWallSelfTrade, boolean useOtherTraderFooting);
    }

    private QuickTradePanelListener listener;

    private static final MarketLogger logger = MarketLogger.getInstance();

    // 背景下單執行緒：使用 daemon thread，確保 JVM 退出時不會被此 pool 阻塞
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4),
            r -> {
                Thread t = new Thread(r, "QuickTrade-Executor");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean tradeInFlight = new AtomicBoolean();

    // 防抖 Timer：updateCurrentPrice / updateAvailableFunds / updateCurrentHoldings
    // 批次更新時，最多 50ms 後才觸發一次 updatePreview()，避免同一 tick 觸發三次重繪
    private final javax.swing.Timer previewDebounceTimer =
            new javax.swing.Timer(50, e -> updatePreview());

    public QuickTradePanel() {
        previewDebounceTimer.setRepeats(false);
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("快捷交易"));

        // 創建主要內容面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 上部：快捷按鈕區
        JPanel northPanel = new JPanel(new BorderLayout());
        createQuickButtonPanel(); // 建立空容器，存入 this.quickButtonPanel
        JPanel interventionPanel = createInterventionPanel();
        northPanel.add(quickButtonPanel, BorderLayout.CENTER);
        northPanel.add(interventionPanel, BorderLayout.SOUTH);

        // 中部：當前狀態和預覽
        JPanel statusAndPreviewPanel = createStatusAndPreviewPanel();

        // 下部：配置列表和控制按鈕
        JPanel configPanel = createConfigPanel();

        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(statusAndPreviewPanel, BorderLayout.CENTER);
        mainPanel.add(configPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * 建立快捷按鈕容器（初始為空，由 rebuildQuickButtons 填入按鈕）
     */
    private void createQuickButtonPanel() {
        quickButtonPanel = new JPanel(new GridLayout(2, 4, 5, 5));
        quickButtonPanel.setBorder(BorderFactory.createTitledBorder("快捷按鈕"));
        quickButtons = new JButton[0];
    }

    /**
     * 依目前配置清單動態重建所有快捷按鈕（支援任意數量）
     */
    private void rebuildQuickButtons(List<QuickTradeConfig> configs) {
        quickButtonPanel.removeAll();

        int count = configs.size();
        int cols  = Math.max(1, Math.min(4, count));
        int rows  = count == 0 ? 1 : (int) Math.ceil(count / (double) cols);
        quickButtonPanel.setLayout(new GridLayout(rows, cols, 5, 5));

        quickButtons = new JButton[count];
        for (int i = 0; i < count; i++) {
            QuickTradeConfig cfg = configs.get(i);
            String hotkey  = (cfg.getHotkey() != null && !cfg.getHotkey().isEmpty())
                             ? "<br><small>[" + cfg.getHotkey() + "]</small>" : "";
            String label   = "<html><center>" + escapeHtml(cfg.getName()) + hotkey + "</center></html>";

            JButton btn = new JButton(label);
            btn.setPreferredSize(new Dimension(120, 40));
            btn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 10));

            if (cfg.isBuy()) {
                btn.setBackground(new Color(220, 255, 220));
                btn.setForeground(new Color(0, 100, 0));
            } else {
                btn.setBackground(new Color(255, 220, 220));
                btn.setForeground(new Color(150, 0, 0));
            }
            final int idx = i;
            btn.addActionListener(e -> executeQuickTrade(idx));

            quickButtons[i] = btn;
            quickButtonPanel.add(btn);
        }

        quickButtonPanel.revalidate();
        quickButtonPanel.repaint();
    }

    /** HTML 特殊字元轉義（防止名稱含 < > 破壞按鈕顯示） */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * 市場介入（實驗/教學用途）：拉抬/出貨（打壓）
     * 注意：價格推動必須靠「成交」，所以這裡以「分批市價單」做為主要推動手段。
     */
    private JPanel createInterventionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("市場介入（實驗）"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        pumpButton = new JButton("拉抬(分批市價買)");
        pumpButton.setBackground(new Color(210, 255, 210));
        pumpButton.setForeground(new Color(0, 90, 0));

        dumpButton = new JButton("出貨/打壓(分批市價賣)");
        dumpButton.setBackground(new Color(255, 210, 210));
        dumpButton.setForeground(new Color(140, 0, 0));

        pumpButton.addActionListener(e -> showInterventionDialog(true));
        dumpButton.addActionListener(e -> showInterventionDialog(false));

        gc.gridx = 0;
        gc.gridy = 0;
        panel.add(pumpButton, gc);
        gc.gridx = 1;
        panel.add(dumpButton, gc);

        return panel;
    }

    private void showInterventionDialog(boolean pump) {
        try {
            JPanel p = new JPanel(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4, 6, 4, 6);
            gc.anchor = GridBagConstraints.WEST;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1.0;

            JSpinner qty = new JSpinner(new SpinnerNumberModel(5000, 1, 1_000_000, 100));
            JSpinner slices = new JSpinner(new SpinnerNumberModel(10, 1, 200, 1));
            JCheckBox useMainForce = new JCheckBox("使用主力帳戶（建議）", true);
            JCheckBox enableLiquidity = new JCheckBox("先補深度(掛牆/墊腳石)", true);
            JCheckBox enableCounterWallSelfTrade = new JCheckBox("進階模式：對手牆 + 自成交", false);
            JCheckBox useOtherTraderFooting = new JCheckBox("打壓買墊腳改由另一交易者掛(預設關)", false);
            JSpinner depthLevels = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
            JSpinner depthSpan = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 10.0, 0.1)); // %

            gc.gridx = 0; gc.gridy = 0;
            p.add(new JLabel("總量(股):"), gc);
            gc.gridx = 1;
            p.add(qty, gc);

            gc.gridx = 0; gc.gridy = 1;
            p.add(new JLabel("分批數:"), gc);
            gc.gridx = 1;
            p.add(slices, gc);

            gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2;
            p.add(useMainForce, gc);

            gc.gridx = 0; gc.gridy = 3; gc.gridwidth = 2;
            p.add(enableLiquidity, gc);

            gc.gridwidth = 1;
            gc.gridx = 0; gc.gridy = 4;
            gc.gridwidth = 2;
            p.add(enableCounterWallSelfTrade, gc);

            gc.gridx = 0; gc.gridy = 5;
            p.add(useOtherTraderFooting, gc);

            gc.gridwidth = 1;
            gc.gridx = 0; gc.gridy = 6;
            p.add(new JLabel("補深度檔數:"), gc);
            gc.gridx = 1;
            p.add(depthLevels, gc);

            gc.gridx = 0; gc.gridy = 7;
            p.add(new JLabel("補深度跨度(%):"), gc);
            gc.gridx = 1;
            p.add(depthSpan, gc);

            String title = pump ? "拉抬設定" : "出貨/打壓設定";
            int ok = JOptionPane.showConfirmDialog(this, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;

            int totalQty = ((Number) qty.getValue()).intValue();
            int nSlices = ((Number) slices.getValue()).intValue();
            boolean useMF = useMainForce.isSelected();
            boolean enLiq = enableLiquidity.isSelected();
            boolean enCounterSelf = enableCounterWallSelfTrade.isSelected();
            boolean useOtherFooting = useOtherTraderFooting.isSelected();
            int lvl = ((Number) depthLevels.getValue()).intValue();
            double spanPct = ((Number) depthSpan.getValue()).doubleValue() / 100.0;

            if (listener == null) return;
            if (pump) listener.onPumpPrice(totalQty, nSlices, useMF, enLiq, lvl, spanPct, enCounterSelf, useOtherFooting);
            else listener.onDumpPrice(totalQty, nSlices, useMF, enLiq, lvl, spanPct, enCounterSelf, useOtherFooting);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "介入操作失敗：" + ex.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 創建狀態和預覽面板
     */
    private JPanel createStatusAndPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 左側：當前狀態
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("當前狀態"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        statusPanel.add(new JLabel("當前價格:"), gbc);
        gbc.gridx = 1;
        currentPriceLabel = new JLabel("0.00");
        currentPriceLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        statusPanel.add(currentPriceLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        statusPanel.add(new JLabel("可用資金:"), gbc);
        gbc.gridx = 1;
        availableFundsLabel = new JLabel("0.00");
        statusPanel.add(availableFundsLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        statusPanel.add(new JLabel("當前持股:"), gbc);
        gbc.gridx = 1;
        currentHoldingsLabel = new JLabel("0");
        statusPanel.add(currentHoldingsLabel, gbc);

        // 右側：交易預覽
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder("交易預覽"));

        previewArea = new JTextArea(6, 30);
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        previewArea.setBackground(new Color(250, 250, 250));
        previewArea.setText("請選擇快捷交易配置...");

        JScrollPane previewScrollPane = new JScrollPane(previewArea);
        previewPanel.add(previewScrollPane, BorderLayout.CENTER);

        // 執行按鈕
        executeButton = new JButton("執行交易");
        executeButton.setPreferredSize(new Dimension(100, 30));
        executeButton.setFont(new Font("Microsoft JhengHei", Font.BOLD, 12));
        executeButton.setEnabled(false);
        executeButton.addActionListener(e -> executeSelectedTrade());
        // [UX] Enter 送單 / Esc 清空
        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        previewArea.getInputMap(JComponent.WHEN_FOCUSED).put(enter, "doExec");
        previewArea.getActionMap().put("doExec", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ executeSelectedTrade(); }
        });
        previewArea.getInputMap(JComponent.WHEN_FOCUSED).put(esc, "doClear");
        previewArea.getActionMap().put("doClear", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ clearPreview(); }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(executeButton);
        previewPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 組合左右面板
        panel.add(statusPanel, BorderLayout.WEST);
        panel.add(previewPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 創建配置面板
     */
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("快捷交易配置"));

        // 配置列表
        listModel = new DefaultListModel<>();
        configList = new JList<>(listModel);
        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setCellRenderer(new QuickTradeConfigRenderer());
        configList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedConfig = configList.getSelectedValue();
                updatePreview();
            }
        });

        JScrollPane listScrollPane = new JScrollPane(configList);
        listScrollPane.setPreferredSize(new Dimension(400, 100));

        // 控制按鈕
        JPanel controlPanel = new JPanel(new FlowLayout());

        configButton = new JButton("配置管理");
        configButton.setEnabled(true);
        configButton.setToolTipText("開啟快捷交易配置管理（新增 / 編輯 / 刪除策略組）");
        configButton.addActionListener(e -> {
            if (listener != null) {
                listener.onConfigureQuickTrade();
            }
        });

        JButton helpButton = new JButton("快捷鍵說明");
        helpButton.addActionListener(e -> showHotkeyHelp());

        JButton refreshButton = new JButton("刷新預覽");
        refreshButton.addActionListener(e -> updatePreview());

        controlPanel.add(configButton);
        controlPanel.add(helpButton);
        controlPanel.add(refreshButton);

        panel.add(listScrollPane, BorderLayout.CENTER);
        panel.add(controlPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 自定義配置渲染器
     */
    private class QuickTradeConfigRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof QuickTradeConfig) {
                QuickTradeConfig config = (QuickTradeConfig) value;

                // 方案一：如果 QuickTradeConfig 有 getTradeType() 方法
                String tradeTypeDisplay;
                try {
                    tradeTypeDisplay = config.getTradeType().getDisplayName();
                } catch (Exception e) {
                    // 如果 getTradeType() 不存在，使用 isBuy() 作為備選
                    tradeTypeDisplay = config.isBuy() ? "買入" : "賣出";
                }

                String text = String.format("%s [%s] - %s",
                        config.getName(),
                        config.getHotkey().isEmpty() ? "無快捷鍵" : config.getHotkey(),
                        tradeTypeDisplay
                );
                setText(text);

                // 根據交易類型設置顏色（只在未選中時）
                if (!isSelected) {
                    if (config.isBuy()) {
                        setForeground(new Color(0, 120, 0));  // 深綠色，更好的可讀性
                    } else {
                        setForeground(new Color(180, 0, 0));  // 深紅色，更好的可讀性
                    }
                }
            }

            return this;
        }
    }

    /**
     * 執行快捷交易
     */
    private void executeQuickTrade(int buttonIndex) {
        if (listener == null) {
            logger.warn("快捷交易觸發失敗：listener 為 null，index=" + buttonIndex, "QUICK_TRADE");
            return;
        }

        QuickTradeConfig config = getQuickButtonConfig(buttonIndex);
        if (config != null) {
            selectedConfig = config;
            updatePreview();

            if (config.isAutoExecute()) {
                submitQuickTrade(config);
            } else {
                executeButton.setEnabled(true);
            }
        } else {
            logger.warn("快捷交易配置不存在，index=" + buttonIndex, "QUICK_TRADE");
        }
    }

    /**
     * 獲取快捷按鈕對應的配置
     */
    /**
     * 依索引從配置清單取得配置（直接讀 listModel，不再硬編碼）
     */
    private QuickTradeConfig getQuickButtonConfig(int buttonIndex) {
        if (buttonIndex >= 0 && buttonIndex < listModel.getSize()) {
            return listModel.getElementAt(buttonIndex);
        }
        return null;
    }


    /**
     * 執行選中的交易
     */
    private void executeSelectedTrade() {
        if (selectedConfig == null || listener == null || !executeButton.isEnabled()) return;
        submitQuickTrade(selectedConfig);
    }

    private void submitQuickTrade(QuickTradeConfig config) {
        if (!tradeInFlight.compareAndSet(false, true)) {
            showSubmissionBusy();
            return;
        }
        executeButton.setEnabled(false);
        try {
            executor.execute(() -> {
                try {
                    listener.onQuickTradeExecute(config);
                } catch (RuntimeException failure) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            "快捷交易失敗：" + failure.getMessage(), "交易失敗",
                            JOptionPane.ERROR_MESSAGE));
                } finally {
                    tradeInFlight.set(false);
                    SwingUtilities.invokeLater(this::updatePreview);
                }
            });
        } catch (RejectedExecutionException rejected) {
            tradeInFlight.set(false);
            showSubmissionBusy();
        }
    }

    private void showSubmissionBusy() {
        Runnable message = () -> {
            executeButton.setEnabled(false);
            previewArea.append("\n系統忙碌，交易未提交，請稍後再試。");
        };
        if (SwingUtilities.isEventDispatchThread()) message.run();
        else SwingUtilities.invokeLater(message);
    }

    /**
     * 更新交易預覽
     */
    private void updatePreview() {
        if (selectedConfig == null) {
            previewArea.setText("請選擇快捷交易配置...");
            executeButton.setEnabled(false);
            return;
        }

        StringBuilder preview = new StringBuilder();
        preview.append("=== 交易預覽 ===\n");
        preview.append(String.format("配置名稱: %s\n", selectedConfig.getName()));
        preview.append(String.format("交易類型: %s\n", selectedConfig.isBuy() ? "買入" : "賣出"));

        // 計算交易數量和金額
        int quantity = calculateTradeQuantity(selectedConfig);
        double amount = quantity * currentPrice;

        preview.append(String.format("交易數量: %d 股\n", quantity));
        preview.append(String.format("預估金額: %.2f 元\n", amount));

        // 計算交易後狀態
        if (selectedConfig.isBuy()) {
            preview.append(String.format("交易後資金: %.2f 元\n", availableFunds - amount));
            preview.append(String.format("交易後持股: %d 股\n", currentHoldings + quantity));
        } else {
            preview.append(String.format("交易後資金: %.2f 元\n", availableFunds + amount));
            preview.append(String.format("交易後持股: %d 股\n", currentHoldings - quantity));
        }

        // 風險提醒
        if (selectedConfig.isBuy() && amount > availableFunds) {
            preview.append("\n⚠️ 警告: 資金不足！");
        } else if (!selectedConfig.isBuy() && quantity > currentHoldings) {
            preview.append("\n⚠️ 警告: 持股不足！");
        } else {
            preview.append("\n✅ 可以執行此交易");
            executeButton.setEnabled(!tradeInFlight.get());
        }

        previewArea.setText(preview.toString());
    }

    /**
     * 計算交易數量
     */
    private int calculateTradeQuantity(QuickTradeConfig config) {
        if (config.getFixedQuantity() > 0) {
            return config.getFixedQuantity();
        } else if (config.getPercentage() > 0) {
            if (config.isBuy()) {
                // 買入：根據可用資金百分比計算
                double funds = availableFunds * config.getPercentage() / 100.0;
                return (int) (funds / currentPrice);
            } else {
                // 賣出：根據持股百分比計算
                return (int) (currentHoldings * config.getPercentage() / 100.0);
            }
        }
        return 0;
    }

    /**
     * 顯示快捷鍵說明
     */
    private void showHotkeyHelp() {
        String helpText
                = "=== 快捷鍵說明 ===\n\n"
                + "F1 - 買入100股\n"
                + "F2 - 賣出100股\n"
                + "F3 - 用50%資金買入\n"
                + "F4 - 賣出50%持股\n"
                + "Ctrl+B - 全倉買入\n"
                + "Ctrl+S - 全倉賣出\n"
                + "Ctrl+Q - 智能買入(30%資金)\n"
                + "Ctrl+W - 智能賣出(30%持股)\n\n"
                + "注意：快捷鍵需要在主視窗獲得焦點時才能使用";

        JOptionPane.showMessageDialog(this, helpText, "快捷鍵說明",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // === 公開方法 ===
    /**
     * 設置事件監聽器
     */
    public void setListener(QuickTradePanelListener listener) {
        this.listener = listener;
    }

    /**
     * 更新當前價格（批次防抖：50ms 內多次呼叫只觸發一次 updatePreview）
     */
    public void updateCurrentPrice(double price) {
        this.currentPrice = price;
        currentPriceLabel.setText(String.format("%.2f", price));
        currentPriceLabel.setForeground(price > 0 ? Color.BLACK : Color.RED);
        previewDebounceTimer.restart();
    }

    /**
     * 更新可用資金（批次防抖）
     */
    public void updateAvailableFunds(double funds) {
        this.availableFunds = funds;
        availableFundsLabel.setText(String.format("%.2f", funds));
        previewDebounceTimer.restart();
    }

    /**
     * 更新當前持股（批次防抖）
     */
    public void updateCurrentHoldings(int holdings) {
        this.currentHoldings = holdings;
        currentHoldingsLabel.setText(String.valueOf(holdings));
        previewDebounceTimer.restart();
    }

    /**
     * 載入快捷交易配置列表
     */
    public void loadQuickTradeConfigs(List<QuickTradeConfig> configs) {
        listModel.clear();
        for (QuickTradeConfig config : configs) {
            listModel.addElement(config);
        }
        if (!configs.isEmpty()) {
            configList.setSelectedIndex(0);
        }
        // 重建快捷按鈕（支援任意數量、名稱同步）
        rebuildQuickButtons(configs);
    }

    /**
     * 註冊快捷鍵
     */
    public void registerHotkeys(JFrame parentFrame) {
        // 註冊功能鍵
        registerHotkey(parentFrame, "F1", KeyEvent.VK_F1, 0, () -> executeQuickTrade(0));
        registerHotkey(parentFrame, "F2", KeyEvent.VK_F2, 0, () -> executeQuickTrade(1));
        registerHotkey(parentFrame, "F3", KeyEvent.VK_F3, 0, () -> executeQuickTrade(2));
        registerHotkey(parentFrame, "F4", KeyEvent.VK_F4, 0, () -> executeQuickTrade(3));

        // 註冊組合鍵
        registerHotkey(parentFrame, "Ctrl+B", KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK,
                () -> executeQuickTrade(4));
        registerHotkey(parentFrame, "Ctrl+S", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK,
                () -> executeQuickTrade(5));
        registerHotkey(parentFrame, "Ctrl+Q", KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK,
                () -> executeQuickTrade(6));
        registerHotkey(parentFrame, "Ctrl+W", KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK,
                () -> executeQuickTrade(7));
    }

    /**
     * 註冊單個快捷鍵
     */
    private void registerHotkey(JFrame frame, String name, int keyCode, int modifiers,
            Runnable action) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers);
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(keyStroke, name);
        frame.getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /**
     * 啟用/禁用快捷交易功能
     */
    public void setQuickTradeEnabled(boolean enabled) {
        for (JButton button : quickButtons) {
            button.setEnabled(enabled);
        }
        executeButton.setEnabled(enabled && selectedConfig != null);
        // configButton 不受 enabled 影響，由 QuickTradeConfigDialog 決定是否開放
        configButton.setEnabled(true);
    }

    /**
     * 清除預覽
     */
    public void clearPreview() {
        selectedConfig = null;
        previewArea.setText("請選擇快捷交易配置...");
        executeButton.setEnabled(false);
        configList.clearSelection();
    }

    /**
     * 重置所有狀態
     */
    public void reset() {
        currentPrice = 0.0;
        availableFunds = 0.0;
        currentHoldings = 0;

        currentPriceLabel.setText("0.00");
        availableFundsLabel.setText("0.00");
        currentHoldingsLabel.setText("0");

        clearPreview();
    }

    /**
     * 當面板從容器移除時（例如視窗關閉），立即釋放背景執行緒資源。
     */
    @Override
    public void removeNotify() {
        super.removeNotify();
        previewDebounceTimer.stop();
    }

    @Override
    public void close() {
        previewDebounceTimer.stop();
        executor.shutdownNow();
        logger.info("QuickTradePanel executor 已關閉", "QUICK_TRADE");
    }
}
