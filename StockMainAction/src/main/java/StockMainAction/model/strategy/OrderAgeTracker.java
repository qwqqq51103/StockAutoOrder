package StockMainAction.model.strategy;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Tracks live strategy orders against an injectable time source. */
public final class OrderAgeTracker {
    private final Clock clock;
    private final Map<String, Long> creationTimes = new HashMap<>();

    public OrderAgeTracker(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void track(String orderId) {
        if (orderId != null) creationTimes.put(orderId, clock.millis());
    }

    public boolean isOlderThan(String orderId, long maximumAgeMillis) {
        if (maximumAgeMillis < 0) {
            throw new IllegalArgumentException("maximumAgeMillis must not be negative");
        }
        Long creationTime = creationTimes.get(orderId);
        return creationTime != null && clock.millis() - creationTime > maximumAgeMillis;
    }

    public void remove(String orderId) {
        creationTimes.remove(orderId);
    }

    public void retain(Set<String> activeOrderIds) {
        creationTimes.keySet().retainAll(Objects.requireNonNull(activeOrderIds, "activeOrderIds"));
    }

    public int size() {
        return creationTimes.size();
    }
}
