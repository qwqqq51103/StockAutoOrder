package StockMainAction.model.game;

public enum GameMode {
    BEGINNER("新手", false, 1.0),
    PRO("專業", false, 1.25),
    SANDBOX("沙盒", true, 0.0);

    private final String displayName;
    private final boolean sandboxToolsEnabled;
    private final double scoreMultiplier;

    GameMode(String displayName, boolean sandboxToolsEnabled, double scoreMultiplier) {
        this.displayName = displayName;
        this.sandboxToolsEnabled = sandboxToolsEnabled;
        this.scoreMultiplier = scoreMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isSandboxToolsEnabled() {
        return sandboxToolsEnabled;
    }

    public double getScoreMultiplier() {
        return scoreMultiplier;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
