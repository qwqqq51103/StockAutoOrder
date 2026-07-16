// === 2. 快捷交易管理器 ===
// 位置: controller/QuickTradeManager.java
package StockMainAction.controller;

import StockMainAction.model.core.QuickTradeConfig;
import StockMainAction.util.logging.MarketLogger;

import java.util.*;

/**
 * 快捷交易管理器
 */
public class QuickTradeManager {

    private List<QuickTradeConfig> quickTradeConfigs;
    private Map<String, QuickTradeConfig> hotkeyMap;
    private static final MarketLogger logger = MarketLogger.getInstance();

    // 快捷交易執行結果
    public static class QuickTradeResult {

        private boolean success;
        private String message;
        private int quantity;
        private double price;
        private double totalAmount;

        public QuickTradeResult(boolean success, String message, int quantity, double price, double totalAmount) {
            this.success = success;
            this.message = message;
            this.quantity = quantity;
            this.price = price;
            this.totalAmount = totalAmount;
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
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
    }

    public QuickTradeManager() {
        this.quickTradeConfigs = new ArrayList<>();
        this.hotkeyMap = new HashMap<>();
        initializeDefaultConfigs();
    }

    /**
     * 初始化預設快捷交易配置
     */
    private void initializeDefaultConfigs() {
        // 預設配置1：快速買入100股
        QuickTradeConfig quickBuy100 = new QuickTradeConfig(
                "快速買入100股",
                QuickTradeConfig.QuickTradeType.FIXED_QUANTITY,
                QuickTradeConfig.PriceStrategy.CURRENT_PRICE,
                true
        );
        quickBuy100.setFixedQuantity(100);
        quickBuy100.setHotkey("F1");
        addQuickTradeConfig(quickBuy100);

        // 預設配置2：快速賣出100股
        QuickTradeConfig quickSell100 = new QuickTradeConfig(
                "快速賣出100股",
                QuickTradeConfig.QuickTradeType.FIXED_QUANTITY,
                QuickTradeConfig.PriceStrategy.CURRENT_PRICE,
                false
        );
        quickSell100.setFixedQuantity(100);
        quickSell100.setHotkey("F2");
        addQuickTradeConfig(quickSell100);

        // 預設配置3：使用50%資金買入
        QuickTradeConfig halfFundsBuy = new QuickTradeConfig(
                "50%資金買入",
                QuickTradeConfig.QuickTradeType.PERCENTAGE_FUNDS,
                QuickTradeConfig.PriceStrategy.CURRENT_PRICE,
                true
        );
        halfFundsBuy.setPercentage(50.0);
        halfFundsBuy.setHotkey("F3");
        addQuickTradeConfig(halfFundsBuy);

        // 預設配置4：賣出50%持股
        QuickTradeConfig halfHoldingsSell = new QuickTradeConfig(
                "賣出50%持股",
                QuickTradeConfig.QuickTradeType.PERCENTAGE_HOLDINGS,
                QuickTradeConfig.PriceStrategy.CURRENT_PRICE,
                false
        );
        halfHoldingsSell.setPercentage(50.0);
        halfHoldingsSell.setHotkey("F4");
        addQuickTradeConfig(halfHoldingsSell);

        // 預設配置5：全倉買入
        QuickTradeConfig allInBuy = new QuickTradeConfig(
                "全倉買入",
                QuickTradeConfig.QuickTradeType.ALL_IN,
                QuickTradeConfig.PriceStrategy.MARKET,
                true
        );
        allInBuy.setHotkey("Ctrl+B");
        addQuickTradeConfig(allInBuy);

        // 預設配置6：全倉賣出
        QuickTradeConfig allOutSell = new QuickTradeConfig(
                "全倉賣出",
                QuickTradeConfig.QuickTradeType.ALL_OUT,
                QuickTradeConfig.PriceStrategy.MARKET,
                false
        );
        allOutSell.setHotkey("Ctrl+S");
        addQuickTradeConfig(allOutSell);

        // 預設配置7：智能買入
        QuickTradeConfig smartBuy = new QuickTradeConfig(
                "智能買入",
                QuickTradeConfig.QuickTradeType.SMART_BUY,
                QuickTradeConfig.PriceStrategy.PREMIUM,
                true
        );
        smartBuy.setPriceOffset(0.5); // 溢價0.5%買入
        smartBuy.setHotkey("Ctrl+Q");
        addQuickTradeConfig(smartBuy);

        // 預設配置8：智能賣出
        QuickTradeConfig smartSell = new QuickTradeConfig(
                "智能賣出",
                QuickTradeConfig.QuickTradeType.SMART_SELL,
                QuickTradeConfig.PriceStrategy.DISCOUNT,
                false
        );
        smartSell.setPriceOffset(0.5); // 折價0.5%賣出
        smartSell.setHotkey("Ctrl+W");
        addQuickTradeConfig(smartSell);

        logger.info("初始化了 " + quickTradeConfigs.size() + " 個預設快捷交易配置", "QUICK_TRADE");
    }

