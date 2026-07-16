package StockMainAction.view.chart;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TradeTapeMetricsTest {
    @Test
    public void calculatesRollingTradeMetrics() {
        MutableClock clock = new MutableClock(1_000);
        TradeTapeMetrics metrics = new TradeTapeMetrics(10_000, clock);

        metrics.record(true, 100, 0.2);
        metrics.record(true, 50, 0.4);
        metrics.record(false, 50, 0.0);
        TradeTapeMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(3, snapshot.tradeCount());
        assertEquals(200, snapshot.totalVolume());
        assertEquals(75.0, snapshot.buyPercentage(), 0.001);
        assertEquals(0.3, snapshot.tradesPerSecond(), 0.001);
        assertEquals(20.0, snapshot.volumePerSecond(), 0.001);
        assertEquals(0.2, snapshot.averageSlippage(), 0.001);
        assertEquals(2, snapshot.maximumBuyStreak());
        assertEquals(1, snapshot.maximumSellStreak());
    }

    @Test
    public void prunesTradesOutsideWindow() {
        MutableClock clock = new MutableClock(1_000);
        TradeTapeMetrics metrics = new TradeTapeMetrics(10_000, clock);
        metrics.record(true, 100, 0.2);

        clock.setMillis(11_001);

        assertEquals(0, metrics.snapshot().tradeCount());
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
