// === 1. 快捷交易配置數據模型 ===
// 位置: model/core/QuickTradeConfig.java
package StockMainAction.model.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 快捷交易配置數據模型
 */
public class QuickTradeConfig {

    /**
     * 快捷交易類型枚舉
     */
    public enum QuickTradeType {
        FIXED_QUANTITY("固定數量"),
        PERCENTAGE_FUNDS("資金百分比"),
        PERCENTAGE_HOLDINGS("持股百分比"),
        ALL_IN("全倉買入"),
        ALL_OUT("全倉賣出"),
        SMART_BUY("智能買入"),
        SMART_SELL("智能賣出");

        private String displayName;

        QuickTradeType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 價格策略枚舉
     */
    public enum PriceStrategy {
        MARKET("市價"),
        CURRENT_PRICE("當前價"),
        PREMIUM("溢價買入"),
        DISCOUNT("折價賣出"),
        CUSTOM("自定義");

        private String displayName;

        PriceStrategy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // 配置屬性
    private String name;                    // 快捷交易名稱
    private QuickTradeType tradeType;       // 交易類型
    private PriceStrategy priceStrategy;    // 價格策略
    private boolean isBuy;                  // 是否為買入（false為賣出）
    private int fixedQuantity;              // 固定數量
    private double percentage;              // 百分比（0-100）
    private double priceOffset;             // 價格偏移（百分比或固定值）
    private boolean usePercentageOffset;    // 是否使用百分比偏移
    private String hotkey;                  // 快捷鍵
    private boolean autoExecute;            // 🆕 是否自動執行（無需確認）

    // 建構函數
    public QuickTradeConfig(String name, QuickTradeType tradeType, PriceStrategy priceStrategy, boolean isBuy) {
        this.name = name;
        this.tradeType = tradeType;
        this.priceStrategy = priceStrategy;
        this.isBuy = isBuy;
        this.fixedQuantity = 100;
        this.percentage = 50.0;
        this.priceOffset = 0.0;
        this.usePercentageOffset = true;
        this.hotkey = "";
        this.autoExecute = false;  // 🆕 預設為需要確認
    }

    /**
     * 計算交易數量
     */
    public int calculateQuantity(double availableFunds, int currentHoldings, double currentPrice) {
        switch (tradeType) {
            case FIXED_QUANTITY:
                return fixedQuantity;

            case PERCENTAGE_FUNDS:
                if (isBuy && currentPrice > 0) {
                    double fundsToUse = availableFunds * (percentage / 100.0);
                    return (int) Math.floor(fundsToUse / currentPrice);
                }
                return 0;

            case PERCENTAGE_HOLDINGS:
                if (!isBuy) {
                    return (int) Math.floor(currentHoldings * (percentage / 100.0));
                }
                return 0;

            case ALL_IN:
                if (isBuy && currentPrice > 0) {
                    return (int) Math.floor(availableFunds / currentPrice);
                }
                return 0;

            case ALL_OUT:
                if (!isBuy) {
                    return currentHoldings;
                }
                return 0;

            case SMART_BUY:
                if (isBuy && currentPrice > 0) {
                    // 智能買入：使用可用資金的70%，避免全倉風險
                    double smartFunds = availableFunds * 0.7;
                    return (int) Math.floor(smartFunds / currentPrice);
                }
                return 0;

            case SMART_SELL:
                if (!isBuy) {
                    // 智能賣出：賣出持股的50%，保留一部分倉位
                    return (int) Math.floor(currentHoldings * 0.5);
                }
                return 0;

            default:
                return 0;
        }
    }

    /**
     * 計算交易價格
     */
    public double calculatePrice(double currentPrice) {
        switch (priceStrategy) {
            case MARKET:
                return 0.0; // 市價交易

            case CURRENT_PRICE:
                return currentPrice;

            case PREMIUM:
                if (usePercentageOffset) {
                    return currentPrice * (1 + priceOffset / 100.0);
                } else {
                    return currentPrice + priceOffset;
                }

            case DISCOUNT:
                if (usePercentageOffset) {
                    return currentPrice * (1 - priceOffset / 100.0);
                } else {
                    return currentPrice - priceOffset;
                }

            case CUSTOM:
                return priceOffset; // 直接使用自定義價格

            default:
                return currentPrice;
        }
    }

