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

    // [ADAPT] 由 MainView 噪音統計推送的多/空命中率（B:+10根）
    private volatile StockMarketModel.NoiseSignalQuality noiseQuality;
    private volatile boolean adaptiveEnabled = true;
    private volatile StockMarketModel.NoiseAdaptiveConfig adaptiveConfig;

    // [ADAPT] 參數上限/下限
    private int baseCooldownMin = 1;
    private int baseCooldownMax = 3;
    private int baseVolMin = 10;
    private int baseVolMax = 200;

    public NoiseTraderAI(double initialCash, int initialStocks, String traderId,
                         StockMarketModel model, OrderBook orderBook, Stock stock) {
        this.traderId = (traderId == null ? "NoiseTrader" : traderId);
        this.orderBook = orderBook;
        this.stock = stock;
        this.account = new UserAccount(initialCash, initialStocks);
    }

    // 由模型每 tick 注入最新命中率
    public void setNoiseSignalQuality(StockMarketModel.NoiseSignalQuality q) {
        this.noiseQuality = q;
    }

    public void setNoiseAdaptiveConfig(StockMarketModel.NoiseAdaptiveConfig cfg) {
        this.adaptiveConfig = cfg;
    }

    public void setAdaptiveEnabled(boolean enabled) {
        this.adaptiveEnabled = enabled;
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

        // [BASELINE] 保底撤單：不論是否啟用自適應，只要掛單離 mid 太遠就偶爾撤掉幾張，避免長時間堆積
        try {
            if (random.nextDouble() < 0.12) {
                cancelFarOrdersBaseline(bestBid, bestAsk);
            }
        } catch (Exception ignore) {}

        // 小額：10~200 股
        // --- 自適應：根據多/空命中率調整頻率/單量/順逆勢/追價/撤單 ---
        StockMarketModel.NoiseSignalQuality q = noiseQuality;
        StockMarketModel.NoiseAdaptiveConfig cfg = adaptiveConfig != null ? adaptiveConfig : StockMarketModel.NoiseAdaptiveConfig.defaults();
        boolean useAdaptive = adaptiveEnabled && cfg.enabled && q != null && q.enabled;
        double longHit = useAdaptive ? q.longHitRate01 : 0.5;
        double shortHit = useAdaptive ? q.shortHitRate01 : 0.5;
        double avgHit = (longHit + shortHit) / 2.0;

        // 決定順勢或逆勢：命中率高→順勢；命中率低→逆勢；中間→隨機
        boolean followTrend;
        if (!useAdaptive) followTrend = random.nextBoolean();
        else if (avgHit >= cfg.followHi) followTrend = true;
        else if (avgHit <= cfg.followLo) followTrend = false;
        else followTrend = random.nextBoolean();

        // 偏向哪一邊：用多/空命中率差做 bias
        double bias = Math.max(-1.0, Math.min(1.0, (longHit - shortHit))); // >0 偏多
        double buyProb = 0.5 + (followTrend ? bias : -bias) * cfg.biasWeight;
        buyProb = Math.max(0.1, Math.min(0.9, buyProb));
        boolean buySide = random.nextDouble() < buyProb;

        // 下單頻率（冷卻）：命中率高→更頻繁；命中率低→更保守
        double freqFactor = useAdaptive ? Math.max(0.7, Math.min(1.6, 0.7 + avgHit)) : 1.0;
        int cdMin = Math.max(1, (int) Math.round(baseCooldownMin / freqFactor));
        int cdMax = Math.max(cdMin, (int) Math.round(baseCooldownMax / freqFactor));

        // 單量：命中率高→放大；命中率低→縮小
        double volScale = useAdaptive ? Math.max(0.6, Math.min(2.0, 0.5 + 1.5 * avgHit)) : 1.0;
        int vol = baseVolMin + random.nextInt(Math.max(1, baseVolMax - baseVolMin + 1));
        vol = (int) Math.max(1, Math.round(vol * volScale));

        // 追價幅度：順勢且命中率高才追價（以 tick 為單位）
        int chaseTicks = 0;
        if (useAdaptive && followTrend && avgHit > cfg.followHi) {
            double span = Math.max(0.05, 1.0 - cfg.followHi);
            double t = (avgHit - cfg.followHi) / span;
            chaseTicks = (int) Math.round(Math.min(cfg.maxChaseTicks, Math.max(0.0, t * cfg.maxChaseTicks)));
        }

        // 市價/限價比例：順勢且命中率高→更多市價吃單；逆勢或命中率低→更多限價掛單（需要撤單）
        double marketProb = useAdaptive ? (followTrend ? (0.35 + 0.6 * avgHit) : (0.15 + 0.3 * avgHit)) : 0.5;
        marketProb = Math.max(cfg.marketProbMin, Math.min(cfg.marketProbMax, marketProb));
        boolean useMarket = random.nextDouble() < marketProb;

        // 撤單：命中率低→更常撤（避免掛著變成倉位累積）
        if (useAdaptive && avgHit < 0.5) {
            tryCancelSomeOrders(cfg, avgHit, bestBid, bestAsk);
        }

        if (buySide) {
            if (bestAsk <= 0) {
                cooldownTicks = 1;
                return;
            }
            double estCost = bestAsk * vol;
            if (account.getAvailableFunds() < estCost) {
                cooldownTicks = Math.max(2, cdMax);
                return;
            }
            if (useMarket) {
                orderBook.marketBuy(this, vol);
            } else {
                // 限價：順勢→侵略（bestAsk+追價tick）；逆勢→保守（bestBid 附近）
                double px = followTrend ? (bestAsk + chaseTicks * orderBook.getTickSize(bestAsk)) : bestBid;
                px = orderBook.adjustPriceToUnit(px);
                Order o = Order.createLimitBuyOrder(px, vol, this);
                orderBook.submitBuyOrder(o, px);
            }
        } else {
            if (bestBid <= 0) {
                cooldownTicks = 1;
                return;
            }
            if (account.getStockInventory() < vol) {
                cooldownTicks = Math.max(2, cdMax);
                return;
            }
            if (useMarket) {
                orderBook.marketSell(this, vol);
            } else {
                // 限價：順勢→侵略（bestBid-追價tick）；逆勢→保守（bestAsk 附近）
                double px = followTrend ? (bestBid - chaseTicks * orderBook.getTickSize(bestBid)) : bestAsk;
                px = orderBook.adjustPriceToUnit(px);
                Order o = Order.createLimitSellOrder(px, vol, this);
                orderBook.submitSellOrder(o, px);
            }
        }

        // 下一次下單延遲 1~3 tick
        cooldownTicks = cdMin + random.nextInt(Math.max(1, cdMax - cdMin + 1));
    }

    // [ADAPT] 撤單策略：命中率越低，撤單越積極（只撤自己的掛單）
    private void tryCancelSomeOrders(StockMarketModel.NoiseAdaptiveConfig cfg, double avgHit, double bestBid, double bestAsk) {
        try {
            // 撤單機率：avgHit=0.5→0.15，avgHit=0.3→0.35
            double cancelProb = Math.max(0.0, Math.min(0.95, cfg.cancelProbBase + (0.5 - avgHit) * cfg.cancelProbSlope));
            if (random.nextDouble() > cancelProb) return;

            double mid = (bestBid > 0 && bestAsk > 0) ? (bestBid + bestAsk) / 2.0 : stock.getPrice();
            if (mid <= 0) return;
            double replaceThreshold = cfg.replaceThBase + (0.5 - avgHit) * cfg.replaceThSlope; // 命中越低，越容易撤

            // 撤 1~3 張
            int toCancel = 1 + random.nextInt(3);
            List<Order> myBuys = orderBook.getBuyOrdersByTraderType(getTraderType());
            List<Order> mySells = orderBook.getSellOrdersByTraderType(getTraderType());

            for (Order ob : myBuys) {
                if (toCancel <= 0) break;
                if (ob == null) continue;
                double diff = (mid - ob.getPrice()) / mid;
                if (diff > replaceThreshold) {
                    if (orderBook.cancelOrder(ob.getId())) toCancel--;
                }
            }
            for (Order os : mySells) {
                if (toCancel <= 0) break;
                if (os == null) continue;
                double diff = (os.getPrice() - mid) / mid;
                if (diff > replaceThreshold) {
                    if (orderBook.cancelOrder(os.getId())) toCancel--;
                }
            }
        } catch (Exception ignore) {}
    }

    // 保底撤單策略：只撤自己的掛單（避免 traderType 比對/多交易者共用 type 時抓錯單）
    private void cancelFarOrdersBaseline(double bestBid, double bestAsk) {
        try {
            double mid = (bestBid > 0 && bestAsk > 0 && bestBid <= bestAsk)
                    ? (bestBid + bestAsk) / 2.0
                    : stock.getPrice();
            if (mid <= 0) return;

            // 硬門檻：離 mid 超過 1.5% 就算太遠（可再視需求參數化）
            double th = 0.015;
            int toCancel = 2;

            List<Order> buys = orderBook.getBuyOrders();
            for (int i = buys.size() - 1; i >= 0 && toCancel > 0; i--) {
                Order o = buys.get(i);
                if (o == null || o.getTrader() != this) continue;
                double px = o.getPrice();
                if (px <= 0) continue;
                double diff = (mid - px) / mid;
                if (diff > th) {
                    if (orderBook.cancelOrder(o.getId())) toCancel--;
                }
            }

            List<Order> sells = orderBook.getSellOrders();
            for (int i = sells.size() - 1; i >= 0 && toCancel > 0; i--) {
                Order o = sells.get(i);
                if (o == null || o.getTrader() != this) continue;
                double px = o.getPrice();
                if (px <= 0) continue;
                double diff = (px - mid) / mid;
                if (diff > th) {
                    if (orderBook.cancelOrder(o.getId())) toCancel--;
                }
            }
        } catch (Exception ignore) {}
    }
}

