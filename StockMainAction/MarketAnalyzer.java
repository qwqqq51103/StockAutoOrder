package StockMainAction;

import java.util.Deque;
import java.util.LinkedList;

/**
 * 市場分析器 - 用於計算 SMA、波動性和平均成交量
 */
public class MarketAnalyzer {

    private Deque<Double> recentPrices = new LinkedList<>();
    private Deque<Integer> recentVolumes = new LinkedList<>();
    private final int smaPeriod;  // 設定 SMA 的計算週期
    private double volatility;

    public MarketAnalyzer(int smaPeriod) {
        this.smaPeriod = smaPeriod;
    }

    // 添加價格並更新價格歷史
    public void addPrice(double price) {
        if (recentPrices.size() >= smaPeriod) {
            recentPrices.poll();  // 移除最早的價格
        }
        recentPrices.add(price);
    }

    // 添加成交量並更新成交量歷史
    public void addVolume(int volume) {
        if (recentVolumes.size() >= smaPeriod) {
            recentVolumes.poll();  // 移除最早的成交量
        }
        recentVolumes.add(volume);
    }

    // 計算 SMA
    public double calculateSMA() {
        return recentPrices.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    // 計算波動性
    public double calculateVolatility() {
        if (recentPrices.size() < 2) {
            return 0.0;
        }
        double mean = recentPrices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = recentPrices.stream().mapToDouble(p -> Math.pow(p - mean, 2)).sum() / recentPrices.size();
        volatility = Math.sqrt(variance);
        return volatility;
    }

    // 計算最近平均成交量
    public int getRecentAverageVolume() {
        return (int) recentVolumes.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    public double getSMA() {
        return calculateSMA();
    }

    public double getVolatility() {
        return volatility;
    }
}
