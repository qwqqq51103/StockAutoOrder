package AIStrategies;

import Core.OrderBook;
import Core.Stock;
import StockMainAction.StockMarketSimulation;

/**
 * 個人戶 AI，繼承自散戶 AI RetailInvestorAI，
 * 但可以覆寫部分方法，以符合個人戶的行為需求。
 */
public class PersonalAI extends RetailInvestorAI {

    // 如果想在 PersonalAI 自行紀錄平均成本，可加一個欄位：
    private double personalAverageCost = 0.0;

    // 同理，自行加一個個人戶的 takeProfitPrice
    private Double personalTakeProfitPrice = null;

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
     * 取得個人戶的平均成本價格
     * （此欄位您可以在您的交易邏輯中手動維護）
     */
    public double getAverageCostPrice() {
        return personalAverageCost;
    }

    /**
     * 設定個人戶的平均成本價格
     * （若需要，可在交易後自行更新）
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
     * 假設您想要讓個人戶AI的決策中，
     * 可以自行調整風險或門檻，可覆寫父類別makeDecision
     */
    @Override
    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketSimulation simulation) {
        // 個人戶邏輯... 
        // (若想沿用父類邏輯，可先呼叫 super.makeDecision(stock, orderBook, simulation);)
        super.makeDecision(stock, orderBook, simulation);

        // 假設交易成功後，想自動幫自己更新 personalAverageCost
        // 或自動幫自己設定個人的 takeProfitPrice 也行

        // 例如：
        // if (...) {
        //     setAverageCostPrice( ... ); 
        //     setTakeProfitPrice( ... );
        // }
    }
}
