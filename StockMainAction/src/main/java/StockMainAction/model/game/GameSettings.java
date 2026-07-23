package StockMainAction.model.game;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Properties;

public final class GameSettings {
    private static final String DIR_NAME = ".stock-main-action";
    private static final String FILE_NAME = "game.properties";

    private final long seed;
    private final GameMode mode;
    private final SimulationSpeed speed;

    public GameSettings(long seed, GameMode mode, SimulationSpeed speed) {
        this.seed = seed;
        this.mode = mode == null ? GameMode.BEGINNER : mode;
        this.speed = speed == null ? SimulationSpeed.NORMAL : speed;
    }

    public static GameSettings load(String[] args) {
        Properties props = new Properties();
        Path file = settingsFile();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                props.load(input);
            } catch (IOException ignored) {
                // Corrupt settings should not stop the game from starting.
            }
        }

        applyArgs(props, args);

        long defaultSeed = LocalDate.now().toEpochDay();
        long seed = parseLong(props.getProperty("seed"), defaultSeed);
        GameMode mode = parseEnum(GameMode.class, props.getProperty("mode"), GameMode.BEGINNER);
        SimulationSpeed speed = parseEnum(SimulationSpeed.class, props.getProperty("speed"), SimulationSpeed.NORMAL);
        return new GameSettings(seed, mode, speed);
    }

    public void save() throws IOException {
        Path file = settingsFile();
        Files.createDirectories(file.getParent());
        Properties props = new Properties();
        props.setProperty("seed", Long.toString(seed));
        props.setProperty("mode", mode.name());
        props.setProperty("speed", speed.name());
        try (OutputStream output = Files.newOutputStream(file)) {
            props.store(output, "StockMainAction game settings");
        }
    }

    public GameSettings withMode(GameMode nextMode) {
        return new GameSettings(seed, nextMode, speed);
    }

    public GameSettings withSpeed(SimulationSpeed nextSpeed) {
        return new GameSettings(seed, mode, nextSpeed);
    }

    public long getSeed() {
        return seed;
    }

    public GameMode getMode() {
        return mode;
    }

    public SimulationSpeed getSpeed() {
        return speed;
    }

    public static Path settingsFile() {
        return Path.of(System.getProperty("user.home"), DIR_NAME, FILE_NAME);
    }

    private static void applyArgs(Properties props, String[] args) {
        if (args == null) return;
        for (String arg : args) {
            if (arg == null) continue;
            if (arg.startsWith("--seed=")) {
                props.setProperty("seed", arg.substring("--seed=".length()).trim());
            } else if (arg.startsWith("--mode=")) {
                props.setProperty("mode", arg.substring("--mode=".length()).trim());
            } else if (arg.startsWith("--speed=")) {
                props.setProperty("speed", arg.substring("--speed=".length()).trim());
            }
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