    /**
     * 添加快捷交易配置
     */
    public void addQuickTradeConfig(QuickTradeConfig config) {
        quickTradeConfigs.add(config);
        if (config.getHotkey() != null && !config.getHotkey().isEmpty()) {
            hotkeyMap.put(config.getHotkey(), config);
        }
    }

    /**
     * 移除快捷交易配置
     */
    public void removeQuickTradeConfig(QuickTradeConfig config) {
        quickTradeConfigs.remove(config);
        if (config.getHotkey() != null && !config.getHotkey().isEmpty()) {
            hotkeyMap.remove(config.getHotkey());
        }
    }

    /**
     * 通過快捷鍵獲取配置
     */
    public QuickTradeConfig getConfigByHotkey(String hotkey) {
        return hotkeyMap.get(hotkey);
    }

    /**
     * 計算快捷交易參數
     */
    public QuickTradeResult calculateQuickTrade(QuickTradeConfig config,
            double availableFunds,
            int currentHoldings,
            double currentPrice) {
        try {
            // 計算交易數量
            int quantity = config.calculateQuantity(availableFunds, currentHoldings, currentPrice);

            if (quantity <= 0) {
                return new QuickTradeResult(false, "計算的交易數量為0或負數", 0, 0, 0);
            }

            // 計算交易價格
            double tradePrice = config.calculatePrice(currentPrice);

            // 驗證交易可行性
            if (config.isBuy()) {
                // 買入驗證
                if (config.isMarketOrder()) {
                    // 市價買入：檢查是否有足夠資金（預估）
                    double estimatedCost = quantity * currentPrice * 1.02; // 預留2%的價格波動
                    if (availableFunds < estimatedCost) {
                        return new QuickTradeResult(false,
                                String.format("資金不足，需要約 %.2f，可用 %.2f", estimatedCost, availableFunds),
                                quantity, tradePrice, estimatedCost);
                    }
                } else {
                    // 限價買入：精確計算
                    double totalCost = quantity * tradePrice;
                    if (availableFunds < totalCost) {
                        return new QuickTradeResult(false,
                                String.format("資金不足，需要 %.2f，可用 %.2f", totalCost, availableFunds),
                                quantity, tradePrice, totalCost);
                    }
                }
            } else {
                // 賣出驗證
                if (currentHoldings < quantity) {
                    return new QuickTradeResult(false,
                            String.format("持股不足，需要 %d 股，持有 %d 股", quantity, currentHoldings),
                            quantity, tradePrice, quantity * tradePrice);
                }
            }

            double totalAmount = quantity * (config.isMarketOrder() ? currentPrice : tradePrice);

            return new QuickTradeResult(true, "交易參數計算成功", quantity, tradePrice, totalAmount);

        } catch (Exception e) {
            logger.error("計算快捷交易參數失敗: " + e.getMessage(), "QUICK_TRADE");
            return new QuickTradeResult(false, "計算失敗: " + e.getMessage(), 0, 0, 0);
        }
    }

    /**
     * 獲取所有快捷交易配置
     */
    public List<QuickTradeConfig> getAllConfigs() {
        return new ArrayList<>(quickTradeConfigs);
    }

    /**
     * 獲取所有快捷鍵映射
     */
    public Map<String, String> getHotkeyMappings() {
        Map<String, String> mappings = new HashMap<>();
        for (QuickTradeConfig config : quickTradeConfigs) {
            if (config.getHotkey() != null && !config.getHotkey().isEmpty()) {
                mappings.put(config.getHotkey(), config.getName());
            }
        }
        return mappings;
    }

    /**
     * 導出配置為JSON格式（簡化版）
     */
    public String exportConfigs() {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        for (int i = 0; i < quickTradeConfigs.size(); i++) {
            QuickTradeConfig config = quickTradeConfigs.get(i);
            json.append("  {\n");
            json.append("    \"name\": \"").append(config.getName()).append("\",\n");
            json.append("    \"tradeType\": \"").append(config.getTradeType()).append("\",\n");
            json.append("    \"priceStrategy\": \"").append(config.getPriceStrategy()).append("\",\n");
            json.append("    \"isBuy\": ").append(config.isBuy()).append(",\n");
            json.append("    \"hotkey\": \"").append(config.getHotkey()).append("\"\n");
            json.append("  }");
            if (i < quickTradeConfigs.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("]");
        return json.toString();
    }
}
