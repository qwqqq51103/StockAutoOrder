package StockMainAction.model.core;

import StockMainAction.model.user.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimeBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-03-04T05:06:07Z");

    @Test
    public void marketTransactionAndFillUseInjectedClock() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        Transaction transaction = new Transaction(
                "T1", "test", "MARKET_BUY", 2, 10, 10, clock);

        transaction.addFillRecord(10, 2, "counterparty", 1);
        transaction.completeMarketOrderTransaction(10, 1, null);

        assertEquals(clock.millis(), transaction.getTimestamp());
        assertEquals(clock.millis(), transaction.getFillRecords().get(0).getTimestamp());
        assertEquals(0, transaction.getExecutionTimeMs());
    }

    @Test
    public void personalStatisticsUsesInjectedLocalDate() {
        ZoneId zone = ZoneId.of("Asia/Taipei");
        Clock clock = Clock.fixed(NOW, zone);
        PersonalStatistics statistics = new PersonalStatistics(
                new UserAccount(1_000, 10), null, 1_000, clock);

        statistics.addTradeRecord("買入", 1, 10);

        assertEquals(NOW.atZone(zone).toLocalDateTime(),
                statistics.getTradeHistory().get(0).getTimestamp());
        assertEquals(1, statistics.getTradesByPeriod(PersonalStatistics.StatsPeriod.TODAY).size());
    }
}
