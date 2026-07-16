package StockMainAction.view.swing;

import java.awt.Dimension;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WindowSizingTest {
    @Test
    public void fitsPreferredSizeWithinAvailableBounds() {
        assertEquals(new Dimension(1200, 800), WindowSizing.fit(
                new Dimension(1600, 900), new Dimension(900, 600),
                new Dimension(1200, 800)));
    }

    @Test
    public void honorsMinimumWhenSpaceAllows() {
        assertEquals(new Dimension(900, 600), WindowSizing.fit(
                new Dimension(400, 300), new Dimension(900, 600),
                new Dimension(1400, 900)));
    }

    @Test
    public void shrinksBelowMinimumOnSmallScreens() {
        assertEquals(new Dimension(700, 500), WindowSizing.fit(
                new Dimension(1200, 800), new Dimension(900, 600),
                new Dimension(700, 500)));
    }
}
