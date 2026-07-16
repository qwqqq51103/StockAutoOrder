package StockMainAction.model.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 完整整合版交易記錄類 - 支持限價單和市價單的詳細記錄 保持與原有系統的完全兼容性，同時增加市價單增強功能
 */
public class Transaction {

    /**
     * 交易類型枚舉
     */
    public enum TransactionType {
        LIMIT_ORDER("限價單"),
        MARKET_ORDER("市價單"),
        FOK_ORDER("FOK單");

        private final String displayName;

        TransactionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 市價單填單記錄 - 記錄每筆部分成交的詳細信息
     */
    public static class FillRecord {

        private double price;           // 成交價格
        private int volume;            // 成交數量
        private String counterpartyType; // 對手方類型
        private long timestamp;        // 成交時間戳
        private int orderBookDepth;    // 當時訂單簿深度位置

        public FillRecord(double price, int volume, String counterpartyType, int orderBookDepth) {
            this.price = price;
            this.volume = volume;
            this.counterpartyType = counterpartyType;
            this.orderBookDepth = orderBookDepth;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public double getPrice() {
            return price;
        }

        public int getVolume() {
            return volume;
        }

        public String getCounterpartyType() {
            return counterpartyType;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getOrderBookDepth() {
            return orderBookDepth;
        }

        public double getTotalValue() {
            return price * volume;
        }

        @Override
        public String toString() {
            return String.format("%.2f×%d股(對手:%s,深度:%d)",
                    price, volume, counterpartyType, orderBookDepth);
        }
    }

    // === 原有屬性（保持完全兼容） ===
    private String id;
    private Order buyOrder;
    private Order sellOrder;
    private double price;
    private int volume;
    private long timestamp;
    private int buyOrderRemainingVolume;
    private int sellOrderRemainingVolume;
    private String matchingMode;
    private boolean buyerInitiated;

    // === 新增：交易類型和市價單相關屬性 ===
    private TransactionType transactionType;
    private boolean isMarketOrder;
    private String initiatingTraderType;        // 發起交易的交易者類型
    private String orderType;                   // MARKET_BUY, MARKET_SELL, LIMIT_BUY, LIMIT_SELL

    // 市價單特有屬性
    private double estimatedPrice;              // 預估價格（市價單下單時的參考價）
    private double averagePrice;                // 平均成交價（市價單可能多次填單）
    private double slippage;                    // 滑價（實際均價與預估價的差異）
    private double slippagePercentage;          // 滑價百分比
    private int requestedVolume;                // 請求交易量（市價單原始請求）
    private int actualVolume;                   // 實際成交量
    private boolean fullyFilled;                // 是否完全成交
    private String failureReason;               // 失敗或部分成交原因
    private int depthLevels = 0;                // 深度層數
    private long executionStartTime;            // 執行開始時間
    private long executionEndTime;              // 執行結束時間

    // 詳細填單記錄（主要用於市價單）
    private List<FillRecord> fillRecords;

    // 市場狀態信息
    private double preTradePrice;               // 交易前股價
    private double postTradePrice;              // 交易後股價
    private int availableDepth;                 // 可用訂單簿深度

    // === 原有構造函數（保持完全兼容） ===
    public Transaction(String id, Order buyOrder, Order sellOrder,
            double price, int volume, long timestamp) {
        this.id = id;
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
        this.price = price;
        this.depthLevels = 1; // 限價單通常只涉及一個價格層級
        this.volume = volume;
        this.timestamp = timestamp;

        // 初始化新增屬性（保持兼容性）
        this.transactionType = TransactionType.LIMIT_ORDER;
        this.isMarketOrder = false;
        this.fillRecords = new ArrayList<>();
        this.fullyFilled = true; // 限價單默認完全成交
        this.actualVolume = volume;
        this.requestedVolume = volume;
        this.averagePrice = price;
        this.executionStartTime = timestamp;
        this.executionEndTime = timestamp;
        this.estimatedPrice = price;
        this.slippage = 0.0;
        this.slippagePercentage = 0.0;

        // 根據訂單類型設置orderType
        if (buyOrder != null && sellOrder != null) {
            if (buyOrder.isMarketOrder()) {
                this.orderType = "MARKET_BUY";
                this.transactionType = TransactionType.MARKET_ORDER;
                this.isMarketOrder = true;
            } else if (sellOrder.isMarketOrder()) {
                this.orderType = "MARKET_SELL";
                this.transactionType = TransactionType.MARKET_ORDER;
                this.isMarketOrder = true;
            } else if (buyOrder.isFillOrKill() || sellOrder.isFillOrKill()) {
                this.orderType = buyOrder.isFillOrKill() ? "FOK_BUY" : "FOK_SELL";
                this.transactionType = TransactionType.FOK_ORDER;
            } else {
                this.orderType = "LIMIT_ORDER";
            }
        }
    }

    // === 新增：市價單專用構造函數 ===
    public Transaction(String id, String initiatingTraderType, String orderType,
            int requestedVolume, double estimatedPrice, double preTradePrice) {
        this.id = id;
        this.initiatingTraderType = initiatingTraderType;
        this.orderType = orderType;
        this.requestedVolume = requestedVolume;
        this.estimatedPrice = estimatedPrice;
        this.preTradePrice = preTradePrice;
        this.timestamp = System.currentTimeMillis();
        this.executionStartTime = timestamp;

        // 設置為市價單交易
        this.transactionType = TransactionType.MARKET_ORDER;
        this.isMarketOrder = true;
        this.fillRecords = new ArrayList<>();
        this.actualVolume = 0;
        this.fullyFilled = false;
        this.price = estimatedPrice; // 初始設置為預估價格
        this.depthLevels = 0; // 初始化為0，隨著填單增加
        this.volume = 0; // 將在填單時累計
        this.averagePrice = estimatedPrice;
        this.slippage = 0.0;
        this.slippagePercentage = 0.0;
        this.buyOrderRemainingVolume = 0;
        this.sellOrderRemainingVolume = 0;

        // 設置買方發起標誌
        this.buyerInitiated = "MARKET_BUY".equals(orderType);
    }

    // === 市價單專用方法 ===
    /**
     * 添加填單記錄（市價單用）
     */
    public void addFillRecord(double fillPrice, int fillVolume, String counterpartyType, int depth) {
        FillRecord fill = new FillRecord(fillPrice, fillVolume, counterpartyType, depth);
        fillRecords.add(fill);

        // 更新累計數據
        actualVolume += fillVolume;
        volume = actualVolume; // 保持原有屬性同步

        // 重新計算平均價格
        double totalValue = fillRecords.stream()
                .mapToDouble(FillRecord::getTotalValue)
                .sum();
        if (actualVolume > 0) {
            averagePrice = totalValue / actualVolume;
            price = averagePrice; // 保持原有屬性同步
        }
    }

    /**
     * 完成市價單交易記錄
     */
    public void completeMarketOrderTransaction(double postTradePrice, int availableDepth, String failureReason) {
        this.executionEndTime = System.currentTimeMillis();
        this.postTradePrice = postTradePrice;
        this.availableDepth = availableDepth;
        this.failureReason = failureReason;
        this.fullyFilled = (actualVolume == requestedVolume);

        // 計算滑價
        if (actualVolume > 0 && estimatedPrice > 0) {
            this.slippage = averagePrice - estimatedPrice;
            this.slippagePercentage = (slippage / estimatedPrice) * 100;
        }
    }

    /**
     * 獲取交易執行時間（毫秒）
     */
    public long getExecutionTimeMs() {
        return executionEndTime - executionStartTime;
    }

    /**
     * 獲取填單次數
     */
    public int getFillCount() {
        return fillRecords.size();
    }

    /**
     * 獲取最佳成交價格（市價單用）
     */
    public double getBestFillPrice() {
        if (fillRecords.isEmpty()) {
            return price;
        }

        if ("MARKET_BUY".equals(orderType)) {
            return fillRecords.stream().mapToDouble(FillRecord::getPrice).min().orElse(price);
        } else {
            return fillRecords.stream().mapToDouble(FillRecord::getPrice).max().orElse(price);
        }
    }

    /**
     * 獲取最差成交價格（市價單用）
     */
    public double getWorstFillPrice() {
        if (fillRecords.isEmpty()) {
            return price;
        }

        if ("MARKET_BUY".equals(orderType)) {
            return fillRecords.stream().mapToDouble(FillRecord::getPrice).max().orElse(price);
        } else {
            return fillRecords.stream().mapToDouble(FillRecord::getPrice).min().orElse(price);
        }
    }

    /**
     * 獲取價格分布摘要
     */
    public String getPriceDistributionSummary() {
        if (!isMarketOrder || fillRecords.isEmpty()) {
            return String.format("單一價格: %.2f", price);
        }

        double bestPrice = getBestFillPrice();
        double worstPrice = getWorstFillPrice();
        double priceSpread = Math.abs(worstPrice - bestPrice);

        return String.format("價格分布: %.2f~%.2f (價差%.2f)",
                bestPrice, worstPrice, priceSpread);
    }

    /**
     * 生成詳細的交易描述
     */
    public String getDetailedDescription() {
        StringBuilder desc = new StringBuilder();

        if (isMarketOrder) {
            desc.append(String.format("%s %s: %d/%d股 @均價%.2f",
                    transactionType.getDisplayName(),
                    orderType.contains("BUY") ? "買入" : "賣出",
                    actualVolume, requestedVolume, averagePrice));

            if (Math.abs(slippagePercentage) > 0.01) {
                desc.append(String.format(" (滑價%.2f%%)", slippagePercentage));
            }

            if (fillRecords.size() > 1) {
                desc.append(String.format(" [%d次填單]", fillRecords.size()));
            }
        } else {
            desc.append(String.format("%s: %d股 @%.2f",
                    transactionType.getDisplayName(), volume, price));

            if (buyOrder != null && sellOrder != null) {
                desc.append(String.format(" (%s→%s)",
                        getTraderTypeDisplay(buyOrder.getTrader().getTraderType()),
                        getTraderTypeDisplay(sellOrder.getTrader().getTraderType())));
            }
        }

        return desc.toString();
    }

    /**
     * 交易者類型顯示轉換
     */
    private String getTraderTypeDisplay(String traderType) {
        switch (traderType) {
            case "RETAIL_INVESTOR":
                return "散戶";
            case "MAIN_FORCE":
                return "主力";
            case "PERSONAL":
                return "個人";
            case "MarketBehavior":
                return "市場";
            default:
                return traderType;
        }
    }

    // === 原有的 Getter 和 Setter 方法（保持完全兼容） ===
    public String getId() {
        return id;
    }

    public Order getBuyOrder() {
        return buyOrder;
    }

    public Order getSellOrder() {
        return sellOrder;
    }

    public Trader getBuyer() {
        if (buyOrder != null) {
            return buyOrder.getTrader();
        }
        // 如果是市價賣單，返回null（因為沒有特定的買方）
        return null;
    }

    public Trader getSeller() {
        if (sellOrder != null) {
            return sellOrder.getTrader();
        }
        // 如果是市價買單，返回null（因為沒有特定的賣方）
        return null;
    }

    public double getPrice() {
        return price;
    }

    public int getVolume() {
        return volume;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getBuyOrderRemainingVolume() {
        return buyOrderRemainingVolume;
    }

    public void setBuyOrderRemainingVolume(int volume) {
        this.buyOrderRemainingVolume = volume;
    }

    public int getSellOrderRemainingVolume() {
        return sellOrderRemainingVolume;
    }

    public void setSellOrderRemainingVolume(int volume) {
        this.sellOrderRemainingVolume = volume;
    }

    public String getMatchingMode() {
        return matchingMode;
    }

    public void setMatchingMode(String mode) {
        this.matchingMode = mode;
    }

    public boolean isBuyerInitiated() {
        return buyerInitiated;
    }

    public void setBuyerInitiated(boolean initiated) {
        this.buyerInitiated = initiated;
    }

    // === 新增的 Getter 和 Setter 方法 ===
    public TransactionType getTransactionType() {
        return transactionType;
    }

    public boolean isMarketOrder() {
        return isMarketOrder;
    }

    public String getInitiatingTraderType() {
        return initiatingTraderType;
    }

    public String getOrderType() {
        return orderType;
    }

    public double getEstimatedPrice() {
        return estimatedPrice;
    }

    public double getAveragePrice() {
        return averagePrice;
    }

    public double getSlippage() {
        return slippage;
    }

    public double getSlippagePercentage() {
        return slippagePercentage;
    }

    public int getRequestedVolume() {
        return requestedVolume;
    }

    public int getActualVolume() {
        return actualVolume;
    }

    public boolean isFullyFilled() {
        return fullyFilled;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<FillRecord> getFillRecords() {
        return new ArrayList<>(fillRecords);
    }

    public double getPreTradePrice() {
        return preTradePrice;
    }

    public double getPostTradePrice() {
        return postTradePrice;
    }

    public int getAvailableDepth() {
        return availableDepth;
    }

    public long getExecutionStartTime() {
        return executionStartTime;
    }

    public long getExecutionEndTime() {
        return executionEndTime;
    }

    /**
     * 設置交易相關的訂單（用於兼容現有代碼）
     */
    public void setBuyOrder(Order buyOrder) {
        this.buyOrder = buyOrder;
    }

    public void setSellOrder(Order sellOrder) {
        this.sellOrder = sellOrder;
    }

    /**
     * 判斷是否為成功的交易
     */
    public boolean isSuccessful() {
        return actualVolume > 0;
    }

    /**
     * 獲取成交率（百分比）
     */
    public double getFillRate() {
        if (requestedVolume <= 0) {
            return 0.0;
        }
        return (double) actualVolume / requestedVolume * 100.0;
    }

    /**
     * 獲取總成交金額
     */
    public double getTotalValue() {
        if (isMarketOrder && !fillRecords.isEmpty()) {
            return fillRecords.stream().mapToDouble(FillRecord::getTotalValue).sum();
        }
        return price * volume;
    }

    /**
     * 覆寫toString方法，提供更豐富的信息
     */
    @Override
    public String toString() {
        if (isMarketOrder) {
            return String.format("Transaction[%s] %s: %d/%d股@%.2f 滑價%.2f%% [%dms]",
                    id.substring(0, Math.min(8, id.length())),
                    orderType,
                    actualVolume,
                    requestedVolume,
                    averagePrice,
                    slippagePercentage,
                    getExecutionTimeMs()
            );
        } else {
            return String.format("Transaction[%s] %s: %d股@%.2f %s",
                    id.substring(0, Math.min(8, id.length())),
                    transactionType.getDisplayName(),
                    volume,
                    price,
                    matchingMode != null ? matchingMode : ""
            );
        }
    }
}
