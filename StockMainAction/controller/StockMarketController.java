package StockMainAction.controller;

import StockMainAction.MatchingEnginePanel;
import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Order;
import StockMainAction.model.core.PriceAlert;
import StockMainAction.view.ControlView;
import StockMainAction.view.components.PriceAlertPanel;
import StockMainAction.view.components.QuickTradePanel;
import StockMainAction.view.MainView;
import StockMainAction.view.OrderViewer;
import java.util.AbstractMap.SimpleEntry;
import StockMainAction.util.logging.MarketLogger;
import StockMainAction.view.components.PersonalStatsPanel;
import StockMainAction.model.core.PersonalStatistics;
import StockMainAction.model.core.QuickTradeConfig;
import StockMainAction.model.core.Transaction;
import StockMainAction.model.core.ExecutionResult;
import StockMainAction.model.core.TradeExecuted;
import StockMainAction.controller.trading.PersonalTradeCoordinator;
import StockMainAction.controller.trading.TradeOutcome;
import StockMainAction.controller.trading.TradeOutcomeStatus;
import StockMainAction.model.user.UserAccount;
import StockMainAction.model.core.Trader;
import StockMainAction.view.TransactionHistoryViewer;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 股票市場控制器 - 負責連接模型與視圖 作為MVC架構中的Controller組件
 */
public class StockMarketController implements StockMarketModel.ModelListener, PriceAlertPanel.PriceAlertPanelListener, QuickTradePanel.QuickTradePanelListener {

    private StockMarketModel model;
    private MainView mainView;
    private ControlView controlView;
    private PriceAlertManager priceAlertManager;
    // Optional 取代裸 null，消除全區散落的 if (personalStatsManager != null) 檢查
    private Optional<PersonalStatisticsManager> personalStatsMgr = Optional.empty();
    private TransactionHistoryViewer transactionHistoryViewer;
    private static final MarketLogger logger = MarketLogger.getInstance();

    // 新增快捷交易相關屬性
    private QuickTradePanel quickTradePanel;
    private QuickTradeManager quickTradeManager;
    private final PersonalTradeCoordinator tradeCoordinator;

