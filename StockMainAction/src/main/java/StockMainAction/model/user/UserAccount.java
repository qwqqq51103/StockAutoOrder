package StockMainAction.model.user;

import StockMainAction.model.account.AccountLedger;
import StockMainAction.model.account.AccountSnapshot;
import StockMainAction.model.account.AccountMutationResult;
import java.util.List;

/** Backward-compatible account facade backed by the invariant-safe ledger. */
public class UserAccount {
    private final AccountLedger ledger;

    public UserAccount(double initialFunds, int initialStocks) {
        this.ledger = new AccountLedger(initialFunds, initialStocks);
    }

    public boolean freezeFunds(double amount) { return ledger.reserveFunds(amount); }
    public boolean freezeStocks(int quantity) { return ledger.reserveStocks(quantity); }
    public double getAvailableFunds() { return ledger.getAvailableFunds(); }
    public int getStockInventory() { return ledger.getAvailableStocks(); }
    public int getFrozenStocks() { return ledger.getFrozenStocks(); }

    public void unfreezeStocks(int quantity) {
        if (!ledger.releaseStocks(quantity)) {
            throw new IllegalStateException("insufficient frozen stocks to release");
        }
    }

    public double getFrozenFunds() { return ledger.getFrozenFunds(); }

    public boolean unfreezeFunds(double amount) { return ledger.releaseFunds(amount); }
    public double getTotalFunds() { return ledger.snapshot().totalCashCents() / 100.0; }
    public int getTotalStocks() { return ledger.snapshot().totalStocks(); }
    public AccountSnapshot snapshot() { return ledger.snapshot(); }
    public List<AccountMutationResult> auditTrail() { return ledger.auditTrail(); }

    public static void settleTrade(UserAccount buyer, UserAccount seller,
            double buyerReservedAmount, double executionAmount, int quantity,
            boolean buyerUsesReservation, boolean sellerUsesReservation) {
        if (buyer == null || seller == null) throw new IllegalArgumentException("accounts are required");
        AccountLedger.settleTrade(buyer.ledger, seller.ledger, buyerReservedAmount,
                executionAmount, quantity, buyerUsesReservation, sellerUsesReservation);
    }

    public static void settleTrades(List<TradeSettlement> settlements) {
        AccountLedger.settleTrades(settlements.stream().map(settlement ->
                new AccountLedger.TradeRequest(settlement.buyer.ledger, settlement.seller.ledger,
                        settlement.buyerReservedAmount, settlement.executionAmount,
                        settlement.quantity, settlement.buyerUsesReservation,
                        settlement.sellerUsesReservation)).toList());
    }

    public record TradeSettlement(UserAccount buyer, UserAccount seller,
            double buyerReservedAmount, double executionAmount, int quantity,
            boolean buyerUsesReservation, boolean sellerUsesReservation) {
        public TradeSettlement {
            if (buyer == null || seller == null) throw new IllegalArgumentException("accounts are required");
        }
    }
}
