package Core;

import UserManagement.UserAccount;
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

    // 構造函數
    public Order(String type, double price, int volume, Trader trader, boolean isSimulation, boolean isMarketOrder) {
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
        this.timestamp = System.currentTimeMillis();
        this.id = UUID.randomUUID().toString();  // 使用 UUID 生成唯一 ID
    }

    @Override
    public String toString() {
        return "Order{"
                + "id='" + id + '\''
                + ", type='" + type + '\''
                + ", price=" + price
                + ", volume=" + volume
                + ", traderType='" + trader.getTraderType() + '\''
                + ", timestamp=" + timestamp
                + ", isMarketOrder=" + isMarketOrder
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
}
