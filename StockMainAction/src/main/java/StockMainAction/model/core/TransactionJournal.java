package StockMainAction.model.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Bounded, thread-safe journal of committed transactions. */
public final class TransactionJournal {
    private final int capacity;
    private final Deque<Transaction> transactions;

    public TransactionJournal(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.transactions = new ArrayDeque<>(capacity);
    }

    public synchronized void add(Transaction transaction) {
        transactions.addLast(Objects.requireNonNull(transaction, "transaction"));
        while (transactions.size() > capacity) {
            transactions.removeFirst();
        }
    }

    public synchronized List<Transaction> all() {
        return List.copyOf(transactions);
    }

    public synchronized List<Transaction> recent(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (count == 0 || transactions.isEmpty()) {
            return List.of();
        }
        int skip = Math.max(0, transactions.size() - count);
        ArrayList<Transaction> result = new ArrayList<>(Math.min(count, transactions.size()));
        int index = 0;
        for (Transaction transaction : transactions) {
            if (index++ >= skip) result.add(transaction);
        }
        return List.copyOf(result);
    }
}
