package AIStrategies;

import Core.OrderBook;
import Core.Stock;
import StockMainAction.StockMarketSimulation;
import UserManagement.UserAccount;

/**
 * 個人戶 AI，繼承自散戶 AI RetailInvestorAI， 但可以覆寫部分方法，以符合個人戶的行為需求。
 */
public class PersonalAI extends RetailInvestorAI {

    // 如果想在 PersonalAI 自行紀錄平均成本，可加一個欄位：
    private double personalAverageCost = 0.0;

    // 同理，自行加一個個人戶的 takeProfitPrice
    private Double personalTakeProfitPrice = null;
    
    private UserAccount account;
    private StockMarketSimulation simulation;

    public PersonalAI(double initialCash, String traderID, StockMarketSimulation simulation) {
        super(initialCash, traderID, simulation);
    }

    /**
     * 覆寫 getTraderType()，改為回傳 "PERSONAL"
     */
    @Override
    public String getTraderType() {
        return "PERSONAL";
    }

    /**
     * 取得個人戶的平均成本價格 （此欄位您可以在您的交易邏輯中手動維護）
     */
    public double getAverageCostPrice() {
        return personalAverageCost;
    }

    /**
     * 設定個人戶的平均成本價格 （若需要，可在交易後自行更新）
     */
    public void setAverageCostPrice(double newAvgPrice) {
        this.personalAverageCost = newAvgPrice;
    }

    /**
     * 取得個人戶的止盈價位
     */
    public double getTakeProfitPrice() {
        return (personalTakeProfitPrice != null) ? personalTakeProfitPrice : 0.0;
    }

    /**
     * 設定個人戶的止盈價位
     */
    public void setTakeProfitPrice(double price) {
        this.personalTakeProfitPrice = price;
    }

    /**
     * 假設您想要讓個人戶AI的決策中， 可以自行調整風險或門檻，可覆寫父類別makeDecision
     */
    @Override
    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketSimulation simulation) {
        super.makeDecision(stock, orderBook, simulation);

        double currentPrice = stock.getPrice();

        // 如果價格達到止盈價，賣出
        if (personalTakeProfitPrice != null && currentPrice >= personalTakeProfitPrice) {
            int sellVolume = account.getStockInventory();
            if (sellVolume > 0) {
                orderBook.marketSell(this, sellVolume);
                System.out.println("[個人AI] 已達止盈價，市價賣出 " + sellVolume + " 股。");
            }
        }
    }

    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if (type.equals("buy")) {
            // 更新持有股數
            account.incrementStocks(volume);

            // 更新個人平均成本
            int totalStocks = account.getStockInventory();
            if (totalStocks > 0) {
                personalAverageCost = ((personalAverageCost * (totalStocks - volume)) + transactionAmount) / totalStocks;
            }

            // 設定止盈價 (例如 +10% 利潤)
            personalTakeProfitPrice = personalAverageCost * 1.1;

            System.out.println(String.format("[個人AI] 買入 %d 股，成交價 %.2f，更新後均價 %.2f，目標止盈價 %.2f",
                    volume, price, personalAverageCost, personalTakeProfitPrice));

        } else if (type.equals("sell")) {
            // 增加可用資金
            account.incrementFunds(transactionAmount);

            // 如果全部賣掉，重置均價
            if (account.getStockInventory() == 0) {
                personalAverageCost = 0.0;
                personalTakeProfitPrice = null;
            }

            System.out.println(String.format("[個人AI] 賣出 %d 股，成交價 %.2f，剩餘持股 %d 股，更新後均價 %.2f",
                    volume, price, account.getStockInventory(), personalAverageCost));
        }

        // 更新 UI 標籤
        simulation.updateLabels();
    }

}
