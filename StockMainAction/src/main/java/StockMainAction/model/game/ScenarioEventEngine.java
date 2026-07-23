package StockMainAction.model.game;

import java.util.Random;

public final class ScenarioEventEngine {
    private static final int FIRST_EVENT_TICK = 45;
    private static final int EVENT_INTERVAL_TICKS = 120;

    private final Random random;
    private int lastEventTick = -EVENT_INTERVAL_TICKS;

    public ScenarioEventEngine(long seed) {
        this.random = new Random(seed ^ 0x5DEECE66DL);
    }

    public ScenarioEvent nextEvent(int tick) {
        if (tick < FIRST_EVENT_TICK || tick - lastEventTick < EVENT_INTERVAL_TICKS) {
            return null;
        }
        lastEventTick = tick;
        int roll = random.nextInt(4);
        return switch (roll) {
            case 0 -> new ScenarioEvent("供應鏈利多", "買盤情緒升溫，短線容易追價。", Bias.BULLISH);
            case 1 -> new ScenarioEvent("法說會保守", "賣壓可能放大，留意滑價。", Bias.BEARISH);
            case 2 -> new ScenarioEvent("量能急縮", "市場觀望，限價單耐心更重要。", Bias.NEUTRAL);
            default -> new ScenarioEvent("主力試單", "委託簿深度變化加快，適合觀察內外盤。", Bias.VOLATILE);
        };
    }

    public enum Bias {
        BULLISH,
        BEARISH,
        NEUTRAL,
        VOLATILE
    }

    public record ScenarioEvent(String title, String description, Bias bias) {
        public String displayText() {
            return title + "：" + description;
        }
    }
}
