package StockMainAction.model;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.List;

/**
 * 市場分析器 - 用於計算 SMA、波動性、平均成交量和加權平均價格
 */
public class MarketAnalyzer {

    private final Deque<Double> recentPriceData = new LinkedList<>(); // 價格波動數據
    private final Deque<Double> recentTransactionPrices = new LinkedList<>(); // 交易價格數據
    private final Deque<Integer> recentVolumes = new LinkedList<>(); // 成交量數據
    private final int smaPeriod;  // SMA 的計算週期
    private double sumPrices = 0.0;
    private double volatility = 0.0;

    // 用於計算加權平均價格
    private double sumPriceVolume = 0.0; // 累積價格 × 成交量
    private int sumVolume = 0;            // 累積成交量

    // 用於 RSI 計算
    private final int rsiPeriod = 14; // RSI 的計算週期
    private final Deque<Double> gainLossHistory = new LinkedList<>();
    private double averageGain = 0.0;
    private double averageLoss = 0.0;
    private boolean rsiInitialized = false;

    /**
     * 構造函數
     *
     * @param smaPeriod SMA 計算的週期
     */
    public MarketAnalyzer(int smaPeriod) {
        this.smaPeriod = smaPeriod;
    }

    /**
     * 添加交易數據並更新價格和成交量歷史
     *
     * @param price 成交價格
     * @param volume 成交量
     */
    public synchronized void addTransaction(double price, int volume) {
        // 調試輸出
        //System.out.println("MarketAnalyzer - 添加交易數據 - 價格: " + price + ", 成交量: " + volume);

        // 更新交易價格歷史
        if (recentTransactionPrices.size() >= smaPeriod) {
            double removedPrice = recentTransactionPrices.poll();
            int removedVolume = recentVolumes.poll();
            sumPriceVolume -= removedPrice * removedVolume;
            sumVolume -= removedVolume;
        }
        recentTransactionPrices.add(price);
        recentVolumes.add(volume);
        sumPriceVolume += price * volume;
        sumVolume += volume;

        // 調試輸出當前累積數據
        //System.out.println("MarketAnalyzer - 累積價格*量: " + sumPriceVolume + ", 累積量: " + sumVolume);
    }

    /**
     * 添加價格波動數據並更新 SMA 和 RSI
     *
     * @param price 當前價格
     */
    public synchronized void addPrice(double price) {
        // 調試輸出
        //System.out.println("MarketAnalyzer - 添加價格波動數據 - 價格: " + price);

        // 更新價格波動歷史
        if (recentPriceData.size() >= smaPeriod) {
            recentPriceData.poll();
        }
        recentPriceData.add(price);

        // RSI 計算
        if (recentPriceData.size() > 1) { // 確保有至少兩個價格點來計算變化
            Iterator<Double> iterator = recentPriceData.iterator();
            Double previousPrice = iterator.next();
            Double currentPrice = price;
            double change = currentPrice - previousPrice;
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);

            gainLossHistory.add(gain);
            if (gainLossHistory.size() > rsiPeriod) {
                gainLossHistory.poll();
            }

            if (gainLossHistory.size() == rsiPeriod) {
                if (!rsiInitialized) {
                    averageGain = gainLossHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    averageLoss = gainLossHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    rsiInitialized = true;
                } else {
                    averageGain = (averageGain * (rsiPeriod - 1) + gain) / rsiPeriod;
                    averageLoss = (averageLoss * (rsiPeriod - 1) + loss) / rsiPeriod;
                }
            }
        }

