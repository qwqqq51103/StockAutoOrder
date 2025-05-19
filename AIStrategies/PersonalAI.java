package AIStrategies;

import Core.Order;
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

    private StockMarketSimulation simulation;
    private OrderBook orderBook; // 亦可在 makeDecision 時指定
    private Stock stock;  // 股票實例

    public PersonalAI(double initialCash, String traderID, StockMarketSimulation simulation, OrderBook orderBook, Stock stock) {
        super(initialCash, traderID, simulation);
        this.simulation = simulation;
        this.orderBook = orderBook;
        this.stock = stock;
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

    // ========== 實際掛單操作 (成功/失敗印出) ==========
    /**
     * 市價買入操作：先檢查資金，再呼叫 orderBook.marketBuy
     *
     * @param buyAmount 欲買股數
     * @return 實際買入數 (0=失敗)
     */
    public int 市價買入操作(int buyAmount) {
        double price = stock.getPrice();
        double totalCost = price * buyAmount;
        double funds = getAccount().getAvailableFunds();

        if (stock == null) {
            //System.out.println("【錯誤】市價買入失敗: stock 為 null");
            return 0;
        }

        if (orderBook == null) {
            //System.out.println("【錯誤】市價買入失敗: orderBook 為 null");
            return 0;
        }

        if (funds < totalCost) {
            // 資金不足
            return 0;
        }

        // 使用 orderBook 的 marketBuy 方法（已經實現了市價單邏輯）
        orderBook.marketBuy(this, buyAmount);
        // 若有部分成交亦可視情況而定，這裡直接回傳 buyAmount
        return buyAmount;
    }

    /**
     * 市價賣出操作：先檢查持股，再呼叫 orderBook.marketSell
     *
     * @param sellAmount 欲賣股數
     * @return 實際賣出數 (0=失敗)
     */
    public int 市價賣出操作(int sellAmount) {
        int hold = getAccumulatedStocks();
        if (hold < sellAmount) {
            // 持股不足
            return 0;
        }

        // 使用 orderBook 的 marketSell 方法（已經實現了市價單邏輯）
        orderBook.marketSell(this, sellAmount);
        return sellAmount;
    }

    /**
     * 限價買入操作：檢查資金 & 市場可賣數量，再掛 buyOrder
     *
     * @param amount 欲買股數
     * @param currentPrice 目前股價
     * @return 實際買入股數 (0=失敗)
     */
    public int 限價買入操作(int amount, double currentPrice) {
        double totalCost = currentPrice * amount;
        double funds = getAccount().getAvailableFunds();

        // 檢查資金
        if (funds < totalCost) {
            return 0;
        }

        // 檢查市場中是否有足夠賣單 (若要模擬失敗的話)
        //int availableSell = orderBook.getAvailableSellVolume(currentPrice);
        //if (availableSell < amount) {
        //    return 0;
        //}
        // 成功掛限價買單 - 使用工廠方法
        Order buyOrder = Order.createLimitBuyOrder(currentPrice, amount, this);
        orderBook.submitBuyOrder(buyOrder, currentPrice);
        return amount;
    }

    /**
     * 限價賣出操作：檢查持股 & 市場可買數量，再掛 sellOrder
     *
     * @param amount 欲賣股數
     * @param currentPrice 目前股價
     * @return 實際賣出股數 (0=失敗)
     */
    public int 限價賣出操作(int amount, double currentPrice) {
        int hold = getAccumulatedStocks();
        if (hold < amount) {
            return 0;
        }
        //int availableBuy = orderBook.getAvailableBuyVolume(currentPrice);
        //if (availableBuy < amount) {
        //    return 0;
        //}

        // 成功掛限價賣單 - 使用工廠方法
        Order sellOrder = Order.createLimitSellOrder(currentPrice, amount, this);
        orderBook.submitSellOrder(sellOrder, currentPrice);
        return amount;
    }

    /**
     * FOK買入操作：檢查資金 & 市場可賣數量，使用FOK訂單
     *
     * @param amount 欲買股數
     * @param currentPrice 目前股價
     * @return 實際買入股數 (0=失敗)
     */
    public int FOK買入操作(int amount, double currentPrice) {
        double totalCost = currentPrice * amount;
        double funds = getAccount().getAvailableFunds();

        // 檢查資金
        if (funds < totalCost) {
            return 0;
        }

        // 使用 orderBook 的 submitFokBuyOrder 方法
        boolean success = orderBook.submitFokBuyOrder(currentPrice, amount, this);
        if (success) {
            return amount;
        } else {
            return 0; // FOK單失敗，無法完全滿足
        }
    }

    /**
     * FOK賣出操作：檢查持股 & 市場可買數量，使用FOK訂單
     *
     * @param amount 欲賣股數
     * @param currentPrice 目前股價
     * @return 實際賣出股數 (0=失敗)
     */
    public int FOK賣出操作(int amount, double currentPrice) {
        int hold = getAccumulatedStocks();
        if (hold < amount) {
            return 0;
        }

        // 使用 orderBook 的 submitFokSellOrder 方法
        boolean success = orderBook.submitFokSellOrder(currentPrice, amount, this);
        if (success) {
            return amount;
        } else {
            return 0; // FOK單失敗，無法完全滿足
        }
    }

    public void onOrderCancelled(Order order) {
        System.out.println("[個人AI] 訂單已取消，ID：" + order.getId()
                + "，價格：" + order.getPrice()
                + "，數量：" + order.getVolume()
                + "，類型：" + order.getType());

        // 你可以在這裡加入更多動作（如重設目標價、通知 UI、統計等）
    }

    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if (type.equals("buy")) {
            // 更新持有股數
            getAccount().incrementStocks(volume);

            // 更新個人平均成本
            int totalStocks = getAccount().getStockInventory();
            if (totalStocks > 0) {
                personalAverageCost = ((personalAverageCost * (totalStocks - volume)) + transactionAmount) / totalStocks;
            }

            // 設定止盈價 (例如 +10% 利潤)
            personalTakeProfitPrice = personalAverageCost * 1.1;

            System.out.println(String.format("[個人AI] 買入 %d 股，成交價 %.2f，更新後均價 %.2f，目標止盈價 %.2f",
                    volume, price, personalAverageCost, personalTakeProfitPrice));

        } else if (type.equals("sell")) {
            // 增加可用資金
            getAccount().incrementFunds(transactionAmount);

            // 如果全部賣掉，重置均價
            if (getAccount().getStockInventory() == 0) {
                personalAverageCost = 0.0;
                personalTakeProfitPrice = null;
            }

            System.out.println(String.format("[個人AI] 賣出 %d 股，成交價 %.2f，剩餘持股 %d 股，更新後均價 %.2f",
                    volume, price, getAccount().getStockInventory(), personalAverageCost));
        }

        // 更新 UI 標籤
        simulation.updateLabels();
    }
}
