package StockMainAction.model.core;

public record StockSnapshot(String name, double price, double previousPrice, int volume) { }
