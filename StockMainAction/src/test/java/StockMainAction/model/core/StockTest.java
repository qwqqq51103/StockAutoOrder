package StockMainAction.model.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class StockTest {
    @Test
    public void validatesConstructionAndMutations() {
        assertIllegalArgument(() -> new Stock("", 10, 1));
        assertIllegalArgument(() -> new Stock("T", Double.NaN, 1));
        assertIllegalArgument(() -> new Stock("T", 10, -1));

        Stock stock = new Stock("T", 10, 1);
        assertIllegalArgument(() -> stock.setPrice(0));
        assertIllegalArgument(() -> stock.setPrice(Double.POSITIVE_INFINITY));
        assertIllegalArgument(() -> stock.setVolume(-1));
    }

    @Test
    public void snapshotCapturesOneConsistentState() {
        Stock stock = new Stock("T", 10, 100);
        StockSnapshot snapshot = stock.snapshot();

        stock.setPrice(11);
        stock.setVolume(200);

        assertEquals("T", snapshot.name());
        assertEquals(10, snapshot.price(), 0.0);
        assertEquals(10, snapshot.previousPrice(), 0.0);
        assertEquals(100, snapshot.volume());
        assertEquals(new StockSnapshot("T", 11, 10, 200), stock.snapshot());
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
