package StockMainAction.model.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TransactionJournalTest {
    @Test
    public void retainsOnlyNewestTransactions() {
        TransactionJournal journal = new TransactionJournal(2);
        journal.add(transaction("T1"));
        journal.add(transaction("T2"));
        journal.add(transaction("T3"));

        assertEquals(2, journal.all().size());
        assertEquals("T2", journal.all().get(0).getId());
        assertEquals("T3", journal.recent(1).get(0).getId());
        assertEquals(0, journal.recent(0).size());
    }

    @Test
    public void rejectsInvalidCapacityAndCount() {
        assertIllegalArgument(() -> new TransactionJournal(0));
        TransactionJournal journal = new TransactionJournal(1);
        assertIllegalArgument(() -> journal.recent(-1));
    }

    private static Transaction transaction(String id) {
        return new Transaction(id, (Order) null, (Order) null, 10, 1, 1);
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
