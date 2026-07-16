package StockMainAction.view.transaction;

import StockMainAction.model.core.Transaction;
import java.util.ArrayDeque;
import java.util.Deque;

/** Rolling transaction statistics with bounded retention and incremental totals. */
public final class TransactionStatisticsModel {
    private final int maxTransactions;
    private final Deque<Transaction> transactions = new ArrayDeque<>();
    private final BoundedTransactionIds seenTransactionIds;
    private long totalVolume;
    private double totalAmount;
    private int marketOrderCount;
    private int buyerInitiatedCount;

    public TransactionStatisticsModel(int maxTransactions) {
        if (maxTransactions <= 0) {
            throw new IllegalArgumentException("maxTransactions must be positive");
        }
        this.maxTransactions = maxTransactions;
        this.seenTransactionIds = new BoundedTransactionIds(maxTransactions);
    }

    public boolean addTransaction(Transaction transaction) {
        if (!seenTransactionIds.add(transaction)) {
            return false;
        }
        if (transactions.size() == maxTransactions) {
            subtract(transactions.removeFirst());
        }
        transactions.addLast(transaction);
        add(transaction);
        return true;
    }

    public int addTransactions(Iterable<Transaction> values) {
        if (values == null) return 0;
        int added = 0;
        for (Transaction value : values) {
            if (addTransaction(value)) added++;
        }
        return added;
    }

    public Statistics snapshot() {
        double averagePrice = totalVolume == 0 ? 0.0 : totalAmount / totalVolume;
        return new Statistics(transactions.size(), totalVolume, totalAmount, averagePrice,
                marketOrderCount, transactions.size() - marketOrderCount,
                buyerInitiatedCount, transactions.size() - buyerInitiatedCount);
    }

    public int size() {
        return transactions.size();
    }

    public void clear() {
        transactions.clear();
        seenTransactionIds.clear();
        totalVolume = 0;
        totalAmount = 0;
        marketOrderCount = 0;
        buyerInitiatedCount = 0;
    }

    private void add(Transaction transaction) {
        totalVolume += transaction.getVolume();
        totalAmount += transaction.getTotalValue();
        if (transaction.isMarketOrder()) {
            marketOrderCount++;
        }
        if (isBuyerInitiated(transaction)) {
            buyerInitiatedCount++;
        }
    }

    private void subtract(Transaction transaction) {
        totalVolume -= transaction.getVolume();
        totalAmount -= transaction.getTotalValue();
        if (transaction.isMarketOrder()) {
            marketOrderCount--;
        }
        if (isBuyerInitiated(transaction)) {
            buyerInitiatedCount--;
        }
    }

    private static boolean isBuyerInitiated(Transaction transaction) {
        return transaction.isBuyerInitiated()
                || "MARKET_BUY".equals(transaction.getOrderType())
                || "FOK_BUY".equals(transaction.getOrderType());
    }

    public record Statistics(int transactionCount, long totalVolume, double totalAmount,
            double averagePrice, int marketOrderCount, int limitOrderCount,
            int buyerInitiatedCount, int sellerInitiatedCount) {
    }
}
