package StockMainAction.util.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 市場日誌記錄器，提供高效、線程安全的日誌記錄功能
 */
public class MarketLogger {

    // 將日誌輸出到使用者桌面
    private static final String LOG_DIRECTORY = System.getProperty("user.home") + File.separator + "Desktop";
    private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private PrintWriter writer;
    private ConcurrentLinkedQueue<String> logQueue;
    private ExecutorService logExecutor;
    private boolean isRunning;

    // 新增日誌級別常量
    public static final int LEVEL_DEBUG = 1;
    public static final int LEVEL_INFO = 2;
    public static final int LEVEL_WARN = 3;
    public static final int LEVEL_ERROR = 4;

    // 當前全局日誌級別，可以從配置文件或環境變量中設置
    private int currentLogLevel = LEVEL_INFO; // 預設改為 INFO，避免長時間運行刷爆

    // 是否輸出到 console（可用 System property 關閉：-Dmarket.log.console=false）
    private volatile boolean consoleEnabled = false;

    // 分類控制：可禁用分類或設定分類最低級別
    private final Set<String> disabledCategories = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> categoryMinLevel = new ConcurrentHashMap<>();

    // 簡易節流：同 (category,key) 在 intervalMs 內只記一次
    private final Map<String, Long> throttles = new ConcurrentHashMap<>();

    // 日誌監聽器接口和監聽器列表
    public interface LogListener {

        void onNewLog(String timestamp, String level, String category, String message);
    }

    private List<LogListener> logListeners = new ArrayList<>();

    private static class SingletonHolder {

        private static final MarketLogger INSTANCE = new MarketLogger();
    }

