package StockMainAction;

/**
 *
 * @author Joe 股票類別 專門負責處理股票相關的屬性與方法
 */
import java.util.ArrayList;
import java.util.List;

public class Stock {

    private String name;
    private double price;
    private double previousPrice;  // 用於記錄上一筆成交價格
    private double volume;
    private double mainForceHolding;
    private List<Double> priceHistory; // 儲存歷史價格

    public Stock(String name, double price, double volume) {
        this.name = name;
        this.price = price;
        this.previousPrice = price; // 初始化為相同價格
        this.volume = volume;
        this.mainForceHolding = 0;
        this.priceHistory = new ArrayList<>();
        this.priceHistory.add(price); // 將初始價格加入歷史數據
    }

    public double getPrice() {
        return price;
    }

    // 設定價格並記錄到價格歷史
    public void setPrice(double newPrice) {
        this.previousPrice = this.price;  // 保存當前價格為 previousPrice
        this.price = newPrice;            // 更新股價為新成交價格
    }
    
    public double getPreviousPrice() {
        return previousPrice;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getMainForceHolding() {
        return mainForceHolding;
    }

    public void setMainForceHolding(double holding) {
        this.mainForceHolding = holding;
    }

    // 計算最近數筆價格的平均值，預設最近10筆
    public double getAveragePrice() {
        int historySize = priceHistory.size();
        int range = Math.min(historySize, 10); // 預設最近10筆
        double sum = 0;

        for (int i = historySize - range; i < historySize; i++) {
            sum += priceHistory.get(i);
        }
        return range > 0 ? sum / range : price;
    }
}
