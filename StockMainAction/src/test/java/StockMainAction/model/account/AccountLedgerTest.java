package StockMainAction.model.account;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class AccountLedgerTest {
    @Test
    public void reserveReleaseAndLimitSettlementUseExactCents() {
        AccountLedger ledger = new AccountLedger(1_000.00, 20);

        assertTrue(ledger.reserveFunds(333.33));
        assertTrue(ledger.reserveStocks(5));
        assertEquals(new AccountSnapshot(66_667, 33_333, 15, 5), ledger.snapshot());

        ledger.settleLimitBuy(200.00, 199.99, 2);
        ledger.settleLimitSell(3, 300.03);
        assertTrue(ledger.releaseFunds(133.33));
        assertTrue(ledger.releaseStocks(2));

        assertEquals(new AccountSnapshot(110_004, 0, 19, 0), ledger.snapshot());
    }

    @Test
    public void marketSettlementNeverCreatesNegativeBalances() {
        AccountLedger ledger = new AccountLedger(100.00, 2);
        ledger.settleMarketBuy(50.00, 1);
        ledger.settleMarketSell(2, 80.00);

        assertEquals(new AccountSnapshot(13_000, 0, 1, 0), ledger.snapshot());
        assertIllegalState(() -> ledger.settleMarketBuy(130.01, 1));
        assertIllegalState(() -> ledger.settleMarketSell(2, 1.00));
    }

    @Test
    public void invalidAmountsAndQuantitiesAreRejected() {
        assertIllegalArgument(() -> new AccountLedger(-0.01, 0));
        assertIllegalArgument(() -> new AccountLedger(Double.NaN, 0));
        assertIllegalArgument(() -> new AccountLedger(0, -1));

        AccountLedger ledger = new AccountLedger(10, 10);
        assertIllegalArgument(() -> ledger.reserveFunds(0));
        assertIllegalArgument(() -> ledger.reserveFunds(-1));
        assertIllegalArgument(() -> ledger.reserveFunds(Double.POSITIVE_INFINITY));
        assertIllegalArgument(() -> ledger.reserveStocks(0));
        assertIllegalArgument(() -> ledger.addStocks(-1));
    }

    @Test
    public void auditTrailIncludesSuccessAndFailureReasons() {
        AccountLedger ledger = new AccountLedger(10, 1);
        assertTrue(ledger.reserveFundsResult(5).success());
        AccountMutationResult failure = ledger.reserveFundsResult(6);

        assertFalse(failure.success());
        assertEquals("insufficient available funds", failure.failureReason());
        assertEquals(failure.before(), failure.after());
        assertEquals(2, ledger.auditTrail().size());
        assertEquals(AccountOperation.RESERVE_FUNDS, failure.operation());
    }

    @Test
    public void tradeSettlementChangesBothLedgersAtomically() {
        AccountLedger buyer = new AccountLedger(1_000, 0);
        AccountLedger seller = new AccountLedger(0, 10);
        assertTrue(buyer.reserveFunds(250));
        assertTrue(seller.reserveStocks(2));

        AccountLedger.settleTrade(buyer, seller, 250, 200, 2, true, true);

        assertEquals(new AccountSnapshot(80_000, 0, 2, 0), buyer.snapshot());
        assertEquals(new AccountSnapshot(20_000, 0, 8, 0), seller.snapshot());
    }

    @Test
    public void failedTradeSettlementLeavesBothLedgersUnchanged() {
        AccountLedger buyer = new AccountLedger(100, 0);
        AccountLedger seller = new AccountLedger(0, 1);
        AccountSnapshot buyerBefore = buyer.snapshot();
        AccountSnapshot sellerBefore = seller.snapshot();

        assertIllegalState(() -> AccountLedger.settleTrade(
                buyer, seller, 0, 200, 1, false, false));

        assertEquals(buyerBefore, buyer.snapshot());
        assertEquals(sellerBefore, seller.snapshot());
    }

    @Test
    public void failedBatchSettlementRollsBackEveryLedger() {
        AccountLedger buyer = new AccountLedger(1_000, 0);
        AccountLedger firstSeller = new AccountLedger(0, 2);
        AccountLedger secondSeller = new AccountLedger(0, 1);
        assertTrue(buyer.reserveFunds(400));
        assertTrue(firstSeller.reserveStocks(2));
        assertTrue(secondSeller.reserveStocks(1));

        AccountSnapshot buyerBefore = buyer.snapshot();
        AccountSnapshot firstSellerBefore = firstSeller.snapshot();
        AccountSnapshot secondSellerBefore = secondSeller.snapshot();

        assertIllegalState(() -> AccountLedger.settleTrades(List.of(
                new AccountLedger.TradeRequest(
                        buyer, firstSeller, 200, 180, 2, true, true),
                new AccountLedger.TradeRequest(
                        buyer, secondSeller, 200, 180, 2, true, true))));

        assertEquals(buyerBefore, buyer.snapshot());
        assertEquals(firstSellerBefore, firstSeller.snapshot());
        assertEquals(secondSellerBefore, secondSeller.snapshot());
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }
}