    // 市場介入背景執行緒池：daemon thread，確保 JVM 退出時不會被阻塞
    // 使用 newCachedThreadPool 支援多次同時介入，但實務上建議一次一個
    private final ExecutorService interventionExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("MarketIntervention");
        return t;
    });

    // 初始資金配置（用於損益計算）—統一由模型提供
    public final double initialRetailCash;
    private final double initialMainForceCash;
    private final double initialPersonalCash;

    /**
     * 構造函數（修正版）
     */
    public StockMarketController(StockMarketModel model, MainView mainView, ControlView controlView) {
        this.model = model;
        this.mainView = mainView;
        this.controlView = controlView;
        this.tradeCoordinator = new PersonalTradeCoordinator(model);

        // 將模型注入主視圖，供工具列事件直接呼叫模型參數
        try {
            this.mainView.setModel(model);
        } catch (Throwable t) {
            logger.warn("mainView.setModel 初始化失敗（不影響主流程）：" + t.getMessage(), "CONTROLLER_INIT");
        }

        //修正：只初始化一次 PriceAlertManager
        this.priceAlertManager = new PriceAlertManager();

        // 初始化初始資金（改由模型提供，若模型無法提供則使用估值）
        double retailAvgFunds = model.getAverageRetailCash();
        this.initialRetailCash = retailAvgFunds > 0 ? retailAvgFunds : 1698000;
        this.initialMainForceCash = model.getMainForce() != null
                ? model.getMainForce().getAccount().getAvailableFunds()
                : 3698000;
        this.initialPersonalCash = model.getInitialPersonalCash();

        // 初始化快捷交易功能
        this.quickTradeManager = new QuickTradeManager();
        this.quickTradePanel = controlView.getQuickTradePanel();
        this.quickTradePanel.setListener(this);  // 設置控制器為監聽器

        quickTradePanel.loadQuickTradeConfigs(quickTradeManager.getAllConfigs());
        // 註冊快捷鍵（需要主視窗）
        quickTradePanel.registerHotkeys(mainView);
        // 初始更新狀態
        updateQuickTradePanelStatus();

        // 安全地初始化個人統計管理器；失敗時保持 Optional.empty()，後續呼叫一律透過 ifPresent/map
        try {
            UserAccount userAccount = model.getUserInvestor().getAccount();
            this.personalStatsMgr = Optional.of(new PersonalStatisticsManager(userAccount, initialPersonalCash));
            logger.info("個人統計管理器初始化成功", "CONTROLLER_INIT");
        } catch (Exception e) {
            logger.error("初始化個人統計管理器失敗: " + e.getMessage(), "CONTROLLER_INIT");
        }

        if (model.getOrderBook() != null) {
            model.getOrderBook().addTradeExecutedListener(this::recordPersonalTrade);
        }

        // 註冊為模型監聽器
        model.addModelListener(this);
        // 註冊成交逐筆監聽：即時推給 OrderBookView/主視窗 Tape
        try {
            model.addTransactionListener(transaction -> {
                try {
                    OrderBook ob = model.getOrderBook();
                    double bestBid = transaction.getPrice();
                    double bestAsk = transaction.getPrice();
                    if (ob != null) {
                        java.util.List<Order> b = ob.getTopBuyOrders(1);
                        java.util.List<Order> s = ob.getTopSellOrders(1);
                        if (b != null && !b.isEmpty()) bestBid = b.get(0).getPrice();
                        if (s != null && !s.isEmpty()) bestAsk = s.get(0).getPrice();
                    }
                    final double fBestBid = bestBid, fBestAsk = bestAsk;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        // pushTapeTrade 為純 UI 更新，失敗可安全忽略（避免每筆成交刷 log）
                        try {
                            mainView.pushTapeTrade(transaction.isBuyerInitiated(), transaction.getPrice(),
                                    transaction.getVolume(), fBestBid, fBestAsk);
                        } catch (RuntimeException ex) {
                            logger.debugThrottled("逐筆成交 UI 更新失敗：" + ex.getMessage(),
                                    "UI_UPDATE", "tape-trade", 60_000);
                        }
                    });
                } catch (Throwable tt) {
                    logger.warn("成交逐筆推送處理失敗：" + tt.getMessage(), "CONTROLLER_INIT");
                }
            });
        } catch (Throwable tt) {
            logger.warn("addTransactionListener 註冊失敗：" + tt.getMessage(), "CONTROLLER_INIT");
        }

        // 初始化按鈕事件
        initializeButtonActions();

        // 設置關閉事件：先停止模擬與介入執行緒，再退出
        mainView.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                interventionExecutor.shutdownNow();
                model.stopAutoPriceFluctuation();
                System.exit(0);
            }
        });

        // 精簡：不自動開啟日誌視窗，保留手動啟用能力

        // 設置價格提醒面板的監聽器
        controlView.getPriceAlertPanel().setListener(this);

        // 主力狀態：套用按鈕監聽器
        if (controlView.getMainForceApplyButton() != null) {
            controlView.getMainForceApplyButton().addActionListener(e -> {
                try {
                    String phase = (String) controlView.getMainForcePhaseCombo().getSelectedItem();
                    boolean lock = controlView.getMainForceLockCheck().isSelected();
                    Integer interval = controlView.getMainForceReplaceIntervalOrNull();
                    if (model.getMainForce() != null) {
                        model.getMainForce().setManualPhase(phase, lock);
                        if (interval != null) {
                            model.getMainForce().setReplaceIntervalTicks(interval);
                        }
                        logger.info("主力手動階段套用：phase=" + phase + ", lock=" + lock, "MAIN_FORCE_PANEL");
                    }
                } catch (Exception ex) {
                    logger.error("套用主力手動階段失敗：" + ex.getMessage(), "MAIN_FORCE_PANEL");
                }
            });
        }

        // 只有在統計管理器初始化成功時才設置監聽器
        personalStatsMgr.ifPresent(mgr -> {
            try {
                controlView.getPersonalStatsPanel().setListener(new PersonalStatsPanelListener());
                logger.info("個人統計面板監聽器設置成功", "CONTROLLER_INIT");
            } catch (Exception e) {
                logger.error("設置個人統計面板監聽器失敗: " + e.getMessage(), "CONTROLLER_INIT");
            }
        });

        // 初始化撮合引擎控制
        initializeMatchingEngineControl();

        // 🔄 修正：更新日誌訊息以包含所有功能
        logger.info("控制器初始化完成，包括撮合引擎控制、價格提醒功能和個人統計功能", "CONTROLLER_INIT");
    }

    /**
     * 初始化按鈕動作
     */
    private void initializeButtonActions() {
        // 停止按鈕事件
        controlView.getStopButton().addActionListener(e -> {
            if (model.isRunning()) {
                model.stopAutoPriceFluctuation();
                controlView.getStopButton().setText("繼續");
                mainView.appendToInfoArea("模擬已暫停。");
                // 禁用快捷交易
                if (quickTradePanel != null) {
                    quickTradePanel.setQuickTradeEnabled(false);
                }
            } else {
                model.startAutoPriceFluctuation();
                controlView.getStopButton().setText("暫停");
                mainView.appendToInfoArea("模擬已繼續。");
                // 啟用快捷交易
                if (quickTradePanel != null) {
                    quickTradePanel.setQuickTradeEnabled(true);
                }
            }
        });

        // 限價買入按鈕事件
        controlView.getLimitBuyButton().addActionListener(e -> handleLimitBuy());

        // 限價賣出按鈕事件
        controlView.getLimitSellButton().addActionListener(e -> handleLimitSell());

        // 市價買入按鈕事件
        controlView.getMarketBuyButton().addActionListener(e -> handleMarketBuy());

        // 市價賣出按鈕事件
        controlView.getMarketSellButton().addActionListener(e -> handleMarketSell());

        // 取消訂單按鈕事件
        controlView.getCancelOrderButton().addActionListener(e -> handleCancelOrders());

        // 查看訂單按鈕事件
        controlView.getViewOrdersButton().addActionListener(e -> {
            OrderViewer orderViewer = new OrderViewer(model.getOrderBook());
            orderViewer.setVisible(true);
        });

        // 成交記錄按鈕事件
        JButton transactionHistoryButton = controlView.getTransactionHistoryButton();
        if (transactionHistoryButton != null) {
            transactionHistoryButton.addActionListener(e -> {
                if (transactionHistoryViewer == null || !transactionHistoryViewer.isVisible()) {
                    // 創建新視窗並傳入 model
                    transactionHistoryViewer = new TransactionHistoryViewer(model);

                    // 載入歷史記錄
                    List<Transaction> transactions = model.getTransactionHistory();
                    if (transactions != null && !transactions.isEmpty()) {
                        transactionHistoryViewer.addTransactions(transactions);
                    }

                    transactionHistoryViewer.setVisible(true);

                    // 視窗關閉時清空引用
                    transactionHistoryViewer.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent e) {
                            transactionHistoryViewer = null;
                        }
                    });
                } else {
                    // 如果已經開啟，將視窗帶到前面
                    transactionHistoryViewer.toFront();
                    transactionHistoryViewer.requestFocus();
                }

                mainView.appendToInfoArea("打開成交記錄視窗");
            });
        }
    }

    /**
     * 初始化撮合引擎控制
     */
    public void initializeMatchingEngineControl() {
        MatchingEnginePanel panel = controlView.getMatchingEnginePanel();
        if (panel == null) {
            logger.error("無法獲取撮合引擎面板", "CONTROLLER_INIT");
            return;
        }

        // 設置初始值
        OrderBook orderBook = model.getOrderBook();
        if (orderBook != null) {
            MatchingMode currentMode = orderBook.getMatchingMode();
            panel.updateCurrentMode(currentMode);

            // 添加詳細日誌
            logger.info("初始化撮合引擎面板：當前模式=" + currentMode, "MATCHING_ENGINE");

            // 設置應用按鈕監聽器，並添加詳細日誌
            panel.setApplyButtonListener(e -> {
                try {
                    MatchingMode selectedMode = panel.getSelectedMatchingMode();
                    logger.info("嘗試更改撮合模式：" + selectedMode, "MATCHING_ENGINE");

                    // 確保 OrderBook 實例有效
                    if (model.getOrderBook() == null) {
                        logger.error("OrderBook 實例為 null", "MATCHING_ENGINE");
                        return;
                    }

                    // 設置撮合模式
                    model.getOrderBook().setMatchingMode(selectedMode);
                    panel.updateCurrentMode(selectedMode);

                    // 設置隨機切換和流動性參數
                    boolean randomSwitching = panel.isRandomModeSwitchingEnabled();
                    double probability = panel.getRandomModeSwitchingProbability();
                    double liquidityFactor = panel.getLiquidityFactor();

                    model.getOrderBook().setRandomModeSwitching(randomSwitching, probability);
                    model.getOrderBook().setLiquidityFactor(liquidityFactor);

                    // 記錄成功的操作
                    logger.info("成功更改撮合設置：模式=" + selectedMode
                            + ", 隨機切換=" + (randomSwitching ? "啟用" : "禁用")
                            + ", 切換概率=" + probability
                            + ", 流動性=" + liquidityFactor, "MATCHING_ENGINE");

                    // 通知用戶
                    model.sendInfoMessage("撮合設置已更新：模式=" + selectedMode);
                } catch (Exception ex) {
                    logger.error("更改撮合模式時發生錯誤：" + ex.getMessage(), "MATCHING_ENGINE");
                }
            });
        } else {
            logger.error("OrderBook 實例為 null", "CONTROLLER_INIT");
        }
    }

    /**
     * 處理限價買入操作（修正版）
     */
    private void handleLimitBuy() {
        // 先輸入股數
        String qtyStr = mainView.showInputDialog("輸入購買股數:", "限價買入", JOptionPane.PLAIN_MESSAGE);
        if (qtyStr == null) {
            return;   // 取消
        }

        // 再輸入價格
        String priceStr = mainView.showInputDialog("輸入掛單價格:", "限價買入", JOptionPane.PLAIN_MESSAGE);
        if (priceStr == null) {
            return; // 取消
        }

        try {
            int quantity = Integer.parseInt(qtyStr.trim());
            double limitPrice = Double.parseDouble(priceStr.trim());

            if (quantity <= 0 || limitPrice <= 0) {
                mainView.showErrorMessage("股數與價格都必須大於 0。", "錯誤");
                return;
            }

            double totalCost = limitPrice * quantity;
            if (model.getUserInvestor().getAccount().getAvailableFunds() < totalCost) {
                mainView.showErrorMessage("資金不足以購買 " + quantity + " 股。", "錯誤");
                return;
            }

            // 送出限價買單
            TradeOutcome outcome = tradeCoordinator.limitBuy(quantity, limitPrice);
            if (!outcome.successful()) {
                mainView.showErrorMessage("限價買入失敗：可能資金不足、市場無對應賣單，或價格超出允許範圍。", "失敗");
                return;
            }

            mainView.appendToInfoArea(String.format("限價買入委託已送出：%d 股 @ %.2f，預留金額 %.2f",
                    quantity, limitPrice, totalCost));

        } catch (NumberFormatException ex) {
            mainView.showErrorMessage("請輸入有效的數字。", "錯誤");
        }
    }

    /**
     * 處理限價賣出操作（修正版）
     */
    private void handleLimitSell() {
        // 先輸入股數
        String qtyStr = mainView.showInputDialog("輸入賣出股數:", "限價賣出", JOptionPane.PLAIN_MESSAGE);
        if (qtyStr == null) {
            return;   // 取消
        }

        // 再輸入價格
        String priceStr = mainView.showInputDialog("輸入掛單價格:", "限價賣出", JOptionPane.PLAIN_MESSAGE);
        if (priceStr == null) {
            return; // 取消
        }

        try {
            int quantity = Integer.parseInt(qtyStr.trim());
            double limitPrice = Double.parseDouble(priceStr.trim());

            if (quantity <= 0 || limitPrice <= 0) {
                mainView.showErrorMessage("股數與價格都必須大於 0。", "錯誤");
                return;
            }

            if (model.getUserInvestor().getAccount().getStockInventory() < quantity) {
                mainView.showErrorMessage("持股不足以賣出 " + quantity + " 股。", "錯誤");
                return;
            }

            double totalRevenue = limitPrice * quantity;

            // 送出限價賣單
            TradeOutcome outcome = tradeCoordinator.limitSell(quantity, limitPrice);
            if (!outcome.successful()) {
                mainView.showErrorMessage("限價賣出失敗：可能持股不足、市場無對應買單，或價格超出允許範圍。", "失敗");
                return;
            }

            mainView.appendToInfoArea(String.format("限價賣出委託已送出：%d 股 @ %.2f，預估金額 %.2f",
                    quantity, limitPrice, totalRevenue));

        } catch (NumberFormatException ex) {
            mainView.showErrorMessage("請輸入有效的數字。", "錯誤");
        }
    }

    /**
     * 處理市價買入操作（修正版）
     */
    private void handleMarketBuy() {
        String input = mainView.showInputDialog("輸入購買股數:", "市價買入", JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return;
        }

        try {
            int quantity = Integer.parseInt(input);
            if (quantity <= 0) {
                mainView.showErrorMessage("股數必須大於0。", "錯誤");
                return;
            }

            TradeOutcome result = tradeCoordinator.marketBuy(quantity);
            if (!result.successful()) {
                mainView.showErrorMessage("市價買入未成交：" + result.reason(), "錯誤");
                return;
            }
            mainView.appendToInfoArea(String.format("市價買入成交 %d 股，均價 %.2f，實際成本 %.2f",
                    result.filledQuantity(), result.averagePrice(), result.totalValue()));
            if (result.status() == TradeOutcomeStatus.PARTIALLY_FILLED) {
                mainView.showInfoMessage("市價買入部分成交，已完成 " + result.filledQuantity()
                        + " 股，剩餘需求未滿足。", "部分成交");
            }
        } catch (NumberFormatException ex) {
            mainView.showErrorMessage("請輸入有效的股數。", "錯誤");
        }
    }

    /**
     * 處理市價賣出操作（修正版）
     */
    private void handleMarketSell() {
        String input = mainView.showInputDialog("輸入賣出股數:", "市價賣出", JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return;
        }

        try {
            int quantity = Integer.parseInt(input);
            if (quantity <= 0) {
                mainView.showErrorMessage("股數必須大於0。", "錯誤");
                return;
            }

            TradeOutcome result = tradeCoordinator.marketSell(quantity);
            if (!result.successful()) {
                mainView.showErrorMessage("市價賣出未成交：" + result.reason(), "錯誤");
                return;
            }
            mainView.appendToInfoArea(String.format("市價賣出成交 %d 股，均價 %.2f，實際收入 %.2f",
                    result.filledQuantity(), result.averagePrice(), result.totalValue()));
            if (result.status() == TradeOutcomeStatus.PARTIALLY_FILLED) {
                mainView.showInfoMessage("市價賣出部分成交，已完成 " + result.filledQuantity()
                        + " 股，剩餘需求未滿足。", "部分成交");
            }
        } catch (NumberFormatException ex) {
            mainView.showErrorMessage("請輸入有效的股數。", "錯誤");
        }
    }

    /**
     * 處理取消訂單操作
     */
    private void handleCancelOrders() {
        int confirm = mainView.showConfirmDialog("確定要取消所有掛單嗎？", "確認取消訂單", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            model.cancelAllOrders();
            mainView.appendToInfoArea("所有訂單已取消。");
        }
    }

    /**
     * 啟動模擬
     */
    public void startSimulation() {
        model.startAutoPriceFluctuation();
    }

    // 已移除自動開啟日誌視窗的方法

    // ======== 模型事件監聽器方法 ========
    @Override
    public void onPriceChanged(double price, double sma) {
        mainView.updatePriceChart(model.getTimeStep(), price, sma);
        updatePrice(price);

        // 更新快捷交易面板的當前價格
        if (quickTradePanel != null) {
            quickTradePanel.updateCurrentPrice(price);
        }

        personalStatsMgr.ifPresent(mgr -> {
            mgr.updateCurrentPrice(price);
            if (model.getTimeStep() % 10 == 0) {
                SwingUtilities.invokeLater(() ->
                    controlView.getPersonalStatsPanel().updateStatistics(mgr.getStatistics()));
            }
        });
    }

    @Override
    public void onTechnicalIndicatorsUpdated(double volatility, double rsi, double wap) {
        // 同時更新損益圖表
        mainView.updateProfitChart(
                model.getAverageRetailCash(),
                model.getAverageRetailStocks(),
                model.getStock().getPrice(),
                initialRetailCash,
                model.getMainForce().getAccount().getAvailableFunds(),
                model.getMainForce().getAccount().getStockInventory(),
                initialMainForceCash
        );

        // 同步更新：散戶資訊表與散戶損益圖
        java.util.List<StockMainAction.model.RetailInvestorAI> investors = model.getRetailInvestors();
        if (investors != null && !investors.isEmpty()) {
            mainView.updateRetailInfoTable(investors, model.getStock().getPrice());
            mainView.updateRetailProfitChart(investors, model.getStock().getPrice(), model.getInitialRetailCash());
        }

        // 新增：更新「市場參與者」表格（主力/做市/噪音/散戶/個人）
        // 為避免 UI 過度頻繁刷新，這裡每 2 tick 更新一次即可
        try {
            if (model.getTimeStep() % 2 == 0) {
                mainView.updateTraderInfoTable(model.getTraderSnapshots());
            }
        } catch (Exception uiEx1) {
            logger.warn("更新市場參與者表格失敗：" + uiEx1.getMessage(), "UI_UPDATE");
        }

        // 新增：更新主力階段與近期趨勢顯示
        try {
            double recentTrend = model.getMarketAnalyzer().getRecentPriceTrend();
            String phaseName = model.getMainForce() != null ? model.getMainForce().getPhaseName() : "-";
            mainView.updateMainForceStatus(phaseName, recentTrend);
            controlView.updateMainForceStatus(phaseName, recentTrend);
        } catch (Exception uiEx2) {
            logger.warn("更新主力狀態顯示失敗：" + uiEx2.getMessage(), "UI_UPDATE");
        }
    }

    @Override
    public void onMACDUpdated(double macdLine, double signalLine, double histogram) {
        // ModelListener 介面要求實作，但指標分頁已從視圖移除，此處無需更新 UI。
        // 如需重新加入指標視圖，在此呼叫對應的 mainView.updateMACDChart(...)。
    }

    @Override
    public void onBollingerBandsUpdated(double upperBand, double middleBand, double lowerBand) {
        // 同上：保留空實作以滿足介面要求。
    }

    @Override
    public void onKDJUpdated(double kValue, double dValue, double jValue) {
        // 同上：保留空實作以滿足介面要求。
    }

    @Override
    public void onVolumeUpdated(int volume) {
        mainView.updateVolumeChart(model.getTimeStep(), volume);
    }

    @Override
    public void onMarketStateChanged(double retailCash, int retailStocks,
            double mainForceCash, int mainForceStocks,
            double targetPrice, double avgCostPrice,
            double funds, int inventory) {
        mainView.updateMarketStateLabels(
                model.getStock().getPrice(),
                retailCash,
                retailStocks,
                mainForceCash,
                mainForceStocks,
                targetPrice,
                avgCostPrice,
                funds,
                inventory,
                model.getMarketAnalyzer().getWeightedAveragePrice()
        );
    }

    @Override
    public void onUserAccountUpdated(int stockQuantity, double cash, double avgPrice, double targetPrice) {
        controlView.updateUserInfo(stockQuantity, cash, avgPrice, targetPrice);

        // 更新快捷交易面板的資金和持股
        if (quickTradePanel != null) {
            quickTradePanel.updateAvailableFunds(cash);
            quickTradePanel.updateCurrentHoldings(stockQuantity);
        }
    }

    @Override
    public void onInfoMessage(String message) {
        mainView.appendToInfoArea(message);
    }

    @Override
    public void onOrderBookChanged() {
        mainView.updateOrderBookDisplay(model.getOrderBook());
    }

    // ======== 價格提醒事件監聽器方法 ========
    /**
     * 處理添加價格提醒事件 當用戶在UI中點擊"添加提醒"按鈕時觸發
     */
    @Override
    public void onAddAlert(double targetPrice, PriceAlert.AlertType type, boolean sound, boolean popup) {
        try {
            PriceAlert alert = new PriceAlert(targetPrice, type, sound, popup);
            priceAlertManager.addAlert(alert);
            updateAlertDisplay();

            logger.info(String.format("新增價格提醒：%s %.2f，音效=%s，彈窗=%s",
                    type.getDisplayName(), targetPrice, sound, popup), "PRICE_ALERT");

            mainView.appendToInfoArea(String.format("已添加價格提醒：%s %.2f",
                    type.getDisplayName(), targetPrice));
        } catch (Exception e) {
            logger.error("添加價格提醒失敗：" + e.getMessage(), "PRICE_ALERT");
            mainView.showErrorMessage("添加價格提醒失敗：" + e.getMessage(), "錯誤");
        }
    }

    /**
     * 處理刪除價格提醒事件 當用戶在UI中點擊"刪除選中"按鈕時觸發
     */
    @Override
    public void onRemoveAlert(int index) {
        try {
            if (index >= 0 && index < priceAlertManager.getAlerts().size()) {
                PriceAlert removedAlert = priceAlertManager.getAlerts().get(index);
                priceAlertManager.removeAlert(index);
                updateAlertDisplay();

                logger.info(String.format("刪除價格提醒：%s %.2f",
                        removedAlert.getType().getDisplayName(),
                        removedAlert.getTargetPrice()), "PRICE_ALERT");

                mainView.appendToInfoArea("已刪除價格提醒");
            }
        } catch (Exception e) {
            logger.error("刪除價格提醒失敗：" + e.getMessage(), "PRICE_ALERT");
            mainView.showErrorMessage("刪除價格提醒失敗：" + e.getMessage(), "錯誤");
        }
    }

    /**
     * 處理清空所有價格提醒事件 當用戶在UI中點擊"清空全部"按鈕時觸發
     */
    @Override
    public void onClearAllAlerts() {
        try {
            int alertCount = priceAlertManager.getAlerts().size();
            priceAlertManager.clearAllAlerts();
            updateAlertDisplay();

            logger.info("清空所有價格提醒，共" + alertCount + "個", "PRICE_ALERT");
            mainView.appendToInfoArea("已清空所有價格提醒（" + alertCount + "個）");
        } catch (Exception e) {
            logger.error("清空價格提醒失敗：" + e.getMessage(), "PRICE_ALERT");
            mainView.showErrorMessage("清空價格提醒失敗：" + e.getMessage(), "錯誤");
        }
    }

    /**
     * 更新UI中的提醒列表顯示 私有方法，用於同步數據到UI
     */
    private void updateAlertDisplay() {
        SwingUtilities.invokeLater(() -> {
            controlView.getPriceAlertPanel().updateAlertList(priceAlertManager.getAlerts());
        });
    }

    /**
     * 更新當前價格並觸發提醒檢查 這是連接價格變化和提醒系統的關鍵方法
     */
    private void updatePrice(double newPrice) {
        try {
            // 更新價格提醒管理器
            priceAlertManager.updatePrice(newPrice);

            // 更新UI顯示
            controlView.getPriceAlertPanel().updateCurrentPrice(newPrice);

            // 更新提醒列表顯示（如果有提醒被觸發，狀態會改變）
            updateAlertDisplay();

        } catch (Exception e) {
            logger.error("更新價格提醒時發生錯誤：" + e.getMessage(), "PRICE_ALERT");
        }
    }

    /**
     * 重置所有提醒狀態 公開方法，可以從外部調用來重置提醒
     */
    public void resetAllPriceAlerts() {
        try {
            priceAlertManager.resetAllAlerts();
            updateAlertDisplay();

            logger.info("重置所有價格提醒狀態", "PRICE_ALERT");
            mainView.appendToInfoArea("已重置所有價格提醒狀態");
        } catch (Exception e) {
            logger.error("重置價格提醒失敗：" + e.getMessage(), "PRICE_ALERT");
        }
    }

    /**
     * 獲取價格提醒管理器 提供對外接口，用於擴展功能
     */
    public PriceAlertManager getPriceAlertManager() {
        return priceAlertManager;
    }

    // ======== 內部類別與個人統計監聽器方法 ========
    // 定義一個私人統計面板的事件監聽器
    private class PersonalStatsPanelListener implements PersonalStatsPanel.PersonalStatsPanelListener {

        @Override
        public void onRefreshStats() {
            try {
                double currentPrice = model.getStock().getPrice();
                personalStatsMgr.ifPresent(mgr -> {
                    mgr.updateCurrentPrice(currentPrice);
                    controlView.getPersonalStatsPanel().updateStatistics(mgr.getStatistics());
                });
                logger.info("刷新個人統計資料", "PERSONAL_STATS");
            } catch (Exception e) {
                logger.error("刷新個人統計失敗: " + e.getMessage(), "PERSONAL_STATS");
                mainView.showErrorMessage("刷新統計失敗: " + e.getMessage(), "錯誤");
            }
        }

        @Override
        public void onResetStats() {
            try {
                personalStatsMgr.ifPresent(mgr -> {
                    mgr.resetStatistics();
                    controlView.getPersonalStatsPanel().updateStatistics(mgr.getStatistics());
                });
                mainView.appendToInfoArea("個人統計資料已重置");
                logger.info("重置個人統計資料", "PERSONAL_STATS");
            } catch (Exception e) {
                logger.error("重置個人統計失敗: " + e.getMessage(), "PERSONAL_STATS");
                mainView.showErrorMessage("重置統計失敗: " + e.getMessage(), "錯誤");
            }
        }

        @Override
        public void onExportStats() {
            try {
                // 產生統計報告
                String report = generateStatsReport();

                // 顯示報告的對話視窗
                JTextArea reportArea = new JTextArea(report);
                reportArea.setEditable(false);
                reportArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

                JScrollPane scrollPane = new JScrollPane(reportArea);
                scrollPane.setPreferredSize(new Dimension(600, 400));

                JOptionPane.showMessageDialog(
                        controlView,
                        scrollPane,
                        "個人交易統計報告",
                        JOptionPane.INFORMATION_MESSAGE
                );

                // 紀錄日誌
                logger.info("導出個人統計報告", "PERSONAL_STATS");
            } catch (Exception e) {
                // 若導出失敗，紀錄錯誤並顯示錯誤訊息
                logger.error("導出個人統計報告失敗: " + e.getMessage(), "PERSONAL_STATS");
                mainView.showErrorMessage("導出報告失敗: " + e.getMessage(), "錯誤");
            }
        }
    }

    // === 統計報告生成方法 ===
    private String generateStatsReport() {
        PersonalStatistics stats = personalStatsMgr.map(PersonalStatisticsManager::getStatistics).orElse(null);
        if (stats == null) return "個人統計管理器未初始化，無法產生報告。";
        StringBuilder report = new StringBuilder();

        report.append("===============================\n");
        report.append("     個人交易統計報告\n");
        report.append("===============================\n\n");

        report.append("【投資組合概況】\n");
        report.append(String.format("初始資金: %.2f\n", stats.getInitialCash()));
        report.append(String.format("當前投資組合價值: %.2f\n", stats.getCurrentPortfolioValue()));
        report.append(String.format("總損益: %.2f (%.2f%%)\n", stats.getTotalProfitLoss(), stats.getReturnRate()));
        report.append(String.format("  已實現損益: %.2f\n", stats.getTotalProfitLoss() - stats.getUnrealizedProfitLoss()));
        report.append(String.format("  未實現損益: %.2f\n", stats.getUnrealizedProfitLoss()));
        report.append(String.format("今日損益: %.2f\n", stats.getTodayProfitLoss()));
        report.append("\n");

        report.append("【持倉情況】\n");
        report.append(String.format("當前現金: %.2f\n", stats.getCurrentCash()));
        report.append(String.format("當前持股: %d股\n", stats.getCurrentHoldings()));
        report.append(String.format("平均成本價: %.2f\n", stats.getAvgCostPrice()));
        report.append("\n");

        report.append("【交易統計】\n");
        report.append(String.format("總交易次數: %d筆\n", stats.getTotalTrades()));
        report.append(String.format("獲利交易: %d筆\n", stats.getWinningTrades()));
        report.append(String.format("虧損交易: %d筆\n", stats.getLosingTrades()));
        report.append(String.format("勝率: %.1f%%\n", stats.getWinRate()));
        report.append(String.format("平均每筆損益: %.2f\n", stats.getAvgProfitPerTrade()));
        report.append("\n");

        report.append("【風險指標】\n");
        report.append(String.format("最大回撤: %.2f%%\n", stats.getMaxDrawdown()));
        report.append(String.format("單筆最大獲利: %.2f\n", stats.getMaxSingleProfit()));
        report.append(String.format("單筆最大虧損: %.2f\n", stats.getMaxSingleLoss()));
        report.append("\n");

        report.append("【最近交易紀錄】\n");
        List<PersonalStatistics.TradeRecord> recentTrades = stats.getTradesByPeriod(PersonalStatistics.StatsPeriod.TODAY);
        if (recentTrades.isEmpty()) {
            report.append("今日無交易紀錄\n");
        } else {
            for (int i = Math.max(0, recentTrades.size() - 10); i < recentTrades.size(); i++) {
                PersonalStatistics.TradeRecord record = recentTrades.get(i);
                report.append(String.format("%s\n", record.toString()));
            }
        }

        report.append("\n===============================\n");
        report.append("報告生成時間: " + java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return report.toString();
    }

    /**
     * 獲取個人統計管理（可能為空）
     */
    public Optional<PersonalStatisticsManager> getPersonalStatisticsManager() {
        return personalStatsMgr;
    }

    /**
     * 手動刷新個人紀錄
     */
    public void refreshPersonalStatistics() {
        double currentPrice = model.getStock().getPrice();
        personalStatsMgr.ifPresent(mgr -> {
            mgr.updateCurrentPrice(currentPrice);
            controlView.getPersonalStatsPanel().updateStatistics(mgr.getStatistics());
        });
    }

    private void recordPersonalTrade(TradeExecuted event) {
        boolean personalBuy = "PERSONAL".equalsIgnoreCase(event.buyerType());
        boolean personalSell = "PERSONAL".equalsIgnoreCase(event.sellerType());
        if (!personalBuy && !personalSell) {
            return;
        }
        personalStatsMgr.ifPresent(manager -> {
            if (personalBuy) {
                manager.recordBuyTrade(event.volume(), event.price(), event.price());
            }
            if (personalSell) {
                manager.recordSellTrade(event.volume(), event.price(), event.price());
            }
            SwingUtilities.invokeLater(() ->
                    controlView.getPersonalStatsPanel().updateStatistics(manager.getStatistics()));
        });
    }

    // ======== 快捷交易事件監聽器方法 ========
    @Override
    public void onQuickTradeExecute(QuickTradeConfig config) {
        try {
            // 獲取當前帳戶狀態
            UserAccount userAccount = model.getUserInvestor().getAccount();
            double availableFunds = userAccount.getAvailableFunds();
            int currentHoldings = userAccount.getStockInventory();
            double currentPrice = model.getStock().getPrice();

            // 計算交易參數
            QuickTradeManager.QuickTradeResult result = quickTradeManager.calculateQuickTrade(
                    config, availableFunds, currentHoldings, currentPrice
            );

            if (!result.isSuccess()) {
                mainView.showErrorMessage(result.getMessage(), "快捷交易失敗");
                return;
            }

            // 執行交易
            boolean success;
            ExecutionResult execution = null;
            if (config.isBuy()) {
                if (config.isMarketOrder()) {
                    execution = model.executeMarketBuyResult(result.getQuantity());
                    success = execution.filledVolume() > 0;
                } else {
                    success = model.executeLimitBuy(result.getQuantity(), result.getPrice());
                }
            } else {
                if (config.isMarketOrder()) {
                    execution = model.executeMarketSellResult(result.getQuantity());
                    success = execution.filledVolume() > 0;
                } else {
                    success = model.executeLimitSell(result.getQuantity(), result.getPrice());
                }
            }

            if (success) {
                int reportedQuantity = config.isMarketOrder()
                        ? execution.filledVolume() : result.getQuantity();
                double reportedPrice = config.isMarketOrder()
                        ? execution.averagePrice() : result.getPrice();
                double reportedTotal = config.isMarketOrder()
                        ? execution.totalValue() : result.getTotalAmount();
                // 顯示成功訊息
                String tradeType = config.isBuy() ? "買入" : "賣出";
                if (config.isMarketOrder()) {
                    mainView.appendToInfoArea(String.format("快捷交易成交：%s %d 股 @ %.2f，總金額 %.2f",
                            tradeType, reportedQuantity, reportedPrice, reportedTotal));
                } else {
                    mainView.appendToInfoArea(String.format("快捷交易委託已送出：%s %d 股 @ %.2f",
                            tradeType, reportedQuantity, reportedPrice));
                }

                logger.info(String.format("快捷交易已處理：%s - %s %d 股",
                        config.getName(), tradeType, reportedQuantity), "QUICK_TRADE");
            } else {
                mainView.showErrorMessage("快捷交易執行失敗", "錯誤");
            }

        } catch (Exception e) {
            logger.error("執行快捷交易時發生錯誤：" + e.getMessage(), "QUICK_TRADE");
            mainView.showErrorMessage("快捷交易失敗：" + e.getMessage(), "錯誤");
        }
    }

    @Override
    public void onConfigureQuickTrade() {
        JFrame parent = (controlView != null) ? controlView : mainView;
        StockMainAction.view.components.QuickTradeConfigDialog dialog =
                new StockMainAction.view.components.QuickTradeConfigDialog(parent, quickTradeManager);
        dialog.setVisible(true);
        // 對話框關閉後，重新將最新配置載入面板
        quickTradePanel.loadQuickTradeConfigs(quickTradeManager.getAllConfigs());
        logger.info("快捷交易配置已更新", "QUICK_TRADE");
    }

    @Override
    public void onPreviewQuickTrade(QuickTradeConfig config) {
        // 預覽功能已經在 QuickTradePanel 內部實現
        logger.info("預覽快捷交易：" + config.getName(), "QUICK_TRADE");
    }

    @Override
    public void onPumpPrice(int totalQty, int slices, boolean useMainForce,
            boolean enableLiquidity, int depthLevels, double depthSpanPct,
            boolean enableCounterWallSelfTrade, boolean useOtherTraderFooting) {
        runMarketIntervention(true, totalQty, slices, useMainForce, enableLiquidity, depthLevels, depthSpanPct,
                enableCounterWallSelfTrade, useOtherTraderFooting);
    }

    @Override
    public void onDumpPrice(int totalQty, int slices, boolean useMainForce,
            boolean enableLiquidity, int depthLevels, double depthSpanPct,
            boolean enableCounterWallSelfTrade, boolean useOtherTraderFooting) {
        runMarketIntervention(false, totalQty, slices, useMainForce, enableLiquidity, depthLevels, depthSpanPct,
                enableCounterWallSelfTrade, useOtherTraderFooting);
    }

    private void runMarketIntervention(boolean pump, int totalQty, int slices, boolean useMainForce,
            boolean enableLiquidity, int depthLevels, double depthSpanPct, boolean enableCounterWallSelfTrade,
            boolean useOtherTraderFooting) {
        try {
            if (model == null || model.getOrderBook() == null || model.getStock() == null) {
                mainView.showErrorMessage("模型/訂單簿未初始化", "介入失敗");
                return;
            }
            if (totalQty <= 0) return;
            int n = Math.max(1, Math.min(500, slices));

            Trader actor = null;
            if (useMainForce) {
                actor = model.getMainForce();
            } else {
                actor = model.getUserInvestor();
            }
            if (actor == null) {
                mainView.showErrorMessage("找不到交易者(主力/個人戶)", "介入失敗");
                return;
            }

            final Trader fActor = actor;
            final int fTotal = totalQty;
            final int fSlices = n;
            final boolean fPump = pump;
            final boolean fEnableLiquidity = enableLiquidity;
            final int fDepthLevels = Math.max(1, Math.min(30, depthLevels));
            final double fDepthSpanPct = Math.max(0.0, Math.min(0.15, depthSpanPct)); // 上限 15%
            final boolean fEnableCounterWallSelfTrade = enableCounterWallSelfTrade;
            final boolean fUseOtherTraderFooting = useOtherTraderFooting;

            // 避免卡住 EDT：透過受管理的 executor 背景執行，支援 shutdownNow() 取消
            // 明確指定為 Runnable，避免 Java 型別推斷誤用 Callable<T>
            interventionExecutor.submit((Runnable) () -> {
                try {
                    // 0) 先補深度（掛牆/墊腳石）
                    if (fEnableLiquidity) {
                        seedLiquidity(fActor, fPump, fTotal, fDepthLevels, fDepthSpanPct, fUseOtherTraderFooting);
                        try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    }
                    // 進階模式：補對手牆，讓後續市價單可「左手換右手」吃自己牆
                    if (fEnableCounterWallSelfTrade) {
                        seedCounterWallForSelfTrade(fActor, fPump, fTotal, fDepthLevels, fDepthSpanPct);
                        try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    }

                    int remain = fTotal;
                    int chunkMin = Math.max(1, fTotal / fSlices);
                    for (int i = 0; i < fSlices && remain > 0; i++) {
                        // 支援外部 shutdownNow() 中斷
                        if (Thread.currentThread().isInterrupted()) return;
                        int chunk = (i == fSlices - 1) ? remain : Math.min(remain, chunkMin);
                        if (chunk <= 0) break;
                        if (fPump) {
                            model.getOrderBook().marketBuy(fActor, chunk);
                        } else {
                            model.getOrderBook().marketSell(fActor, chunk);
                        }
                        remain -= chunk;
                        try { Thread.sleep(30); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    }

                    final int executed = fTotal - Math.max(0, remain);
                    SwingUtilities.invokeLater(() -> {
                        try {
                            String who = useMainForce ? "主力" : "個人戶";
                            String act = fPump ? "拉抬(分批市價買)" : "出貨/打壓(分批市價賣)";
                            mainView.appendToInfoArea(String.format(
                                    "【市場介入】%s %s：總量=%d，分批=%d，同側牆=%b(檔=%d,跨度=%.2f%%)，進階(對手牆+自成交)=%b，另一交易者墊腳=%b，嘗試執行=%d",
                                    who, act, fTotal, fSlices, fEnableLiquidity, fDepthLevels, fDepthSpanPct * 100.0,
                                    fEnableCounterWallSelfTrade, fUseOtherTraderFooting, executed));
                        } catch (Exception ignore) {
                            // appendToInfoArea 為非關鍵 UI 日誌，失敗可安全忽略
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> mainView.showErrorMessage("介入失敗：" + e.getMessage(), "錯誤"));
                }
            });

        } catch (Exception e) {
            mainView.showErrorMessage("介入失敗：" + e.getMessage(), "錯誤");
        }
    }

    /**
     * 補深度（掛牆/墊腳石）
     * - pump(拉抬)：補買盤（提供支撐/降低向上掃單滑價）
     * - dump(打壓)：補買盤「墊腳石」（仍是買盤，但掛在保護帶內較低檔，讓市價賣可持續成交而不被 minPx 擋住）
     *
     * 注意：這裡只負責掛限價單提供深度，不保證成交。
     */
    private void seedLiquidity(Trader actor, boolean pump, int totalQty, int depthLevels, double spanPct,
            boolean useOtherTraderFooting) {
        try {
            final StockMainAction.model.core.OrderBook ob = model.getOrderBook();
            final double last = model.getStock().getPrice();
            if (last <= 0) return;

            final double slip = ob.getMaxMarketSlippageRatio();
            final double minPx = ob.adjustPriceToUnit(last * (1.0 - slip));
            final double maxPx = ob.adjustPriceToUnit(last * (1.0 + slip));

            // 補深度總量：用總量的一部分（預設 30%），避免完全把倉位用在掛牆
            int depthTotal = Math.max(0, (int) Math.round(totalQty * 0.30));
            if (depthTotal <= 0) return;

            // 價格區間：以 spanPct 決定，且限制在保護帶內
            double span = Math.max(0.0, spanPct);
            if (span <= 0) span = 0.01; // 預設 1%

            if (pump) {
                // 一般模式-拉抬：補買牆（現價下方）
                placeBuyLayers(actor, depthTotal, depthLevels,
                        last * (1.0 - 0.001), last * (1.0 - span), minPx, maxPx, ob);
            } else {
                // 一般模式-打壓：補賣牆（現價上方）+ 少量買墊腳（避免跌停帶阻塞）
                int sellPart = Math.max(1, (int) Math.round(depthTotal * 0.70));
                int buyPart = Math.max(0, depthTotal - sellPart);
                placeSellLayers(actor, sellPart, depthLevels,
                        last * (1.0 + 0.001), last * (1.0 + span), minPx, maxPx, ob);
                if (buyPart > 0) {
                    // 可選：打壓時的買墊腳改由另一交易者掛（預設關閉）
                    Trader footingTrader = useOtherTraderFooting ? chooseFootingTrader(actor) : actor;
                    placeBuyLayers(footingTrader, buyPart, Math.max(1, depthLevels / 2),
                            last * (1.0 - Math.min(0.02, slip * 0.6)),
                            last * (1.0 - Math.min(span + 0.02, slip * 0.95)),
                            minPx, maxPx, ob);
                }
            }
        } catch (Exception ex) {
            logger.warn("建立市場深度掛單失敗：" + ex.getMessage(), "MARKET_INTERVENTION");
        }
    }

    private Trader chooseFootingTrader(Trader actor) {
        try {
            Trader main = model != null ? model.getMainForce() : null;
            Trader user = model != null ? model.getUserInvestor() : null;
            if (actor == main && user != null) return user;
            if (actor == user && main != null) return main;
            // 若無法判定，退回原 actor（最壞情況仍可運作）
            return actor;
        } catch (Exception e) {
            logger.warn("選擇墊腳交易者失敗：" + e.getMessage(), "MARKET_INTERVENTION");
            return actor;
        }
    }

    /**
     * 進階模式：補對手牆，讓後續分批市價單可優先吃到自己的掛單（左手換右手）
     * - pump：先掛賣牆，再市價買
     * - dump：先掛買牆，再市價賣
     */
    private void seedCounterWallForSelfTrade(Trader actor, boolean pump, int totalQty, int depthLevels, double spanPct) {
        try {
            final StockMainAction.model.core.OrderBook ob = model.getOrderBook();
            final double last = model.getStock().getPrice();
            if (last <= 0) return;
            final double slip = ob.getMaxMarketSlippageRatio();
            final double minPx = ob.adjustPriceToUnit(last * (1.0 - slip));
            final double maxPx = ob.adjustPriceToUnit(last * (1.0 + slip));
            double span = Math.max(0.0, spanPct);
            if (span <= 0) span = 0.01;

            // 對手牆只取總量一小部分，避免全都變成自成交
            int counterTotal = Math.max(0, (int) Math.round(totalQty * 0.20));
            if (counterTotal <= 0) return;

            if (pump) {
                // 拉抬：掛賣牆在現價附近上方，給市價買去吃
                placeSellLayers(actor, counterTotal, depthLevels,
                        last * (1.0 + 0.0005), last * (1.0 + Math.min(span, slip * 0.8)), minPx, maxPx, ob);
            } else {
                // 打壓：掛買牆在現價附近下方，給市價賣去吃
                placeBuyLayers(actor, counterTotal, depthLevels,
                        last * (1.0 - 0.0005), last * (1.0 - Math.min(span, slip * 0.8)), minPx, maxPx, ob);
            }
        } catch (Exception ex) {
            logger.warn("建立自成交對手牆失敗：" + ex.getMessage(), "MARKET_INTERVENTION");
        }
    }

    private void placeBuyLayers(Trader actor, int total, int levels, double hi, double lo,
            double minPx, double maxPx, StockMainAction.model.core.OrderBook ob) {
        if (actor == null || actor.getAccount() == null || total <= 0) return;
        double hiBuy = Math.max(minPx, Math.min(maxPx, ob.adjustPriceToUnit(hi)));
        double loBuy = Math.max(minPx, Math.min(maxPx, ob.adjustPriceToUnit(lo)));
        if (loBuy > hiBuy) {
            double t = loBuy; loBuy = hiBuy; hiBuy = t;
        }
        int lv = Math.max(1, levels);
        int per = Math.max(1, total / lv);
        for (int i = 0; i < lv; i++) {
            int q = (i == lv - 1) ? (total - per * (lv - 1)) : per;
            if (q <= 0) break;
            double t = (lv == 1) ? 0.0 : (i / (double) (lv - 1));
            double px = ob.adjustPriceToUnit(hiBuy + (loBuy - hiBuy) * t);
            px = Math.max(minPx, Math.min(maxPx, px));
            double need = px * q;
            if (actor.getAccount().getAvailableFunds() < need) {
                int q2 = (int) Math.floor(actor.getAccount().getAvailableFunds() / Math.max(0.01, px));
                if (q2 <= 0) break;
                q = q2;
            }
            StockMainAction.model.core.Order o = StockMainAction.model.core.Order.createLimitBuyOrder(px, q, actor);
            ob.submitBuyOrder(o, px);
        }
    }

    private void placeSellLayers(Trader actor, int total, int levels, double lo, double hi,
            double minPx, double maxPx, StockMainAction.model.core.OrderBook ob) {
        if (actor == null || actor.getAccount() == null || total <= 0) return;
        double loSell = Math.max(minPx, Math.min(maxPx, ob.adjustPriceToUnit(lo)));
        double hiSell = Math.max(minPx, Math.min(maxPx, ob.adjustPriceToUnit(hi)));
        if (loSell > hiSell) {
            double t = loSell; loSell = hiSell; hiSell = t;
        }
        int lv = Math.max(1, levels);
        int per = Math.max(1, total / lv);
        for (int i = 0; i < lv; i++) {
            int q = (i == lv - 1) ? (total - per * (lv - 1)) : per;
            if (q <= 0) break;
            double t = (lv == 1) ? 0.0 : (i / (double) (lv - 1));
            double px = ob.adjustPriceToUnit(loSell + (hiSell - loSell) * t);
            px = Math.max(minPx, Math.min(maxPx, px));
            int avail = actor.getAccount().getStockInventory();
            if (avail <= 0) break;
            if (q > avail) q = avail;
            StockMainAction.model.core.Order o = StockMainAction.model.core.Order.createLimitSellOrder(px, q, actor);
            ob.submitSellOrder(o, px);
        }
    }

    /**
     * 更新快捷交易面板的狀態
     */
    private void updateQuickTradePanelStatus() {
        if (quickTradePanel != null) {
            UserAccount userAccount = model.getUserInvestor().getAccount();
            double currentPrice = model.getStock().getPrice();

            quickTradePanel.updateCurrentPrice(currentPrice);
            quickTradePanel.updateAvailableFunds(userAccount.getAvailableFunds());
            quickTradePanel.updateCurrentHoldings(userAccount.getStockInventory());
        }
    }

    /**
     * 重置快捷交易功能
     */
    public void resetQuickTradePanel() {
        if (quickTradePanel != null) {
            quickTradePanel.reset();
            quickTradePanel.loadQuickTradeConfigs(quickTradeManager.getAllConfigs());
        }
    }
}
