package StockMainAction.view.main;

import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * K 線資料量快捷控制列。
 *
 * <p>這個小元件把圖表密度控制從大型 MainView 拆出來，MainView 只保留
 * 實際圖表 domain/range 與 renderer 切換邏輯。</p>
 */
public final class KlineQuickControlBar extends JPanel {
    private final JLabel statusLabel = new JLabel("K線：自動");
    private final JToggleButton autoButton = new JToggleButton("自動", true);
    private Listener listener = Listener.noop();

    public KlineQuickControlBar() {
        super(new FlowLayout(FlowLayout.LEFT, 8, 4));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JLabel label = new JLabel("K線顯示：");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        add(label);

        add(button("20", () -> listener.onFixedCandles(20)));
        add(button("50", () -> listener.onFixedCandles(50)));
        add(button("100", () -> listener.onFixedCandles(100)));
        add(button("全部", () -> listener.onShowAll()));

        autoButton.addActionListener(e -> listener.onAuto(autoButton.isSelected()));
        add(autoButton);
        add(statusLabel);
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? Listener.noop() : listener;
    }

    public void setStatus(String text, boolean autoSelected) {
        statusLabel.setText(text == null || text.isBlank() ? "K線：自動" : text);
        autoButton.setSelected(autoSelected);
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    public interface Listener {
        void onFixedCandles(int count);
        void onShowAll();
        void onAuto(boolean enabled);

        static Listener noop() {
            return new Listener() {
                @Override public void onFixedCandles(int count) { }
                @Override public void onShowAll() { }
                @Override public void onAuto(boolean enabled) { }
            };
        }
    }
}
