package StockMainAction;

/**
 * 股票類別，專門負責處理股票相關的屬性與方法
 */
public class Stock {

    private String name;
    private double price;
    private double previousPrice;  // 用於記錄上一筆成交價格
    private double volume;

    /**
     * 構造函數
     *
     * @param name 股票名稱
     * @param price 初始價格
     * @param volume 初始成交量
     */
    public Stock(String name, double price, double volume) {
        this.name = name;
        this.price = price;
        this.previousPrice = price; // 初始化為相同價格
        this.volume = volume;
    }

    /**
     * 獲取股票名稱
     *
     * @return 股票名稱
     */
    public String getName() {
        return name;
    }

    /**
     * 獲取當前價格
     *
     * @return 當前價格
     */
    public double getPrice() {
        return price;
    }

    /**
     * 設定價格並記錄上一價格
     *
     * @param newPrice 新價格
     */
    public void setPrice(double newPrice) {
        this.previousPrice = this.price;  // 保存當前價格為 previousPrice
        this.price = newPrice;            // 更新股價為新成交價格
    }

    /**
     * 獲取前一價格
     *
     * @return 前一價格
     */
    public double getPreviousPrice() {
        return previousPrice;
    }

    /**
     * 獲取成交量
     *
     * @return 成交量
     */
    public double getVolume() {
        return volume;
    }

    /**
     * 設定成交量
     *
     * @param volume 新的成交量
     */
    public void setVolume(double volume) {
        this.volume = volume;
    }
}
