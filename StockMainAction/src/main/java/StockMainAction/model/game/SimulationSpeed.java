package StockMainAction.model.game;

public enum SimulationSpeed {
    PAUSED("暫停", 0),
    SLOW("0.5x", 2000),
    NORMAL("1x", 1000),
    FAST("2x", 500),
    TURBO("5x", 200);

    private final String displayName;
    private final int periodMillis;

    SimulationSpeed(String displayName, int periodMillis) {
        this.displayName = displayName;
        this.periodMillis = periodMillis;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPeriodMillis() {
        return periodMillis;
    }

    public boolean isPaused() {
        return periodMillis <= 0;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
