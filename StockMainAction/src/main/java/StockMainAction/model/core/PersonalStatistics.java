// === 1. 個人統計數據模型 ===
// 位置: model/core/PersonalStatistics.java
package StockMainAction.model.core;

import StockMainAction.model.PersonalAI;
import StockMainAction.model.user.UserAccount;
import StockMainAction.util.logging.MarketLogger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * 個人交易統計數據模型
 */
public class PersonalStatistics {

    /**
     * 統計時間段枚舉
     */
    public enum StatsPeriod {
        TODAY("今日"),
        THIS_WEEK("本週"),
        THIS_MONTH("本月"),
        ALL_TIME("全部時間");

        private String displayName;

        StatsPeriod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 交易記錄類別
     */
    public static class TradeRecord {

        private LocalDateTime timestamp;
        private String type; // "買入" or "賣出"
        private int quantity;
        private double price;
        private double totalAmount;
        private double profitLoss; // 僅賣出時有效

        public TradeRecord(LocalDateTime timestamp, String type, int quantity,
                double price, double totalAmount, double profitLoss) {
            this.timestamp = timestamp;
            this.type = type;
            this.quantity = quantity;
            this.price = price;
            this.totalAmount = totalAmount;
            this.profitLoss = profitLoss;
        }

        // Getters
        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getType() {
            return type;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getPrice() {
            return price;
        }

        public double getTotalAmount() {
            return totalAmount;
        }

        public double getProfitLoss() {
            return profitLoss;
        }

        @Override
        public String toString() {
            String profitText = profitLoss != 0 ? String.format(" (損益: %.2f)", profitLoss) : "";
            return String.format("%s %s %d股 @ %.2f%s",
                    timestamp.toLocalDate(), type, quantity, price, profitText);
        }
    }

    // 🔄 修正：添加多種方式獲取平均成本價
    private UserAccount userAccount; // 引用真實的用戶帳戶
    private PersonalAI personalAI;   // 引用個人AI（備用獲取方式）
    private double initialCash; // 初始資金（用於計算總回報率）
    private double currentStockPrice = 0.0; // 當前股價

    // 🆕 新增：自行維護平均成本價（作為備用）
    private double avgCostPrice = 0.0;
    private int totalSharesBought = 0;
    private double totalCostBasis = 0.0;

    // 統計相關數據
    private double totalRealizedProfitLoss = 0.0; // 已實現總損益
    private double todayProfitLoss = 0.0;         // 今日損益
    private double maxDrawdown = 0.0;             // 最大回撤
    private double winRate = 0.0;                 // 勝率
    private int totalTrades = 0;                  // 總交易次數
    private int winningTrades = 0;                // 獲利交易次數
    private int losingTrades = 0;                 // 虧損交易次數
    private double avgProfitPerTrade = 0.0;       // 平均每筆交易損益
    private double maxSingleProfit = 0.0;         // 單筆最大獲利
    private double maxSingleLoss = 0.0;           // 單筆最大虧損

    // 交易記錄
    private List<TradeRecord> tradeHistory = new ArrayList<>();

    private static final MarketLogger logger = MarketLogger.getInstance();

    // 每日統計記錄（用於計算最大回撤等）
    private List<Double> dailyPortfolioValues = new ArrayList<>();
    private double highWaterMark = 0.0;
    private final Clock clock;

    /**
     * 建構函數（修正版）
     *
     * @param userAccount 用戶帳戶引用
     * @param personalAI 個人AI引用（可為null）
     * @param initialCash 初始資金
     */
    public PersonalStatistics(UserAccount userAccount, PersonalAI personalAI, double initialCash) {
        this(userAccount, personalAI, initialCash, Clock.systemDefaultZone());
    }

