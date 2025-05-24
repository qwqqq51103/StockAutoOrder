package StockMainAction.model.core;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.Trader;

public class Transaction {

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

    // 詳細的建構函數
    public Transaction(String id, Order buyOrder, Order sellOrder,
            double price, int volume, long timestamp) {
        this.id = id;
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    // Getter 和 Setter 方法
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
        return buyOrder.getTrader();
    }

    public Trader getSeller() {
        return sellOrder.getTrader();
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
}
