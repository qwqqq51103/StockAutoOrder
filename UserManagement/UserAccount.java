package UserManagement;

/**
 * UserAccount 類別，表示交易者的帳戶
 */
public class UserAccount {

    private double availableFunds;
    private int stockInventory;

    /**
     * 構造函數
     *
     * @param initialFunds 初始資金
     * @param initialStocks 初始股票數量
     */
    public UserAccount(double initialFunds, int initialStocks) {
        this.availableFunds = initialFunds;
        this.stockInventory = initialStocks; // 初始化可用股票餘額
    }

    // 凍結資金
    public boolean freezeFunds(double amount) {
        if (availableFunds >= amount) {
            availableFunds -= amount;  // 扣除資金，視為凍結
            return true;
        }
        return false;
    }

    // 凍結股票
    public boolean freezeStocks(int quantity) {
        if (stockInventory >= quantity) {
            stockInventory -= quantity;  // 扣除股票，視為凍結
            return true;
        }
        return false;
    }

    /**
     * 獲取可用資金
     *
     * @return 可用資金
     */
    public double getAvailableFunds() {
        return availableFunds;
    }

    /**
     * 獲取股票庫存
     *
     * @return 股票庫存
     */
    public int getStockInventory() {
        return stockInventory;
    }

    /**
     * 增加股票數量
     *
     * @param amount 增加的股票數量
     */
    public void incrementStocks(int amount) {
        stockInventory += amount;
    }

    /**
     * 增加資金
     *
     * @param amount 增加的資金
     */
    public void incrementFunds(double amount) {
        availableFunds += amount;
    }

    /**
     * 減少股票數量
     *
     * @param amount 減少的股票數量
     */
    public void decrementStocks(int amount) {
        if (amount <= stockInventory) {
            stockInventory -= amount;
        } else {
            throw new IllegalArgumentException("可用股票不足，無法減少指定數量的股票。");
        }
    }

    /**
     * 減少資金
     *
     * @param amount 減少的資金
     */
    public void decrementFunds(double amount) {
        if (amount <= availableFunds) {
            availableFunds -= amount;
        } else {
            throw new IllegalArgumentException("可用資金不足，無法減少指定數量的資金。");
        }
    }

    // 其他方法，如解凍資金和股票等
}
