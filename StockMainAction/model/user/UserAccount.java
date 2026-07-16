package StockMainAction.model.user;

/**
 * UserAccount 類別，表示交易者的帳戶
 */
public class UserAccount {

    private double availableFunds;
    private int stockInventory;
    private int frozenStocks;
    private double frozenFunds;

    /**
     * 構造函數
     *
     * @param initialFunds 初始資金
     * @param initialStocks 初始股票數量
     */
    public UserAccount(double initialFunds, int initialStocks) {
        this.availableFunds = initialFunds;
        this.stockInventory = initialStocks;
        this.frozenStocks = 0;
        this.frozenFunds = 0.0;
    }

    public boolean freezeFunds(double amount) {
        if (availableFunds >= amount) {
            availableFunds -= amount;
            frozenFunds += amount;
            return true;
        }
        return false;
    }

    // 凍結股票
    public boolean freezeStocks(int quantity) {
        if (quantity <= 0) {
            return false;
        }
        if (stockInventory >= quantity) {
            stockInventory -= quantity;  // 從可用轉為凍結
            frozenStocks += quantity;
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
     * 取得凍結中的股票數量（尚未成交或撤單）
     */
    public int getFrozenStocks() {
        return frozenStocks;
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

    /**
     * 撤單時解凍股票：從凍結轉回可用
     */
    public void unfreezeStocks(int quantity) {
        if (quantity <= 0) return;
        if (quantity > frozenStocks) {
            throw new IllegalArgumentException("凍結股票不足，無法解凍指定數量。");
        }
        frozenStocks -= quantity;
        stockInventory += quantity;
    }

    public void consumeFrozenStocks(int quantity) {
        if (quantity <= 0) return;
        int useFrozen = Math.min(quantity, frozenStocks);
        frozenStocks -= useFrozen;
        int remain = quantity - useFrozen;
        if (remain > 0) {
            if (remain > stockInventory) {
                throw new IllegalArgumentException("可用與凍結股票合計不足，無法消耗指定數量。");
            }
            stockInventory -= remain;
        }
    }
    
    public double getFrozenFunds() {
        return frozenFunds;
    }
    
    public boolean consumeFrozenFunds(double amount) {
        if (frozenFunds >= amount) {
            frozenFunds -= amount;
            return true;
        }
        if (availableFunds >= amount) {
            availableFunds -= amount;
            return true;
        }
        return false;
    }
    
    public boolean unfreezeFunds(double amount) {
        if (frozenFunds >= amount) {
            frozenFunds -= amount;
            availableFunds += amount;
            return true;
        }
        return false;
    }
    
    public double getTotalFunds() {
        return availableFunds + frozenFunds;
    }
    
    public int getTotalStocks() {
        return stockInventory + frozenStocks;
    }
}
