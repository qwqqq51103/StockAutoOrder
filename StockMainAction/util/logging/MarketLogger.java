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
    private int currentLogLevel = LEVEL_DEBUG;

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
        if (currentLogLevel <= LEVEL_DEBUG) {
            log("DEBUG", category, message);
        }
    }

    // 原有的 info、warn、error 方法保持不变
    public void info(String message, String category) {
        if (currentLogLevel <= LEVEL_INFO) {
            log("INFO", category, message);
        }
    }

    public void warn(String message, String category) {
        if (currentLogLevel <= LEVEL_WARN) {
            log("WARN", category, message);
        }
    }

    public void error(String message, String category) {
        if (currentLogLevel <= LEVEL_ERROR) {
            log("ERROR", category, message);
        }
    }

    public void error(Throwable e, String category) {
        if (currentLogLevel <= LEVEL_ERROR) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log("ERROR", category, sw.toString());
        }
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
        System.out.println(logEntry);

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
