package StockMainAction.service;

import StockMainAction.model.StockMarketModel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MarketInterventionServiceTest {
    @Test
    public void requestClampsWorkAndDepthLimits() {
        MarketInterventionService.Request request = new MarketInterventionService.Request(
                true, 10, 10_000, true, true, 100, 0.9, false, false);

        assertEquals(500, request.slices());
        assertEquals(30, request.depthLevels());
        assertEquals(0.15, request.depthSpanRatio(), 0.0);
    }

    @Test
    public void closedServiceRejectsNewWork() throws Exception {
        StockMarketModel model = new StockMarketModel(42L,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        MarketInterventionService service = new MarketInterventionService(model);
        service.close();

        try {
            service.execute(new MarketInterventionService.Request(
                    true, 1, 1, false, false, 1, 0, false, false)).get();
            fail("Expected rejected intervention");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        } finally {
            model.close();
        }
    }
}
