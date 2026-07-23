package StockMainAction.view.main;

import StockMainAction.model.StockMarketModel.RetailLogicModel;
import StockMainAction.model.StockMarketModel.RetailStrategyConfig;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class RetailStrategySettingsPanelTest {
    @Test
    public void setConfigAndToConfigRoundTripModelAndPercentValues() {
        RetailStrategySettingsPanel panel = new RetailStrategySettingsPanel(c -> { }, () -> { }, () -> { });
        RetailStrategyConfig config = new RetailStrategyConfig(
                RetailLogicModel.TREND_FOLLOW,
                0.07,
                0.03,
                0.012,
                25.0,
                76.0,
                0.35,
                0.04,
                12,
                45);

        panel.setConfig(config);
        RetailStrategyConfig actual = panel.toConfig();

        assertEquals(RetailLogicModel.TREND_FOLLOW, actual.model);
        assertEquals(0.07, actual.riskPerTrade, 0.0001);
        assertEquals(0.03, actual.randomTradeProb, 0.0001);
        assertEquals(0.012, actual.spreadLimitRatio, 0.0001);
        assertEquals(12, actual.minTradeWaitTicks);
        assertEquals("趨勢追隨", panel.selectedLabel());
    }

    @Test
    public void applyLoadAndResetButtonsForwardCallbacks() {
        AtomicReference<RetailStrategyConfig> applied = new AtomicReference<>();
        AtomicInteger loadCount = new AtomicInteger();
        AtomicInteger resetCount = new AtomicInteger();
        RetailStrategySettingsPanel panel = new RetailStrategySettingsPanel(
                applied::set,
                loadCount::incrementAndGet,
                resetCount::incrementAndGet);

        findFirst(panel, JComboBox.class).setSelectedItem("保守");
        findButton(panel, "套用").doClick();
        findButton(panel, "讀取目前設定").doClick();
        findButton(panel, "重置預設").doClick();

        assertEquals(RetailLogicModel.CONSERVATIVE, applied.get().model);
        assertEquals(1, loadCount.get());
        assertEquals(1, resetCount.get());
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
