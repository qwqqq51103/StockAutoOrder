package StockMainAction.view;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.view.components.OrderBookTable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 訂單簿視圖 - 負責訂單簿的顯示
 */
public class OrderBookView {

    private OrderBookTable orderBookTable;
    private JScrollPane scrollPane;

    /**
     * 構造函數
     */
    public OrderBookView() {
        orderBookTable = new OrderBookTable();
        scrollPane = orderBookTable.getScrollPane();
    }

    /**
     * 獲取滾動面板
     */
    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    /**
     * 更新訂單簿顯示
     */
    public void updateOrderBookDisplay(OrderBook orderBook) {
        if (orderBook == null) {
            return;
        }

        // 創建一個更大的數據表以容納更多信息
        // 買單部分: [數量, 價格, 類型]
        // 賣單部分: [價格, 數量, 類型]
        Object[][] updatedData = new Object[12][6]; // 增加兩行用於顯示標題和撮合模式

        // 第一行顯示當前撮合模式
        updatedData[0][0] = "當前撮合模式:";
        updatedData[0][1] = orderBook.getMatchingMode().toString();
        updatedData[0][2] = "";
        updatedData[0][3] = "";
        updatedData[0][4] = "";
        updatedData[0][5] = "";

        // 第二行顯示列標題
        updatedData[1][0] = "買單數量";
        updatedData[1][1] = "買單價格";
        updatedData[1][2] = "買單類型";
        updatedData[1][3] = "賣單價格";
        updatedData[1][4] = "賣單數量";
        updatedData[1][5] = "賣單類型";

        List<Order> buyOrders = orderBook.getTopBuyOrders(10);
        List<Order> sellOrders = orderBook.getTopSellOrders(10);

        for (int i = 0; i < 10; i++) {
            int rowIndex = i + 2; // 前兩行已用於標題

            // 填充買單
            if (i < buyOrders.size()) {
                Order buyOrder = buyOrders.get(i);
                if (buyOrder != null && buyOrder.getTrader() != null) {
                    updatedData[rowIndex][0] = buyOrder.getVolume();

                    // 處理市價單的價格顯示
                    if (buyOrder.isMarketOrder()) {
                        updatedData[rowIndex][1] = "市價";
                    } else {
                        updatedData[rowIndex][1] = String.format("%.2f", buyOrder.getPrice());
                    }

                    // 顯示訂單類型
                    if (buyOrder.isMarketOrder()) {
                        updatedData[rowIndex][2] = "市價單";
                    } else if (buyOrder.isFillOrKill()) {
                        updatedData[rowIndex][2] = "FOK單";
                    } else {
                        updatedData[rowIndex][2] = "限價單";
                    }
                } else {
                    updatedData[rowIndex][0] = "";
                    updatedData[rowIndex][1] = "";
                    updatedData[rowIndex][2] = "";
                }
            } else {
                updatedData[rowIndex][0] = "";
                updatedData[rowIndex][1] = "";
                updatedData[rowIndex][2] = "";
            }

            // 填充賣單
            if (i < sellOrders.size()) {
                Order sellOrder = sellOrders.get(i);
                if (sellOrder != null && sellOrder.getTrader() != null) {
                    // 處理市價單的價格顯示
                    if (sellOrder.isMarketOrder()) {
                        updatedData[rowIndex][3] = "市價";
                    } else {
                        updatedData[rowIndex][3] = String.format("%.2f", sellOrder.getPrice());
                    }

                    updatedData[rowIndex][4] = sellOrder.getVolume();

                    // 顯示訂單類型
                    if (sellOrder.isMarketOrder()) {
                        updatedData[rowIndex][5] = "市價單";
                    } else if (sellOrder.isFillOrKill()) {
                        updatedData[rowIndex][5] = "FOK單";
                    } else {
                        updatedData[rowIndex][5] = "限價單";
                    }
                } else {
                    updatedData[rowIndex][3] = "";
                    updatedData[rowIndex][4] = "";
                    updatedData[rowIndex][5] = "";
                }
            } else {
                updatedData[rowIndex][3] = "";
                updatedData[rowIndex][4] = "";
                updatedData[rowIndex][5] = "";
            }
        }

        // 使用修改後的數據更新訂單簿表格
        orderBookTable.updateData(updatedData);
    }
}
