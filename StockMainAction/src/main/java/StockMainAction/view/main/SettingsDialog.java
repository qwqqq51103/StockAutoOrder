package StockMainAction.view.main;

import StockMainAction.view.swing.WindowSizing;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import javax.swing.JComponent;
import javax.swing.JDialog;

/** Owned, non-modal settings window. */
public final class SettingsDialog extends JDialog {
    public SettingsDialog(Frame owner, JComponent content) {
        super(owner, "參數設定", false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        WindowSizing.apply(this, new Dimension(860, 560), new Dimension(700, 480), owner);
    }

    public void showAndFocus() {
        if (!isVisible()) setVisible(true);
        toFront();
        requestFocusInWindow();
    }
}
