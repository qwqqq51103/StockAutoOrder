package AIStrategies;

import UserManagement.UserAccount;
import Core.Order;
import Core.Trader;
import Core.OrderBook;
import Core.Stock;
import StockMainAction.StockMarketSimulation;
import java.util.Random;
import java.util.LinkedList;
import java.util.Queue;
import javax.swing.JOptionPane;
import java.text.DecimalFormat;
import jdk.nashorn.internal.runtime.regexp.joni.Config;

/**
 * AI散戶行為，實現 Trader 接口，自動輸入下單金額
 */
public class RetailInvestorAI implements Trader {

    private StockMarketSimulation simulation;
    private static final Random random = new Random();
    private double buyThreshold = 0.95; // 調低門檻以增加買入機會
    private double sellThreshold = 3.5; // 調低門檻以增加賣出機會
    private boolean ignoreThreshold = false;
    private Queue<Double> priceHistory; // 儲存最近價格數據，用於分析趨勢
    private String traderID; // 散戶的唯一標識符
    private UserAccount account;
    private OrderBook orderBook; // 亦可在 makeDecision 時指定
    private Stock stock;  // 股票實例
    private boolean autoInputOrderAmount = true; // 是否自動輸入訂單金額
    private DecimalFormat decimalFormat = new DecimalFormat("#,##0.00"); // 格式化價格顯示

    // 停損和止盈價格
    private Double stopLossPrice = null;
    private Double takeProfitPrice = null;

    // 風險管理參數
    private final double riskPerTrade = 0.05; // 每次交易風險不超過 5% 資金 (原本註解寫2%，實際數字可調)

    // 短期和長期 SMA (如有用到可自行擴充)
    private int shortSmaPeriod = 5;
    private int longSmaPeriod = 20;

    /* ==== 依技術面計算掛單價的權重（可視需要調整） ==== */
    private double smaWeight = 0.50;   // 價格偏離 SMA 的影響
    private double rsiWeight = 0.30;   // RSI 超買超賣的影響
    private double volatilityWeight = 0.20;   // 波動度的影響

    /* 偏移上限（%）；避免掛到離現價過遠的位置 */
    private double maxOffsetRatio = 0.08;   // 最多 ±8%

    /**
     * 構造函數
     *
     * @param initialCash 初始現金
     * @param traderID 散戶的唯一標識符
     * @param simulation 模擬實例
     */
    public RetailInvestorAI(double initialCash, String traderID, StockMarketSimulation simulation) {
        this.traderID = traderID;
        this.simulation = simulation;

        // 初始化價格歷史
        this.priceHistory = new LinkedList<>();

        // 設置隨機的買入和賣出門檻
        this.buyThreshold = 0.01 + random.nextDouble() * 0.05; // 1% 到 6%
        this.sellThreshold = 0.03 + random.nextDouble() * 0.09; // 3% 到 12%

        // 隨機決定該散戶是否忽略門檻，30% 機率
        this.ignoreThreshold = random.nextDouble() < 0.3;

        // 初始化帳戶
        this.account = new UserAccount(initialCash, 0);
    }

    // ========== Trader 介面實作 ==========
    @Override
    public UserAccount getAccount() {
        return account;
    }

    @Override
    public String getTraderType() {
        return "RETAIL_INVESTOR";
    }

    @Override
    public void updateAfterTransaction(String type, int volume, double price) {
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            // 限價單買入
            account.incrementStocks(volume);
            // System.out.println(String.format("【散戶-限價買入更新】買入 %d 股，價格 %.2f", volume, price));
        } else if ("sell".equals(type)) {
            // 限價單賣出
            account.incrementFunds(transactionAmount);
            // System.out.println(String.format("【散戶-限價賣出更新】賣出 %d 股，價格 %.2f", volume, price));
        }

