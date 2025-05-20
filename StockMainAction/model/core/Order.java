package StockMainAction.model.core;

import StockMainAction.model.user.UserAccount;
import java.util.UUID;

/**
 * 訂單類別，表示單個買賣訂單
 */
public class Order {

    private String id; // 唯一識別符
    private String type; // "buy" 或 "sell"
    private double price;
    private int volume;
    private Trader trader; // 使用 Trader 接口
    private UserAccount traderAccount; // 交易者帳戶
    private long timestamp; // 用於時間優先
    private boolean isSimulation; // 新增屬性
    private boolean isMarketOrder; // 用於標記是否為市價單
    private boolean isFillOrKill; // 成交或取消標記

    // 構造函數
    public Order(String type, double price, int volume, Trader trader, boolean isSimulation, boolean isMarketOrder, boolean isFillOrKill) {
        if (type == null || (!type.equalsIgnoreCase("buy") && !type.equalsIgnoreCase("sell"))) {
            throw new IllegalArgumentException("訂單類型必須是 'buy' 或 'sell'");
        }
        if (trader == null) {
            throw new IllegalArgumentException("Trader 不能為 null");
        }
        this.type = type.toLowerCase(); // 確保類型為小寫
        this.price = price;
        this.volume = volume;
        this.trader = trader;
        this.traderAccount = trader.getAccount(); // 從 Trader 接口獲取帳戶
        this.isSimulation = isSimulation;
        this.isMarketOrder = isMarketOrder; // 設定是否為市價單
        this.isFillOrKill = isFillOrKill;
        this.timestamp = System.currentTimeMillis();
        this.id = UUID.randomUUID().toString();  // 使用 UUID 生成唯一 ID
    }

    /**
     * 靜態工廠方法 - 正確版本，包含所有必要的參數
     */
// 建立市價買單
    public static Order createMarketBuyOrder(int volume, Trader trader) {
        return new Order("buy", 0, volume, trader, false, true, false);
        // type, price, volume, trader, isSimulation, isMarketOrder, isFillOrKill
    }

// 建立市價賣單
    public static Order createMarketSellOrder(int volume, Trader trader) {
        return new Order("sell", 0, volume, trader, false, true, false);
        // type, price, volume, trader, isSimulation, isMarketOrder, isFillOrKill
    }

// 建立限價買單
    public static Order createLimitBuyOrder(double price, int volume, Trader trader) {
        return new Order("buy", price, volume, trader, false, false, false);
        // type, price, volume, trader, isSimulation, isMarketOrder, isFillOrKill
    }

// 建立限價賣單
    public static Order createLimitSellOrder(double price, int volume, Trader trader) {
        return new Order("sell", price, volume, trader, false, false, false);
        // type, price, volume, trader, isSimulation, isMarketOrder, isFillOrKill
    }

// 建立FOK買單 (Fill or Kill，成交或取消)
    public static Order createFokBuyOrder(double price, int volume, Trader trader) {
        return new Order("buy", price, volume, trader, false, false, true);
        // type, price, volume, trader, isSimulation, isMarketOrder, isFillOrKill
    }

// 建立FOK賣單
    public static Order createFokSellOrder(double price, int volume, Trader trader) {
        return new Order("sell", price, volume, trader, false, false, true);
        // type, price, volume, trader, isSimulation, isMarketOrder, isFillOrKill
    }

    @Override
    public String toString() {
        if (isMarketOrder) {
            return String.format("%s市價單 %d股 (Trader:%s)",
                    "buy".equals(type) ? "買入" : "賣出", volume, trader.getTraderType());
        } else {
            return String.format("%s限價單 %.2f元 %d股 %s (Trader:%s)",
                    "buy".equals(type) ? "買入" : "賣出", price, volume,
                    isFillOrKill ? "FOK" : "", trader.getTraderType());
        }
    }

    // Getter 和 Setter 方法
    public String getType() {
        return type;
    }

    public double getPrice() {
        return price;
    }

    public int getVolume() {
        return volume;
    }

    public Trader getTrader() {
        return trader;
    }

    public UserAccount getTraderAccount() {
        return traderAccount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, volume);
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public boolean isSimulation() {
        return isSimulation;
    }

    public String getId() {
        return id;
    }

    // 用於檢查訂單是否為市價單
    public boolean isMarketOrder() {
        return isMarketOrder;
    }

    public boolean isFillOrKill() {
        return isFillOrKill;
    }
}
