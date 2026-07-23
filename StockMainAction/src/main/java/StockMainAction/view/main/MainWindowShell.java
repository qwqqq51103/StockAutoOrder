package StockMainAction.view.main;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Root layout for the main application window. */
public final class MainWindowShell extends JPanel {
    public MainWindowShell(JComponent content) {
        super(new BorderLayout());
        add(content, BorderLayout.CENTER);
    }

    public void setContent(JComponent content) {
        removeAll();
        add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}
