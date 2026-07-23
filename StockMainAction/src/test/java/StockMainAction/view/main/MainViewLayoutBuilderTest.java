package StockMainAction.view.main;

import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JSplitPane;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MainViewLayoutBuilderTest {
    @Test
    public void verticalSplitUsesProjectDefaults() {
        JSplitPane pane = MainViewLayoutBuilder.verticalSplit(new JLabel("a"), new JLabel("b"), 0.8);

        assertEquals(JSplitPane.VERTICAL_SPLIT, pane.getOrientation());
        assertEquals(0.8, pane.getResizeWeight(), 0.001);
        assertEquals(6, pane.getDividerSize());
        assertTrue(pane.isOneTouchExpandable());
        assertTrue(pane.isContinuousLayout());
    }

    @Test
    public void horizontalSplitUsesProjectDefaults() {
        JSplitPane pane = MainViewLayoutBuilder.horizontalSplit(new JLabel("a"), new JLabel("b"), 0.7);

        assertEquals(JSplitPane.HORIZONTAL_SPLIT, pane.getOrientation());
        assertEquals(0.7, pane.getResizeWeight(), 0.001);
        assertEquals(6, pane.getDividerSize());
    }
}
