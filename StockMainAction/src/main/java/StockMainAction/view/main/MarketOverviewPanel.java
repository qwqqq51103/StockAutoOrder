package StockMainAction.view.main;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Layout boundary for market participant and summary views. */
public final class MarketOverviewPanel extends JPanel {
    public MarketOverviewPanel(JComponent overview) {
        super(new BorderLayout());
        add(overview, BorderLayout.CENTER);
    }
}
