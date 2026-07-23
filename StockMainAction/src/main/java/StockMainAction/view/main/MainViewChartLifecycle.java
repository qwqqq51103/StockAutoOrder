package StockMainAction.view.main;

import org.jfree.data.xy.XYSeries;

import javax.swing.SwingUtilities;
import java.util.function.Consumer;

/**
 * Testable chart-data lifecycle bridge used by MainView.
 *
 * <p>MainView receives indicator updates from controller/model paths that are not guaranteed
 * to run on the Swing EDT. This helper centralizes the rule: mutate chart series only on EDT,
 * flush only when a valid sample was written, and never partially write invalid triples.</p>
 */
public final class MainViewChartLifecycle {
    private MainViewChartLifecycle() {
    }

    public static void updateIndicatorTripleOnEdt(
            int timeStep,
            double first,
            double second,
            double third,
            XYSeries firstSeries,
            XYSeries secondSeries,
            XYSeries thirdSeries,
            int maxPoints,
            Runnable flush,
            Consumer<Exception> failureHandler) {
        Runnable update = () -> {
            try {
                boolean changed = TechnicalIndicatorSeriesUpdater.updateTriple(
                        timeStep,
                        first,
                        second,
                        third,
                        firstSeries,
                        secondSeries,
                        thirdSeries,
                        maxPoints);
                if (changed && flush != null) {
                    flush.run();
                }
            } catch (Exception ex) {
                if (failureHandler != null) {
                    failureHandler.accept(ex);
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }
}
