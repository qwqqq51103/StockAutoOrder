package StockMainAction.util.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 市場日誌記錄器，提供高效、線程安全的日誌記錄功能
 */
public class MarketLogger {

    private static final int LOG_QUEUE_CAPACITY = 10_000;
    private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private PrintWriter writer;
    private final BlockingQueue<String> logQueue = new ArrayBlockingQueue<>(LOG_QUEUE_CAPACITY);
    private final AtomicLong droppedLogCount = new AtomicLong();
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "market-log-writer");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean isRunning;

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

    private final List<LogListener> logListeners = new CopyOnWriteArrayList<>();

    private static class SingletonHolder {

        private static final MarketLogger INSTANCE = new MarketLogger();
    }

    public static MarketLogger getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private MarketLogger() {
        applySystemProperties();
        isRunning = true;
        try {
            writer = createWriter();
        } catch (IOException | RuntimeException e) {
            System.err.println("無法初始化檔案日誌，已切換為無檔案模式：" + e.getMessage());
        }
        startLogWriter();
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "market-log-shutdown"));
        } catch (RuntimeException ignored) {
            // Logging remains available even when shutdown hooks are restricted.
        }
    }

    private static PrintWriter createWriter() throws IOException {
        String configured = System.getProperty("market.log.dir", "").trim();
        File logDir = configured.isEmpty()
                ? new File(System.getProperty("user.home"), ".stock-main-action" + File.separator + "logs")
                : new File(configured);
        if ((!logDir.exists() && !logDir.mkdirs()) || !logDir.isDirectory()) {
            throw new IOException("無法建立日誌目錄：" + logDir.getAbsolutePath());
        }
        File logFile = new File(logDir,
                "market_simulation_" + LocalDateTime.now().format(FILE_FORMATTER) + ".log");
        return new PrintWriter(new FileWriter(logFile, true));
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
                        if (writer.checkError()) {
                            writer.close();
                            writer = null;
                        }
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
        if (!logQueue.offer(logEntry)) {
            droppedLogCount.incrementAndGet();
        }

        // 通知所有監聽器
        for (LogListener listener : logListeners) {
            try {
                listener.onNewLog(timestamp, level, category, message);
            } catch (RuntimeException ignored) {
                // A UI listener must never break the caller's business operation.
            }
        }
    }

    // 允許動態設置日誌級別
    public void setLogLevel(int level) {
        this.currentLogLevel = level;
    }

    public int getLogLevel() {
        return currentLogLevel;
    }

    public long getDroppedLogCount() {
        return droppedLogCount.get();
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
        } catch (RuntimeException ex) {
            System.err.println("無法讀取 market.log.level：" + ex.getMessage());
        }

        try {
            String c = System.getProperty("market.log.console", "false").trim().toLowerCase();
            consoleEnabled = "1".equals(c) || "true".equals(c) || "yes".equals(c) || "on".equals(c);
        } catch (RuntimeException ex) {
            System.err.println("無法讀取 market.log.console：" + ex.getMessage());
        }

        // -Dmarket.log.disabledCategories=RETAIL_INVESTOR_DECISION,ORDER_PROCESSING
        try {
            String dis = System.getProperty("market.log.disabledCategories", "").trim();
            if (!dis.isEmpty()) {
                for (String s : dis.split(",")) {
                    String cat = s.trim();
                    if (!cat.isEmpty()) disabledCategories.add(cat);
                }
            }
        } catch (RuntimeException ex) {
            System.err.println("無法讀取 market.log.disabledCategories：" + ex.getMessage());
        }

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
        } catch (RuntimeException ex) {
            System.err.println("無法讀取 market.log.categoryMinLevels：" + ex.getMessage());
        }
    }

    /**
     * 關閉日誌系統
     */
    public void shutdown() {
        isRunning = false;
        logExecutor.shutdownNow();
        if (writer != null) {
            writer.close();
        }
    }
}
