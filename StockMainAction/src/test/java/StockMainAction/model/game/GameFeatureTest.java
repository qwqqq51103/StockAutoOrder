package StockMainAction.model.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import StockMainAction.model.StockMarketModel;
import java.time.Clock;
import java.util.List;
import org.junit.Test;

public class GameFeatureTest {
    @Test
    public void loadSettingsFromArgs() {
        GameSettings settings = GameSettings.load(new String[] {
                "--seed=12345",
                "--mode=pro",
                "--speed=fast"
        });

        assertEquals(12345L, settings.getSeed());
        assertEquals(GameMode.PRO, settings.getMode());
        assertEquals(SimulationSpeed.FAST, settings.getSpeed());
    }

    @Test
    public void scenarioEventsStartAfterOpeningWindow() {
        ScenarioEventEngine engine = new ScenarioEventEngine(7L);

        assertNull(engine.nextEvent(44));
        assertNotNull(engine.nextEvent(45));
        assertNull(engine.nextEvent(46));
        assertNotNull(engine.nextEvent(165));
    }

    @Test
    public void trackersHandleMissingStatistics() {
        MissionTracker missionTracker = new MissionTracker();
        AchievementTracker achievementTracker = new AchievementTracker();

        assertEquals(4, missionTracker.evaluate(null, null).size());
        List<String> achievements = achievementTracker.evaluate(null, null);
        assertTrue(achievements.isEmpty());
    }

    @Test
    public void riskOrderTriggersOnceAndClosesBracket() {
        RiskOrderManager manager = new RiskOrderManager();
        RiskOrder order = manager.addBracketOrder(100.0, 500, 300, 5.0, 10.0, 2.0, true);

        assertTrue(order.isActive());
        assertTrue(manager.evaluate(103.0, 500).isEmpty());
        List<RiskOrder.Trigger> triggers = manager.evaluate(90.0, 500);

        assertEquals(1, triggers.size());
        assertEquals("停損", triggers.get(0).reason());
        assertEquals(300, triggers.get(0).quantity());
        assertTrue(manager.evaluate(80.0, 500).isEmpty());
    }

    @Test
    public void watchlistIsDeterministicForSameSeed() {
        MarketWatchlist a = new MarketWatchlist(99L, 10.0);
        MarketWatchlist b = new MarketWatchlist(99L, 10.0);

        List<MarketWatchlist.InstrumentSnapshot> first =
                a.update(10, ScenarioEventEngine.Bias.BULLISH);
        List<MarketWatchlist.InstrumentSnapshot> second =
                b.update(10, ScenarioEventEngine.Bias.BULLISH);

        assertEquals(first.size(), second.size());
        assertEquals(first.get(1).price(), second.get(1).price(), 0.000001);
    }

    @Test
    public void modelSpeedPeriodCanBeChangedWithoutStartingSimulation() {
        try (StockMarketModel model = new StockMarketModel(42L, Clock.systemUTC())) {
            model.setSimulationPeriodMillis(SimulationSpeed.FAST.getPeriodMillis());
            assertEquals(SimulationSpeed.FAST.getPeriodMillis(), model.getSimulationPeriodMillis());
        }
    }
}
