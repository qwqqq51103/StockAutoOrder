package StockMainAction.model.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Owns all cash and stock transitions and retains a bounded audit trail. */
public final class AccountLedger {
    private static final int MAX_AUDIT_ENTRIES = 1_000;
    private static final AtomicLong LEDGER_SEQUENCE = new AtomicLong();

    private final long ledgerSequence = LEDGER_SEQUENCE.incrementAndGet();
    private long availableCashCents;
    private long frozenCashCents;
    private int availableStocks;
    private int frozenStocks;
    private long auditSequence;
    private final Deque<AccountMutationResult> auditTrail = new ArrayDeque<>();

    public AccountLedger(double initialFunds, int initialStocks) {
        availableCashCents = toCentsAllowZero(initialFunds, "initialFunds");
        if (initialStocks < 0) throw new IllegalArgumentException("initialStocks must not be negative");
        availableStocks = initialStocks;
    }

    public synchronized AccountMutationResult reserveFundsResult(double amount) {
        long cents = toPositiveCents(amount, "amount");
        AccountSnapshot before = snapshot();
        if (availableCashCents < cents) {
            return record(AccountOperation.RESERVE_FUNDS, false, "insufficient available funds", before);
        }
        availableCashCents -= cents;
        frozenCashCents = Math.addExact(frozenCashCents, cents);
        return record(AccountOperation.RESERVE_FUNDS, true, null, before);
    }

    public synchronized AccountMutationResult reserveStocksResult(int quantity) {
        requirePositiveQuantity(quantity);
        AccountSnapshot before = snapshot();
        if (availableStocks < quantity) {
            return record(AccountOperation.RESERVE_STOCKS, false, "insufficient available stocks", before);
        }
        availableStocks -= quantity;
        frozenStocks = Math.addExact(frozenStocks, quantity);
        return record(AccountOperation.RESERVE_STOCKS, true, null, before);
    }

    public synchronized AccountMutationResult releaseFundsResult(double amount) {
        long cents = toPositiveCents(amount, "amount");
        AccountSnapshot before = snapshot();
        if (frozenCashCents < cents) {
            return record(AccountOperation.RELEASE_FUNDS, false, "insufficient frozen funds", before);
        }
        frozenCashCents -= cents;
        availableCashCents = Math.addExact(availableCashCents, cents);
        return record(AccountOperation.RELEASE_FUNDS, true, null, before);
    }

    public synchronized AccountMutationResult releaseStocksResult(int quantity) {
        requirePositiveQuantity(quantity);
        AccountSnapshot before = snapshot();
        if (frozenStocks < quantity) {
            return record(AccountOperation.RELEASE_STOCKS, false, "insufficient frozen stocks", before);
        }
        frozenStocks -= quantity;
        availableStocks = Math.addExact(availableStocks, quantity);
        return record(AccountOperation.RELEASE_STOCKS, true, null, before);
    }

    public synchronized AccountMutationResult settleLimitBuyResult(
            double reservedAmount, double executionAmount, int quantity) {
        long reserved = toPositiveCents(reservedAmount, "reservedAmount");
        long executed = toPositiveCents(executionAmount, "executionAmount");
        requirePositiveQuantity(quantity);
        AccountSnapshot before = snapshot();
        if (executed > reserved) {
            return record(AccountOperation.SETTLE_LIMIT_BUY, false,
                    "execution amount exceeds reservation", before);
        }
        if (frozenCashCents < reserved) {
            return record(AccountOperation.SETTLE_LIMIT_BUY, false,
                    "insufficient frozen funds", before);
        }
        frozenCashCents -= reserved;
        availableCashCents = Math.addExact(availableCashCents, reserved - executed);
        availableStocks = Math.addExact(availableStocks, quantity);
        return record(AccountOperation.SETTLE_LIMIT_BUY, true, null, before);
    }

    public synchronized AccountMutationResult settleLimitSellResult(int quantity, double proceeds) {
        requirePositiveQuantity(quantity);
        long proceedsCents = toPositiveCents(proceeds, "proceeds");
        AccountSnapshot before = snapshot();
        if (frozenStocks < quantity) {
            return record(AccountOperation.SETTLE_LIMIT_SELL, false,
                    "insufficient frozen stocks", before);
        }
        frozenStocks -= quantity;
        availableCashCents = Math.addExact(availableCashCents, proceedsCents);
        return record(AccountOperation.SETTLE_LIMIT_SELL, true, null, before);
    }