    /**
     * 是否為市價交易
     */
    public boolean isMarketOrder() {
        return priceStrategy == PriceStrategy.MARKET;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public QuickTradeType getTradeType() {
        return tradeType;
    }

    public void setTradeType(QuickTradeType tradeType) {
        this.tradeType = tradeType;
    }

    public PriceStrategy getPriceStrategy() {
        return priceStrategy;
    }

    public void setPriceStrategy(PriceStrategy priceStrategy) {
        this.priceStrategy = priceStrategy;
    }

    public boolean isBuy() {
        return isBuy;
    }

    public void setBuy(boolean buy) {
        isBuy = buy;
    }

    public int getFixedQuantity() {
        return fixedQuantity;
    }

    public void setFixedQuantity(int fixedQuantity) {
        this.fixedQuantity = fixedQuantity;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    public double getPriceOffset() {
        return priceOffset;
    }

    public void setPriceOffset(double priceOffset) {
        this.priceOffset = priceOffset;
    }

    public boolean isUsePercentageOffset() {
        return usePercentageOffset;
    }

    public void setUsePercentageOffset(boolean usePercentageOffset) {
        this.usePercentageOffset = usePercentageOffset;
    }

    public String getHotkey() {
        return hotkey;
    }

    public void setHotkey(String hotkey) {
        this.hotkey = hotkey;
    }

    /**
     * 🆕 獲取是否自動執行
     */
    public boolean isAutoExecute() {
        return autoExecute;
    }

    /**
     * 🆕 設置是否自動執行
     */
    public void setAutoExecute(boolean autoExecute) {
        this.autoExecute = autoExecute;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)%s",
                name,
                isBuy ? "買入" : "賣出",
                autoExecute ? " [自動]" : "");  // 🆕 顯示是否自動執行
    }

    /**
     * 🆕 複製配置（用於創建副本）
     */
    public QuickTradeConfig copy() {
        QuickTradeConfig copy = new QuickTradeConfig(name, tradeType, priceStrategy, isBuy);
        copy.setFixedQuantity(fixedQuantity);
        copy.setPercentage(percentage);
        copy.setPriceOffset(priceOffset);
        copy.setUsePercentageOffset(usePercentageOffset);
        copy.setHotkey(hotkey);
        copy.setAutoExecute(autoExecute);
        return copy;
    }

    /**
     * 🆕 驗證配置是否有效
     */
    public boolean isValid() {
        // 檢查基本屬性
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // 檢查數量設置
        switch (tradeType) {
            case FIXED_QUANTITY:
                return fixedQuantity > 0;
            case PERCENTAGE_FUNDS:
            case PERCENTAGE_HOLDINGS:
                return percentage > 0 && percentage <= 100;
            case ALL_IN:
            case ALL_OUT:
            case SMART_BUY:
            case SMART_SELL:
                return true;
            default:
                return false;
        }
    }

    /**
     * 🆕 獲取配置描述
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(name).append(" - ");
        desc.append(isBuy ? "買入" : "賣出").append(" ");

        switch (tradeType) {
            case FIXED_QUANTITY:
                desc.append(fixedQuantity).append("股");
                break;
            case PERCENTAGE_FUNDS:
                desc.append(percentage).append("%資金");
                break;
            case PERCENTAGE_HOLDINGS:
                desc.append(percentage).append("%持股");
                break;
            case ALL_IN:
                desc.append("全部資金");
                break;
            case ALL_OUT:
                desc.append("全部持股");
                break;
            case SMART_BUY:
                desc.append("智能買入(70%資金)");
                break;
            case SMART_SELL:
                desc.append("智能賣出(50%持股)");
                break;
        }

        desc.append(" @ ").append(priceStrategy.getDisplayName());

        if (autoExecute) {
            desc.append(" [自動執行]");
        }

        if (!hotkey.isEmpty()) {
            desc.append(" (").append(hotkey).append(")");
        }

        return desc.toString();
    }
}