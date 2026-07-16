package StockMainAction.model.core;

/**
 * 撮合模式枚舉 - 定義不同的撮合算法
 */
public enum MatchingMode {
    /**
     * 台股（連續交易）撮合：價格優先、時間優先；成交必須交叉（買價 >= 賣價），
     * 成交價以「被動方（簿內較早者）」委託價為準，並遵守 tick size。
     *
     * 舊的「標準/加權/市場壓力/隨機」等模式已停用（避免產生不符合台股的成交行為）。
     */
    TWSE_STRICT("台股撮合（價格時間優先）");

    private final String displayName;

    MatchingMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    // 取得隨機撮合模式 (可用於每次交易時隨機選擇模式)
    public static MatchingMode getRandom() {
        // 舊模式已停用；一律回傳台股撮合
        return TWSE_STRICT;
    }
}