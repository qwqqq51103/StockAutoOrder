package StockMainAction.controller;

import StockMainAction.MatchingEnginePanel;
import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.MatchingMode;
import StockMainAction.model.core.OrderBook;
import StockMainAction.view.ControlView;
import StockMainAction.view.MainView;
import StockMainAction.view.OrderViewer;
import javafx.util.Pair;
import StockMainAction.util.logging.LogViewerWindow;
import StockMainAction.util.logging.MarketLogger;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 股票市場控制器 - 負責連接模型與視圖 作為MVC架構中的Controller組件
 */
public class StockMarketController implements StockMarketModel.ModelListener {

    private StockMarketModel model;
    private MainView mainView;
    private ControlView controlView;
    private static final MarketLogger logger = MarketLogger.getInstance();

    // 初始資金配置（用於損益計算）
    private final double initialRetailCash = 100000;
    private final double initialMainForceCash = 200000;

    /**
     * 構造函數
     */
    public StockMarketController(StockMarketModel model, MainView mainView, ControlView controlView) {
        this.model = model;
        this.mainView = mainView;
        this.controlView = controlView;

        // 註冊為模型監聽器
        model.addModelListener(this);

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

        // 打開日誌視窗
        openLogViewer();

        //初始化撮合引擎控制
        initializeMatchingEngineControl();
        logger.info("控制器初始化完成，包括撮合引擎控制", "CONTROLLER_INIT");
    }

    /**
     * 初始化按鈕動作
     */
    private void initializeButtonActions() {
        // 停止按鈕事件
        controlView.getStopButton().addActionListener(e -> {
            if (model.isRunning()) {
                model.stopAutoPriceFluctuation();
                controlView.getStopButton().setText("開始");
                mainView.appendToInfoArea("模擬已停止。");
            } else {
                model.startAutoPriceFluctuation();
                controlView.getStopButton().setText("停止");
                mainView.appendToInfoArea("模擬已開始。");
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
                    logger.error("成功更改撮合設置：模式=" + selectedMode
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
     * 處理限價買入操作
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

        } catch (NumberFormatException ex) {
            mainView.showErrorMessage("請輸入有效的數字。", "錯誤");
        }
    }

    /**
     * 處理限價賣出操作
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

        } catch (NumberFormatException ex) {
            mainView.showErrorMessage("請輸入有效的數字。", "錯誤");
        }
    }

    /**
     * 處理市價買入操作
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
            Pair<Integer, Double> result = model.calculateActualCost(
                    model.getOrderBook().getSellOrders(), quantity);
            int actualQuantity = result.getKey();
            double actualCost = result.getValue();

            // 檢查資金是否足夠
            if (actualQuantity > 0 && model.getUserInvestor().getAccount().getAvailableFunds() >= actualCost) {
                // 執行市價單
                boolean success = model.executeMarketBuy(actualQuantity);
                if (success) {
                    mainView.appendToInfoArea("市價買入 " + actualQuantity + " 股，實際成本：" + String.format("%.2f", actualCost));

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
     * 處理市價賣出操作
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
                Pair<Integer, Double> result = model.calculateActualRevenue(
                        model.getOrderBook().getBuyOrders(), quantity);
                int actualQuantity = result.getKey();
                double actualRevenue = result.getValue();

                // 執行市價賣出
                if (actualQuantity > 0) {
                    boolean success = model.executeMarketSell(actualQuantity);
                    if (success) {
                        mainView.appendToInfoArea("市價賣出 " + actualQuantity + " 股，實際收入：" + String.format("%.2f", actualRevenue));

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

    /**
     * 打開日誌查看視窗
     */
    private void openLogViewer() {
        SwingUtilities.invokeLater(() -> {
            new LogViewerWindow();
        });
    }

    // ======== 模型事件監聽器方法 ========
    @Override
    public void onPriceChanged(double price, double sma) {
        mainView.updatePriceChart(model.getTimeStep(), price, sma);
    }

    @Override
    public void onTechnicalIndicatorsUpdated(double volatility, double rsi, double wap) {
        mainView.updateTechnicalIndicators(model.getTimeStep(), volatility, rsi, wap);

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
    }

    @Override
    public void onInfoMessage(String message) {
        mainView.appendToInfoArea(message);
    }

    @Override
    public void onOrderBookChanged() {
        mainView.updateOrderBookDisplay(model.getOrderBook());
    }
}