    public static MarketLogger getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private MarketLogger() {
        try {
            // 讀取 system properties（方便你不用改碼也能調整）
            applySystemProperties();

            // 確保日誌目錄存在
            File logDir = new File(LOG_DIRECTORY);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // 創建帶時間戳的日誌文件
            String fileName = LOG_DIRECTORY + File.separator
                    + "market_simulation_" + LocalDateTime.now().format(FILE_FORMATTER) + ".log";

            writer = new PrintWriter(new FileWriter(fileName, true));
            logQueue = new ConcurrentLinkedQueue<>();
            logExecutor = Executors.newSingleThreadExecutor();
            isRunning = true;

            // 啟動日誌寫入線程
            startLogWriter();

            // 添加JVM關閉鉤子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        } catch (IOException e) {
            System.err.println("無法初始化日誌系統：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 添加日誌監聽器
     *
     * @param listener 日誌監聽器
     */
    public void addLogListener(LogListener listener) {
        if (listener != null) {
            logListeners.add(listener);
        }
    }

    /**
     * 移除日誌監聽器
     *
     * @param listener 日誌監聽器
     */
    public void removeLogListener(LogListener listener) {
        logListeners.remove(listener);
    }

    /**
     * 新增 debug 方法
     *
     * @param message 日誌消息
     * @param category 日誌類別
     */
    public void debug(String message, String category) {
        if (isEnabled(LEVEL_DEBUG, category)) {
            log("DEBUG", category, message);
        }
    }

    // 原有的 info、warn、error 方法保持不变
    public void info(String message, String category) {
        if (isEnabled(LEVEL_INFO, category)) {
            log("INFO", category, message);
        }
    }

    public void warn(String message, String category) {
        if (isEnabled(LEVEL_WARN, category)) {
            log("WARN", category, message);
        }
    }

    public void error(String message, String category) {
        if (isEnabled(LEVEL_ERROR, category)) {
            log("ERROR", category, message);
        }
    }

    public void error(Throwable e, String category) {
        if (isEnabled(LEVEL_ERROR, category)) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log("ERROR", category, sw.toString());
        }
    }

    /**
     * 節流版：同一個 (category,key) 在 intervalMs 內最多輸出一次
     */
    public void infoThrottled(String message, String category, String key, long intervalMs) {
        if (!isEnabled(LEVEL_INFO, category)) return;
        if (!hitThrottle(category, key, intervalMs)) return;
        log("INFO", category, message);
    }

    public void debugThrottled(String message, String category, String key, long intervalMs) {
        if (!isEnabled(LEVEL_DEBUG, category)) return;
        if (!hitThrottle(category, key, intervalMs)) return;
        log("DEBUG", category, message);
    }

    private void startLogWriter() {
        logExecutor.submit(() -> {
            while (isRunning) {
                String logMessage;
                while ((logMessage = logQueue.poll()) != null) {
                    if (writer != null) {
                        writer.println(logMessage);
                        writer.flush();
                    }
                }
                try {
                    Thread.sleep(100); // 避免不必要的CPU佔用
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void log(String level, String category, String message) {
        String timestamp = LocalDateTime.now().format(LOG_FORMATTER);
        String logEntry = String.format("[%s] [%s] [%s] %s",
                timestamp, level, category, message);

        // 控制台輸出
        if (consoleEnabled) {
            System.out.println(logEntry);
        }

        // 加入隊列
        logQueue.offer(logEntry);

        // 通知所有監聽器
        for (LogListener listener : logListeners) {
            listener.onNewLog(timestamp, level, category, message);
        }
    }

    // 允許動態設置日誌級別
    public void setLogLevel(int level) {
        this.currentLogLevel = level;
    }

    public int getLogLevel() {
        return currentLogLevel;
    }

    public void setConsoleEnabled(boolean enabled) {
        this.consoleEnabled = enabled;
    }

    public boolean isConsoleEnabled() {
        return consoleEnabled;
    }

    /**
     * 設定分類最低輸出級別（例如：ORDER_PROCESSING=LEVEL_WARN 代表只輸出 warn/error）
     */
    public void setCategoryMinLevel(String category, int minLevel) {
        if (category == null || category.isEmpty()) return;
        categoryMinLevel.put(category, minLevel);
    }

    public void setCategoryEnabled(String category, boolean enabled) {
        if (category == null || category.isEmpty()) return;
        if (enabled) disabledCategories.remove(category);
        else disabledCategories.add(category);
    }

    private boolean isEnabled(int levelInt, String category) {
        if (currentLogLevel > levelInt) return false;
        if (category == null) return true;
        if (disabledCategories.contains(category)) return false;
        Integer min = categoryMinLevel.get(category);
        if (min != null && levelInt < min) return false;
        return true;
    }

    private boolean hitThrottle(String category, String key, long intervalMs) {
        long now = System.currentTimeMillis();
        String k = (category == null ? "" : category) + "#" + (key == null ? "" : key);
        Long last = throttles.get(k);
        if (last != null && (now - last) < intervalMs) return false;
        throttles.put(k, now);
        return true;
    }

    private void applySystemProperties() {
        try {
            String lv = System.getProperty("market.log.level", "INFO").trim().toUpperCase();
            if ("DEBUG".equals(lv)) currentLogLevel = LEVEL_DEBUG;
            else if ("INFO".equals(lv)) currentLogLevel = LEVEL_INFO;
            else if ("WARN".equals(lv) || "WARNING".equals(lv)) currentLogLevel = LEVEL_WARN;
            else if ("ERROR".equals(lv)) currentLogLevel = LEVEL_ERROR;
        } catch (Exception ignore) {}

        try {
            String c = System.getProperty("market.log.console", "false").trim().toLowerCase();
            consoleEnabled = "1".equals(c) || "true".equals(c) || "yes".equals(c) || "on".equals(c);
        } catch (Exception ignore) {}

        // -Dmarket.log.disabledCategories=RETAIL_INVESTOR_DECISION,ORDER_PROCESSING
        try {
            String dis = System.getProperty("market.log.disabledCategories", "").trim();
            if (!dis.isEmpty()) {
                for (String s : dis.split(",")) {
                    String cat = s.trim();
                    if (!cat.isEmpty()) disabledCategories.add(cat);
                }
            }
        } catch (Exception ignore) {}

        // -Dmarket.log.categoryMinLevels=ORDER_PROCESSING=WARN;RETAIL_INVESTOR_DECISION=ERROR
        try {
            String mins = System.getProperty("market.log.categoryMinLevels", "").trim();
            if (!mins.isEmpty()) {
                for (String part : mins.split(";")) {
                    String p = part.trim();
                    if (p.isEmpty() || !p.contains("=")) continue;
                    String[] kv = p.split("=");
                    if (kv.length < 2) continue;
                    String cat = kv[0].trim();
                    String ml = kv[1].trim().toUpperCase();
                    int v = LEVEL_INFO;
                    if ("DEBUG".equals(ml)) v = LEVEL_DEBUG;
                    else if ("INFO".equals(ml)) v = LEVEL_INFO;
                    else if ("WARN".equals(ml) || "WARNING".equals(ml)) v = LEVEL_WARN;
                    else if ("ERROR".equals(ml)) v = LEVEL_ERROR;
                    if (!cat.isEmpty()) categoryMinLevel.put(cat, v);
                }
            }
        } catch (Exception ignore) {}
    }

    /**
     * 關閉日誌系統
     */
    public void shutdown() {
        isRunning = false;
        logExecutor.shutdown();
        if (writer != null) {
            writer.close();
        }
    }
}
