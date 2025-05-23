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

        // 列表面板
        alertListModel = new DefaultListModel<>();
        alertList = new JList<>(alertListModel);
        JScrollPane listScrollPane = new JScrollPane(alertList);

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
}
