package Core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Transaction {

    private final String buyer;
    private final String seller;
    private final double price;
    private final int volume;
    private final String timestamp;

    public Transaction(String buyer, String seller, double price, int volume) {
        this.buyer = buyer;
        this.seller = seller;
        this.price = price;
        this.volume = volume;
        this.timestamp = getCurrentTimestamp();
    }

    @Override
    public String toString() {
        return String.format("[%s] 買方：%s，賣方：%s，價格：%.2f，成交量：%d",
                timestamp, buyer, seller, price, volume);
    }

    // 獲取當前時間戳
    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