    public synchronized AccountMutationResult settleMarketBuyResult(double cost, int quantity) {
        long costCents = toPositiveCents(cost, "cost");
        requirePositiveQuantity(quantity);
        AccountSnapshot before = snapshot();
        if (availableCashCents < costCents) {
            return record(AccountOperation.SETTLE_MARKET_BUY, false,
                    "insufficient available funds", before);
        }
        availableCashCents -= costCents;
        availableStocks = Math.addExact(availableStocks, quantity);
        return record(AccountOperation.SETTLE_MARKET_BUY, true, null, before);
    }

    public synchronized AccountMutationResult settleMarketSellResult(int quantity, double proceeds) {
        requirePositiveQuantity(quantity);
        long proceedsCents = toPositiveCents(proceeds, "proceeds");
        AccountSnapshot before = snapshot();
        if (availableStocks < quantity) {
            return record(AccountOperation.SETTLE_MARKET_SELL, false,
                    "insufficient available stocks", before);
        }
        availableStocks -= quantity;
        availableCashCents = Math.addExact(availableCashCents, proceedsCents);
        return record(AccountOperation.SETTLE_MARKET_SELL, true, null, before);
    }

    public synchronized boolean reserveFunds(double amount) { return reserveFundsResult(amount).success(); }
    public synchronized boolean reserveStocks(int quantity) { return reserveStocksResult(quantity).success(); }
    public synchronized boolean releaseFunds(double amount) { return releaseFundsResult(amount).success(); }
    public synchronized boolean releaseStocks(int quantity) { return releaseStocksResult(quantity).success(); }

    public synchronized void settleLimitBuy(double reservedAmount, double executionAmount, int quantity) {
        requireSuccess(settleLimitBuyResult(reservedAmount, executionAmount, quantity));
    }

    public synchronized void settleLimitSell(int quantity, double proceeds) {
        requireSuccess(settleLimitSellResult(quantity, proceeds));
    }

    public synchronized void settleMarketBuy(double cost, int quantity) {
        requireSuccess(settleMarketBuyResult(cost, quantity));
    }

    public synchronized void settleMarketSell(int quantity, double proceeds) {
        requireSuccess(settleMarketSellResult(quantity, proceeds));
    }

    public static void settleTrade(AccountLedger buyer, AccountLedger seller,
            double buyerReservedAmount, double executionAmount, int quantity,
            boolean buyerUsesReservation, boolean sellerUsesReservation) {
        settleTrades(List.of(new TradeRequest(buyer, seller, buyerReservedAmount,
                executionAmount, quantity, buyerUsesReservation, sellerUsesReservation)));
    }

    public static void settleTrades(List<TradeRequest> requests) {
        List<TradeRequest> batch = List.copyOf(requests);
        if (batch.isEmpty()) throw new IllegalArgumentException("trade batch must not be empty");
        Set<AccountLedger> unique = new HashSet<>();
        for (TradeRequest request : batch) {
            unique.add(request.buyer());
            unique.add(request.seller());
        }
        List<AccountLedger> ledgers = new ArrayList<>(unique);
        ledgers.sort(Comparator.comparingLong(ledger -> ledger.ledgerSequence));
        withLedgerLocks(ledgers, 0, () -> settleTradesLocked(batch, ledgers));
    }

    private static void withLedgerLocks(List<AccountLedger> ledgers, int index, Runnable action) {
        if (index == ledgers.size()) {
            action.run();
            return;
        }
        synchronized (ledgers.get(index)) {
            withLedgerLocks(ledgers, index + 1, action);
        }
    }

    private static void settleTradesLocked(List<TradeRequest> requests, List<AccountLedger> ledgers) {
        Map<AccountLedger, LedgerState> states = new HashMap<>();
        Map<AccountLedger, AccountSnapshot> before = new HashMap<>();
        for (AccountLedger ledger : ledgers) {
            AccountSnapshot snapshot = ledger.snapshot();
            before.put(ledger, snapshot);
            states.put(ledger, new LedgerState(snapshot));
        }
        for (TradeRequest request : requests) simulateTrade(request, states);

        for (AccountLedger ledger : ledgers) {
            LedgerState state = states.get(ledger);
            ledger.availableCashCents = state.availableCashCents;
            ledger.frozenCashCents = state.frozenCashCents;
            ledger.availableStocks = state.availableStocks;
            ledger.frozenStocks = state.frozenStocks;
        }
        for (TradeRequest request : requests) {
            request.buyer().record(request.buyerUsesReservation()
                    ? AccountOperation.SETTLE_LIMIT_BUY : AccountOperation.SETTLE_MARKET_BUY,
                    true, null, before.get(request.buyer()));
            request.seller().record(request.sellerUsesReservation()
                    ? AccountOperation.SETTLE_LIMIT_SELL : AccountOperation.SETTLE_MARKET_SELL,
                    true, null, before.get(request.seller()));
        }
    }

