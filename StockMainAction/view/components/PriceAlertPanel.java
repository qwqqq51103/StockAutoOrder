// === 3. 價格提醒UI組件 ===
// 位置: view/components/PriceAlertPanel.java
package StockMainAction.view.components;

import StockMainAction.controller.PriceAlertManager;
import StockMainAction.model.core.PriceAlert;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * 價格提醒面板組件 - 只負責UI顯示
 */
public class PriceAlertPanel extends JPanel {

    private JTextField alertPriceField;
    private JComboBox<String> alertTypeCombo;
    private JCheckBox soundAlertCheckBox;
    private JCheckBox popupAlertCheckBox;
    private JList<PriceAlert> alertList;
    private DefaultListModel<PriceAlert> alertListModel;
    private JLabel currentPriceLabel;

    // 事件監聽器接口
    public interface PriceAlertPanelListener {

        void onAddAlert(double targetPrice, PriceAlert.AlertType type, boolean sound, boolean popup);

        void onRemoveAlert(int index);

        void onClearAllAlerts();
    }

    private PriceAlertPanelListener listener;

    public PriceAlertPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("價格提醒"));

        // 當前價格顯示
        currentPriceLabel = new JLabel("當前價格: 0.00");
        currentPriceLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));

        // 設置面板
        JPanel settingsPanel = createSettingsPanel();

        // [FIX] 列表面板 + 自訂渲染器
        alertListModel = new DefaultListModel<>();
        alertList = new JList<>(alertListModel);
        alertList.setCellRenderer(new AlertListRenderer());
        JScrollPane listScrollPane = new JScrollPane(alertList);
        listScrollPane.setPreferredSize(new Dimension(0, 160));
        listScrollPane.setBorder(BorderFactory.createTitledBorder("目前提醒列表"));

        add(currentPriceLabel, BorderLayout.NORTH);
        add(settingsPanel, BorderLayout.CENTER);
        add(listScrollPane, BorderLayout.SOUTH);
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // 提醒類型
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("提醒類型:"), gbc);
        gbc.gridx = 1;
        alertTypeCombo = new JComboBox<>(new String[]{"高於指定價格", "低於指定價格", "上漲百分比", "下跌百分比"});
        panel.add(alertTypeCombo, gbc);

        // 目標價格
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("目標值:"), gbc);
        gbc.gridx = 1;
        alertPriceField = new JTextField(10);
        // [UX] 即時驗證：僅允許數字與小數點；>0 才能啟用新增
        JButton[] addBtnHolder = new JButton[1];
        alertPriceField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void validateNow() {
                String t = alertPriceField.getText().trim();
                boolean ok;
                try { ok = !t.isEmpty() && Double.parseDouble(t) > 0; }
                catch (Exception ex) { ok = false; }
                if (addBtnHolder[0] != null) addBtnHolder[0].setEnabled(ok);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateNow(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateNow(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateNow(); }
        });
        panel.add(alertPriceField, gbc);

        // 提醒方式
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("提醒方式:"), gbc);
        gbc.gridx = 1;
        JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        soundAlertCheckBox = new JCheckBox("音效", true);
        popupAlertCheckBox = new JCheckBox("彈窗", true);
        checkBoxPanel.add(soundAlertCheckBox);
        checkBoxPanel.add(popupAlertCheckBox);
        panel.add(checkBoxPanel, gbc);

        // 按鈕
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton addButton = new JButton("添加提醒");
        addButton.setEnabled(false); // [UX] 初始不可用，驗證通過才啟用
        addBtnHolder[0] = addButton;
        JButton removeButton = new JButton("刪除選中");
        JButton clearButton = new JButton("清空全部");

        addButton.addActionListener(e -> handleAddAlert());
        removeButton.addActionListener(e -> handleRemoveAlert());
        clearButton.addActionListener(e -> handleClearAll());

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private void handleAddAlert() {
        if (listener == null) {
            return;
        }

        try {
            double targetPrice = Double.parseDouble(alertPriceField.getText().trim());
            int typeIndex = alertTypeCombo.getSelectedIndex();
            PriceAlert.AlertType alertType = PriceAlert.AlertType.values()[typeIndex];

            listener.onAddAlert(targetPrice, alertType,
                    soundAlertCheckBox.isSelected(),
                    popupAlertCheckBox.isSelected());

            alertPriceField.setText(""); // 清空輸入框

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "請輸入有效的數字", "錯誤", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleRemoveAlert() {
        if (listener != null && alertList.getSelectedIndex() >= 0) {
            listener.onRemoveAlert(alertList.getSelectedIndex());
        }
    }

    private void handleClearAll() {
        if (listener != null) {
            listener.onClearAllAlerts();
        }
    }

    // UI更新方法
    public void updateCurrentPrice(double price) {
        SwingUtilities.invokeLater(() -> {
            currentPriceLabel.setText(String.format("當前價格: %.2f", price));
        });
    }

    public void updateAlertList(java.util.List<PriceAlert> alerts) {
        SwingUtilities.invokeLater(() -> {
            alertListModel.clear();
            for (PriceAlert alert : alerts) {
                alertListModel.addElement(alert);
            }
        });
    }

    public void setListener(PriceAlertPanelListener listener) {
        this.listener = listener;
    }

    /**
     * [FIX] 自訂警示列表渲染器：依觸發狀態與類型著色，並附帶 Tooltip
     */
    private static class AlertListRenderer extends DefaultListCellRenderer {
        private static final Color COLOR_UP   = new Color(0, 128, 0);
        private static final Color COLOR_DOWN = new Color(200, 0, 0);
        private static final Color COLOR_DONE = new Color(130, 130, 130);
        private static final Color BG_DONE    = new Color(245, 245, 245);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof PriceAlert) {
                PriceAlert alert = (PriceAlert) value;
                setText(alert.toString());

                if (!isSelected) {
                    if (alert.isTriggered()) {
                        setForeground(COLOR_DONE);
                        setBackground(BG_DONE);
                    } else {
                        switch (alert.getType()) {
                            case ABOVE:
                            case CHANGE_UP:
                                setForeground(COLOR_UP);
                                break;
                            case BELOW:
                            case CHANGE_DOWN:
                                setForeground(COLOR_DOWN);
                                break;
                            default:
                                setForeground(Color.BLACK);
                        }
                    }
                }

                // Tooltip：顯示完整資訊
                String modes = (alert.isSoundEnabled() ? "音效 " : "") +
                               (alert.isPopupEnabled() ? "彈窗" : "");
                setToolTipText(String.format("[%s] 目標: %.2f  提醒: %s  狀態: %s",
                        alert.getType().getDisplayName(),
                        alert.getTargetPrice(),
                        modes.trim().isEmpty() ? "無" : modes.trim(),
                        alert.isTriggered() ? "已觸發" : "等待中"));
            }
            return this;
        }
    }
}
