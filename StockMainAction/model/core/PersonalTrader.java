package StockMainAction.model.core;

import StockMainAction.model.user.UserAccount;

/**
 * 個人戶交易者類別，實現 Trader 接口
 */
public class PersonalTrader implements Trader {

    private String name;
    private UserAccount account;

    /**
     * 構造函數
     *
     * @param name 交易者名稱
     * @param account 交易者帳戶
     */
    public PersonalTrader(String name, UserAccount account) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("交易者名稱不能為空");
        }
        if (account == null) {
            throw new IllegalArgumentException("UserAccount 不能為 null");
        }
        this.name = name;
        this.account = account;
    }

    /**
     * 獲取交易者的帳戶
     *
     * @return UserAccount 實例
     */
    @Override
    public UserAccount getAccount() {
        return this.account;
    }

    /**
     * 獲取交易者的類型
     *
     * @return 交易者類型的字串表示
     */
    @Override
    public String getTraderType() {
        return "PERSONAL";
    }

    /**
     * 更新交易者在交易後的帳戶狀態
     *
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        if (type.equalsIgnoreCase("buy")) {
            // 買入後更新帳戶狀態
            account.incrementStocks(volume);
            account.decrementFunds(price * volume);
            System.out.println("個人戶 " + name + " 買入 " + volume + " 股，每股價格 " + price);
        } else if (type.equalsIgnoreCase("sell")) {
            // 賣出後更新帳戶狀態
            account.decrementStocks(volume);
            account.incrementFunds(price * volume);
            System.out.println("個人戶 " + name + " 賣出 " + volume + " 股，每股價格 " + price);
        } else {
            throw new IllegalArgumentException("未知的交易類型: " + type);
        }
    }

    /**
     * 市價買賣更新交易者在交易後的帳戶狀態
     *
     * @param type 交易類型（"buy" 或 "sell"）
     * @param transactionVolume 交易量
     * @param transactionPrice 交易價格（每股價格）
     */
    @Override
    public void updateAverageCostPrice(String type, int transactionVolume, double transactionPrice) {
        // 這裡可以實現更複雜的邏輯來更新平均成本價格
        // 目前僅作為示例，打印交易資訊
        System.out.println("個人戶 " + name + " 更新平均成本價格: 類型=" + type
                + ", 數量=" + transactionVolume + ", 價格=" + transactionPrice);
    }

    /**
     * 獲取交易者名稱
     *
     * @return 交易者名稱
     */
    public String getName() {
        return this.name;
    }

    // 您可以在這裡添加更多個人戶特有的方法或屬性
}
