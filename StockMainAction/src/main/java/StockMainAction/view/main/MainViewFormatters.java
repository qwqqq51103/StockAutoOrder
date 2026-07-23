package StockMainAction.view.main;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * MainView 中重複使用的顯示文字格式化。
 */
public final class MainViewFormatters {
    private MainViewFormatters() {
    }

    public static String slippageStatus(int percent) {
        return "滑價保護: " + Math.max(0, Math.min(50, percent)) + "%";
    }

    public static String volumeBucketLabel(long alignedMillis, int periodKey) {
        String pattern = periodKey < 0 ? "HH:mm:ss" : "HH:mm";
        return new SimpleDateFormat(pattern).format(new Date(alignedMillis));
    }

    public static String ohlcChange(double open, double close) {
        double change = close - open;
        double changePct = open != 0.0 ? (change / open) * 100.0 : 0.0;
        return String.format("%+.2f (%+.2f%%)", change, changePct);
    }

    public static String ohlcHtml(String time, double open, double high, double low, double close) {
        String change = ohlcChange(open, close);
        String color = close >= open ? "#00897B" : "#D32F2F";
        return String.format(
                "<html><b>%s</b> <span style='color:%s'>%s</span><br/>O: %.2f H: %.2f L: %.2f C: %.2f</html>",
                time, color, change, open, high, low, close);
    }
}
