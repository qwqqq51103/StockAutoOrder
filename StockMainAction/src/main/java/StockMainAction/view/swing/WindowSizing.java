package StockMainAction.view.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.Objects;

/** Applies usable-screen-aware initial bounds to Swing windows. */
public final class WindowSizing {
    private static final int SCREEN_MARGIN = 32;

    private WindowSizing() { }

    public static void apply(Window window, Dimension preferred, Dimension minimum,
            Component relativeTo) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(preferred, "preferred");
        Objects.requireNonNull(minimum, "minimum");
        Dimension available = preferred;
        if (!GraphicsEnvironment.isHeadless()) {
            Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds();
            available = new Dimension(Math.max(1, bounds.width - SCREEN_MARGIN),
                    Math.max(1, bounds.height - SCREEN_MARGIN));
        }
        window.setMinimumSize(fit(minimum, new Dimension(1, 1), available));
        window.setSize(fit(preferred, minimum, available));
        window.setLocationRelativeTo(relativeTo);
    }

    public static Dimension fit(Dimension preferred, Dimension minimum, Dimension available) {
        Objects.requireNonNull(preferred, "preferred");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(available, "available");
        int width = clamp(preferred.width, minimum.width, available.width);
        int height = clamp(preferred.height, minimum.height, available.height);
        return new Dimension(width, height);
    }

    private static int clamp(int preferred, int minimum, int available) {
        int maximum = Math.max(1, available);
        int effectiveMinimum = Math.min(Math.max(1, minimum), maximum);
        return Math.max(effectiveMinimum, Math.min(Math.max(1, preferred), maximum));
    }
}
