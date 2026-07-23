package StockMainAction.view.main;

import org.jfree.data.time.ohlc.OHLCItem;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * OHLC 資訊面板的文字與節流判斷。
 */
public final class OhlcInfoPresenter {
    private static final long DEFAULT_THROTTLE_MILLIS = 200L;

    private OhlcInfoPresenter() {
    }

    public static boolean shouldUpdate(long nextXMillis, long previousXMillis,
            long nowMillis, long previousUpdateMillis) {
        return shouldUpdate(nextXMillis, previousXMillis, nowMillis,
                previousUpdateMillis, DEFAULT_THROTTLE_MILLIS);
    }

    public static boolean shouldUpdate(long nextXMillis, long previousXMillis,
            long nowMillis, long previousUpdateMillis, long throttleMillis) {
        return nextXMillis != previousXMillis || (nowMillis - previousUpdateMillis) >= Math.max(0L, throttleMillis);
    }

    public static String html(OHLCItem item) {
        if (item == null) {
            return "";
        }
        long xMillis = KlineOhlcAggregator.itemXMillis(item);
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date(xMillis));
        return html(time, item.getOpenValue(), item.getHighValue(), item.getLowValue(), item.getCloseValue());
    }

    public static String html(String time, double open, double high, double low, double close) {
        String color = close >= open ? "#26a69a" : "#ef5350";
        return String.format(
                "<html><div style='font-family: Monospaced; font-size: 11px;'>" +
                "<b>%s</b>  <span style='color: %s;'>%s</span><br/>" +
                "O: %.2f  H: %.2f  L: %.2f  C: <span style='color: %s; font-weight: bold;'>%.2f</span>" +
                "</div></html>",
                time, color, MainViewFormatters.ohlcChange(open, close),
                open, high, low, color, close);
    }
}
