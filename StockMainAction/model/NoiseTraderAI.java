package StockMainAction.model;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Stock;
import StockMainAction.model.core.Trader;
import StockMainAction.model.user.UserAccount;
import java.util.List;
import java.util.Random;

/**
 * 噪音交易者（遊玩用）
 * - 目標：用小額市價/侵略性限價「主動吃單」，增加成交量與價格跳動
 * - 不改變台股撮合規則，只是增加市場參與者與主動性
 */
public class NoiseTraderAI implements Trader {
    private final String traderId;
    private final OrderBook orderBook;
    private final Stock stock;
    private final UserAccount account;
    private final Random random = new Random();

    // 簡單節流：避免每個 tick 都下單
    private int cooldownTicks = 0;

    public NoiseTraderAI(double initialCash, int initialStocks, String traderId,
                         StockMarketModel model, OrderBook orderBook, Stock stock) {
        this.traderId = (traderId == null ? "NoiseTrader" : traderId);
        this.orderBook = orderBook;
        this.stock = stock;
        this.account = new UserAccount(initialCash, initialStocks);
    }

    @Override
    public UserAccount getAccount() {
        return account;
    }

    @Override
    public String getTraderType() {
        // 讓 UI / log 能看到不同噪音交易者
        return traderId;
    }

    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double amount = price * volume;
        if ("buy".equals(type)) {
            // 限價買：先消耗凍結資金，再入庫
            try {
                account.consumeFrozenFunds(amount);
            } catch (Exception e) {
                account.decrementFunds(amount);
            }
            account.incrementStocks(volume);
        } else if ("sell".equals(type)) {
            // 限價賣：成交後入帳；庫存由撮合端 consumeFrozenStocks 後再更新
            account.incrementFunds(amount);
        }
    }

    @Override
    public void updateAverageCostPrice(String type, int volume, double price) {
        double amount = price * volume;
        if ("buy".equals(type)) {
            // 市價買：直接扣款+入庫（市價單不走 freeze）
            if (account.getAvailableFunds() >= amount) {
                account.decrementFunds(amount);
                account.incrementStocks(volume);
            }
        } else if ("sell".equals(type)) {
            // 市價賣：直接扣庫存+入帳
            if (account.getStockInventory() >= volume) {
                account.decrementStocks(volume);
                account.incrementFunds(amount);
            }
        }
    }

    /**
     * 每個 tick 呼叫一次，隨機做小額主動交易
     */
    public void makeDecision() {
        if (orderBook == null || stock == null) return;
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        double last = stock.getPrice();
        if (last <= 0) return;

        // 取得買一/賣一
        double bestBid = 0.0;
        double bestAsk = 0.0;
        List<Order> topBuys = orderBook.getTopBuyOrders(1);
        List<Order> topSells = orderBook.getTopSellOrders(1);
        if (!topBuys.isEmpty() && topBuys.get(0) != null) bestBid = topBuys.get(0).getPrice();
        if (!topSells.isEmpty() && topSells.get(0) != null) bestAsk = topSells.get(0).getPrice();

        // 沒有對手盤就不做主動吃單
        if (bestBid <= 0 && bestAsk <= 0) {
            cooldownTicks = 2;
            return;
        }

        // 小額：10~200 股
        int vol = 10 + random.nextInt(191);
        boolean buySide = random.nextBoolean();

        // 50%：市價單（吃單）；50%：侵略性限價（直接掛在對手價位）
        boolean useMarket = random.nextDouble() < 0.5;

        if (buySide) {
            if (bestAsk <= 0) {
                cooldownTicks = 1;
                return;
            }
            double estCost = bestAsk * vol;
            if (account.getAvailableFunds() < estCost) {
                cooldownTicks = 2;
                return;
            }
            if (useMarket) {
                orderBook.marketBuy(this, vol);
            } else {
                // 侵略性限價：掛在賣一，理論上可立即成交（交叉）
                Order o = Order.createLimitBuyOrder(bestAsk, vol, this);
                orderBook.submitBuyOrder(o, bestAsk);
            }
        } else {
            if (bestBid <= 0) {
                cooldownTicks = 1;
                return;
            }
            if (account.getStockInventory() < vol) {
                cooldownTicks = 2;
                return;
            }
            if (useMarket) {
                orderBook.marketSell(this, vol);
            } else {
                // 侵略性限價：掛在買一
                Order o = Order.createLimitSellOrder(bestBid, vol, this);
                orderBook.submitSellOrder(o, bestBid);
            }
        }

        // 下一次下單延遲 1~3 tick
        cooldownTicks = 1 + random.nextInt(3);
    }
}

