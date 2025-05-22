package StockMainAction;

import StockMainAction.controller.StockMarketController;
import StockMainAction.model.StockMarketModel;
import StockMainAction.util.logging.LogViewerWindow;
import StockMainAction.util.logging.MarketLogger;
import StockMainAction.view.ControlView;
import StockMainAction.view.MainView;
import javax.swing.*;

/**
 * 股票市場模擬 - 應用程式入口點 基於MVC架構的主控程式，負責初始化並啟動各個組件
 */
public class StockMarketSimulation {

    private static final MarketLogger logger = MarketLogger.getInstance();

    /**
     * 主程式入口點
     *
     * @param args 命令列參數
     */
    public static void main(String[] args) {
        logger.info("股票市場模擬程式啟動", "APPLICATION_START");

        // 使用Swing的事件分派線程初始化UI
        SwingUtilities.invokeLater(() -> {
            try {
                // 1. 建立模型 (Model) - 包含所有業務邏輯和數據
                StockMarketModel model = new StockMarketModel();
                logger.info("市場模型初始化完成", "APPLICATION_START");

                // 2. 建立視圖 (View) - 用戶界面組件
                MainView mainView = new MainView();
                ControlView controlView = new ControlView();
                logger.info("界面視圖初始化完成", "APPLICATION_START");

                // 3. 建立控制器 (Controller) - 連接模型和視圖
                StockMarketController controller = new StockMarketController(model, mainView, controlView);
                logger.info("控制器初始化完成", "APPLICATION_START");

                // 4. 顯示視圖
                mainView.setVisible(true);
                controlView.setVisible(true);

                // 5. 開啟日誌查看器
                //關閉原因 已在StockMarketController構造函數啟用
//                new LogViewerWindow();

                // 6. 啟動模擬
                controller.startSimulation();
                logger.info("股票市場模擬程式初始化完成，模擬已啟動", "APPLICATION_START");

            } catch (Exception e) {
                logger.error("股票市場模擬程式啟動失敗", "APPLICATION_START");
                logger.error(e, "APPLICATION_START");
                JOptionPane.showMessageDialog(null,
                        "股票市場模擬程式啟動失敗：" + e.getMessage(),
                        "錯誤",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
