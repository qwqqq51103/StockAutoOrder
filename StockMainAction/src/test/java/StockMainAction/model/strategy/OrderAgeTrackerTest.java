package StockMainAction.model.strategy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrderAgeTrackerTest {
    @Test
    public void tracksAgeAndRemovesInactiveOrders() {
        MutableClock clock = new MutableClock(1_000);
        OrderAgeTracker tracker = new OrderAgeTracker(clock);
        tracker.track("A");
        tracker.track("B");

        clock.setMillis(2_001);
        assertTrue(tracker.isOlderThan("A", 1_000));
        assertFalse(tracker.isOlderThan("missing", 1_000));

        tracker.retain(Set.of("B"));
        assertEquals(1, tracker.size());
        tracker.remove("B");
        assertEquals(0, tracker.size());
    }

    private static final class MutableClock extends Clock {
        private long millis;
        private MutableClock(long millis) { this.millis = millis; }
        private void setMillis(long millis) { this.millis = millis; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    }
}
