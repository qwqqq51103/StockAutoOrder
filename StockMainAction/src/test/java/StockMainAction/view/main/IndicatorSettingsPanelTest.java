package StockMainAction.view.main;

import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IndicatorSettingsPanelTest {
    @Test
    public void toConfigReflectsUserSelections() {
        IndicatorSettingsPanel panel = new IndicatorSettingsPanel(defaultConfig(), c -> { }, () -> { });

        JCheckBox sma5 = findCheckBox(panel, "SMA5");
        sma5.setSelected(false);
        JSpinner firstSpinner = findFirst(panel, JSpinner.class);
        firstSpinner.setValue(8);

        IndicatorSettingsPanel.Config config = panel.toConfig();

        assertFalse(config.showSma5);
        assertEquals(8, config.sma5Period);
        assertTrue(config.showSma10);
    }

    @Test
    public void applyButtonPassesCurrentConfigOnce() {
        AtomicReference<IndicatorSettingsPanel.Config> applied = new AtomicReference<>();
        IndicatorSettingsPanel panel = new IndicatorSettingsPanel(defaultConfig(), applied::set, () -> { });

        findCheckBox(panel, "買賣失衡標記").setSelected(true);
        findButton(panel, "套用指標設定").doClick();

        assertTrue(applied.get().showTickImbalanceMarkers);
    }

    @Test
    public void resetAvwapButtonRunsCallback() {
        AtomicInteger resetCount = new AtomicInteger();
        IndicatorSettingsPanel panel = new IndicatorSettingsPanel(defaultConfig(), c -> { }, resetCount::incrementAndGet);

        findButton(panel, "重設 AVWAP 起點(使用目前視窗起點)").doClick();

        assertEquals(1, resetCount.get());
    }

    private static IndicatorSettingsPanel.Config defaultConfig() {
        IndicatorSettingsPanel.Config config = new IndicatorSettingsPanel.Config();
        config.showSma5 = true;
        config.showSma10 = true;
        config.showEma12 = true;
        config.showVwap = true;
        config.showAvwap = true;
        config.sma5Period = 5;
        config.sma10Period = 10;
        config.ema12Period = 12;
        config.smaLineWidth = 1.2f;
        config.emaLineWidth = 1.4f;
        config.showSignalMarkers = true;
        config.showBigMarkers = true;
        config.showTickImbalanceMarkers = false;
        config.autoHideMarkersWhenZoomedOut = true;
        config.bigOrderThreshold = 1000;
        config.markersMaxVisibleCandles = 100;
        config.markerAlpha = 160;
        config.lockRangeToOhlc = true;
        return config;
    }

    private static JCheckBox findCheckBox(Container container, String text) {
        JCheckBox result = tryFindCheckBox(container, text);
        if (result == null) {
            throw new AssertionError("CheckBox not found: " + text);
        }
        return result;
    }

    private static JCheckBox tryFindCheckBox(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JCheckBox && text.equals(((JCheckBox) component).getText())) {
                return (JCheckBox) component;
            }
            if (component instanceof Container) {
                JCheckBox result = tryFindCheckBox((Container) component, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
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