        // 更新 GUI
        simulation.updateLabels();
    }

    @Override
    public void updateAverageCostPrice(String type, int volume, double price) {
        // 市價單更新，若需更進階的平均成本處理，可在此加上
        double transactionAmount = price * volume;

        if ("buy".equals(type)) {
            // System.out.println(String.format("【散戶-市價買入更新】買入 %d 股，價格 %.2f", volume, price));
            account.incrementStocks(volume);
        } else if ("sell".equals(type)) {
            // System.out.println(String.format("【散戶-市價賣出更新】賣出 %d 股，價格 %.2f", volume, price));
            account.incrementFunds(transactionAmount);
        }

        simulation.updateLabels();
    }

    // ========== 核心行為決策 ==========
    /**
     * 散戶行為決策
     *
     * @param stock 股票實例
     * @param orderBook 訂單簿實例
     * @param simulation 模擬實例
     */
    public void makeDecision(Stock stock, OrderBook orderBook, StockMarketSimulation simulation) {
        this.orderBook = orderBook; // 如需使用私有函式下單時需有此參考
        double availableFunds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double rsi = simulation.getMarketAnalyzer().getRSI();
        double volatility = simulation.getMarketAnalyzer().getVolatility();
        StringBuilder decisionReason = new StringBuilder();

        // 每次決策時，動態調整買/賣門檻
        double dynamicBuyThreshold = buyThreshold * (0.8 + 0.4 * random.nextDouble());
        double dynamicSellThreshold = sellThreshold * (0.8 + 0.4 * random.nextDouble());

        if (this.orderBook == null) {
            this.orderBook = orderBook; // 確保每次執行時有訂單簿
        }

        if (this.stock == null) {
            this.stock = stock; // 確保 stock 不為 null
        }

        // 可能改變是否忽略門檻
        if (random.nextDouble() < 0.1) {
            ignoreThreshold = !ignoreThreshold;
        }

        if (!Double.isNaN(sma)) {
            double priceDifferenceRatio = (currentPrice - sma) / sma;
            priceDifferenceRatio = Math.max(-0.5, Math.min(priceDifferenceRatio, 0.5));
            double actionProbability = random.nextDouble();

            // 停損 / 止盈
            handleStopLossTakeProfit(currentPrice, decisionReason);

            // 忽略門檻
            if (this.shouldIgnoreThreshold()) {
                if (availableFunds >= currentPrice && priceDifferenceRatio < 0 && actionProbability > 0.3) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                    decisionReason.append("【忽略門檻】隨機決定買入 ").append(buyAmount).append(" 股。");

                    if (random.nextBoolean()) {
                        int actualBuy = 市價買入操作(buyAmount);
                        if (actualBuy > 0) {
                            decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                            setStopLossAndTakeProfit(currentPrice, volatility);
                        } else {
                            //decisionReason.append("【失敗】市價買入。資金或掛單量不足。\n");
                        }
                    } else {
                        double buyLimitPrice = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                        int actualBuy = 限價買入操作(buyAmount, buyLimitPrice);
                        if (actualBuy > 0) {
                            decisionReason.append("【成功】限價買入 " + actualBuy + " 股，價格 " + decimalFormat.format(buyLimitPrice) + "。\n");
                            setStopLossAndTakeProfit(currentPrice, volatility);
                        } else {
                            //decisionReason.append("【失敗】限價買入。資金或賣單量不足。\n");
                        }
                    }
                } else if (getAccumulatedStocks() > 0 && priceDifferenceRatio > 0 && actionProbability > 0.3) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                    decisionReason.append("【忽略門檻】隨機決定賣出 ").append(sellAmount).append(" 股。");

                    if (random.nextBoolean()) {
                        int actualSell = 市價賣出操作(sellAmount);
                        if (actualSell > 0) {
                            decisionReason.append("【成功】市價賣出 ").append(actualSell).append(" 股。\n");
                        } else {
                            //decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                        }
                    } else {
                        double sellLimitPrice = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                        int actualSell = 限價賣出操作(sellAmount, sellLimitPrice);
                        if (actualSell > 0) {
                            decisionReason.append("【成功】限價賣出 ").append(actualSell).append(" 股，價格 " + decimalFormat.format(sellLimitPrice) + "。\n");
                        } else {
                            //decisionReason.append("【失敗】限價賣出。持股或買單量不足。\n");
                        }
                    }

                    stopLossPrice = null;
                    takeProfitPrice = null;
                }
            } else {
                // 遵循門檻
                if (priceDifferenceRatio < -dynamicBuyThreshold && availableFunds >= currentPrice && actionProbability > 0.2) {
                    int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                    decisionReason.append(String.format("【遵循門檻】股價低於 SMA 的 %.2f%% 門檻，買入 %d 股。",
                            dynamicBuyThreshold * 100, buyAmount));

                    if (random.nextBoolean()) {
                        int actualBuy = 市價買入操作(buyAmount);
                        if (actualBuy > 0) {
                            decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                            setStopLossAndTakeProfit(currentPrice, volatility);
                        } else {
                            //decisionReason.append("【失敗】市價買入。資金或掛單量不足。\n");
                        }
                    } else {
                        double buyLimitPrice = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                        int actualBuy = 限價買入操作(buyAmount, buyLimitPrice);
                        if (actualBuy > 0) {
                            decisionReason.append("【成功】限價買入 " + actualBuy + " 股，價格 " + decimalFormat.format(buyLimitPrice) + "。\n");
                            setStopLossAndTakeProfit(currentPrice, volatility);
                        } else {
                            //decisionReason.append("【失敗】限價買入。資金或賣單量不足。\n");
                        }
                    }
                } else if (priceDifferenceRatio > dynamicSellThreshold && getAccumulatedStocks() > 0 && actionProbability > 0.2) {
                    int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                    decisionReason.append(String.format("【遵循門檻】股價高於 SMA 的 %.2f%% 門檻，賣出 %d 股。",
                            dynamicSellThreshold * 100, sellAmount));

                    if (random.nextBoolean()) {
                        int actualSell = 市價賣出操作(sellAmount);
                        if (actualSell > 0) {
                            decisionReason.append("【成功】市價賣出 " + actualSell + " 股。\n");
                        } else {
                            //decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                        }
                    } else {
                        double sellLimitPrice = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                        int actualSell = 限價賣出操作(sellAmount, sellLimitPrice);
                        if (actualSell > 0) {
                            decisionReason.append("【成功】限價賣出 " + actualSell + " 股，價格 " + decimalFormat.format(sellLimitPrice) + "。\n");
                        } else {
                            //decisionReason.append("【失敗】限價賣出。持股或買單量不足。\n");
                        }
                    }

                    stopLossPrice = null;
                    takeProfitPrice = null;
                } else if (random.nextDouble() < 0.05) {
                    executeRandomTransaction(availableFunds, currentPrice, decisionReason, stock);
                }
            }

            // RSI 判斷
            if (rsi < 30 && availableFunds >= currentPrice) {
                int buyAmount = calculateTransactionVolume(availableFunds, currentPrice, volatility);
                decisionReason.append("【RSI < 30】買入訊號，嘗試買入 ").append(buyAmount).append(" 股。");
                int actualBuy = 市價買入操作(buyAmount);
                if (actualBuy > 0) {
                    decisionReason.append("【成功】市價買入 " + actualBuy + " 股。\n");
                    setStopLossAndTakeProfit(currentPrice, volatility);
                } else {
                    //decisionReason.append("【失敗】市價買入。資金或掛單量不足。\n");
                }
            } else if (rsi > 70 && getAccumulatedStocks() > 0) {
                int sellAmount = calculateSellVolume(priceDifferenceRatio, volatility);
                decisionReason.append("【RSI > 70】賣出訊號，嘗試賣出 ").append(sellAmount).append(" 股。");
                int actualSell = 市價賣出操作(sellAmount);
                if (actualSell > 0) {
                    decisionReason.append("【成功】市價賣出 ").append(actualSell).append(" 股。\n");
                } else {
                    //decisionReason.append("【失敗】市價賣出。持股或買單量不足。\n");
                }
                stopLossPrice = null;
                takeProfitPrice = null;
            }

            simulation.updateInfoTextArea(decisionReason.toString());
        } else {
            simulation.updateInfoTextArea("【散戶】尚無法計算 SMA，暫無決策。\n");
        }
    }

    // ========== 停損/止盈處理 ==========
    private void handleStopLossTakeProfit(double currentPrice, StringBuilder decisionReason) {
        // 停損
        if (stopLossPrice != null && currentPrice <= stopLossPrice) {
            int sellAll = getAccumulatedStocks();
            if (sellAll > 0) {
                int actualSell = 市價賣出操作(sellAll);
                if (actualSell > 0) {
                    decisionReason.append("【停損觸發】市價賣出全部 ").append(actualSell).append(" 股。\n");
                } else {
                    //decisionReason.append("【停損觸發】失敗，持股或買單量不足。\n");
                }
            }
            stopLossPrice = null;
            takeProfitPrice = null;
        }
        // 止盈
        if (takeProfitPrice != null && currentPrice >= takeProfitPrice) {
            int sellAll = getAccumulatedStocks();
            if (sellAll > 0) {
                int actualSell = 市價賣出操作(sellAll);
                if (actualSell > 0) {
                    decisionReason.append("【止盈觸發】市價賣出全部 ").append(actualSell).append(" 股。\n");
                } else {
                    //decisionReason.append("【止盈觸發】失敗，持股或買單量不足。\n");
                }
            }
            stopLossPrice = null;
            takeProfitPrice = null;
        }
    }

    // ========== 隨機操作 ==========
    /**
     * 隨機交易 - 增強版，加入不同訂單類型
     */
    private void executeRandomTransaction(double availableFunds, double currentPrice, StringBuilder decisionReason, Stock stock) {
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double rsi = simulation.getMarketAnalyzer().getRSI();
        double volatility = simulation.getMarketAnalyzer().getVolatility();

        // 選擇交易類型
        String txType = random.nextDouble() < 0.5 ? "buy" : "sell";

        if ("buy".equals(txType) && availableFunds >= currentPrice) {
            int buyAmount = random.nextInt(50) + 1; // 1~50 股

            // 選擇訂單類型
            double orderTypeRandom = random.nextDouble();
            if (orderTypeRandom < 0.4) {
                // 40% 機率市價單
                int actualBuy = 市價買入操作(buyAmount);
                if (actualBuy > 0) {
                    decisionReason.append("【隨機操作】市價買入 ").append(actualBuy).append(" 股。\n");
                    setStopLossAndTakeProfit(currentPrice, simulation.getMarketAnalyzer().getVolatility());
                }
            } else if (orderTypeRandom < 0.9) {
                // 50% 機率限價單
                double buyLimitPrice = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                int actualBuy = 限價買入操作(buyAmount, buyLimitPrice);
                if (actualBuy > 0) {
                    decisionReason.append("【隨機操作】限價買入 ").append(actualBuy).append(" 股，價格 " + decimalFormat.format(buyLimitPrice) + "。\n");
                    setStopLossAndTakeProfit(currentPrice, simulation.getMarketAnalyzer().getVolatility());
                }
            } else {
                // 10% 機率FOK單
                double buyPrice = computeBuyLimitPrice(currentPrice, sma, rsi, volatility);
                boolean success = orderBook.submitFokBuyOrder(buyPrice, buyAmount, this);
                if (success) {
                    decisionReason.append("【隨機操作】FOK買入 ").append(buyAmount).append(" 股，價格 " + decimalFormat.format(buyPrice) + "。\n");
                    setStopLossAndTakeProfit(currentPrice, simulation.getMarketAnalyzer().getVolatility());
                } else {
                    decisionReason.append("【隨機操作】FOK買入失敗，無法完全滿足。\n");
                }
            }
        } else if (getAccumulatedStocks() > 0) {
            int sellAmount = random.nextInt(getAccumulatedStocks()) + 1;

            // 選擇訂單類型
            double orderTypeRandom = random.nextDouble();
            if (orderTypeRandom < 0.4) {
                // 40% 機率市價單
                int actualSell = 市價賣出操作(sellAmount);
                if (actualSell > 0) {
                    decisionReason.append("【隨機操作】市價賣出 ").append(actualSell).append(" 股。\n");
                }
            } else if (orderTypeRandom < 0.9) {
                // 50% 機率限價單
                double sellLimitPrice = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                int actualSell = 限價賣出操作(sellAmount, sellLimitPrice);
                if (actualSell > 0) {
                    decisionReason.append("【隨機操作】限價賣出 ").append(actualSell).append(" 股，價格 " + decimalFormat.format(sellLimitPrice) + "。\n");
                }
            } else {
                // 10% 機率FOK單
                double sellPrice = computeSellLimitPrice(currentPrice, sma, rsi, volatility);
                boolean success = orderBook.submitFokSellOrder(sellPrice, sellAmount, this);
                if (success) {
                    decisionReason.append("【隨機操作】FOK賣出 ").append(sellAmount).append(" 股，價格 " + decimalFormat.format(sellPrice) + "。\n");
                } else {
                    decisionReason.append("【隨機操作】FOK賣出失敗，無法完全滿足。\n");
                }
            }

            stopLossPrice = null;
            takeProfitPrice = null;
        }
    }

    /**
     * 根據技術指標回傳「買單」理想掛價
     */
    private double computeBuyLimitPrice(double currentPrice, double sma,
            double rsi, double volatility) {

        // 1. 價格偏離：低於 SMA → 想買，取負值
        double smaOffset = (sma == 0) ? 0
                : (currentPrice - sma) / sma;          // 正：高於均線，負：低於均線
        smaOffset = -smaOffset;                      // 低於均線 => 負偏移 → 買單掛更高？反向

        // 2. RSI：RSI < 50 偏向買方；用 (50 - RSI) / 100 作為偏移
        double rsiOffset = (Double.isNaN(rsi)) ? 0
                : (50.0 - rsi) / 100.0;                // RSI 30 → +0.20；RSI 70 → -0.20

        // 3. 波動度：波動越大 → 掛單價離現價遠一點（保守）
        double volOffset = -volatility * 0.5;        // volatility ≈ 0.02 → -0.01

        // 4. 加權
        double totalOffset = smaOffset * smaWeight
                + rsiOffset * rsiWeight
                + volOffset * volatilityWeight;

        // 5. 限制在 ±maxOffsetRatio
        totalOffset = Math.max(-maxOffsetRatio,
                Math.min(totalOffset, maxOffsetRatio));

        // 6. 計算掛單價並回傳（向下取價差）
        double limitPrice = currentPrice * (1.0 + totalOffset);
        return orderBook.adjustPriceToUnit(limitPrice);
    }

    /**
     * 根據技術指標回傳「賣單」理想掛價
     */
    private double computeSellLimitPrice(double currentPrice, double sma,
            double rsi, double volatility) {

        double smaOffset = (sma == 0) ? 0
                : (currentPrice - sma) / sma;          // 高於均線 → 正值（想賣）

        double rsiOffset = (Double.isNaN(rsi)) ? 0
                : (rsi - 50.0) / 100.0;                // RSI 70 → +0.20；RSI 30 → -0.20

        double volOffset = volatility * 0.5;         // 波動大 → 再遠離一點

        double totalOffset = smaOffset * smaWeight
                + rsiOffset * rsiWeight
                + volOffset * volatilityWeight;

        totalOffset = Math.max(-maxOffsetRatio,
                Math.min(totalOffset, maxOffsetRatio));

        double limitPrice = currentPrice * (1.0 + totalOffset);
        return orderBook.adjustPriceToUnit(limitPrice);
    }

    // ========== 實際掛單操作 (成功/失敗印出) ==========
    /**
     * 修改散戶AI類的市價買入操作方法 - 使用新的市價單API
     */
    private int 市價買入操作(int buyAmount) {
        double price = stock.getPrice();
        double totalCost = price * buyAmount;
        double funds = account.getAvailableFunds();

        if (stock == null) {
            return 0;
        }

        if (orderBook == null) {
            return 0;
        }

        if (funds < totalCost) {
            // 資金不足
            return 0;
        }

        // 使用新的市價買單API
        orderBook.marketBuy(this, buyAmount);
        return buyAmount;
    }

    /**
     * 修改散戶AI類的市價賣出操作方法 - 使用新的市價單API
     */
    private int 市價賣出操作(int sellAmount) {
        int hold = getAccumulatedStocks();
        if (hold < sellAmount) {
            // 持股不足
            return 0;
        }

        // 使用新的市價賣單API
        orderBook.marketSell(this, sellAmount);
        return sellAmount;
    }

    /**
     * 限價買入操作 - 增強版，支援多種訂單類型
     *
     * @param amount 欲買股數
     * @param suggestedPrice 系統計算的建議價格
     * @return 實際買入股數 (0=失敗)
     */
    private int 限價買入操作(int amount, double suggestedPrice) {
        double funds = account.getAvailableFunds();
        double currentPrice = stock.getPrice();

        // 處理價格輸入 - 使用智能決策或手動輸入
        double finalPrice = 處理買入價格輸入(amount, suggestedPrice, currentPrice, funds);

        if (finalPrice <= 0) {
            return 0;
        }

        // 檢查資金
        double totalCost = finalPrice * amount;
        if (funds < totalCost) {
            if (!autoInputOrderAmount) {
                showError("資金不足，交易取消");
            }
            return 0;
        }

        // 決定訂單類型 (根據隨機性和當前模式)
        if (random.nextDouble() < 0.1) {
            // 10% 機率使用FOK訂單
            boolean success = orderBook.submitFokBuyOrder(finalPrice, amount, this);
            if (success) {
                System.out.println("提交FOK買單成功: " + amount + "股，價格 " + finalPrice);
                return amount;
            } else {
                System.out.println("提交FOK買單失敗: 無法完全滿足");
                return 0;
            }
        } else {
            // 90% 機率使用普通限價單
            Order buyOrder = Order.createLimitBuyOrder(finalPrice, amount, this);
            orderBook.submitBuyOrder(buyOrder, finalPrice);
            return amount;
        }
    }

    /**
     * 處理買入價格輸入 - 增強版，讓AI散戶可以根據市場情況自由決定價格
     *
     * @param amount 買入數量
     * @param suggestedPrice 建議價格
     * @param currentPrice 當前市價
     * @param funds 可用資金
     * @return 最終決定的價格
     */
    private double 處理買入價格輸入(int amount, double suggestedPrice, double currentPrice, double funds) {
        if (!autoInputOrderAmount) {
            // 手動模式邏輯保持不變
            String message = String.format(
                    "散戶 %s 限價買入\n欲買入數量: %d 股\n建議價格: %s\n目前市價: %s\n可用資金: %s\n請輸入買入價格 (或直接確認使用建議價格):",
                    traderID, amount,
                    decimalFormat.format(suggestedPrice),
                    decimalFormat.format(currentPrice),
                    decimalFormat.format(funds)
            );

            String inputPrice = JOptionPane.showInputDialog(null, message, decimalFormat.format(suggestedPrice));

            if (inputPrice == null) {
                return -1; // 取消
            }

            try {
                if (!inputPrice.trim().isEmpty()) {
                    inputPrice = inputPrice.replace(",", "");
                    double parsed = Double.parseDouble(inputPrice);
                    return orderBook.adjustPriceToUnit(parsed);
                }
            } catch (NumberFormatException e) {
                showError("價格格式錯誤，交易取消");
                return -1;
            }

            return orderBook.adjustPriceToUnit(suggestedPrice);
        } else {
            // 自動模式 - 增強AI決策能力
            return 智能決定買入價格(suggestedPrice, currentPrice);
        }
    }

    /**
     * 智能決定買入價格 考慮多種因素來確定一個合理的買入價格
     *
     * @param suggestedPrice 系統建議價格
     * @param currentPrice 當前市價
     * @return 智能決定的買入價格
     */
    private double 智能決定買入價格(double suggestedPrice, double currentPrice) {
        // 獲取市場分析數據
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double rsi = simulation.getMarketAnalyzer().getRSI();
        double volatility = simulation.getMarketAnalyzer().getVolatility();

        // 1. 基於市場狀況的決策因子
        double marketConditionFactor = 0.0;

        // 1.1 RSI因子 (RSI低時更願意買入，但價格可能會更低)
        double rsiFactor = 0.0;
        if (!Double.isNaN(rsi)) {
            // RSI < 30 (超賣) - 願意更接近市價買入
            // RSI > 70 (超買) - 應該出價更低以等待回調
            if (rsi < 30) {
                rsiFactor = 0.02; // 願意付出接近市價
            } else if (rsi > 70) {
                rsiFactor = -0.05; // 降低買入價以等待回調
            } else {
                // 30-70 之間線性映射
                rsiFactor = 0.02 - ((rsi - 30) / 40.0) * 0.07;
            }
        }

        // 1.2 波動率因子 (高波動應該更謹慎)
        double volatilityFactor = Math.min(-0.01 * volatility * 100, -0.001);

        // 1.3 價格與SMA的關係 (低於SMA時更願意買入)
        double smaDiffFactor = 0.0;
        if (!Double.isNaN(sma) && sma > 0) {
            double priceDiffPercent = (currentPrice - sma) / sma;
            if (priceDiffPercent < -0.05) {
                // 已經低於SMA 5%，可能是好買點
                smaDiffFactor = 0.01;
            } else if (priceDiffPercent > 0.05) {
                // 高於SMA 5%，應該等待下跌
                smaDiffFactor = -0.02;
            } else {
                // 接近SMA，適度調整
                smaDiffFactor = -priceDiffPercent * 0.2;
            }
        }

        // 2. 個性化因子 (每個散戶有不同特性)
        double personalityFactor = 0.0;

        // 2.1 隨機性 (模擬散戶常有的不理性)
        double randomFactor = (random.nextDouble() - 0.5) * 0.04;

        // 2.2 風險偏好 (可以根據traderID固定或其他特徵來決定)
        double riskAppetite = (traderID.hashCode() % 10) / 100.0; // -0.05 ~ 0.04

        // 2.3 急迫感 (想要快速成交還是願意等待更好價格)
        double urgencyFactor = 0.0;
        if (ignoreThreshold) {
            // 忽略閾值的交易者通常更急迫
            urgencyFactor = 0.02;
        } else {
            // 遵守閾值的通常更有耐心
            urgencyFactor = -0.01;
        }

        // 3. 市場技術分析建議價與當前價的差異
        double suggestedDiff = (suggestedPrice - currentPrice) / currentPrice;
        double technicalFactor = suggestedDiff * 0.5; // 採納50%的技術建議

        // 4. 綜合所有因子
        marketConditionFactor = rsiFactor + volatilityFactor + smaDiffFactor;
        personalityFactor = randomFactor + riskAppetite + urgencyFactor;

        // 計算最終價格調整因子 (各因子權重可調)
        double priceFactor = marketConditionFactor * 0.5 + personalityFactor * 0.3 + technicalFactor * 0.2;

        // 限制調整範圍在 -8% ~ +3% 之間 (買入價通常不會高太多)
        priceFactor = Math.max(-0.08, Math.min(priceFactor, 0.03));

        // 計算最終價格
        double finalPrice = currentPrice * (1 + priceFactor);

        // 調整為市場最小單位並返回
        double adjustedPrice = orderBook.adjustPriceToUnit(finalPrice);

        // 輸出決策過程供調試
        System.out.println(String.format(
                "AI買入決策過程: 市價=%.2f, 建議價=%.2f, RSI=%.2f, VOL=%.4f, "
                + "市場因子=%.4f, 個性因子=%.4f, 技術因子=%.4f, 最終調整=%.2f%%, 決定價=%.2f",
                currentPrice, suggestedPrice, rsi, volatility,
                marketConditionFactor, personalityFactor, technicalFactor,
                priceFactor * 100, adjustedPrice
        ));

        return adjustedPrice;
    }

    /**
     * 限價賣出操作 - 增強版，支援多種訂單類型
     *
     * @param amount 欲賣股數
     * @param suggestedPrice 系統計算的建議價格
     * @return 實際賣出股數 (0=失敗)
     */
    private int 限價賣出操作(int amount, double suggestedPrice) {
        int hold = getAccumulatedStocks();
        double currentPrice = stock.getPrice();

        // 檢查持股是否足夠
        if (hold < amount) {
            if (!autoInputOrderAmount) {
                showError("持股不足，交易取消");
            }
            return 0;
        }

        // 處理價格輸入 - 使用智能決策或手動輸入
        double finalPrice = 處理賣出價格輸入(amount, suggestedPrice, currentPrice, hold);

        if (finalPrice <= 0) {
            return 0; // 使用者取消或輸入錯誤
        }

        // 決定訂單類型 (根據隨機性和當前模式)
        if (random.nextDouble() < 0.1) {
            // 10% 機率使用FOK訂單
            boolean success = orderBook.submitFokSellOrder(finalPrice, amount, this);
            if (success) {
                System.out.println("提交FOK賣單成功: " + amount + "股，價格 " + finalPrice);
                return amount;
            } else {
                System.out.println("提交FOK賣單失敗: 無法完全滿足");
                return 0;
            }
        } else {
            // 90% 機率使用普通限價單
            Order sellOrder = Order.createLimitSellOrder(finalPrice, amount, this);
            orderBook.submitSellOrder(sellOrder, finalPrice);
            return amount;
        }
    }

    /**
     * 處理賣出價格輸入 - 增強版，讓AI散戶可以根據市場情況自由決定價格
     *
     * @param amount 賣出數量
     * @param suggestedPrice 建議價格
     * @param currentPrice 當前市價
     * @param hold 持有股數
     * @return 最終決定的價格
     */
    private double 處理賣出價格輸入(int amount, double suggestedPrice, double currentPrice, int hold) {
        if (!autoInputOrderAmount) {
            // 手動模式邏輯保持不變
            String message = String.format(
                    "散戶 %s 限價賣出\n欲賣出數量: %d 股\n建議價格: %s\n目前市價: %s\n持有股數: %d\n請輸入賣出價格 (或直接確認使用建議價格):",
                    traderID, amount,
                    decimalFormat.format(suggestedPrice),
                    decimalFormat.format(currentPrice),
                    hold
            );

            System.out.println("AI限價賣出操作：" + message);

            String inputPrice = JOptionPane.showInputDialog(null, message, decimalFormat.format(suggestedPrice));

            if (inputPrice == null) {
                return -1; // 表示使用者取消
            }

            try {
                if (!inputPrice.trim().isEmpty()) {
                    inputPrice = inputPrice.replace(",", "");
                    double parsed = Double.parseDouble(inputPrice);
                    return orderBook.adjustPriceToUnit(parsed);
                }
            } catch (NumberFormatException e) {
                showError("價格格式錯誤，交易取消");
                return -1;
            }

            return orderBook.adjustPriceToUnit(suggestedPrice);
        } else {
            // 自動模式 - 增強AI決策能力
            return 智能決定賣出價格(suggestedPrice, currentPrice);
        }
    }

    /**
     * 智能決定賣出價格 考慮多種因素來確定一個合理的賣出價格
     *
     * @param suggestedPrice 系統建議價格
     * @param currentPrice 當前市價
     * @return 智能決定的賣出價格
     */
    private double 智能決定賣出價格(double suggestedPrice, double currentPrice) {
        // 獲取市場分析數據
        double sma = simulation.getMarketAnalyzer().calculateSMA();
        double rsi = simulation.getMarketAnalyzer().getRSI();
        double volatility = simulation.getMarketAnalyzer().getVolatility();

        // 1. 基於市場狀況的決策因子
        double marketConditionFactor = 0.0;

        // 1.1 RSI因子 (RSI高時更願意賣出，但希望價格更高)
        double rsiFactor = 0.0;
        if (!Double.isNaN(rsi)) {
            // RSI > 70 (超買) - 市場可能會繼續上漲，要求更高價格
            // RSI < 30 (超賣) - 市場可能還會下跌，應盡快賣出
            if (rsi > 70) {
                rsiFactor = 0.03; // 要求更高價格
            } else if (rsi < 30) {
                rsiFactor = -0.02; // 接受更低價格以確保成交
            } else {
                // 30-70 之間線性映射
                rsiFactor = -0.02 + ((rsi - 30) / 40.0) * 0.05;
            }
        }

        // 1.2 波動率因子 (高波動時要求風險溢價)
        double volatilityFactor = Math.min(0.02 * volatility * 100, 0.05);

        // 1.3 價格與SMA的關係 (高於SMA時更願意賣出)
        double smaDiffFactor = 0.0;
        if (!Double.isNaN(sma) && sma > 0) {
            double priceDiffPercent = (currentPrice - sma) / sma;
            if (priceDiffPercent > 0.05) {
                // 已經高於SMA 5%，可能是好賣點
                smaDiffFactor = 0.02;
            } else if (priceDiffPercent < -0.05) {
                // 低於SMA 5%，應該等待反彈
                smaDiffFactor = 0.04;
            } else {
                // 接近SMA，適度調整
                smaDiffFactor = priceDiffPercent * 0.3;
            }
        }

        // 2. 個性化因子 (每個散戶有不同特性)
        double personalityFactor = 0.0;

        // 2.1 隨機性 (模擬散戶常有的不理性)
        double randomFactor = (random.nextDouble() - 0.3) * 0.05; // 偏向略高的隨機值

        // 2.2 風險偏好 (可以根據traderID固定或其他特徵來決定)
        double riskAppetite = (traderID.hashCode() % 10) / 100.0; // -0.05 ~ 0.04

        // 2.3 急迫感 (想要快速成交還是願意等待更好價格)
        double urgencyFactor = 0.0;
        if (ignoreThreshold) {
            // 忽略閾值的交易者通常更急迫
            urgencyFactor = -0.01;
        } else {
            // 遵守閾值的通常更有耐心
            urgencyFactor = 0.02;
        }

        // 3. 市場技術分析建議價與當前價的差異
        double suggestedDiff = (suggestedPrice - currentPrice) / currentPrice;
        double technicalFactor = suggestedDiff * 0.6; // 採納60%的技術建議

        // 4. 綜合所有因子
        marketConditionFactor = rsiFactor + volatilityFactor + smaDiffFactor;
        personalityFactor = randomFactor + riskAppetite + urgencyFactor;

        // 計算最終價格調整因子 (各因子權重可調)
        double priceFactor = marketConditionFactor * 0.45 + personalityFactor * 0.3 + technicalFactor * 0.25;

        // 限制調整範圍在 -3% ~ +8% 之間 (賣出價通常不會低太多)
        priceFactor = Math.max(-0.03, Math.min(priceFactor, 0.08));

        // 計算最終價格
        double finalPrice = currentPrice * (1 + priceFactor);

        // 調整為市場最小單位並返回
        double adjustedPrice = orderBook.adjustPriceToUnit(finalPrice);

        // 輸出決策過程供調試
        System.out.println(String.format(
                "AI賣出決策過程: 市價=%.2f, 建議價=%.2f, RSI=%.2f, VOL=%.4f, "
                + "市場因子=%.4f, 個性因子=%.4f, 技術因子=%.4f, 最終調整=%.2f%%, 決定價=%.2f",
                currentPrice, suggestedPrice, rsi, volatility,
                marketConditionFactor, personalityFactor, technicalFactor,
                priceFactor * 100, adjustedPrice
        ));

        return adjustedPrice;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "錯誤", JOptionPane.ERROR_MESSAGE);
    }

    // ========== 工具/輔助函數 ==========
    /**
     * 計算交易量，根據波動性調整頭寸大小
     */
    private int calculateTransactionVolume(double availableFunds, double currentPrice, double volatility) {
        // 波動性高 -> 減少頭寸
        double positionSize = (availableFunds / currentPrice) * (1 / (1 + volatility));
        positionSize = positionSize * riskPerTrade;
        return (int) Math.max(1, positionSize);
    }

    /**
     * 計算賣出量，根據波動性調整頭寸大小
     */
    private int calculateSellVolume(double priceDifferenceRatio, double volatility) {
        double positionSize = getAccumulatedStocks() * (0.1 + 0.4 * random.nextDouble()) * (1 / (1 + volatility));
        return (int) Math.max(1, positionSize);
    }

    /**
     * 設置停損 & 止盈價格
     */
    private void setStopLossAndTakeProfit(double currentPrice, double volatility) {
        // 停損: 95%
        stopLossPrice = currentPrice * 0.95;
        // 止盈: 150%
        takeProfitPrice = currentPrice * 1.5;
    }

    /**
     * 是否忽略門檻
     */
    public boolean shouldIgnoreThreshold() {
        return ignoreThreshold;
    }

    /**
     * 獲取散戶持股
     */
    public int getAccumulatedStocks() {
        return account.getStockInventory();
    }

    /**
     * 獲取散戶現金
     */
    public double getCash() {
        return account.getAvailableFunds();
    }

    /**
     * 散戶 ID
     */
    public String getTraderID() {
        return traderID;
    }

    /**
     * 買入門檻
     */
    public double getBuyThreshold() {
        return buyThreshold;
    }

    /**
     * 賣出門檻
     */
    public double getSellThreshold() {
        return sellThreshold;
    }

    /**
     * 動量交易 (若需要可擴充)
     */
    private int calculateMomentumVolume(double volatility) {
        return (int) (500 * volatility * (0.8 + random.nextDouble() * 0.4));
    }
}
