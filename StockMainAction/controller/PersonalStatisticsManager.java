// === 2. 個人統計管理器 ===
// 位置: controller/PersonalStatisticsManager.java
package StockMainAction.controller;

import StockMainAction.model.PersonalAI;
import StockMainAction.model.core.PersonalStatistics;
import StockMainAction.model.user.UserAccount;
import StockMainAction.util.logging.MarketLogger;

/**
 * 個人統計管理器 - 負責統計數據的業務邏輯
 */
public class PersonalStatisticsManager {

    private PersonalStatistics statistics;
    private static final MarketLogger logger = MarketLogger.getInstance();

    /**
     * 建構函數（修正版）
     *
     * @param userAccount 用戶帳戶引用
     * @param personalAI 個人AI引用（可為null）
     * @param initialCash 初始資金
     */
    public PersonalStatisticsManager(UserAccount userAccount, PersonalAI personalAI, double initialCash) {
        this.statistics = new PersonalStatistics(userAccount, personalAI, initialCash);
        logger.info("個人統計管理器初始化，初始資金: " + initialCash, "STATS_MANAGER");
    }

    /**
     * 簡化建構函數（當PersonalAI為null時使用）
     *
     * @param userAccount 用戶帳戶引用
     * @param initialCash 初始資金
     */
    public PersonalStatisticsManager(UserAccount userAccount, double initialCash) {
        this(userAccount, null, initialCash);
    }

    /**
     * 記錄買入交易
     */
    public void recordBuyTrade(int quantity, double price, double currentPrice) {
        try {
            statistics.addTradeRecord("買入", quantity, price);
            statistics.updateCurrentPrice(currentPrice);
            logger.info(String.format("記錄買入交易: %d股 @ %.2f", quantity, price), "TRADE_RECORD");
        } catch (Exception e) {
            logger.error("記錄買入交易失敗: " + e.getMessage(), "TRADE_RECORD");
        }
    }

    /**
     * 記錄賣出交易
     */
    public void recordSellTrade(int quantity, double price, double currentPrice) {
        try {
            statistics.addTradeRecord("賣出", quantity, price);
            statistics.updateCurrentPrice(currentPrice);
            logger.info(String.format("記錄賣出交易: %d股 @ %.2f", quantity, price), "TRADE_RECORD");
        } catch (Exception e) {
            logger.error("記錄賣出交易失敗: " + e.getMessage(), "TRADE_RECORD");
        }
    }

    /**
     * 更新當前市價（用於計算未實現損益）
     */
    public void updateCurrentPrice(double currentPrice) {
        try {
            statistics.updateCurrentPrice(currentPrice);
        } catch (Exception e) {
            logger.error("更新投資組合價值失敗: " + e.getMessage(), "PORTFOLIO_UPDATE");
        }
    }

    /**
     * 獲取統計摘要文本
     */
    public String getStatsSummaryText() {
        try {
            StringBuilder summary = new StringBuilder();
            summary.append("=== 個人交易統計 ===\n");
            summary.append(String.format("總損益: %.2f (%.2f%%)\n",
                    statistics.getTotalProfitLoss(), statistics.getReturnRate()));
            summary.append(String.format("  已實現損益: %.2f\n", statistics.getTotalProfitLoss() - statistics.getUnrealizedProfitLoss()));
            summary.append(String.format("  未實現損益: %.2f\n", statistics.getUnrealizedProfitLoss()));
            summary.append(String.format("今日損益: %.2f\n", statistics.getTodayProfitLoss()));
            summary.append(String.format("投資組合價值: %.2f\n", statistics.getCurrentPortfolioValue()));
            summary.append(String.format("交易次數: %d (勝率: %.1f%%)\n",
                    statistics.getTotalTrades(), statistics.getWinRate()));
            summary.append(String.format("最大回撤: %.2f%%\n", statistics.getMaxDrawdown()));
            summary.append(String.format("平均每筆損益: %.2f\n", statistics.getAvgProfitPerTrade()));
            return summary.toString();
        } catch (Exception e) {
            logger.error("生成統計摘要失敗: " + e.getMessage(), "STATS_SUMMARY");
            return "統計摘要生成失敗，請檢查日誌";
        }
    }

    /**
     * 重置統計數據
     */
    public void resetStatistics() {
        try {
            statistics.reset();
            logger.info("重置個人統計數據", "STATS_RESET");
        } catch (Exception e) {
            logger.error("重置統計數據失敗: " + e.getMessage(), "STATS_RESET");
        }
    }

    /**
     * 獲取統計對象
     */
    public PersonalStatistics getStatistics() {
        return statistics;
    }
}