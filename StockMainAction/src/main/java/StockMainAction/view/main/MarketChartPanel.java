package StockMainAction.view.main;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Stable layout boundary for the primary market charts. */
public final class MarketChartPanel extends JPanel {
    public MarketChartPanel(JComponent charts) {
        super(new BorderLayout());
        add(charts, BorderLayout.CENTER);
    }
}