        // 調試輸出當前價格數據
        //System.out.println("MarketAnalyzer - 更新後價格數據數量: " + recentPriceData.size());
    }

    /**
     * 計算簡單移動平均線 (SMA)
     *
     * @return SMA 值，如果數據不足則返回 NaN
     */
    public synchronized double calculateSMA() {
        if (recentPriceData.size() < smaPeriod) {
            //System.out.println("MarketAnalyzer - 計算 SMA: NaN (價格數據不足)");
            return Double.NaN;
        }
        double sma = recentPriceData.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        // 調試輸出 SMA 計算結果
        //System.out.println("MarketAnalyzer - 計算 SMA: " + sma);
        return sma;
    }

    /**
     * 計算市場波動性
     *
     * @return 波動性值
     */
    public synchronized double calculateVolatility() {
        if (recentPriceData.size() < 2) {
            //System.out.println("MarketAnalyzer - 計算波動性: 0.0 (價格數據不足)");
            return 0.0;
        }
        double mean = calculateSMA();
        double variance = 0.0;
        for (double price : recentPriceData) {
            variance += Math.pow(price - mean, 2);
        }
        variance /= recentPriceData.size();
        volatility = Math.sqrt(variance);
        //System.out.println("MarketAnalyzer - 計算波動性: " + volatility);
        return volatility;
    }

    /**
     * 計算最近平均成交量
     *
     * @return 最近平均成交量
     */
    public synchronized double getRecentAverageVolume() {
        if (recentVolumes.isEmpty()) {
            //System.out.println("MarketAnalyzer - 計算最近平均成交量: 0.0 (無成交量數據)");
            return 0.0;
        }
        double avgVolume = recentVolumes.stream().mapToInt(Integer::intValue).average().orElse(0.0);
//        System.out.println("MarketAnalyzer - 計算最近平均成交量: " + avgVolume);
        return avgVolume;
    }

    /**
     * 獲取 SMA 值
     *
     * @return SMA 值
     */
    public double getSMA() {
        return calculateSMA();
    }

    /**
     * 獲取波動性值
     *
     * @return 波動性值
     */
    public double getVolatility() {
        return volatility;
    }

    /**
     * 獲取 RSI 值
     *
     * @return RSI 值，如果數據不足則返回 NaN
     */
    public synchronized double getRSI() {
        if (!rsiInitialized) {
            //System.out.println("MarketAnalyzer - 計算 RSI: NaN (數據不足)");
            return Double.NaN;
        }
        double rs = averageGain / (averageLoss == 0 ? 1 : averageLoss);
        double rsi = 100 - (100 / (1 + rs));
        //System.out.println("MarketAnalyzer - 計算 RSI: " + rsi);
        return rsi;
    }

    /**
     * 獲取加權平均價格（WAP）
     *
     * @return 加權平均價格，如果成交量為 0 則返回 NaN
     */
    public synchronized double getWeightedAveragePrice() {
        if (sumVolume == 0) {
            //System.out.println("MarketAnalyzer - 計算加權平均價格: NaN (成交量為 0)");
            return Double.NaN;
        }
        double wap = sumPriceVolume / sumVolume;
        //System.out.println("MarketAnalyzer - 計算加權平均價格: " + wap);
        return wap;
    }

    /**
     * 計算近期價格趨勢 返回一個介於-1到1之間的值，表示價格變化方向和強度 正值表示上漲趨勢，負值表示下跌趨勢，0表示無明顯趨勢
     *
     * @return 趨勢值，範圍: [-1, 1]
     */
    public double getRecentPriceTrend() {
        // 如果歷史價格數據不足，無法計算趨勢
        if (recentPriceData.size() < 2) {
            return 0.0;
        }

        // 取最近的價格數據來計算趨勢
        int windowSize = Math.min(20, recentPriceData.size() - 1); // 最多使用最近20個數據點

        // 取得窗口內的價格數據
        List<Double> recentPrices = new ArrayList<>(recentPriceData);
        // 只使用最近的 windowSize + 1 個數據點
        if (recentPrices.size() > windowSize + 1) {
            recentPrices = recentPrices.subList(recentPrices.size() - windowSize - 1, recentPrices.size());
        }

        // 計算簡單線性回歸的斜率
        // 首先計算x和y的平均值
        double sumX = 0;
        double sumY = 0;
        for (int i = 0; i < recentPrices.size(); i++) {
            sumX += i;
            sumY += recentPrices.get(i);
        }
        double avgX = sumX / recentPrices.size();
        double avgY = sumY / recentPrices.size();

        // 計算斜率: slope = Σ((x_i - avgX) * (y_i - avgY)) / Σ((x_i - avgX)^2)
        double numerator = 0;
        double denominator = 0;
        for (int i = 0; i < recentPrices.size(); i++) {
            double xDiff = i - avgX;
            double yDiff = recentPrices.get(i) - avgY;
            numerator += xDiff * yDiff;
            denominator += xDiff * xDiff;
        }

        // 防止除以零
        if (Math.abs(denominator) < 0.0001) {
            return 0.0;
        }

        // 計算斜率
        double slope = numerator / denominator;

        // 標準化斜率為-1到1之間的值
        // 使用最後一個價格的百分比變化來標準化
        double lastPrice = recentPrices.get(recentPrices.size() - 1);
        double normalizedSlope = slope * windowSize / lastPrice; // 標準化

        // 限制在 [-1, 1] 範圍內
        return Math.max(-1.0, Math.min(normalizedSlope, 1.0));
    }

    /**
     * 另一種實現: 使用移動平均線計算趨勢 當短期均線在長期均線之上時為上漲趨勢，反之為下跌趨勢
     *
     * @return 趨勢值，範圍: [-1, 1]
     */
    public double getTrendUsingMA() {
        if (recentPriceData.size() < 20) {
            return 0.0; // 數據不足以計算
        }

        // 計算5日和20日移動平均線
        double shortMA = calculateSMA(5); // 短期均線
        double longMA = calculateSMA(20); // 長期均線

        if (shortMA == 0 || longMA == 0) {
            return 0.0; // 均線計算失敗
        }

        // 計算均線差異的百分比
        double diff = (shortMA - longMA) / longMA;

        // 標準化到 [-1, 1] 範圍
        // 通常 ±5% 的差異已經很明顯了
        double normalizedDiff = diff * 20; // 5% 差異將轉換為 ±1

        // 限制在 [-1, 1] 範圍內
        return Math.max(-1.0, Math.min(normalizedDiff, 1.0));
    }

    /**
     * 計算指定期間的簡單移動平均線
     *
     * @param period 期間長度
     * @return 移動平均線值
     */
    public double calculateSMA(int period) {
        if (recentPriceData.size() < period) {
            return 0.0; // 數據不足
        }

        // 創建一個 List 來處理 Deque 資料
        List<Double> prices = new ArrayList<>(recentPriceData);

        // 取最近 period 個價格
        List<Double> recentPrices = prices.subList(prices.size() - period, prices.size());

        // 計算平均值
        double sum = 0;
        for (double price : recentPrices) {
            sum += price;
        }

        return sum / period;
    }
}
