package StockMainAction.view.transaction;

import StockMainAction.model.core.Transaction;

final class TransactionViewSupport {
    private TransactionViewSupport() {
    }

    static String validId(Transaction transaction) {
        if (transaction == null || !Double.isFinite(transaction.getPrice())
                || transaction.getPrice() <= 0 || transaction.getVolume() <= 0) {
            return null;
        }
        String id = transaction.getId();
        return id == null || id.trim().isEmpty() ? null : id.trim();
    }
}
