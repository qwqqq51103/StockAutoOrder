package StockMainAction.view.transaction;

import StockMainAction.model.core.Transaction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded source model for the transaction price chart. */
public final class TransactionChartModel {
    private final int maxPoints;
    private final Deque<Transaction> points = new ArrayDeque<>();
    private final BoundedTransactionIds seenTransactionIds;

    public TransactionChartModel(int maxPoints) {
        if (maxPoints <= 0) {
            throw new IllegalArgumentException("maxPoints must be positive");
        }
        this.maxPoints = maxPoints;
        this.seenTransactionIds = new BoundedTransactionIds(maxPoints);
    }

    public boolean addTransaction(Transaction transaction) {
        if (!seenTransactionIds.add(transaction)) {
            return false;
        }
        if (points.size() == maxPoints) {
            points.removeFirst();
        }
        points.addLast(transaction);
        return true;
    }

    public int addTransactions(Iterable<Transaction> transactions) {
        if (transactions == null) return 0;
        int added = 0;
        for (Transaction transaction : transactions) {
            if (addTransaction(transaction)) added++;
        }
        return added;
    }

    public List<Transaction> snapshot() {
        return new ArrayList<>(points);
    }

    public int size() {
        return points.size();
    }

    public void clear() {
        points.clear();
        seenTransactionIds.clear();
    }
}
