package StockMainAction.model.core;

/** Immutable result of an immediately executed order. */
public record ExecutionResult(
        int requestedVolume,
        int filledVolume,
        double averagePrice,
        double totalValue,
        String failureReason) {

    public ExecutionResult {
        if (requestedVolume <= 0) {
            throw new IllegalArgumentException("requestedVolume must be positive");
        }
        if (filledVolume < 0 || filledVolume > requestedVolume) {
            throw new IllegalArgumentException("filledVolume is out of range");
        }
        if (!Double.isFinite(averagePrice) || averagePrice < 0) {
            throw new IllegalArgumentException("averagePrice must be finite and non-negative");
        }
        if (!Double.isFinite(totalValue) || totalValue < 0) {
            throw new IllegalArgumentException("totalValue must be finite and non-negative");
        }
    }

    public boolean isFilled() {
        return filledVolume == requestedVolume;
    }

    public boolean isPartiallyFilled() {
        return filledVolume > 0 && filledVolume < requestedVolume;
    }

    public boolean isRejected() {
        return filledVolume == 0;
    }
}
