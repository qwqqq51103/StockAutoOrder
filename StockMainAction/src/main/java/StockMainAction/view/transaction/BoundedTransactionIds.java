package StockMainAction.view.transaction;

import StockMainAction.model.core.Transaction;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded recent-ID index used to reject replayed transaction events. */
final class BoundedTransactionIds {
    private static final int MIN_CAPACITY = 4_096;

    private final int capacity;
    private final Set<String> ids = new LinkedHashSet<>();

    BoundedTransactionIds(int retainedTransactions) {
        long scaled = Math.max(MIN_CAPACITY, (long) retainedTransactions * 4L);
        capacity = (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    boolean add(Transaction transaction) {
        String id = TransactionViewSupport.validId(transaction);
        if (id == null || ids.contains(id)) {
            return false;
        }
        if (ids.size() == capacity) {
            Iterator<String> iterator = ids.iterator();
            iterator.next();
            iterator.remove();
        }
        ids.add(id);
        return true;
    }

    void clear() {
        ids.clear();
    }
}
