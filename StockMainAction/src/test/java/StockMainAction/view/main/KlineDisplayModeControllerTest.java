package StockMainAction.view.main;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KlineDisplayModeControllerTest {
    @Test
    public void chooseAutoAggregateTargetRequiresAutoFitOneSecondAndEnoughBars() {
        assertNull(KlineDisplayModeController.chooseAutoAggregateTarget(
                false, false, -1, 1_000, 900, true, true));
        assertNull(KlineDisplayModeController.chooseAutoAggregateTarget(
                true, true, -1, 1_000, 900, true, true));
        assertNull(KlineDisplayModeController.chooseAutoAggregateTarget(
                true, false, -10, 1_000, 900, true, true));
        assertNull(KlineDisplayModeController.chooseAutoAggregateTarget(
                true, false, -1, 899, 900, true, true));
    }

    @Test
    public void chooseAutoAggregateTargetSelectsTenOrThirtySecondSeries() {
        assertEquals(Integer.valueOf(-10), KlineDisplayModeController.chooseAutoAggregateTarget(
                true, false, -1, 1_000, 900, true, true));
        assertEquals(Integer.valueOf(-30), KlineDisplayModeController.chooseAutoAggregateTarget(
                true, false, -1, 1_901, 900, true, true));
        assertNull(KlineDisplayModeController.chooseAutoAggregateTarget(
                true, false, -1, 1_901, 900, true, false));
    }

    @Test
    public void closeLineModeRequiresShowAllAndVisibleThreshold() {
        assertEquals(false, KlineDisplayModeController.shouldUseCloseLineMode(false, 300, 260));
        assertEquals(false, KlineDisplayModeController.shouldUseCloseLineMode(true, 260, 260));
        assertEquals(true, KlineDisplayModeController.shouldUseCloseLineMode(true, 261, 260));
    }
}