    public PersonalStatistics(UserAccount userAccount, PersonalAI personalAI, double initialCash, Clock clock) {
        this.userAccount = userAccount;
        this.personalAI = personalAI;
        this.initialCash = initialCash;
        this.highWaterMark = initialCash;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    /**
     * 🔄 安全獲取平均成本價的方法
     */
    private double getAverageCostPriceSafely() {
        try {
            // 方法1：嘗試從PersonalAI獲取
            if (personalAI != null) {
                double aiAvgPrice = personalAI.getAverageCostPrice();
                if (aiAvgPrice > 0) {
                    return aiAvgPrice;
                }
            }
        } catch (Exception e) {
            logger.warn("從 PersonalAI 獲取平均成本價失敗：" + e.getMessage(), "PERSONAL_STATS");
        }

        try {
            // 方法2：嘗試從UserAccount獲取（如果有這個方法）
            if (userAccount != null) {
                // 假設UserAccount可能有getAverageCostPrice方法
                // 這裡用反射安全地嘗試調用
                java.lang.reflect.Method method = userAccount.getClass().getMethod("getAverageCostPrice");
                Object result = method.invoke(userAccount);
                if (result instanceof Double) {
                    double userAvgPrice = (Double) result;
                    if (userAvgPrice > 0) {
                        return userAvgPrice;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略，說明UserAccount沒有這個方法
        }

        // 方法3：使用自行維護的平均成本價
        if (avgCostPrice > 0) {
            return avgCostPrice;
        }

        // 方法4：如果都沒有，返回當前股價作為預設值
        return currentStockPrice > 0 ? currentStockPrice : 0.0;
    }

    /**
     * 🔄 安全獲取當前持股數量的方法
     */
    private int getCurrentHoldingsSafely() {
        try {
            if (userAccount != null) {
                return userAccount.getStockInventory();
            }
        } catch (Exception e) {
            logger.warn("獲取持股數量失敗：" + e.getMessage(), "PERSONAL_STATS");
        }
        return 0;
    }

    /**
     * 🔄 安全獲取當前現金的方法
     */
    private double getCurrentCashSafely() {
        try {
            if (userAccount != null) {
                return userAccount.getAvailableFunds();
            }
        } catch (Exception e) {
            logger.warn("獲取現金餘額失敗：" + e.getMessage(), "PERSONAL_STATS");
        }
        return 0.0;
    }

    /**
     * 添加交易記錄（修正版）
     */
    public synchronized void addTradeRecord(String type, int quantity, double price) {
        double totalAmount = quantity * price;
        double profitLoss = 0.0;

        if ("買入".equals(type)) {
            // 更新自維護的平均成本價
            updateSelfMaintainedAvgPrice(quantity, price);
        } else if ("賣出".equals(type)) {
            // 計算這筆賣出交易的損益
            double avgCostPriceNow = getAverageCostPriceSafely();
            profitLoss = (price - avgCostPriceNow) * quantity;
            totalRealizedProfitLoss += profitLoss;

            // 更新交易統計
            totalTrades++;
            if (profitLoss > 0) {
                winningTrades++;
                if (profitLoss > maxSingleProfit) maxSingleProfit = profitLoss;
            } else if (profitLoss < 0) {
                losingTrades++;
                if (profitLoss < maxSingleLoss) maxSingleLoss = profitLoss;
            }
        }

        // 建立交易記錄
        TradeRecord record = new TradeRecord(LocalDateTime.now(clock), type, quantity, price, totalAmount, profitLoss);
        tradeHistory.add(record);

        // [FIX] 依記錄的實際日期判斷今日損益，避免跨日後資料錯誤
        if ("賣出".equals(type) && record.getTimestamp().toLocalDate().equals(LocalDate.now(clock))) {
            todayProfitLoss += profitLoss;
        }

        // 更新投資組合價值
        updatePortfolioValue();

        // 重新計算統計數據
        recalculateStats();
    }

    /**
     * 🆕 更新自維護的平均成本價
     */
    private void updateSelfMaintainedAvgPrice(int quantity, double price) {
        double newCost = quantity * price;
        totalCostBasis += newCost;
        totalSharesBought += quantity;

        if (totalSharesBought > 0) {
            avgCostPrice = totalCostBasis / totalSharesBought;
        }
    }

    /**
     * 更新當前股價並重新計算投資組合價值
     */
    public synchronized void updateCurrentPrice(double currentPrice) {
        this.currentStockPrice = currentPrice;
        updatePortfolioValue();
    }

    /**
     * 更新投資組合價值（修正版）
     */
    private void updatePortfolioValue() {
        try {
            double currentCash = getCurrentCashSafely();
            int currentHoldings = getCurrentHoldingsSafely();
            double currentPortfolioValue = currentCash + (currentHoldings * currentStockPrice);

            // 更新高水位線和最大回撤
            if (currentPortfolioValue > highWaterMark) {
                highWaterMark = currentPortfolioValue;
            } else if (highWaterMark > 0) {
                double drawdown = (highWaterMark - currentPortfolioValue) / highWaterMark;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }

            // 記錄每日投資組合價值
            dailyPortfolioValues.add(currentPortfolioValue);
        } catch (Exception e) {
            logger.warn("更新投資組合價值失敗：" + e.getMessage(), "PERSONAL_STATS");
        }
    }

    /**
     * 重新計算統計數據
     */
    private void recalculateStats() {
        // 計算勝率
        if (totalTrades > 0) {
            winRate = ((double) winningTrades / totalTrades) * 100;
        }

        // 計算平均每筆交易損益
        if (totalTrades > 0) {
            avgProfitPerTrade = totalRealizedProfitLoss / totalTrades;
        }
    }

    /**
     * 獲取指定時間段的交易記錄
     */
    public synchronized List<TradeRecord> getTradesByPeriod(StatsPeriod period) {
        LocalDateTime cutoff = LocalDateTime.now(clock);

        switch (period) {
            case TODAY:
                cutoff = cutoff.toLocalDate().atStartOfDay();
                break;
            case THIS_WEEK:
                cutoff = cutoff.minusDays(7);
                break;
            case THIS_MONTH:
                cutoff = cutoff.minusDays(30);
                break;
            case ALL_TIME:
                return new ArrayList<>(tradeHistory);
        }

        final LocalDateTime finalCutoff = cutoff;
        return tradeHistory.stream()
                .filter(record -> record.getTimestamp().isAfter(finalCutoff))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 重新計算今日損益（依各記錄的實際時間戳過濾，可在跨日後呼叫）
     */
    public synchronized void recalculateTodayProfitLoss() {
        LocalDate today = LocalDate.now(clock);
        todayProfitLoss = tradeHistory.stream()
                .filter(r -> "賣出".equals(r.getType()) && r.getTimestamp().toLocalDate().equals(today))
                .mapToDouble(TradeRecord::getProfitLoss)
                .sum();
    }

    /**
     * 重置統計數據
     */
    public synchronized void reset() {
        totalRealizedProfitLoss = 0.0;
        todayProfitLoss = 0.0;
        maxDrawdown = 0.0;
        winRate = 0.0;
        totalTrades = 0;
        winningTrades = 0;
        losingTrades = 0;
        avgProfitPerTrade = 0.0;
        maxSingleProfit = 0.0;
        maxSingleLoss = 0.0;
        tradeHistory.clear();
        dailyPortfolioValues.clear();
        highWaterMark = initialCash;

        // 🆕 重置自維護的數據
        avgCostPrice = 0.0;
        totalSharesBought = 0;
        totalCostBasis = 0.0;
    }

    // === 🔄 修正的Getter方法：添加空指針保護 ===
    /**
     * 獲取未實現損益（修正版）
     */
    public synchronized double getUnrealizedProfitLoss() {
        try {
            if (currentStockPrice > 0) {
                int currentHoldings = getCurrentHoldingsSafely();
                if (currentHoldings > 0) {
                    double avgCostPrice = getAverageCostPriceSafely();
                    if (avgCostPrice > 0) {
                        double currentValue = currentHoldings * currentStockPrice;
                        double costValue = currentHoldings * avgCostPrice;
                        return currentValue - costValue;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("計算未實現損益失敗：" + e.getMessage(), "PERSONAL_STATS");
        }
        return 0.0;
    }

    /**
     * 獲取總損益（已實現 + 未實現）（修正版）
     */
    public synchronized double getTotalProfitLoss() {
        try {
            double unrealizedProfitLoss = getUnrealizedProfitLoss();
            return totalRealizedProfitLoss + unrealizedProfitLoss;
        } catch (Exception e) {
            logger.warn("計算總損益失敗：" + e.getMessage(), "PERSONAL_STATS");
            return totalRealizedProfitLoss;
        }
    }

    /**
     * 獲取當前投資組合價值（修正版）
     */
    public synchronized double getCurrentPortfolioValue() {
        try {
            double currentCash = getCurrentCashSafely();
            int currentHoldings = getCurrentHoldingsSafely();
            return currentCash + (currentHoldings * currentStockPrice);
        } catch (Exception e) {
            logger.warn("計算投資組合價值失敗：" + e.getMessage(), "PERSONAL_STATS");
            return initialCash;
        }
    }

    /**
     * 獲取總回報率（修正版）
     */
    public synchronized double getReturnRate() {
        try {
            if (initialCash > 0) {
                double currentValue = getCurrentPortfolioValue();
                return ((currentValue - initialCash) / initialCash) * 100;
            }
        } catch (Exception e) {
            logger.warn("計算回報率失敗：" + e.getMessage(), "PERSONAL_STATS");
        }
        return 0.0;
    }

    /**
     * 獲取當前現金（修正版）
     */
    public synchronized double getCurrentCash() {
        return getCurrentCashSafely();
    }

    /**
     * 獲取當前持股（修正版）
     */
    public synchronized int getCurrentHoldings() {
        return getCurrentHoldingsSafely();
    }

    /**
     * 獲取平均成本價（修正版）
     */
    public synchronized double getAvgCostPrice() {
        return getAverageCostPriceSafely();
    }

    // === 其他Getter方法保持不變 ===
    public synchronized double getTodayProfitLoss() {
        return todayProfitLoss;
    }

    public synchronized double getTotalInvested() {
        return initialCash;
    }

    public synchronized double getMaxDrawdown() {
        return maxDrawdown * 100;
    } // 轉換為百分比

    public synchronized double getWinRate() {
        return winRate;
    }

    public synchronized int getTotalTrades() {
        return totalTrades;
    }

    public synchronized int getWinningTrades() {
        return winningTrades;
    }

    public synchronized int getLosingTrades() {
        return losingTrades;
    }

    public synchronized double getAvgProfitPerTrade() {
        return avgProfitPerTrade;
    }

    public synchronized double getMaxSingleProfit() {
        return maxSingleProfit;
    }

    public synchronized double getMaxSingleLoss() {
        return maxSingleLoss;
    }

    public synchronized double getPayoffRatio() {
        double winSum = 0.0;
        double lossSum = 0.0;
        int wins = 0;
        int losses = 0;
        for (TradeRecord record : tradeHistory) {
            if (record.getProfitLoss() > 0) {
                winSum += record.getProfitLoss();
                wins++;
            } else if (record.getProfitLoss() < 0) {
                lossSum += Math.abs(record.getProfitLoss());
                losses++;
            }
        }
        double avgWin = wins > 0 ? winSum / wins : 0.0;
        double avgLoss = losses > 0 ? lossSum / losses : 0.0;
        if (avgLoss <= 0.0) {
            return avgWin > 0.0 ? 999.0 : 0.0;
        }
        return avgWin / avgLoss;
    }

    public synchronized double getAverageHoldingMinutes() {
        Queue<OpenLot> openLots = new ArrayDeque<>();
        long matchedMinutes = 0;
        int matchedLots = 0;
        for (TradeRecord record : tradeHistory) {
            if ("買入".equals(record.getType())) {
                openLots.add(new OpenLot(record.getQuantity(), record.getTimestamp()));
            } else if ("賣出".equals(record.getType())) {
                int remaining = record.getQuantity();
                while (remaining > 0 && !openLots.isEmpty()) {
                    OpenLot lot = openLots.peek();
                    int matched = Math.min(remaining, lot.quantity);
                    long minutes = java.time.Duration.between(lot.timestamp, record.getTimestamp()).toMinutes();
                    matchedMinutes += Math.max(0, minutes) * matched;
                    matchedLots += matched;
                    lot.quantity -= matched;
                    remaining -= matched;
                    if (lot.quantity <= 0) {
                        openLots.poll();
                    }
                }
            }
        }
        return matchedLots == 0 ? 0.0 : matchedMinutes / (double) matchedLots;
    }

    public synchronized String getTradeIntensityWarning() {
        if (totalTrades >= 30 && getReturnRate() <= 0.0) {
            return "交易頻率偏高且報酬未轉正，建議降低追價次數。";
        }
        if (maxDrawdown >= 0.10) {
            return "最大回撤已超過 10%，建議縮小單筆部位。";
        }
        if (getCurrentCashSafely() < getCurrentPortfolioValue() * 0.10) {
            return "現金緩衝偏低，遇到跳空時調整空間有限。";
        }
        return "目前風險狀態正常。";
    }

    private static final class OpenLot {
        private int quantity;
        private final LocalDateTime timestamp;

        private OpenLot(int quantity, LocalDateTime timestamp) {
            this.quantity = quantity;
            this.timestamp = timestamp;
        }
    }

    public synchronized double getInitialCash() {
        return initialCash;
    }

    public synchronized List<TradeRecord> getTradeHistory() {
        return new ArrayList<>(tradeHistory);
    }

    public synchronized List<Double> getDailyPortfolioValues() {
        return new ArrayList<>(dailyPortfolioValues);
    }
}
