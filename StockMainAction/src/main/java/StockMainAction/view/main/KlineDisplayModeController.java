package StockMainAction.view.main;

/**
 * K 線快捷顯示、大量資料聚合與折線模式的判斷邏輯。
 */
public final class KlineDisplayModeController {
    private KlineDisplayModeController() {
    }

    public static Integer chooseAutoAggregateTarget(boolean autoFitVisibleCandles,
            boolean showAllCandles, int currentPeriodKey, int oneSecondBarCount,
            int totalThreshold, boolean hasTenSecondSeries, boolean hasThirtySecondSeries) {
        if (!autoFitVisibleCandles || showAllCandles || currentPeriodKey != -1) {
            return null;
        }
        if (oneSecondBarCount < totalThreshold) {
            return null;
        }
        int target = oneSecondBarCount > totalThreshold * 2 ? -30 : -10;
        if (target == -30 && hasThirtySecondSeries) {
            return target;
        }
        if (target == -10 && hasTenSecondSeries) {
            return target;
        }
        return null;
    }

    public static boolean shouldUseCloseLineMode(boolean showAllCandles,
            int visibleCandles, int lineModeVisibleThreshold) {
        return showAllCandles && visibleCandles > Math.max(1, lineModeVisibleThreshold);
    }
}
