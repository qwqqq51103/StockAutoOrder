package StockMainAction;

/**
 *
 * @author chiat
 */
public class UserAccount {

    public double availableFunds;
    public int availableStocks;

    public UserAccount(double initialFunds, int initialStocks) {
        this.availableFunds = initialFunds;
        this.availableStocks = initialStocks; // 初始化可用股票餘額
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
        if (availableStocks >= quantity) {
            availableStocks -= quantity;  // 扣除股票，視為凍結
            return true;
        }
        return false;
    }

    public double getAvailableFunds() {
        return availableFunds;
    }

    public int getStockInventory() {
        return availableStocks;
    }

    public void incrementStocks(int amount) {
        availableStocks += amount;
    }

    public void incrementFunds(double amount) {
        availableFunds += amount;
    }

    public void decrementStocks(int amount) {
        if (amount <= availableStocks) {
            availableStocks -= amount;
        } else {
            throw new IllegalArgumentException("可用股票不足，無法減少指定數量的股票。");
        }
    }

    public void decrementFunds(double amount) {
        if (amount <= availableFunds) {
            availableFunds -= amount;
        } else {
            throw new IllegalArgumentException("可用資金不足，無法減少指定數量的資金。");
        }
    }

    // 其他方法，如解凍資金和股票等
}