    private static void simulateTrade(TradeRequest request, Map<AccountLedger, LedgerState> states) {
        requirePositiveQuantity(request.quantity());
        long executed = toPositiveCents(request.executionAmount(), "executionAmount");
        long reserved = request.buyerUsesReservation()
                ? toPositiveCents(request.buyerReservedAmount(), "buyerReservedAmount") : 0;
        LedgerState buyer = states.get(request.buyer());
        LedgerState seller = states.get(request.seller());
        if (request.buyerUsesReservation() && executed > reserved) {
            throw new IllegalStateException("execution amount exceeds reservation");
        }
        if (request.buyerUsesReservation() && buyer.frozenCashCents < reserved) {
            throw new IllegalStateException("insufficient buyer frozen funds");
        }
        if (!request.buyerUsesReservation() && buyer.availableCashCents < executed) {
            throw new IllegalStateException("insufficient buyer available funds");
        }
        if (request.sellerUsesReservation() && seller.frozenStocks < request.quantity()) {
            throw new IllegalStateException("insufficient seller frozen stocks");
        }
        if (!request.sellerUsesReservation() && seller.availableStocks < request.quantity()) {
            throw new IllegalStateException("insufficient seller available stocks");
        }

        if (buyer == seller) {
            long buyerCashDelta = request.buyerUsesReservation() ? reserved - executed : -executed;
            long availableCashAfter = Math.addExact(buyer.availableCashCents,
                    Math.addExact(buyerCashDelta, executed));
            long frozenCashAfter = request.buyerUsesReservation()
                    ? buyer.frozenCashCents - reserved : buyer.frozenCashCents;
            int availableStocksAfter = request.sellerUsesReservation()
                    ? Math.addExact(buyer.availableStocks, request.quantity()) : buyer.availableStocks;
            int frozenStocksAfter = request.sellerUsesReservation()
                    ? buyer.frozenStocks - request.quantity() : buyer.frozenStocks;
            buyer.set(availableCashAfter, frozenCashAfter, availableStocksAfter, frozenStocksAfter);
            return;
        }

        long buyerCashAfter = request.buyerUsesReservation()
                ? Math.addExact(buyer.availableCashCents, reserved - executed)
                : buyer.availableCashCents - executed;
        int buyerStocksAfter = Math.addExact(buyer.availableStocks, request.quantity());
        long sellerCashAfter = Math.addExact(seller.availableCashCents, executed);
        int sellerAvailableStocksAfter = request.sellerUsesReservation()
                ? seller.availableStocks : seller.availableStocks - request.quantity();
        long buyerFrozenAfter = request.buyerUsesReservation()
                ? buyer.frozenCashCents - reserved : buyer.frozenCashCents;
        int sellerFrozenAfter = request.sellerUsesReservation()
                ? seller.frozenStocks - request.quantity() : seller.frozenStocks;
        buyer.set(buyerCashAfter, buyerFrozenAfter, buyerStocksAfter, buyer.frozenStocks);
        seller.set(sellerCashAfter, seller.frozenCashCents,
                sellerAvailableStocksAfter, sellerFrozenAfter);
    }

    public record TradeRequest(AccountLedger buyer, AccountLedger seller,
            double buyerReservedAmount, double executionAmount, int quantity,
            boolean buyerUsesReservation, boolean sellerUsesReservation) {
        public TradeRequest {
            Objects.requireNonNull(buyer, "buyer");
            Objects.requireNonNull(seller, "seller");
        }
    }

    private static final class LedgerState {
        private long availableCashCents;
        private long frozenCashCents;
        private int availableStocks;
        private int frozenStocks;

