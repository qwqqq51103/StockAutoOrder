package StockMainAction.view.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class KlineQuickControlBarTest {

    @Test
    public void buttonsNotifyListenerAndStatusTracksAutoMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KlineQuickControlBar bar = new KlineQuickControlBar();
            List<String> events = new ArrayList<>();
            bar.setListener(new KlineQuickControlBar.Listener() {
                @Override
                public void onFixedCandles(int count) {
                    events.add("fixed:" + count);
                }

                @Override
                public void onShowAll() {
                    events.add("all");
                }

                @Override
                public void onAuto(boolean enabled) {
                    events.add("auto:" + enabled);
                }
            });

            findButton(bar, "20").doClick();
            findButton(bar, "50").doClick();
            findButton(bar, "100").doClick();
            findButton(bar, "全部").doClick();
            JToggleButton autoButton = findToggle(bar, "自動");
            autoButton.doClick();

            assertEquals(List.of("fixed:20", "fixed:50", "fixed:100", "all", "auto:false"), events);
            bar.setStatus("K線：最近 50 根", false);
            assertFalse(autoButton.isSelected());
            assertEquals("K線：最近 50 根", findStatusLabel(bar).getText());
            bar.setStatus("", true);
            assertTrue(autoButton.isSelected());
            assertEquals("K線：自動", findStatusLabel(bar).getText());
        });
    }

    private static JButton findButton(KlineQuickControlBar bar, String text) {
        for (Component component : bar.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("Button not found: " + text);
    }

    private static JToggleButton findToggle(KlineQuickControlBar bar, String text) {
        for (Component component : bar.getComponents()) {
            if (component instanceof JToggleButton button && text.equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("Toggle not found: " + text);
    }

    private static JLabel findStatusLabel(KlineQuickControlBar bar) {
        JLabel result = null;
        for (Component component : bar.getComponents()) {
            if (component instanceof JLabel label) {
                result = label;
            }
        }
        if (result == null) {
            throw new AssertionError("Status label not found");
        }
        return result;
    }
}
