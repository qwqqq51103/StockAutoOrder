package StockMainAction.view.main;

/**
 * MainView 資訊區搜尋的純邏輯 helper。
 */
public final class MainViewSearchSupport {
    private MainViewSearchSupport() {
    }

    /**
     * 從 caret 後方搜尋，找不到時回到文字開頭搜尋。
     *
     * @return 找到的位置；找不到或輸入無效時回傳 -1。
     */
    public static int findNext(String text, String query, int caretPosition) {
        if (text == null || text.isEmpty() || query == null || query.isEmpty()) {
            return -1;
        }
        int safeCaret = Math.max(0, Math.min(caretPosition, text.length()));
        int found = text.indexOf(query, safeCaret);
        if (found >= 0) {
            return found;
        }
        return text.indexOf(query);
    }
}
