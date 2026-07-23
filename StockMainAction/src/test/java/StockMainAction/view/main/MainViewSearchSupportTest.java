package StockMainAction.view.main;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MainViewSearchSupportTest {

    @Test
    public void findNextStartsAtCaret() {
        assertEquals(8, MainViewSearchSupport.findNext("abc def abc", "abc", 4));
    }

    @Test
    public void findNextWrapsToStartWhenNoLaterMatch() {
        assertEquals(0, MainViewSearchSupport.findNext("abc def", "abc", 4));
    }

    @Test
    public void findNextReturnsMinusOneWhenMissing() {
        assertEquals(-1, MainViewSearchSupport.findNext("abc def", "zzz", 0));
    }

    @Test
    public void findNextHandlesInvalidInputs() {
        assertEquals(-1, MainViewSearchSupport.findNext(null, "abc", 0));
        assertEquals(-1, MainViewSearchSupport.findNext("", "abc", 0));
        assertEquals(-1, MainViewSearchSupport.findNext("abc", null, 0));
        assertEquals(-1, MainViewSearchSupport.findNext("abc", "", 0));
    }

    @Test
    public void findNextClampsCaretToTextBounds() {
        assertEquals(0, MainViewSearchSupport.findNext("abc", "abc", -10));
        assertEquals(0, MainViewSearchSupport.findNext("abc", "abc", 100));
    }
}
