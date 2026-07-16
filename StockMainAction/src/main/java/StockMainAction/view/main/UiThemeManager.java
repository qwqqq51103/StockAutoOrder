package StockMainAction.view.main;

import java.awt.Color;
import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Applies and reliably restores the application theme defaults. */
public final class UiThemeManager {
    private static final Map<String, Color> DARK_COLORS = Map.ofEntries(
            Map.entry("Panel.background", new Color(50, 50, 50)),
            Map.entry("Panel.foreground", new Color(220, 220, 220)),
            Map.entry("Label.foreground", new Color(220, 220, 220)),
            Map.entry("Button.background", new Color(70, 70, 70)),
            Map.entry("Button.foreground", new Color(220, 220, 220)),
            Map.entry("TextField.background", new Color(60, 60, 60)),
            Map.entry("TextField.foreground", new Color(220, 220, 220)),
            Map.entry("TextArea.background", new Color(60, 60, 60)),
            Map.entry("TextArea.foreground", new Color(220, 220, 220)),
            Map.entry("ComboBox.background", new Color(70, 70, 70)),
            Map.entry("ComboBox.foreground", new Color(220, 220, 220)),
            Map.entry("TabbedPane.background", new Color(50, 50, 50)),
            Map.entry("TabbedPane.foreground", new Color(220, 220, 220)),
            Map.entry("TabbedPane.selected", new Color(80, 80, 80)),
            Map.entry("ScrollPane.background", new Color(50, 50, 50)),
            Map.entry("Table.background", new Color(60, 60, 60)),
            Map.entry("Table.foreground", new Color(220, 220, 220)),
            Map.entry("Table.selectionBackground", new Color(100, 100, 100)),
            Map.entry("Table.selectionForeground", Color.WHITE),
            Map.entry("Table.gridColor", new Color(90, 90, 90)));

    private final Map<String, Object> originals = new LinkedHashMap<>();

    public UiThemeManager() {
        DARK_COLORS.keySet().forEach(key -> originals.put(key, UIManager.get(key)));
    }

    public void apply(boolean dark, Component root) {
        if (dark) DARK_COLORS.forEach(UIManager::put);
        else originals.forEach(UIManager::put);
        if (root != null) SwingUtilities.updateComponentTreeUI(root);
    }

    Object originalValue(String key) {
        return originals.get(key);
    }
}
