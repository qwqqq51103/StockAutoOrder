package StockMainAction.view.main;

import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SettingsTabFactoryTest {
    @Test
    public void performanceTabAppliesSelectedMode() {
        AtomicReference<String> selected = new AtomicReference<>();
        JPanel panel = SettingsTabFactory.performanceTab(selected::set);

        JComboBox<?> comboBox = findFirst(panel, JComboBox.class);
        comboBox.setSelectedItem("效能");

        assertEquals("效能", selected.get());
    }

    @Test
    public void klineFollowTabCallsApplyAndShowAll() {
        AtomicBoolean follow = new AtomicBoolean(true);
        AtomicInteger candles = new AtomicInteger();
        AtomicInteger showAllCount = new AtomicInteger();
        JPanel panel = SettingsTabFactory.klineFollowTab(true, 20, new SettingsTabFactory.KlineFollowHandler() {
            @Override
            public void apply(boolean followLatest, int visibleCandles) {
                follow.set(followLatest);
                candles.set(visibleCandles);
            }

            @Override
            public void showAll() {
                showAllCount.incrementAndGet();
            }
        });

        JSpinner spinner = findFirst(panel, JSpinner.class);
        spinner.setValue(55);
        JButton applyButton = findButton(panel, "套用");
        applyButton.doClick();
        JButton showAllButton = findButton(panel, "顯示全部");
        showAllButton.doClick();

        assertTrue(follow.get());
        assertEquals(55, candles.get());
        assertEquals(1, showAllCount.get());
    }

    @Test
    public void replaceIntervalTabAppliesSpinnerValue() {
        AtomicInteger applied = new AtomicInteger();
        JPanel panel = SettingsTabFactory.replaceIntervalTab(applied::set);

        JSpinner spinner = findFirst(panel, JSpinner.class);
        spinner.setValue(33);
        findButton(panel, "套用").doClick();

        assertEquals(33, applied.get());
    }

    private static JButton findButton(Container container, String text) {
        JButton result = tryFindButton(container, text);
        if (result == null) {
            throw new AssertionError("Button not found: " + text);
        }
        return result;
    }

    private static JButton tryFindButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton && text.equals(((JButton) component).getText())) {
                return (JButton) component;
            }
            if (component instanceof Container) {
                JButton result = tryFindButton((Container) component, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static <T> T findFirst(Container container, Class<T> type) {
        T result = tryFindFirst(container, type);
        if (result == null) {
            throw new AssertionError("Component not found: " + type.getName());
        }
        return result;
    }

    private static <T> T tryFindFirst(Container container, Class<T> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container) {
                T result = tryFindFirst((Container) component, type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
