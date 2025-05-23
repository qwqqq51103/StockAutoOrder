package StockMainAction.model.core;

import StockMainAction.model.user.UserAccount;

/**
 * Trader 接口，定義所有交易者的共同行為
 */
public interface Trader {
    /**
     * 獲取交易者的帳戶
     * @return UserAccount 實例
     */
    UserAccount getAccount();

    /**
     * 獲取交易者的類型
     * @return 交易者類型的字串表示
     */
    String getTraderType();

    /**
     * 更新交易者在交易後的帳戶狀態
     * @param type 交易類型（"buy" 或 "sell"）
     * @param volume 交易量
     * @param price 交易價格（每股價格）
     */
    void updateAfterTransaction(String type, int volume, double price);

    /**
     * 市價買賣更新交易者在交易後的帳戶狀態
     * @param type 交易類型（"buy" 或 "sell"）
     * @param transactionVolume 交易量
     * @param transactionPrice 交易價格（每股價格）
     */
    void updateAverageCostPrice(String buy, int transactionVolume, double transactionPrice);
}
