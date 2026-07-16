package StockMainAction.view.chart;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Thread-safe rolling metrics for the recent-trade tape. */
public final class TradeTapeMetrics {
    private final long windowMillis;
    private final Clock clock;
    private final Deque<Trade> trades = new ArrayDeque<>();

    public TradeTapeMetrics(long windowMillis, Clock clock) {
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis must be positive");
        }
        this.windowMillis = windowMillis;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized long record(boolean buyerInitiated, int volume, double absoluteSlippage) {
        if (volume < 0 || !Double.isFinite(absoluteSlippage) || absoluteSlippage < 0) {
            throw new IllegalArgumentException("Invalid trade metrics");
        }
        long timestamp = clock.millis();
        trades.addLast(new Trade(timestamp, buyerInitiated, volume, absoluteSlippage));
        prune(timestamp);
        return timestamp;
    }

    public synchronized Snapshot snapshot() {
        prune(clock.millis());
        if (trades.isEmpty()) {
            return Snapshot.empty();
        }
        int tradeCount = 0;
        long totalVolume = 0;
        long buyVolume = 0;
        long sellVolume = 0;
        double slippageSum = 0;
        int maximumBuyStreak = 0;
        int maximumSellStreak = 0;
        int currentStreak = 0;
        Boolean currentSide = null;

        for (Trade trade : trades) {
            tradeCount++;
            totalVolume += trade.volume();
            if (trade.buyerInitiated()) buyVolume += trade.volume();
            else sellVolume += trade.volume();
            slippageSum += trade.absoluteSlippage();
            if (currentSide == null || currentSide != trade.buyerInitiated()) {
                currentSide = trade.buyerInitiated();
                currentStreak = 1;
            } else {
                currentStreak++;
            }
            if (trade.buyerInitiated()) maximumBuyStreak = Math.max(maximumBuyStreak, currentStreak);
            else maximumSellStreak = Math.max(maximumSellStreak, currentStreak);
        }

        double seconds = windowMillis / 1000.0;
        long directionalVolume = buyVolume + sellVolume;
        double buyPercentage = directionalVolume == 0 ? 0 : buyVolume * 100.0 / directionalVolume;
        return new Snapshot(tradeCount, totalVolume, buyPercentage, 100.0 - buyPercentage,
                tradeCount / seconds, totalVolume / seconds, slippageSum / tradeCount,
                totalVolume / (double) tradeCount, maximumBuyStreak, maximumSellStreak);
    }

    private void prune(long now) {
        while (!trades.isEmpty() && now - trades.peekFirst().timestamp() > windowMillis) {
            trades.removeFirst();
        }
    }

    private record Trade(long timestamp, boolean buyerInitiated, int volume,
            double absoluteSlippage) { }

    public record Snapshot(int tradeCount, long totalVolume, double buyPercentage,
            double sellPercentage, double tradesPerSecond, double volumePerSecond,
            double averageSlippage, double averageVolume, int maximumBuyStreak,
            int maximumSellStreak) {
        public static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
