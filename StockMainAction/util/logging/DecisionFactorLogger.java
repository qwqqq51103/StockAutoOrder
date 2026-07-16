package StockMainAction.util.logging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 決策因子日誌：將每次下單的關鍵因子輸出到使用者桌面 (CSV)
 */
public final class DecisionFactorLogger {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile boolean headerWritten = false;
    private static volatile Path logPath;

    private DecisionFactorLogger() {}

    private static Path resolveLogPath() throws IOException {
        if (logPath != null) return logPath;
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        String fileName = String.format("decision_factors_%s.csv", LocalDate.now());
        Path p = Paths.get(desktop, fileName);
        if (!Files.exists(p)) headerWritten = false; // 讓 FileWriter 來建檔；避免多線程競爭
        logPath = p;
        return p;
    }

    private static void ensureHeader(BufferedWriter bw) throws IOException {
        if (headerWritten) return;
        bw.write("time,actor,phase,action,orderType,volume,price,inPct,outPct,delta,tps,vps,imbalance,effThreshold,positionScale,macdHist,kVal,hasBuyWall,hasSellWall\n");
        headerWritten = true;
    }

    public static void log(String actor,
                           String phase,
                           String action,
                           String orderType,
                           int volume,
                           double price,
                           int inPct,
                           int outPct,
                           long delta,
                           double tps,
                           double vps,
                           double imbalance,
                           int effThreshold,
                           double positionScale,
                           double macdHist,
                           double kVal,
                           boolean hasBuyWall,
                           boolean hasSellWall) {
        synchronized (LOCK) {
            try {
                Path p = resolveLogPath();
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(p.toFile(), true))) {
                    ensureHeader(bw);
                    String ts = LocalDateTime.now().format(TS);
                    String line = String.format("%s,%s,%s,%s,%s,%d,%.6f,%d,%d,%d,%.3f,%.0f,%.3f,%d,%.2f,%.4f,%.2f,%b,%b\n",
                            ts, safe(actor), safe(phase), safe(action), safe(orderType), volume, price,
                            inPct, outPct, delta, tps, vps, imbalance, effThreshold, positionScale, macdHist, kVal, hasBuyWall, hasSellWall);
                    bw.write(line);
                }
            } catch (IOException ignore) {
            }
        }
    }

    private static String safe(String s) { return s == null ? "" : s.replace(',', ';'); }
}


