package StockMainAction.view.components;

import StockMainAction.controller.QuickTradeManager;
import StockMainAction.model.core.QuickTradeConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import StockMainAction.view.swing.WindowSizing;

/**
 * 快捷交易配置管理對話框
 * 支援新增、複製、刪除、編輯各項 QuickTradeConfig，
 * 關閉後由呼叫端從 QuickTradeManager 重新讀取最新配置。
 */
public class QuickTradeConfigDialog extends JDialog {

    private final QuickTradeManager manager;

    // 左側列表
    private final DefaultListModel<QuickTradeConfig> listModel = new DefaultListModel<>();
    private final JList<QuickTradeConfig> configList = new JList<>(listModel);

    // 右側編輯欄位
    private final JTextField nameField        = new JTextField(18);
    private final JComboBox<String> dirCombo  = new JComboBox<>(new String[]{"買入", "賣出"});
    private final JComboBox<QuickTradeConfig.QuickTradeType> typeCombo =
            new JComboBox<>(QuickTradeConfig.QuickTradeType.values());
    private final JSpinner quantitySpinner    = new JSpinner(new SpinnerNumberModel(100, 1, 100000, 100));
    private final JSpinner percentageSpinner  = new JSpinner(new SpinnerNumberModel(50.0, 0.1, 100.0, 5.0));
    private final JComboBox<QuickTradeConfig.PriceStrategy> priceCombo =
            new JComboBox<>(QuickTradeConfig.PriceStrategy.values());
    private final JSpinner offsetSpinner      = new JSpinner(new SpinnerNumberModel(0.0, -50.0, 50.0, 0.5));
    private final JCheckBox pctOffsetCheck    = new JCheckBox("百分比偏移", true);
    private final JTextField hotkeyField      = new JTextField(8);
    private final JCheckBox autoExecCheck     = new JCheckBox("自動執行（無需確認）");

    // 下方操作按鈕
    private final JButton addBtn    = new JButton("＋ 新增");
    private final JButton copyBtn   = new JButton("複製");
    private final JButton deleteBtn = new JButton("✕ 刪除");
    private final JButton saveBtn   = new JButton("儲存變更");

    private boolean dirty = false; // 是否有未儲存變更

    public QuickTradeConfigDialog(Frame parent, QuickTradeManager manager) {
        super(parent, "快捷交易配置管理", true);
        this.manager = manager;
        buildUI();
        loadConfigs();
        WindowSizing.apply(this, new Dimension(720, 480),
                new Dimension(600, 400), parent);
    }

    // ─── UI 建立 ────────────────────────────────────────────
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        root.add(buildListPanel(),  BorderLayout.WEST);
        root.add(buildEditPanel(),  BorderLayout.CENTER);
        root.add(buildBottomBar(), BorderLayout.SOUTH);

        setContentPane(root);

