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
import StockMainAction.model.user.UserAccount;
import StockMainAction.model.core.Trader;
import StockMainAction.view.TransactionHistoryViewer;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * 股票市場控制器 - 負責連接模型與視圖 作為MVC架構中的Controller組件
 */
public class StockMarketController implements StockMarketModel.ModelListener, PriceAlertPanel.PriceAlertPanelListener, QuickTradePanel.QuickTradePanelListener {

    private StockMarketModel model;
    private MainView mainView;
    private ControlView controlView;
    private PriceAlertManager priceAlertManager;
    private PersonalStatisticsManager personalStatsManager;
    private TransactionHistoryViewer transactionHistoryViewer;
    private static final MarketLogger logger = MarketLogger.getInstance();

    // 新增快捷交易相關屬性
    private QuickTradePanel quickTradePanel;
    private QuickTradeManager quickTradeManager;  // 新增的管理器

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

        // 將模型注入主視圖，供工具列事件直接呼叫模型參數
        try { this.mainView.setModel(model); } catch (Throwable ignore) {}

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

        //修正：安全地初始化個人統計管理器
        try {
            UserAccount userAccount = model.getUserInvestor().getAccount();
            // 保留未來擴充點位：如果需要拿到 PersonalAI 實例，可在此處取得
            // 初始化統計管理器（使用簡化版建構函數）
            this.personalStatsManager = new PersonalStatisticsManager(userAccount, initialPersonalCash);

            logger.info("個人統計管理器初始化成功", "CONTROLLER_INIT");

        } catch (Exception e) {
            logger.error("初始化個人統計管理器失敗: " + e.getMessage(), "CONTROLLER_INIT");
            // 如果初始化失敗，設為null，避免後續調用出錯
            this.personalStatsManager = null;
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
                        try { mainView.pushTapeTrade(transaction.isBuyerInitiated(), transaction.getPrice(), transaction.getVolume(), fBestBid, fBestAsk); } catch (Throwable ignore) {}
                    });
                } catch (Throwable ignore) {}
            });
        } catch (Throwable ignore) {}

        // 初始化按鈕事件
        initializeButtonActions();

        // 設置關閉事件
        mainView.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
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

        // 🔄 修正：只有在統計管理器初始化成功時才設置監聽器
        if (personalStatsManager != null) {
            try {
                controlView.getPersonalStatsPanel().setListener(new PersonalStatsPanelListener());
                logger.info("個人統計面板監聽器設置成功", "CONTROLLER_INIT");
            } catch (Exception e) {
                logger.error("設置個人統計面板監聽器失敗: " + e.getMessage(), "CONTROLLER_INIT");
            }
        } else {
            logger.warn("個人統計管理器為null，跳過面板監聽器設置", "CONTROLLER_INIT");
        }

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
                    ex.printStackTrace();
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
            boolean success = model.executeLimitBuy(quantity, limitPrice);
            if (!success) {
                mainView.showErrorMessage("限價買入失敗：可能資金不足、市場無對應賣單，或價格超出允許範圍。", "失敗");
                return;
            }

            mainView.appendToInfoArea(String.format("限價買入 %d 股 @ %.2f，總成本 %.2f", quantity, limitPrice, totalCost));

            // 🔄 修正：記錄買入交易到統計系統
            double currentPrice = model.getStock().getPrice();
            if (personalStatsManager != null) {
                personalStatsManager.recordBuyTrade(quantity, limitPrice, currentPrice);
                controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());
            }

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
            boolean success = model.executeLimitSell(quantity, limitPrice);
            if (!success) {
                mainView.showErrorMessage("限價賣出失敗：可能持股不足、市場無對應買單，或價格超出允許範圍。", "失敗");
                return;
            }

            mainView.appendToInfoArea(String.format("限價賣出 %d 股 @ %.2f，總收入 %.2f", quantity, limitPrice, totalRevenue));

            // 🔄 修正：記錄賣出交易到統計系統（之前錯誤地調用了 recordBuyTrade）
            double currentPrice = model.getStock().getPrice();
            if (personalStatsManager != null) {
                personalStatsManager.recordSellTrade(quantity, limitPrice, currentPrice);
                controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());
            }

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

            // 計算實際成交數量和成本
            SimpleEntry<Integer, Double> result = model.calculateActualCost(
                    model.getOrderBook().getSellOrders(), quantity);
            int actualQuantity = result.getKey();
            double actualCost = result.getValue();

            // 檢查資金是否足夠
            if (actualQuantity > 0 && model.getUserInvestor().getAccount().getAvailableFunds() >= actualCost) {
                // 執行市價單
                boolean success = model.executeMarketBuy(actualQuantity);
                if (success) {
                    mainView.appendToInfoArea("市價買入 " + actualQuantity + " 股，實際成本：" + String.format("%.2f", actualCost));

            // 🆕 新增：記錄市價買入交易到統計系統
            double avgPrice = actualCost / actualQuantity;
            double currentPrice = model.getStock().getPrice();
            if (personalStatsManager != null) {
                personalStatsManager.recordBuyTrade(actualQuantity, avgPrice, currentPrice);
                controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());
            }

                    if (actualQuantity < quantity) {
                        mainView.showInfoMessage("市價買入部分成交，已完成 " + actualQuantity
                                + " 股，剩餘需求未滿足。", "部分成交");
                    }
                } else {
                    mainView.showErrorMessage("市價買入執行失敗。", "錯誤");
                }
            } else if (actualQuantity == 0) {
                mainView.showErrorMessage("市場中無足夠賣單，無法完成市價買入。", "錯誤");
            } else {
                mainView.showErrorMessage("資金不足以完成交易，需要 " + String.format("%.2f", actualCost) + "。", "錯誤");
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

            // 檢查用戶是否有足夠的持股
            if (model.getUserInvestor().getAccount().getStockInventory() >= quantity) {
                // 計算實際成交數量和收入
            SimpleEntry<Integer, Double> result = model.calculateActualRevenue(
                        model.getOrderBook().getBuyOrders(), quantity);
            int actualQuantity = result.getKey();
            double actualRevenue = result.getValue();

                // 執行市價賣出
                if (actualQuantity > 0) {
                    boolean success = model.executeMarketSell(actualQuantity);
                    if (success) {
                        mainView.appendToInfoArea("市價賣出 " + actualQuantity + " 股，實際收入：" + String.format("%.2f", actualRevenue));

                        // 🆕 新增：記錄市價賣出交易到統計系統
                        double avgPrice = actualRevenue / actualQuantity;
                        double currentPrice = model.getStock().getPrice();
                        if (personalStatsManager != null) {
                            personalStatsManager.recordSellTrade(actualQuantity, avgPrice, currentPrice);
                            controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());
                        }

                        if (actualQuantity < quantity) {
                            mainView.showInfoMessage("市價賣出部分成交，已完成 " + actualQuantity
                                    + " 股，剩餘需求未滿足。", "部分成交");
                        }
                    } else {
                        mainView.showErrorMessage("市價賣出執行失敗。", "錯誤");
                    }
                } else {
                    mainView.showErrorMessage("市場中無足夠買單，無法完成市價賣出。", "錯誤");
                }
            } else {
                mainView.showErrorMessage("持股不足以賣出 " + quantity + " 股。", "錯誤");
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

        // 🆕 新增這些行
        if (personalStatsManager != null) {
            personalStatsManager.updateCurrentPrice(price);
            if (model.getTimeStep() % 10 == 0) {
                SwingUtilities.invokeLater(() -> {
                    controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());
                });
            }
        }
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
        } catch (Exception ignore) {}

        // 新增：更新主力階段與近期趨勢顯示
        try {
            double recentTrend = model.getMarketAnalyzer().getRecentPriceTrend();
            String phaseName = model.getMainForce() != null ? model.getMainForce().getPhaseName() : "-";
            mainView.updateMainForceStatus(phaseName, recentTrend);
            // 同步 ControlView 的只讀顯示
            controlView.updateMainForceStatus(phaseName, recentTrend);
        } catch (Exception ignore) {}
    }

    @Override
    public void onMACDUpdated(double macdLine, double signalLine, double histogram) {
        // 指標分頁已移除：不再更新 MACD 視圖
    }

    @Override
    public void onBollingerBandsUpdated(double upperBand, double middleBand, double lowerBand) {
        // 指標分頁已移除：不再更新布林帶視圖
    }

    @Override
    public void onKDJUpdated(double kValue, double dValue, double jValue) {
        // 指標分頁已移除：不再更新 KDJ 視圖
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
            //priceAlertManager.resetAllAlerts();
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
                // 從模型中取得目前股價並更新至統計系統
                double currentPrice = model.getStock().getPrice();
                personalStatsManager.updateCurrentPrice(currentPrice);

                // 更新 UI 顯示統計資料
                controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());

                // 記錄成功刷新統計的日誌
                logger.info("刷新個人統計資料", "PERSONAL_STATS");
            } catch (Exception e) {
                // 若刷新失敗，記錄錯誤並在主畫面顯示錯誤訊息
                logger.error("刷新個人統計失敗: " + e.getMessage(), "PERSONAL_STATS");
                mainView.showErrorMessage("刷新統計失敗: " + e.getMessage(), "錯誤");
            }
        }

        @Override
        public void onResetStats() {
            try {
                // 重置統計資料
                personalStatsManager.resetStatistics();

                // 更新 UI 顯示重置後的統計資料
                controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());

                // 顯示提示訊息與紀錄日誌
                mainView.appendToInfoArea("個人統計資料已重置");
                logger.info("重置個人統計資料", "PERSONAL_STATS");
            } catch (Exception e) {
                // 若重置失敗，紀錄錯誤並顯示錯誤訊息
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

    // === 修正的報告生成方法 ===
    private String generateStatsReport() {
        PersonalStatistics stats = personalStatsManager.getStatistics();
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

    //新增提供给外部調用的方法
    /**
     * 獲取個人統計管理
     */
    public PersonalStatisticsManager getPersonalStatisticsManager() {
        return personalStatsManager;
    }

    /**
     * 手動刷新個人紀錄
     */
    public void refreshPersonalStatistics() {
        if (personalStatsManager != null) {
            double currentPrice = model.getStock().getPrice();
            personalStatsManager.updateCurrentPrice(currentPrice);
            controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());
        }
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
            boolean success = false;
            if (config.isBuy()) {
                if (config.isMarketOrder()) {
                    success = model.executeMarketBuy(result.getQuantity());
                } else {
                    success = model.executeLimitBuy(result.getQuantity(), result.getPrice());
                }
            } else {
                if (config.isMarketOrder()) {
                    success = model.executeMarketSell(result.getQuantity());
                } else {
                    success = model.executeLimitSell(result.getQuantity(), result.getPrice());
                }
            }

            if (success) {
                // 記錄交易到統計系統
                if (personalStatsManager != null) {
                    if (config.isBuy()) {
                        personalStatsManager.recordBuyTrade(result.getQuantity(), result.getPrice(), currentPrice);
                    } else {
                        personalStatsManager.recordSellTrade(result.getQuantity(), result.getPrice(), currentPrice);
                    }
                    // 更新統計面板
                    controlView.getPersonalStatsPanel().updateStatistics(personalStatsManager.getStatistics());
                }

                // 顯示成功訊息
                String tradeType = config.isBuy() ? "買入" : "賣出";
                mainView.appendToInfoArea(String.format("快捷交易成功：%s %d 股 @ %.2f，總金額 %.2f",
                        tradeType, result.getQuantity(), result.getPrice(), result.getTotalAmount()));

                logger.info(String.format("快捷交易執行成功：%s - %s %d 股",
                        config.getName(), tradeType, result.getQuantity()), "QUICK_TRADE");
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
        // 打開配置管理對話框
        SwingUtilities.invokeLater(() -> {
            // 這裡可以創建一個配置管理對話框
            JDialog configDialog = new JDialog(mainView, "快捷交易配置管理", true);
            configDialog.setSize(600, 400);
            configDialog.setLocationRelativeTo(mainView);

            // TODO: 實作配置管理UI
            JPanel panel = new JPanel();
            panel.add(new JLabel("配置管理功能開發中..."));

            configDialog.add(panel);
            configDialog.setVisible(true);
        });

        logger.info("打開快捷交易配置管理", "QUICK_TRADE");
    }

    @Override
    public void onPreviewQuickTrade(QuickTradeConfig config) {
        // 預覽功能已經在 QuickTradePanel 內部實現
        logger.info("預覽快捷交易：" + config.getName(), "QUICK_TRADE");
    }

    @Override
    public void onPumpPrice(int totalQty, int slices, boolean useMainForce,
            boolean enableLiquidity, int depthLevels, double depthSpanPct) {
        runMarketIntervention(true, totalQty, slices, useMainForce, enableLiquidity, depthLevels, depthSpanPct);
    }

    @Override
    public void onDumpPrice(int totalQty, int slices, boolean useMainForce,
            boolean enableLiquidity, int depthLevels, double depthSpanPct) {
        runMarketIntervention(false, totalQty, slices, useMainForce, enableLiquidity, depthLevels, depthSpanPct);
    }

    private void runMarketIntervention(boolean pump, int totalQty, int slices, boolean useMainForce,
            boolean enableLiquidity, int depthLevels, double depthSpanPct) {
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

            // 避免卡住 EDT：背景執行
            new Thread(() -> {
                try {
                    // 0) 先補深度（掛牆/墊腳石）
                    if (fEnableLiquidity) {
                        seedLiquidity(fActor, fPump, fTotal, fDepthLevels, fDepthSpanPct);
                        try {
                            Thread.sleep(50);
                        } catch (Exception ignore) {
                        }
                    }

                    int remain = fTotal;
                    int chunkMin = Math.max(1, fTotal / fSlices);
                    for (int i = 0; i < fSlices && remain > 0; i++) {
                        int chunk = (i == fSlices - 1) ? remain : Math.min(remain, chunkMin);
                        if (chunk <= 0) break;
                        if (fPump) {
                            model.getOrderBook().marketBuy(fActor, chunk);
                        } else {
                            model.getOrderBook().marketSell(fActor, chunk);
                        }
                        remain -= chunk;
                        try {
                            Thread.sleep(30);
                        } catch (Exception ignore) {
                        }
                    }

                    final int executed = fTotal - Math.max(0, remain);
                    SwingUtilities.invokeLater(() -> {
                        try {
                            String who = useMainForce ? "主力" : "個人戶";
                            String act = fPump ? "拉抬(補深度+分批市價買)" : "出貨/打壓(補深度+分批市價賣)";
                            mainView.appendToInfoArea(String.format("【市場介入】%s %s：總量=%d，分批=%d，補深度=%b(檔=%d,跨度=%.2f%%)，嘗試執行=%d",
                                    who, act, fTotal, fSlices, fEnableLiquidity, fDepthLevels, fDepthSpanPct * 100.0, executed));
                        } catch (Exception ignore) {
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> mainView.showErrorMessage("介入失敗：" + e.getMessage(), "錯誤"));
                }
            }, pump ? "PumpPrice" : "DumpPrice").start();

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
    private void seedLiquidity(Trader actor, boolean pump, int totalQty, int depthLevels, double spanPct) {
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

            // pump：買牆靠近現價下方；dump：買墊腳石偏向更低（靠近 minPx）
            double hiBuy = pump ? last * (1.0 - 0.001) : last * (1.0 - Math.min(0.02, slip * 0.6));
            double loBuy = pump ? last * (1.0 - span) : last * (1.0 - Math.min(span + 0.02, slip * 0.95));

            hiBuy = Math.max(minPx, Math.min(maxPx, ob.adjustPriceToUnit(hiBuy)));
            loBuy = Math.max(minPx, Math.min(maxPx, ob.adjustPriceToUnit(loBuy)));
            if (loBuy > hiBuy) {
                double t = loBuy;
                loBuy = hiBuy;
                hiBuy = t;
            }

            int lv = Math.max(1, depthLevels);
            int per = Math.max(1, depthTotal / lv);

            double px = hiBuy;
            for (int i = 0; i < lv; i++) {
                int q = (i == lv - 1) ? (depthTotal - per * (lv - 1)) : per;
                if (q <= 0) break;

                // 線性往下分層，並調整到 tick
                double t = (lv == 1) ? 0.0 : (i / (double) (lv - 1));
                double raw = hiBuy + (loBuy - hiBuy) * t;
                px = ob.adjustPriceToUnit(raw);
                px = Math.max(minPx, Math.min(maxPx, px));

                // 資金檢查（避免拋例外打斷整段流程）
                if (actor.getAccount() == null) break;
                double need = px * q;
                if (actor.getAccount().getAvailableFunds() < need) {
                    // 資金不足就縮量
                    int q2 = (int) Math.floor(actor.getAccount().getAvailableFunds() / Math.max(0.01, px));
                    if (q2 <= 0) break;
                    q = q2;
                }

                StockMainAction.model.core.Order o = StockMainAction.model.core.Order.createLimitBuyOrder(px, q, actor);
                ob.submitBuyOrder(o, px);
            }
        } catch (Exception ignore) {
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
