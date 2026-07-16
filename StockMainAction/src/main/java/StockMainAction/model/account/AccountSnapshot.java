package StockMainAction.model.account;

/** Immutable account state used by tests, UI snapshots, and invariant checks. */
public record AccountSnapshot(
        long availableCashCents,
        long frozenCashCents,
        int availableStocks,
        int frozenStocks) {

    public long totalCashCents() {
        return Math.addExact(availableCashCents, frozenCashCents);
    }

    public int totalStocks() {
        return Math.addExact(availableStocks, frozenStocks);
    }
}