        // 選取變化 → 載入編輯欄位
        configList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelected();
        });

        // 類型變化 → 調整欄位可用性
        typeCombo.addActionListener(e -> refreshFieldVisibility());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent ev) {
                confirmClose();
            }
        });
    }

    private JPanel buildListPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setPreferredSize(new Dimension(200, 0));
        p.setBorder(BorderFactory.createTitledBorder("配置列表"));

        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        configList.setCellRenderer(new ConfigListRenderer());

        p.add(new JScrollPane(configList), BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addBtn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        copyBtn.setFont(addBtn.getFont());
        deleteBtn.setFont(addBtn.getFont());
        deleteBtn.setForeground(new Color(200, 0, 0));
        btnRow.add(addBtn);
        btnRow.add(copyBtn);
        btnRow.add(deleteBtn);
        p.add(btnRow, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> createNewConfig());
        copyBtn.addActionListener(e -> copySelected());
        deleteBtn.addActionListener(e -> deleteSelected());

        return p;
    }

    private JPanel buildEditPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("編輯選中項目"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(p, g, row++, "名稱",       nameField);
        addRow(p, g, row++, "方向",       dirCombo);
        addRow(p, g, row++, "交易類型",   typeCombo);
        addRow(p, g, row++, "固定數量",   quantitySpinner);
        addRow(p, g, row++, "百分比 (%)", percentageSpinner);
        addRow(p, g, row++, "價格策略",   priceCombo);
        addRow(p, g, row++, "偏移值",     buildOffsetRow());
        addRow(p, g, row++, "快捷鍵",     hotkeyField);
        addRow(p, g, row++, "",           autoExecCheck);

        // 儲存按鈕
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        saveBtn.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        saveBtn.setBackground(new Color(33, 150, 243));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setOpaque(true);
        p.add(saveBtn, g);
        saveBtn.addActionListener(e -> saveSelected());

        return p;
    }

    private JPanel buildOffsetRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(offsetSpinner);
        row.add(pctOffsetCheck);
        return row;
    }

    private void addRow(JPanel p, GridBagConstraints g, int row, String label, Component comp) {
        g.gridwidth = 1; g.fill = GridBagConstraints.NONE;
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL;
        p.add(comp, g);
    }

    private JPanel buildBottomBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton closeBtn = new JButton("關閉");
        closeBtn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        closeBtn.addActionListener(e -> confirmClose());
        p.add(closeBtn);
        return p;
    }

    // ─── 資料操作 ────────────────────────────────────────────
    private void loadConfigs() {
        listModel.clear();
        for (QuickTradeConfig c : manager.getAllConfigs()) {
            listModel.addElement(c);
        }
        if (!listModel.isEmpty()) configList.setSelectedIndex(0);
        dirty = false;
    }

    private void loadSelected() {
        QuickTradeConfig c = configList.getSelectedValue();
        if (c == null) { setEditEnabled(false); return; }
        setEditEnabled(true);
        nameField.setText(c.getName());
        dirCombo.setSelectedItem(c.isBuy() ? "買入" : "賣出");
        typeCombo.setSelectedItem(c.getTradeType());
        quantitySpinner.setValue(c.getFixedQuantity());
        percentageSpinner.setValue(c.getPercentage());
        priceCombo.setSelectedItem(c.getPriceStrategy());
        offsetSpinner.setValue(c.getPriceOffset());
        pctOffsetCheck.setSelected(c.isUsePercentageOffset());
        hotkeyField.setText(c.getHotkey());
        autoExecCheck.setSelected(c.isAutoExecute());
        refreshFieldVisibility();
    }

    private void saveSelected() {
        QuickTradeConfig c = configList.getSelectedValue();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "請先選擇要儲存的配置", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "名稱不可為空", "驗證失敗", JOptionPane.ERROR_MESSAGE);
            nameField.requestFocus();
            return;
        }
        c.setName(name);
        c.setBuy("買入".equals(dirCombo.getSelectedItem()));
        c.setTradeType((QuickTradeConfig.QuickTradeType) typeCombo.getSelectedItem());
        c.setFixedQuantity((Integer) quantitySpinner.getValue());
        c.setPercentage((Double) percentageSpinner.getValue());
        c.setPriceStrategy((QuickTradeConfig.PriceStrategy) priceCombo.getSelectedItem());
        c.setPriceOffset((Double) offsetSpinner.getValue());
        c.setUsePercentageOffset(pctOffsetCheck.isSelected());
        c.setHotkey(hotkeyField.getText().trim());
        c.setAutoExecute(autoExecCheck.isSelected());
        // 重新整理列表顯示（通知 model 更新）
        int idx = configList.getSelectedIndex();
        listModel.set(idx, c);
        dirty = true;
        JOptionPane.showMessageDialog(this, "「" + name + "」已儲存", "儲存成功", JOptionPane.INFORMATION_MESSAGE);
    }

    private void createNewConfig() {
        String name = JOptionPane.showInputDialog(this, "請輸入新配置名稱：", "新增配置", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        QuickTradeConfig newCfg = new QuickTradeConfig(
                name.trim(),
                QuickTradeConfig.QuickTradeType.FIXED_QUANTITY,
                QuickTradeConfig.PriceStrategy.CURRENT_PRICE,
                true);
        manager.addQuickTradeConfig(newCfg);
        listModel.addElement(newCfg);
        configList.setSelectedValue(newCfg, true);
        dirty = true;
    }

    private void copySelected() {
        QuickTradeConfig c = configList.getSelectedValue();
        if (c == null) return;
        QuickTradeConfig copy = c.copy();
        copy.setName(c.getName() + "_副本");
        manager.addQuickTradeConfig(copy);
        listModel.addElement(copy);
        configList.setSelectedValue(copy, true);
        dirty = true;
    }

    private void deleteSelected() {
        QuickTradeConfig c = configList.getSelectedValue();
        if (c == null) return;
        if (listModel.getSize() <= 1) {
            JOptionPane.showMessageDialog(this, "至少需保留一個配置", "無法刪除", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "確定刪除「" + c.getName() + "」？", "確認刪除", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        manager.removeQuickTradeConfig(c);
        listModel.removeElement(c);
        if (!listModel.isEmpty()) configList.setSelectedIndex(0);
        dirty = true;
    }

    private void confirmClose() {
        if (dirty) {
            int r = JOptionPane.showConfirmDialog(this,
                    "配置已修改，確定關閉？（變更已套用到管理器）", "關閉確認",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
        }
        dispose();
    }

    private void setEditEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        dirCombo.setEnabled(enabled);
        typeCombo.setEnabled(enabled);
        quantitySpinner.setEnabled(enabled);
        percentageSpinner.setEnabled(enabled);
        priceCombo.setEnabled(enabled);
        offsetSpinner.setEnabled(enabled);
        pctOffsetCheck.setEnabled(enabled);
        hotkeyField.setEnabled(enabled);
        autoExecCheck.setEnabled(enabled);
        saveBtn.setEnabled(enabled);
    }

    private void refreshFieldVisibility() {
        QuickTradeConfig.QuickTradeType t = (QuickTradeConfig.QuickTradeType) typeCombo.getSelectedItem();
        boolean needQty = t == QuickTradeConfig.QuickTradeType.FIXED_QUANTITY;
        boolean needPct = t == QuickTradeConfig.QuickTradeType.PERCENTAGE_FUNDS
                       || t == QuickTradeConfig.QuickTradeType.PERCENTAGE_HOLDINGS;
        quantitySpinner.setEnabled(needQty);
        percentageSpinner.setEnabled(needPct);
    }

    // ─── 列表渲染器 ─────────────────────────────────────────
    private static class ConfigListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof QuickTradeConfig) {
                QuickTradeConfig c = (QuickTradeConfig) value;
                String hotkey = c.getHotkey().isEmpty() ? "" : "  [" + c.getHotkey() + "]";
                setText((c.isBuy() ? "▲ " : "▼ ") + c.getName() + hotkey);
                if (!isSelected) {
                    setForeground(c.isBuy() ? new Color(0, 128, 0) : new Color(180, 0, 0));
                }
            }
            return this;
        }
    }
}
