package StockMainAction.view.main;

import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainToolbarFactoryTest {
    @Test
    public void toolbarForwardsCoreActions() {
        AtomicBoolean theme = new AtomicBoolean();
        AtomicReference<String> performance = new AtomicReference<>();
        AtomicReference<String> eventMode = new AtomicReference<>();
        AtomicInteger replaceInterval = new AtomicInteger();
        AtomicInteger visibleCandles = new AtomicInteger();
        AtomicBoolean follow = new AtomicBoolean(true);

        MainToolbarFactory.Config config = new MainToolbarFactory.Config();
        config.autoFollowLatest = true;
        config.visibleCandles = 20;
        JToolBar toolbar = MainToolbarFactory.create(config, new StubActions() {
            @Override public void toggleTheme() { theme.set(true); }
            @Override public void applyPerformanceMode(String mode) { performance.set(mode); }
            @Override public JComponent createSlippageStatus() { return new JLabel("slippage"); }
            @Override public void applyReplaceInterval(int intervalTicks) { replaceInterval.set(intervalTicks); }
            @Override public void applyEventMode(String mode, int window, int consecutive, int threshold) { eventMode.set(mode); }
            @Override public void changeVisibleCandles(int candles) { visibleCandles.set(candles); }
            @Override public void setKlineFollow(boolean followLatest) { follow.set(followLatest); }
        });

        findButton(toolbar, "主題").doClick();
        comboAt(toolbar, 0).setSelectedItem("效能");
        spinnerAt(toolbar, 0).setValue(22);
        findButton(toolbar, "套用撤換").doClick();
        comboAt(toolbar, 1).setSelectedItem("新聞");
        findButton(toolbar, "套用").doClick();
        spinnerAt(toolbar, 4).setValue(50);
        findButton(toolbar, "🎯 自動跟隨").doClick();

        assertTrue(theme.get());
        assertEquals("效能", performance.get());
        assertEquals(22, replaceInterval.get());
        assertEquals("新聞", eventMode.get());
        assertEquals(50, visibleCandles.get());
        assertFalse(follow.get());
    }

    private static class StubActions implements MainToolbarFactory.Actions {
        @Override public void toggleTheme() { }
        @Override public void openLogViewer() { }
        @Override public void decreaseFont() { }
        @Override public void increaseFont() { }
        @Override public void resetFont() { }
        @Override public void applyPerformanceMode(String mode) { }
        @Override public void openCommandPalette() { }
        @Override public void resetCharts() { }
        @Override public JComponent createSlippageStatus() { return null; }
        @Override public void applyReplaceInterval(int intervalTicks) { }
        @Override public void applyEventMode(String mode, int window, int consecutive, int threshold) { }
        @Override public void changeVisibleCandles(int visibleCandles) { }
        @Override public void setKlineFollow(boolean followLatest) { }
        @Override public void openIndicatorSettings() { }
    }

    private static JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton && text.equals(((JButton) component).getText())) {
                return (JButton) component;
            }
        }
        throw new AssertionError("Button not found: " + text);
    }

    private static JComboBox<?> comboAt(Container container, int index) {
        int found = 0;
        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox) {
                if (found++ == index) return (JComboBox<?>) component;
            }
        }
        throw new AssertionError("ComboBox not found at index " + index);
    }

    private static JSpinner spinnerAt(Container container, int index) {
        int found = 0;
        for (Component component : container.getComponents()) {
            if (component instanceof JSpinner) {
                if (found++ == index) return (JSpinner) component;
            }
        }
        throw new AssertionError("Spinner not found at index " + index);
    }
}
