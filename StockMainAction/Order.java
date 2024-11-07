package StockMainAction;

import java.util.UUID;

/**
 * 訂單類別，表示單個買賣訂單
 */
public class Order {

    private String id; //唯一識別符
    private String type; // "buy" 或 "sell"
    private double price;
    private int volume;
    private String traderType; // "主力" 或 "散戶"
    private Object trader; // 交易者引用
    private UserAccount traderAccount; // 交易者帳戶
    private long timestamp; // 用於時間優先
    private boolean isSimulation; // 新增屬性
    private boolean isMarketOrder; // 用於標記是否為市價單

    // 構造函數
    public Order(String type, double price, int volume, String traderType, Object trader, UserAccount traderAccount, boolean isSimulation, boolean isMarketOrder) {
        if (traderType == null) {
            traderType = "未知";
        }
        this.type = type;
        this.price = price;
        this.volume = volume;
        this.traderType = traderType;
        this.trader = trader; // 將 trader 設置為 MainForceStrategyWithOrderBook
        this.traderAccount = traderAccount; // traderAccount 設置為 UserAccount
        this.isSimulation = isSimulation;
        this.isMarketOrder = isMarketOrder; // 設定是否為市價單
        this.timestamp = System.currentTimeMillis();
        this.id = UUID.randomUUID().toString();  // 使用 UUID 生成唯一 ID
    }

    @Override
    public String toString() {
        return "Order{"
                + "type='" + type + '\''
                + ", price=" + price
                + ", volume=" + volume
                + ", traderType='" + traderType + '\''
                + ", timestamp=" + timestamp
                + '}';
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

    public String getTraderType() {
        return traderType;
    }

    public Object getTrader() {
        return trader;
    }

    public UserAccount getTraderAccount() {
        return this.traderAccount;
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
}
