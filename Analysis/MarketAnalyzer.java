package Analysis;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Iterator;

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
     * @param price  成交價格
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
        //System.out.println("MarketAnalyzer - 計算最近平均成交量: " + avgVolume);
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
}
