package StockMainAction.controller;
import java.util.ArrayList;
import java.util.List;


/**
 * 技術指標計算器
 * 負責計算MACD、布林帶、KDJ等技術指標
 */
public class TechnicalIndicatorsCalculator {

    private List<Double> priceHistory;
    private List<Double> highHistory;
    private List<Double> lowHistory;

    // MACD參數
    private int macdShortPeriod = 12;
    private int macdLongPeriod = 26;
    private int macdSignalPeriod = 9;

    // 布林帶參數
    private int bollingerPeriod = 20;
    private double bollingerStdDevMultiplier = 2.0;

    // KDJ參數
    private int kdjPeriod = 9;
    private int kPeriod = 3;
    private int dPeriod = 3;

    public TechnicalIndicatorsCalculator() {
        this.priceHistory = new ArrayList<>();
        this.highHistory = new ArrayList<>();
        this.lowHistory = new ArrayList<>();
    }

    /**
     * 更新價格數據
     */
    public void updatePriceData(double price, double high, double low) {
        priceHistory.add(price);
        highHistory.add(high);
        lowHistory.add(low);

        // 限制歷史數據大小，保持性能
        int maxHistorySize = 1000;
        if (priceHistory.size() > maxHistorySize) {
            priceHistory.remove(0);
            highHistory.remove(0);
            lowHistory.remove(0);
        }
    }

    /**
     * 計算MACD指標
     *
     * @return [macdLine, signalLine, histogram] 或 null 如果數據不足
     */
    public double[] calculateMACD() {
        if (priceHistory.size() < macdLongPeriod + macdSignalPeriod) {
            return null; // 數據不足
        }

        // 計算短期和長期EMA
        double shortEMA = calculateEMA(priceHistory, macdShortPeriod);
        double longEMA = calculateEMA(priceHistory, macdLongPeriod);

        // MACD線 = 短期EMA - 長期EMA
        double macdLine = shortEMA - longEMA;

        // 計算信號線（MACD線的EMA）
        List<Double> macdHistory = calculateMACDHistory();
        double signalLine = calculateEMA(macdHistory, macdSignalPeriod);

        // 柱狀圖 = MACD線 - 信號線
        double histogram = macdLine - signalLine;

        return new double[]{macdLine, signalLine, histogram};
    }

    /**
     * 計算布林帶指標
     *
     * @return [upperBand, middleBand, lowerBand] 或 null 如果數據不足
     */
    public double[] calculateBollingerBands() {
        if (priceHistory.size() < bollingerPeriod) {
            return null; // 數據不足
        }

        // 計算中軌（簡單移動平均）
        double middleBand = calculateSMA(priceHistory, bollingerPeriod);

        // 計算標準差
        double stdDev = calculateStandardDeviation(priceHistory, bollingerPeriod);

        // 上軌和下軌
        double upperBand = middleBand + (bollingerStdDevMultiplier * stdDev);
        double lowerBand = middleBand - (bollingerStdDevMultiplier * stdDev);

        return new double[]{upperBand, middleBand, lowerBand};
    }

    /**
     * 計算KDJ指標
     *
     * @return [kValue, dValue, jValue] 或 null 如果數據不足
     */
    public double[] calculateKDJ() {
        if (priceHistory.size() < kdjPeriod || highHistory.size() < kdjPeriod || lowHistory.size() < kdjPeriod) {
            return null; // 數據不足
        }

        // 計算RSV (Raw Stochastic Value)
        double currentPrice = priceHistory.get(priceHistory.size() - 1);
        double highestHigh = getHighestHigh(kdjPeriod);
        double lowestLow = getLowestLow(kdjPeriod);

        double rsv = 0;
        if (highestHigh != lowestLow) {
            rsv = ((currentPrice - lowestLow) / (highestHigh - lowestLow)) * 100;
        }

        // 簡化版KDJ計算（實際應用中會使用更複雜的平滑算法）
        double kValue = rsv; // 簡化：K = RSV
        double dValue = calculateSMA(getKHistory(), dPeriod); // D = K的移動平均
        double jValue = 3 * kValue - 2 * dValue; // J = 3K - 2D

        return new double[]{kValue, dValue, jValue};
    }

