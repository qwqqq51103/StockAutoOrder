package StockMainAction.model.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MarketWatchlist {
    private final Random random;
    private final List<Instrument> instruments = new ArrayList<>();

    public MarketWatchlist(long seed, double basePrice) {
        this.random = new Random(seed ^ 0x9E3779B97F4A7C15L);
        double base = basePrice > 0 ? basePrice : 10.0;
        instruments.add(new Instrument("台積電", base));
        instruments.add(new Instrument("AI 伺服器鏈", base * 0.72));
        instruments.add(new Instrument("金融權值", base * 1.18));
        instruments.add(new Instrument("航運景氣", base * 0.54));
    }

    public synchronized List<InstrumentSnapshot> update(int tick, ScenarioEventEngine.Bias bias) {
        double biasDrift = switch (bias == null ? ScenarioEventEngine.Bias.NEUTRAL : bias) {
            case BULLISH -> 0.004;
            case BEARISH -> -0.004;
            case VOLATILE -> 0.0;
            case NEUTRAL -> 0.0;
        };
        double volatility = bias == ScenarioEventEngine.Bias.VOLATILE ? 0.025 : 0.012;
        for (int i = 0; i < instruments.size(); i++) {
            Instrument instrument = instruments.get(i);
            double sectorTilt = (i - 1.5) * 0.001;
            double randomMove = (random.nextDouble() - 0.5) * volatility;
            instrument.price = Math.max(0.01, instrument.price * (1.0 + biasDrift + sectorTilt + randomMove));
            instrument.changePct = ((instrument.price - instrument.openPrice) / instrument.openPrice) * 100.0;
        }
        return snapshot(tick);
    }

    public synchronized List<InstrumentSnapshot> snapshot(int tick) {
        List<InstrumentSnapshot> out = new ArrayList<>();
        for (Instrument instrument : instruments) {
            out.add(new InstrumentSnapshot(instrument.name, instrument.price, instrument.changePct, tick));
        }
        return out;
    }

    private static final class Instrument {
        private final String name;
        private final double openPrice;
        private double price;
        private double changePct;

        private Instrument(String name, double openPrice) {
            this.name = name;
            this.openPrice = openPrice;
            this.price = openPrice;
        }
    }

    public record InstrumentSnapshot(String name, double price, double changePct, int tick) {
        public String displayText() {
            return String.format("%s %.2f (%+.2f%%)", name, price, changePct);
        }
    }
}
