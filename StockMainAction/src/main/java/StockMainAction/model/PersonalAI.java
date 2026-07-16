package StockMainAction.model;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import StockMainAction.model.core.ExecutionResult;
import StockMainAction.model.core.OrderSubmissionResult;
import StockMainAction.model.core.OrderSide;
import StockMainAction.model.strategy.OrderIntent;
import StockMainAction.model.strategy.SignalAction;
import StockMainAction.model.strategy.StrategyExecutionResult;
import StockMainAction.model.strategy.StrategyPipeline;
import StockMainAction.model.strategy.TradingSignal;
import StockMainAction.StockMarketSimulation;
import StockMainAction.model.user.UserAccount;
import StockMainAction.util.logging.MarketLogger;
import java.util.Random;

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
    private StockMarketModel model;

    // 標記：個人戶不使用自動撤單
    private boolean disableAutoCancellation = true;

    private static final MarketLogger logger = MarketLogger.getInstance();
    
    public PersonalAI(double initialCash, String traderID, StockMarketModel model, OrderBook orderBook, Stock stock) {
        this(initialCash, traderID, model, orderBook, stock, new Random());
    }

    public PersonalAI(double initialCash, String traderID, StockMarketModel model,
            OrderBook orderBook, Stock stock, Random random) {
        super(initialCash, traderID, model, random);
        this.model = model;  // 修正：使用 model 參數
        this.orderBook = orderBook;
        this.stock = stock;
        
        logger.info("個人戶 AI 初始化完成，自動撤單已禁用", "PERSONAL_AI");
    }

    /**
     * 覆寫 getTraderType()，改為回傳 "PERSONAL"
     */
    @Override
    public String getTraderType() {
        return "PERSONAL";
    }

    /**
     * 覆寫 makeDecision 方法，移除自動撤單邏輯
     * 個人戶由用戶完全手動控制，不進行自動撤單
     */
    @Override
    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketModel model) {
        // 如果傳入的 model 不為 null，更新當前實例的 model 引用
        if (model != null) {
            this.model = model;
        }
        
        // ❌ 個人戶不執行自動撤單
        // orderCancelCounter++;
        // if (orderCancelCounter >= ORDER_CANCEL_INTERVAL) {
        //     orderCancelCounter = 0;
        //     cancelOutdatedOrders();
        // }
        
        // ✅ 直接調用父類的決策邏輯（不包含撤單部分）
        // 因為父類的 makeDecision 包含撤單邏輯，我們需要重新實現簡化版
        // 個人戶主要由UI手動操作，這裡僅保留基本初始化
        
        try {
            this.orderBook = orderBook;
            this.stock = stock;
            
            // 個人戶不執行自動交易決策，完全由用戶手動控制
            // 如果未來需要自動策略，可以在這裡添加
            
        } catch (Exception e) {
            logger.warn("個人戶 AI 決策過程發生錯誤：" + e.getMessage(), "PERSONAL_AI");
        }
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

    public ExecutionResult executeMarketBuyResult(int quantity) {
        if (orderBook == null) {
            return new ExecutionResult(quantity, 0, 0, 0, "order book unavailable");
        }
        return immediateResult(quantity, executeIntent(
                OrderIntent.market(OrderSide.BUY, quantity, "personal market buy")));
    }

    public ExecutionResult executeMarketSellResult(int quantity) {
        if (orderBook == null) {
            return new ExecutionResult(quantity, 0, 0, 0, "order book unavailable");
        }
        return immediateResult(quantity, executeIntent(
                OrderIntent.market(OrderSide.SELL, quantity, "personal market sell")));
    }

    public OrderSubmissionResult submitLimitBuy(int quantity, double price) {
        StrategyExecutionResult result = executeIntent(
                OrderIntent.limit(OrderSide.BUY, quantity, price, "personal limit buy"));
        return result.submission() != null ? result.submission()
                : new OrderSubmissionResult(null, false, result.failureReason());
    }

    public OrderSubmissionResult submitLimitSell(int quantity, double price) {
        StrategyExecutionResult result = executeIntent(
                OrderIntent.limit(OrderSide.SELL, quantity, price, "personal limit sell"));
        return result.submission() != null ? result.submission()
                : new OrderSubmissionResult(null, false, result.failureReason());
    }

    public ExecutionResult executeFokBuyResult(int quantity, double price) {
        StrategyExecutionResult result = executeIntent(
                OrderIntent.fok(OrderSide.BUY, quantity, price, "personal FOK buy"));
        return immediateResult(quantity, result);
    }

    public ExecutionResult executeFokSellResult(int quantity, double price) {
        StrategyExecutionResult result = executeIntent(
                OrderIntent.fok(OrderSide.SELL, quantity, price, "personal FOK sell"));
        return immediateResult(quantity, result);
    }

    private StrategyExecutionResult executeIntent(OrderIntent intent) {
        SignalAction action = intent.side() == OrderSide.BUY ? SignalAction.BUY : SignalAction.SELL;
        return StrategyPipeline.standard().execute(
                new TradingSignal(action, 1.0, intent.reason()), intent, this, orderBook);
    }

    private static ExecutionResult immediateResult(int quantity, StrategyExecutionResult result) {
        return result.execution() != null ? result.execution()
                : new ExecutionResult(quantity, 0, 0, 0, result.failureReason());
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
        ExecutionResult result = executeMarketBuyResult(buyAmount);
        // 若有部分成交亦可視情況而定，這裡直接回傳 buyAmount
        return result.filledVolume();
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
        return executeMarketSellResult(sellAmount).filledVolume();
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
        return submitLimitBuy(amount, currentPrice).accepted() ? amount : 0;
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
        return submitLimitSell(amount, currentPrice).accepted() ? amount : 0;
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
        return executeFokBuyResult(amount, currentPrice).filledVolume();
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
        return executeFokSellResult(amount, currentPrice).filledVolume();
    }

    public void onOrderCancelled(Order order) {
        logger.info(String.format("個人戶訂單已取消 ID=%s 價格=%.2f 數量=%d 類型=%s",
                order.getId(), order.getPrice(), order.getVolume(), order.getType()),
                "PERSONAL_AI");

        // 你可以在這裡加入更多動作（如重設目標價、通知 UI、統計等）
    }

    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if (type.equals("buy")) {

            // 更新個人平均成本
            int totalStocks = getAccount().getStockInventory();
            if (totalStocks > 0) {
                personalAverageCost = ((personalAverageCost * (totalStocks - volume)) + transactionAmount) / totalStocks;
            }

            // 設定止盈價 (例如 +10% 利潤)
            personalTakeProfitPrice = personalAverageCost * 1.1;

            logger.info(String.format("個人戶買入 %d 股，成交價 %.2f，更新後均價 %.2f，目標止盈價 %.2f",
                    volume, price, personalAverageCost, personalTakeProfitPrice), "PERSONAL_AI");

        } else if (type.equals("sell")) {
            // 增加可用資金

            // 如果全部賣掉，重置均價
            if (getAccount().getStockInventory() == 0) {
                personalAverageCost = 0.0;
                personalTakeProfitPrice = null;
            }

            logger.info(String.format("個人戶賣出 %d 股，成交價 %.2f，剩餘持股 %d 股，更新後均價 %.2f",
                    volume, price, getAccount().getStockInventory(), personalAverageCost), "PERSONAL_AI");
        }

        // 更新 UI 標籤
        model.updateLabels();
    }

    @Override
    public void updateAverageCostPrice(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {

            // 更新個人平均成本
            int totalStocks = getAccount().getStockInventory();
            if (totalStocks > 0) {
                personalAverageCost = ((personalAverageCost * (totalStocks - volume)) + transactionAmount) / totalStocks;
            }

            // 設定止盈價 (例如 +10%)
            personalTakeProfitPrice = personalAverageCost * 1.1;

            logger.info(String.format("個人戶市價買入 %d 股，成交價 %.2f，更新後均價 %.2f，目標止盈價 %.2f",
                    volume, price, personalAverageCost, personalTakeProfitPrice), "PERSONAL_AI");

        } else if ("sell".equals(type)) {
            // 扣股並增加可用資金

            // 若全部賣掉，重置均價
            if (getAccount().getStockInventory() == 0) {
                personalAverageCost = 0.0;
                personalTakeProfitPrice = null;
            }

            logger.info(String.format("個人戶市價賣出 %d 股，成交價 %.2f，剩餘持股 %d 股，更新後均價 %.2f",
                    volume, price, getAccount().getStockInventory(), personalAverageCost), "PERSONAL_AI");
        }

        // 更新 UI 標籤
        model.updateLabels();
    }
}