        private LedgerState(AccountSnapshot snapshot) {
            set(snapshot.availableCashCents(), snapshot.frozenCashCents(),
                    snapshot.availableStocks(), snapshot.frozenStocks());
        }

        private void set(long availableCashCents, long frozenCashCents,
                int availableStocks, int frozenStocks) {
            this.availableCashCents = availableCashCents;
            this.frozenCashCents = frozenCashCents;
            this.availableStocks = availableStocks;
            this.frozenStocks = frozenStocks;
        }
    }

    public synchronized boolean consumeFrozenFunds(double amount) {
        long cents = toPositiveCents(amount, "amount");
        AccountSnapshot before = snapshot();
        if (frozenCashCents < cents) {
            record(AccountOperation.CONSUME_FROZEN_FUNDS, false, "insufficient frozen funds", before);
            return false;
        }
        frozenCashCents -= cents;
        record(AccountOperation.CONSUME_FROZEN_FUNDS, true, null, before);
        return true;
    }

    public synchronized void consumeFrozenStocks(int quantity) {
        requirePositiveQuantity(quantity);
        AccountSnapshot before = snapshot();
        if (frozenStocks < quantity) {
            AccountMutationResult result = record(AccountOperation.CONSUME_FROZEN_STOCKS,
                    false, "insufficient frozen stocks", before);
            requireSuccess(result);
        }
        frozenStocks -= quantity;
        record(AccountOperation.CONSUME_FROZEN_STOCKS, true, null, before);
    }

    public synchronized void addFunds(double amount) {
        AccountSnapshot before = snapshot();
        availableCashCents = Math.addExact(availableCashCents, toPositiveCents(amount, "amount"));
        record(AccountOperation.ADD_FUNDS, true, null, before);
    }

    public synchronized void subtractFunds(double amount) {
        long cents = toPositiveCents(amount, "amount");
        AccountSnapshot before = snapshot();
        if (availableCashCents < cents) {
            requireSuccess(record(AccountOperation.SUBTRACT_FUNDS, false,
                    "insufficient available funds", before));
        }
        availableCashCents -= cents;
        record(AccountOperation.SUBTRACT_FUNDS, true, null, before);
    }

    public synchronized void addStocks(int quantity) {
        requirePositiveQuantity(quantity);
        AccountSnapshot before = snapshot();
        availableStocks = Math.addExact(availableStocks, quantity);
        record(AccountOperation.ADD_STOCKS, true, null, before);
    }

    public synchronized void subtractStocks(int quantity) {
        requirePositiveQuantity(quantity);
        AccountSnapshot before = snapshot();
        if (availableStocks < quantity) {
            requireSuccess(record(AccountOperation.SUBTRACT_STOCKS, false,
                    "insufficient available stocks", before));
        }
        availableStocks -= quantity;
        record(AccountOperation.SUBTRACT_STOCKS, true, null, before);
    }

    public synchronized AccountSnapshot snapshot() {
        return new AccountSnapshot(availableCashCents, frozenCashCents, availableStocks, frozenStocks);
    }

    public synchronized List<AccountMutationResult> auditTrail() {
        return List.copyOf(new ArrayList<>(auditTrail));
    }

    public synchronized double getAvailableFunds() { return fromCents(availableCashCents); }
    public synchronized double getFrozenFunds() { return fromCents(frozenCashCents); }
    public synchronized int getAvailableStocks() { return availableStocks; }
    public synchronized int getFrozenStocks() { return frozenStocks; }

    private AccountMutationResult record(AccountOperation operation, boolean success,
            String failureReason, AccountSnapshot before) {
        AccountMutationResult result = new AccountMutationResult(++auditSequence, operation,
                success, failureReason, before, snapshot());
        if (auditTrail.size() == MAX_AUDIT_ENTRIES) auditTrail.removeFirst();
        auditTrail.addLast(result);
        return result;
    }

    private static void requireSuccess(AccountMutationResult result) {
        if (!result.success()) throw new IllegalStateException(result.failureReason());
    }

    private static long toPositiveCents(double amount, String name) {
        long cents = toCentsAllowZero(amount, name);
        if (cents <= 0) throw new IllegalArgumentException(name + " must be positive");
        return cents;
    }

    private static long toCentsAllowZero(double amount, String name) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact();
    }

    private static double fromCents(long cents) { return BigDecimal.valueOf(cents, 2).doubleValue(); }

    private static void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}
