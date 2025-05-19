package Logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;

/**
 * 日誌配置管理器
 */
public class LoggerConfig {

    private static Properties properties;
    private static final String CONFIG_FILE = "logger.properties";

    // 日誌級別枚舉
    public enum LogLevel {
        ALL(java.util.logging.Level.ALL),
        TRACE(java.util.logging.Level.FINEST),
        DEBUG(java.util.logging.Level.FINE),
        INFO(java.util.logging.Level.INFO),
        WARN(java.util.logging.Level.WARNING),
        ERROR(java.util.logging.Level.SEVERE),
        OFF(java.util.logging.Level.OFF);

        private final java.util.logging.Level level;

        LogLevel(java.util.logging.Level level) {
            this.level = level;
        }

        public java.util.logging.Level getLevel() {
            return level;
        }
    }

    // 靜態初始化區塊
    static {
        properties = new Properties();
        try ( InputStream input = LoggerConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
            } else {
                // 如果找不到配置文件，使用默認配置
                setDefaultConfig();
            }
        } catch (IOException ex) {
            // 發生異常時使用默認配置
            setDefaultConfig();
            System.err.println("無法讀取日誌配置文件：" + ex.getMessage());
        }
    }

    // 設置默認配置
    private static void setDefaultConfig() {
        properties.setProperty("global.level", "INFO");
        properties.setProperty("file.level", "INFO");
        properties.setProperty("console.level", "INFO");

        // 特定類別的日誌級別配置
        properties.setProperty("MARKET_SIMULATION.level", "DEBUG");
        properties.setProperty("MARKET_BEHAVIOR.level", "INFO");
        properties.setProperty("ORDER_PROCESSING.level", "WARN");
    }

    /**
     * 獲取全局日誌級別
     *
     * @return LogLevel
     */
    public static LogLevel getGlobalLogLevel() {
        String levelStr = properties.getProperty("global.level", "INFO");
        return LogLevel.valueOf(levelStr.toUpperCase());
    }

    /**
     * 獲取特定類別的日誌級別
     *
     * @param category 日誌類別
     * @return LogLevel
     */
    public static LogLevel getCategoryLogLevel(String category) {
        String levelStr = properties.getProperty(category + ".level",
                properties.getProperty("global.level", "INFO"));
        return LogLevel.valueOf(levelStr.toUpperCase());
    }

    /**
     * 是否啟用控制台輸出
     *
     * @return boolean
     */
    public static boolean isConsoleOutputEnabled() {
        return Boolean.parseBoolean(
                properties.getProperty("console.output", "true")
        );
    }

    /**
     * 是否啟用文件輸出
     *
     * @return boolean
     */
    public static boolean isFileOutputEnabled() {
        return Boolean.parseBoolean(
                properties.getProperty("file.output", "true")
        );
    }

    /**
     * 最大日誌文件大小（MB）
     *
     * @return int
     */
    public static int getMaxLogFileSize() {
        return Integer.parseInt(
                properties.getProperty("max.log.file.size", "10")
        );
    }

    /**
     * 最大日誌文件備份數量
     *
     * @return int
     */
    public static int getMaxLogFileBackups() {
        return Integer.parseInt(
                properties.getProperty("max.log.file.backups", "5")
        );
    }
}
