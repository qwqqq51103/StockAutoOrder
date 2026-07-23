package StockMainAction.view.main;

import java.awt.Component;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import org.junit.Test;

import static org.junit.Assert.*;

public class SlippageSettingsPanelTest {
    @Test
    public void applyButtonSendsSpinnerValueAndRefreshesFromSupplier() {
        AtomicInteger current = new AtomicInteger(10);
        AtomicInteger applied = new AtomicInteger(-1);
        SlippageSettingsPanel panel = new SlippageSettingsPanel(
                current::get,
                value -> {
                    applied.set(value);
                    current.set(value);
                },
                () -> new JLabel("滑價保護: " + current.get() + "%"));

        JSpinner spinner = find(panel, JSpinner.class);
        JButton button = find(panel, JButton.class);

        spinner.setValue(7);
        button.doClick();

        assertEquals(7, applied.get());
        assertEquals(7, ((Number) spinner.getValue()).intValue());
    }

    @Test
    public void panelContainsStatusLabelFromFactory() {
        SlippageSettingsPanel panel = new SlippageSettingsPanel(
                () -> 10,
                value -> { },
                () -> new JLabel("status-marker"));

        assertNotNull(findLabel(panel, "status-marker"));
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof JPanel panel) {
            for (Component child : panel.getComponents()) {
                try {
                    return find(child, type);
                } catch (AssertionError ignored) {
                    // Continue searching siblings.
                }
            }
        }
        throw new AssertionError("Component not found: " + type.getName());
    }

    private static JLabel findLabel(Component root, String text) {
        if (root instanceof JLabel label && text.equals(label.getText())) {
            return label;
        }
        if (root instanceof JPanel panel) {
            for (Component child : panel.getComponents()) {
                JLabel found = findLabel(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
