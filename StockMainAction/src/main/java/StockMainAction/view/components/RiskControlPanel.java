package StockMainAction.view.components;

import StockMainAction.model.game.RiskOrder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

public class RiskControlPanel extends JPanel {
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1_000_000, 100));
    private final JSpinner stopLossSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.0, 50.0, 0.5));
    private final JSpinner takeProfitSpinner = new JSpinner(new SpinnerNumberModel(6.0, 0.0, 200.0, 0.5));
    private final JSpinner trailingSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.0, 50.0, 0.5));
    private final JCheckBox ocoCheckBox = new JCheckBox("OCO（一邊觸發即關閉）", true);
    private final JLabel currentPriceLabel = new JLabel("-");
    private final JLabel holdingsLabel = new JLabel("0");
    private final DefaultListModel<String> orderModel = new DefaultListModel<>();
    private Listener listener;

    public RiskControlPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("風控條件單"));
        add(createFormPanel(), BorderLayout.NORTH);
        JList<String> orders = new JList<>(orderModel);
        JScrollPane scrollPane = new JScrollPane(orders);
        scrollPane.setBorder(BorderFactory.createTitledBorder("目前條件單"));
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void updateMarketState(double currentPrice, int holdings) {
        SwingUtilities.invokeLater(() -> {
            currentPriceLabel.setText(String.format("%.2f", currentPrice));
            holdingsLabel.setText(Integer.toString(holdings));
        });
    }

    public void updateOrders(List<RiskOrder> orders) {
        SwingUtilities.invokeLater(() -> {
            orderModel.clear();
            if (orders != null) {
                for (RiskOrder order : orders) {
                    orderModel.addElement(order.displayText());
                }
            }
        });
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        addRow(form, gc, 0, "現價", currentPriceLabel);
        addRow(form, gc, 1, "持股", holdingsLabel);
        addRow(form, gc, 2, "保護股數", quantitySpinner);
        addRow(form, gc, 3, "停損 %", stopLossSpinner);
        addRow(form, gc, 4, "停利 %", takeProfitSpinner);
        addRow(form, gc, 5, "移動停損 %", trailingSpinner);
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2;
        form.add(ocoCheckBox, gc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("新增條件單");
        JButton cancelButton = new JButton("全部取消");
        addButton.addActionListener(e -> {
            if (listener != null) {
                listener.onAddRiskOrder(((Number) quantitySpinner.getValue()).intValue(),
                        ((Number) stopLossSpinner.getValue()).doubleValue(),
                        ((Number) takeProfitSpinner.getValue()).doubleValue(),
                        ((Number) trailingSpinner.getValue()).doubleValue(),
                        ocoCheckBox.isSelected());
            }
        });
        cancelButton.addActionListener(e -> {
            if (listener != null) listener.onCancelRiskOrders();
        });
        buttons.add(addButton);
        buttons.add(cancelButton);

        panel.add(form, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints gc, int row, String label, java.awt.Component value) {
        gc.gridwidth = 1;
        gc.gridx = 0; gc.gridy = row;
        panel.add(new JLabel(label), gc);
        gc.gridx = 1;
        panel.add(value, gc);
    }

    public interface Listener {
        void onAddRiskOrder(int quantity, double stopLossPct, double takeProfitPct,
                double trailingPct, boolean oco);
        void onCancelRiskOrders();
    }
}
