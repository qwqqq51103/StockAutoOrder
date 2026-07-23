package StockMainAction.view.main;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

/** Single write-entry panel for market-order slippage protection. */
public final class SlippageSettingsPanel extends JPanel {
    public SlippageSettingsPanel(
            IntSupplier currentPercent,
            IntConsumer applyPercent,
            Supplier<JLabel> statusLabelFactory) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("市價單滑價上限(%):"));

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(currentPercent.getAsInt(), 0, 50, 1));
        JButton applyButton = new JButton("套用");
        applyButton.addActionListener(e -> {
            int value = ((Number) spinner.getValue()).intValue();
            applyPercent.accept(value);
            spinner.setValue(currentPercent.getAsInt());
        });

        controls.add(spinner);
        controls.add(applyButton);
        controls.add(Box.createHorizontalStrut(12));
        if (statusLabelFactory != null) {
            controls.add(statusLabelFactory.get());
        }

        JTextArea note = new JTextArea(
                "此處是唯一會修改市價單滑價保護帶的入口。\n"
                        + "滑價上限越低，市價單越容易被保護帶擋下；越高則成交率提高，但可能掃到較差價格。");
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));

        add(controls, BorderLayout.NORTH);
        add(note, BorderLayout.CENTER);
    }
}