    // ========== 輔助計算方法 ==========
    /**
     * 計算指數移動平均（EMA）
     */
    private double calculateEMA(List<Double> data, int period) {
        if (data.size() < period) {
            return 0;
        }
        // 初始種子用最早 period 筆的 SMA
        double sum = 0;
        for (int i = 0; i < period; i++) sum += data.get(i);
        double ema = sum / period;
        double multiplier = 2.0 / (period + 1);
        for (int i = period; i < data.size(); i++) {
            ema = (data.get(i) - ema) * multiplier + ema;
        }
        return ema;
    }

    /**
     * 計算簡單移動平均（SMA）
     */
    private double calculateSMA(List<Double> data, int period) {
        if (data.size() < period) {
            return 0;
        }

        double sum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            sum += data.get(i);
        }

        return sum / period;
    }

    /**
     * 計算標準差
     */
    private double calculateStandardDeviation(List<Double> data, int period) {
        if (data.size() < period) {
            return 0;
        }

        double mean = calculateSMA(data, period);
        double sumSquaredDiff = 0;

        for (int i = data.size() - period; i < data.size(); i++) {
            double diff = data.get(i) - mean;
            sumSquaredDiff += diff * diff;
        }

        return Math.sqrt(sumSquaredDiff / period);
    }

    /**
     * 獲取指定期間內的最高價
     */
    private double getHighestHigh(int period) {
        if (highHistory.size() < period) {
            return 0;
        }

        double highest = highHistory.get(highHistory.size() - period);
        for (int i = highHistory.size() - period + 1; i < highHistory.size(); i++) {
            highest = Math.max(highest, highHistory.get(i));
        }

        return highest;
    }

    /**
     * 獲取指定期間內的最低價
     */
    private double getLowestLow(int period) {
        if (lowHistory.size() < period) {
            return Double.MAX_VALUE;
        }

        double lowest = lowHistory.get(lowHistory.size() - period);
        for (int i = lowHistory.size() - period + 1; i < lowHistory.size(); i++) {
            lowest = Math.min(lowest, lowHistory.get(i));
        }

        return lowest;
    }

    /**
     * 計算MACD歷史數據（用於信號線計算）
     */
    private List<Double> calculateMACDHistory() {
        List<Double> macdHistory = new ArrayList<>();

        for (int i = macdLongPeriod; i <= priceHistory.size(); i++) {
            List<Double> subPrices = priceHistory.subList(0, i);
            double shortEMA = calculateEMA(subPrices, macdShortPeriod);
            double longEMA = calculateEMA(subPrices, macdLongPeriod);
            macdHistory.add(shortEMA - longEMA);
        }

        return macdHistory;
    }

    /**
     * 獲取K值歷史數據（簡化版）
     */
    private List<Double> getKHistory() {
        // 簡化實作：返回最近幾個RSV值
        List<Double> kHistory = new ArrayList<>();

        for (int i = Math.max(0, priceHistory.size() - dPeriod); i < priceHistory.size(); i++) {
            if (i >= kdjPeriod - 1) {
                // 計算該點的RSV
                double price = priceHistory.get(i);
                double high = getHighestHighAt(i, kdjPeriod);
                double low = getLowestLowAt(i, kdjPeriod);

                double rsv = 0;
                if (high != low) {
                    rsv = ((price - low) / (high - low)) * 100;
                }
                kHistory.add(rsv);
            }
        }

        return kHistory;
    }

    private double getHighestHighAt(int index, int period) {
        int start = Math.max(0, index - period + 1);
        double highest = highHistory.get(start);
        for (int i = start + 1; i <= index; i++) {
            highest = Math.max(highest, highHistory.get(i));
        }
        return highest;
    }

    private double getLowestLowAt(int index, int period) {
        int start = Math.max(0, index - period + 1);
        double lowest = lowHistory.get(start);
        for (int i = start + 1; i <= index; i++) {
            lowest = Math.min(lowest, lowHistory.get(i));
        }
        return lowest;
    }
}
