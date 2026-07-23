package StockMainAction.view.main;

import StockMainAction.model.MainForceStrategyWithOrderBook.MainForceLimitConfig;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class MainForceLimitsPanelTest {
    @Test
    public void toConfigReflectsInitialConfig() {
        MainForceLimitConfig initial = MainForceLimitsPanel.defaultConfig();
        initial.accumulateMinTicks = 77;
        initial.riskExposureWeight = 0.44;

        MainForceLimitsPanel panel = new MainForceLimitsPanel(initial, c -> { }, preset -> { });
        MainForceLimitConfig actual = panel.toConfig();

        assertEquals(77, actual.accumulateMinTicks);
        assertEquals(0.44, actual.riskExposureWeight, 0.0001);
    }

    @Test
    public void applyButtonEmitsCurrentConfig() {
        AtomicReference<MainForceLimitConfig> applied = new AtomicReference<>();
        MainForceLimitsPanel panel = new MainForceLimitsPanel(MainForceLimitsPanel.defaultConfig(), applied::set, preset -> { });

        findButton(panel, "套用主力限制").doClick();

        assertEquals(10, applied.get().replaceIntervalTicks);
        assertEquals(20, applied.get().orderManagementIntervalTicks);
    }

    @Test
    public void presetButtonChangesRiskProfileAndNotifiesName() {
        AtomicReference<String> presetName = new AtomicReference<>();
        MainForceLimitsPanel panel = new MainForceLimitsPanel(MainForceLimitsPanel.defaultConfig(), c -> { }, presetName::set);

        findFirst(panel, JComboBox.class).setSelectedItem("激進");
        findButton(panel, "套用預設組合").doClick();
        MainForceLimitConfig actual = panel.toConfig();

        assertEquals("激進", presetName.get());
        assertEquals(14, actual.replaceIntervalTicks);
        assertEquals(0.50, actual.riskExposureWeight, 0.0001);
    }

    private static JButton findButton(Container container, String text) {
        JButton result = tryFindButton(container, text);
        if (result == null) throw new AssertionError("Button not found: " + text);
        return result;
    }

    private static JButton tryFindButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton && text.equals(((JButton) component).getText())) {
                return (JButton) component;
            }
            if (component instanceof Container) {
                JButton result = tryFindButton((Container) component, text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static <T> T findFirst(Container container, Class<T> type) {
        T result = tryFindFirst(container, type);
        if (result == null) throw new AssertionError("Component not found: " + type.getName());
        return result;
    }

    private static <T> T tryFindFirst(Container container, Class<T> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof Container) {
                T result = tryFindFirst((Container) component, type);
                if (result != null) return result;
            }
        }
        return null;
    }
}
