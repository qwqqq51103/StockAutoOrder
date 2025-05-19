package Core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Transaction {

    private String buyerType;
    private String sellerType;
    private final double price;
    private final int volume;
    private String matchingMode; // 新增字段
    private LocalDateTime timestamp;

    public Transaction(String buyer, String seller, double price, int volume, String matchingMode) {
        this.buyerType = buyer;
        this.sellerType = seller;
        this.price = price;
        this.volume = volume;
        this.matchingMode = matchingMode;
        this.timestamp = LocalDateTime.now();
    }

    // Getter 方法
    public String getBuyerType() {
        return buyerType;
    }

    public String getSellerType() {
        return sellerType;
    }

    public double getPrice() {
        return price;
    }

    public int getVolume() {
        return volume;
    }

    public String getMatchingMode() {
        return matchingMode;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("%s [%s] 買方:%s 賣方:%s 價格:%.2f 數量:%d 模式:%s",
                timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                "交易", buyerType, sellerType, price, volume, matchingMode);
    }

    // 獲取當前時間戳
    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }

    /**
     * 向後兼容的構造函數，默認撮合模式為 "STANDARD"
     *
     * @param buyer 買方類型
     * @param seller 賣方類型
     * @param price 成交價格
     * @param volume 成交數量
     */
    public Transaction(String buyer, String seller, double price, int volume) {
        this(buyer, seller, price, volume, "STANDARD");
    }
}
