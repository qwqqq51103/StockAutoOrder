package StockMainAction.view.main;

import javax.swing.JComponent;
import javax.swing.JSplitPane;

/**
 * MainView 常用 split pane layout 建構。
 */
public final class MainViewLayoutBuilder {
    private MainViewLayoutBuilder() {
    }

    public static JSplitPane verticalSplit(JComponent top, JComponent bottom, double resizeWeight) {
        return split(JSplitPane.VERTICAL_SPLIT, top, bottom, resizeWeight);
    }

    public static JSplitPane horizontalSplit(JComponent left, JComponent right, double resizeWeight) {
        return split(JSplitPane.HORIZONTAL_SPLIT, left, right, resizeWeight);
    }

    private static JSplitPane split(int orientation, JComponent first, JComponent second, double resizeWeight) {
        JSplitPane pane = new JSplitPane(orientation, first, second);
        pane.setResizeWeight(resizeWeight);
        pane.setOneTouchExpandable(true);
        pane.setDividerSize(6);
        pane.setContinuousLayout(true);
        return pane;
    }
}
