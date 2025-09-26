package StockMainAction.util.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * LogicAudit - 輕量級邏輯稽核日誌，用於在關鍵路徑輸出可讀的檢查訊息。
 * 獨立於 MarketLogger，寫入 audit.log 方便分流分析。
 */
public final class LogicAudit {

    private static final Logger AUDIT = Logger.getLogger("LOGIC_AUDIT");
    private static volatile boolean initialized = false;

    private LogicAudit() {}

    private static synchronized void init() {
        if (initialized) return;
        try {
            Path logDir = Paths.get("logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            FileHandler fh = new FileHandler("logs/audit.log", 10 * 1024 * 1024, 3, true);
            fh.setFormatter(new Formatter() {
                private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                @Override
                public String format(LogRecord record) {
                    String ts = sdf.format(new Date(record.getMillis()));
                    return String.format("[%s] [%s] %s%n", ts, record.getLevel(), record.getMessage());
                }
            });
            AUDIT.setUseParentHandlers(false);
            AUDIT.addHandler(fh);
            AUDIT.setLevel(Level.INFO);
            initialized = true;
        } catch (IOException e) {
            // 回退：若檔案建立失敗，改用父處理器（控制台）
            AUDIT.setUseParentHandlers(true);
        }
    }

    public static void info(String tag, String message) {
        init();
        AUDIT.log(Level.INFO, String.format("%s | %s", tag, message));
    }

    public static void warn(String tag, String message) {
        init();
        AUDIT.log(Level.WARNING, String.format("%s | %s", tag, message));
    }

    public static void error(String tag, String message) {
        init();
        AUDIT.log(Level.SEVERE, String.format("%s | %s", tag, message));
    }

    // ====== 常用檢查輔助 ======

    /** 價格跳躍檢查，超過閾值即回傳 true 並輸出 warning。 */
    public static boolean checkPriceJump(String tag, double prevPrice, double newPrice, double thresholdRatio) {
        if (prevPrice <= 0 || newPrice <= 0) return false;
        double jump = Math.abs(newPrice - prevPrice) / prevPrice;
        if (jump >= thresholdRatio) {
            warn(tag, String.format("PriceJump %.2f%% (prev=%.4f -> new=%.4f) threshold=%.2f%%",
                    jump * 100.0, prevPrice, newPrice, thresholdRatio * 100.0));
            return true;
        }
        return false;
    }

    /** 成交檢查：價格/數量異常。*/
    public static void checkTransaction(String tag, double price, int volume, String extra) {
        if (volume <= 0 || price <= 0) {
            warn(tag, String.format("AbnormalTx price=%.4f volume=%d %s", price, volume, extra == null ? "" : extra));
        }
    }
}


