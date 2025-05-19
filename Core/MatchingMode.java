package Core;

import java.util.Random;

/**
 * 撮合模式枚舉 - 定義不同的撮合算法
 */
public enum MatchingMode {
    STANDARD("標準撮合"), // 標準撮合 - 買價>=賣價即成交，價格取中間
    PRICE_TIME("價格時間優先"), // 價格優先時間優先 - 考慮訂單時間因素
    VOLUME_WEIGHTED("成交量加權"), // 成交量加權 - 價格按成交量加權計算
    MARKET_PRESSURE("市場壓力"), // 市場壓力模式 - 考慮供需不平衡
    RANDOM("隨機模式");         // 隨機模式 - 增加一定的隨機性

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
        Random random = new Random();
        MatchingMode[] modes = values();
        return modes[random.nextInt(modes.length)];
    }
}
