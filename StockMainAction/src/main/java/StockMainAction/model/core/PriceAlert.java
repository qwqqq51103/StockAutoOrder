// === 2. 價格提醒數據類 ===
// 位置: model/core/PriceAlert.java
package StockMainAction.model.core;

/**
 * 價格提醒數據模型
 */
public class PriceAlert {

    public enum AlertType {
        ABOVE("高於"), BELOW("低於"), CHANGE_UP("上漲"), CHANGE_DOWN("下跌");

        private String displayName;

        AlertType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private double targetPrice;
    private AlertType type;
    private boolean soundEnabled;
    private boolean popupEnabled;
    private boolean triggered;

    public PriceAlert(double targetPrice, AlertType type, boolean soundEnabled, boolean popupEnabled) {
        this.targetPrice = targetPrice;
        this.type = type;
        this.soundEnabled = soundEnabled;
        this.popupEnabled = popupEnabled;
        this.triggered = false;
    }

    public boolean checkAlert(double currentPrice, double previousPrice) {
        if (triggered) {
            return false;
        }

        boolean shouldAlert = false;
        switch (type) {
            case ABOVE:
                shouldAlert = currentPrice >= targetPrice;
                break;
            case BELOW:
                shouldAlert = currentPrice <= targetPrice;
                break;
            case CHANGE_UP:
                shouldAlert = previousPrice > 0 && ((currentPrice - previousPrice) / previousPrice * 100) >= targetPrice;
                break;
            case CHANGE_DOWN:
                shouldAlert = previousPrice > 0 && ((previousPrice - currentPrice) / previousPrice * 100) >= targetPrice;
                break;
        }

        if (shouldAlert) {
            triggered = true;
        }
        return shouldAlert;
    }

    public void reset() {
        triggered = false;
    }

    // Getters
    public double getTargetPrice() {
        return targetPrice;
    }

    public AlertType getType() {
        return type;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public boolean isPopupEnabled() {
        return popupEnabled;
    }

    public boolean isTriggered() {
        return triggered;
    }

    @Override
    public String toString() {
        String prefix = triggered ? "✓ " : "○ ";
        String condition = String.format("%s %.2f", type.getDisplayName(), targetPrice);
        return prefix + condition;
    }
}
