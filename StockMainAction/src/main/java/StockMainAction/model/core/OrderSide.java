package StockMainAction.model.core;

public enum OrderSide {
    BUY("buy"),
    SELL("sell");

    private final String legacyValue;

    OrderSide(String legacyValue) {
        this.legacyValue = legacyValue;
    }

    public String legacyValue() {
        return legacyValue;
    }

    public static OrderSide fromLegacy(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Order side is required");
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "buy" -> BUY;
            case "sell" -> SELL;
            default -> throw new IllegalArgumentException("Unknown order side: " + value);
        };
    }
}
