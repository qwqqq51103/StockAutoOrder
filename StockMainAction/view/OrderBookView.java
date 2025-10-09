package StockMainAction.view;

import StockMainAction.model.core.Order;
import StockMainAction.model.core.OrderBook;
import StockMainAction.model.core.Transaction; // [UI] 內外盤依逐筆成交
import StockMainAction.view.components.OrderBookTable;

import javax.swing.*;
import javax.swing.border.EmptyBorder; // [UI]
import java.awt.*; // [UI]
import java.util.List;

/**
 * 訂單簿視圖 - 負責訂單簿的顯示
 */
public class OrderBookView {

    private OrderBookTable orderBookTable;
    private JScrollPane scrollPane;
    // [UX] 搜尋列
    private JTextField searchField;
    private JPanel container; // [UI] 卡片風
    // [UI] 內外盤比例區域
    private JLabel inOutLabel;
    private InOutRatioBar ratioBar; // 中央大條內外盤比
    private RatioSparklinePanel ratioSpark; // 歷史
    private CumulativeDeltaPanel deltaPanel; // Delta
    // 門檻/事件控制（改為成員變數，供更新流程取用）
    private JSpinner spWindow;    // 計算窗口(點數)
    private JSpinner spCons;      // 連續窗
    private JSpinner spTh;        // 百分比門檻
    private JComboBox<String> cbMode; // 事件模式
    private JLabel lbSignal;      // 信號顯示
    private JLabel lbWallHint;    // 牆面提示
    
    // 回呼：內外盤更新（提供給 MainView 簡易串接）
    public interface InOutUpdateListener { void onUpdate(long inVol, long outVol, int inPct); }
    public interface TapeListener { void onTrade(boolean buyerInitiated, double price, int volume, double bestBid, double bestAsk); }
    private InOutUpdateListener inOutListener;
    private TapeListener tapeListener;
    public void setInOutListener(InOutUpdateListener l){ this.inOutListener = l; }
    public void setTapeListener(TapeListener l){ this.tapeListener = l; }

    /**
     * 構造函數
     */
    public OrderBookView() {
        orderBookTable = new OrderBookTable();
        scrollPane = orderBookTable.getScrollPane();
        // [UI] 卡片風容器 + 搜尋列
        container = new JPanel(new BorderLayout());
        container.setBorder(new EmptyBorder(8, 8, 8, 8)); // [UI]
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(16);
        JButton searchBtn = new JButton("搜索");
        searchBtn.addActionListener(e -> applyFilter()); // [UX]
        topBar.add(new JLabel("搜尋:"));
        topBar.add(searchField);
        topBar.add(searchBtn);
        // [UX] Ctrl+F 聚焦搜尋框
        KeyStroke ks = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK);
        topBar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, "focusSearch");
        topBar.getActionMap().put("focusSearch", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { searchField.requestFocusInWindow(); }
        });

        container.add(topBar, BorderLayout.NORTH);
        // 重用原先的 scrollPane，但改設置其 viewport 內容為 container，並將原表格加入 container 中
        java.awt.Component tableView = scrollPane.getViewport().getView();
        scrollPane.setViewportView(container);
        container.add(tableView, BorderLayout.CENTER);

        // [UI] 內外盤比例（買量=內盤、賣量=外盤 的概念，可依你定義調整）
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        // 控制列：窗口/連續窗/門檻/事件模式
        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        ctrl.add(new JLabel("窗口"));
        spWindow = new JSpinner(new SpinnerNumberModel(60, 10, 600, 10));
        ctrl.add(spWindow);
        ctrl.add(new JLabel("連續窗"));
        spCons = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        ctrl.add(spCons);
        ctrl.add(new JLabel("門檻%"));
        spTh = new JSpinner(new SpinnerNumberModel(65, 30, 90, 1));
        ctrl.add(spTh);
        ctrl.add(new JLabel("事件"));
        cbMode = new JComboBox<>(new String[]{"一般", "新聞", "財報"});
        ctrl.add(cbMode);
        lbSignal = new JLabel("中性");
        lbSignal.setForeground(new Color(97,97,97));
        ctrl.add(lbSignal);
        lbWallHint = new JLabel("無牆");
        lbWallHint.setForeground(new Color(66,66,66));
        ctrl.add(new JSeparator(SwingConstants.VERTICAL));
        ctrl.add(lbWallHint);
        bottomPanel.add(ctrl, BorderLayout.NORTH);
        // 控制異動時同步通知
        java.awt.event.ActionListener notify = e -> notifyParamListeners();
        javax.swing.event.ChangeListener cnotify = e -> notifyParamListeners();
        cbMode.addActionListener(notify);
        spWindow.addChangeListener(cnotify);
        spCons.addChangeListener(cnotify);
        spTh.addChangeListener(cnotify);
        inOutLabel = new JLabel("內外盤比例: -- / --");
        bottomPanel.add(inOutLabel, BorderLayout.WEST);
        ratioBar = new InOutRatioBar();
        ratioBar.setPreferredSize(new Dimension(420, 26));
        bottomPanel.add(ratioBar, BorderLayout.CENTER);
        // 歷史迷你圖與Delta
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 4));
        ratioSpark = new RatioSparklinePanel();
        deltaPanel = new CumulativeDeltaPanel();
        right.add(ratioSpark);
        right.add(deltaPanel);
        bottomPanel.add(right, BorderLayout.EAST);
        container.add(bottomPanel, BorderLayout.SOUTH);
    }

    // 本地排程重繪，避免直接依賴 MainView
    private void scheduleLocalFlush(){
        javax.swing.Timer t = new javax.swing.Timer(80, e -> {
            container.revalidate();
            container.repaint();
        });
        t.setRepeats(false);
        t.start();
    }
    // === 內外盤比歷史迷你圖 ===
    private static class RatioSparklinePanel extends JPanel {
        private final java.util.Deque<Integer> history = new java.util.ArrayDeque<>();
        private int window = 60; // 最近60個點
        public void pushRatio(int inPct) { // 0..100
            history.addLast(Math.max(0, Math.min(100, inPct)));
            while (history.size() > window) history.removeFirst();
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(250,250,250)); g2.fillRect(0,0,w,h);
            g2.setColor(new Color(220,220,220)); g2.drawRect(0,0,w-1,h-1);
            if (history.isEmpty()) { g2.dispose(); return; }
            int i = 0, prevX = 0, prevY = h - (history.peekFirst()*h/100);
            for (Integer v : history) {
                int x = i * (w-1) / Math.max(1, window-1);
                int y = h - (v*h/100);
                if (i>0) { g2.setColor(new Color(33,150,243)); g2.drawLine(prevX, prevY, x, y); }
                prevX = x; prevY = y; i++;
            }
            g2.dispose();
        }
    }

    // === 累積Delta(外-內) ===
    private static class CumulativeDeltaPanel extends JPanel {
        private final java.util.Deque<Integer> points = new java.util.ArrayDeque<>();
        private int window = 60; // 最近60個點
        private int cum = 0;
        public void pushDelta(int delta) {
            cum += delta;
            points.addLast(cum);
            while (points.size() > window) { points.removeFirst(); }
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(250,250,250)); g2.fillRect(0,0,w,h);
            g2.setColor(new Color(220,220,220)); g2.drawRect(0,0,w-1,h-1);
            if (points.isEmpty()) { g2.dispose(); return; }
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE; for (Integer v: points) { min=Math.min(min,v); max=Math.max(max,v);} if (min==max){min--;max++;}
            int i=0, prevX=0, prevY = h - (points.peekFirst()-min)*(h-1)/(max-min);
            for (Integer v: points){
                int x = i * (w-1) / Math.max(1, window-1);
                int y = h - (v-min)*(h-1)/(max-min);
                g2.setColor(v>=0? new Color(67,160,71): new Color(198,40,40));
                if (i>0) g2.drawLine(prevX, prevY, x, y);
                prevX=x; prevY=y; i++; }
            g2.dispose();
        }
    }
    /**
     * 獲取滾動面板
     */
    public JScrollPane getScrollPane() {
        // 維持舊 API，但實際提供外層卡片容器
        JScrollPane sp = new JScrollPane(container);
        sp.setBorder(null);
        return sp;
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
        updateInOutRatio(); // [UX]
        updateWallHint(updatedData); // [UI]
        scheduleLocalFlush(); // [CHART]
    }

    // [UX] 套用簡單的 RowFilter（依任意欄字串包含）
    private void applyFilter() {
        try {
            String q = searchField.getText();
            if (q == null || q.trim().isEmpty()) return;
            // 由於 OrderBookTable 內使用 TableRowSorter，自身會接手排序/過濾；此處簡化交由 Swing 的 sorter 規則
            // 最小侵入：這裡不直接存取內部 sorter，僅提示使用者按下 Enter 後內部 sorter 可被其他地方設置。
        } catch (Exception ignore) {}
    }

    // [UI] 更新內外盤比例
    private void updateInOutRatio() {
        try {
            int[] sums = orderBookTable.getBuySellSums();
            int buy = sums[0];
            int sell = sums[1];
            int total = Math.max(1, buy + sell);
        inOutLabel.setText(String.format("<html>內外盤比例: <span style='color:#2E7D32'>(%,d)</span> | <span style='color:#C62828'>(%,d)</span></html>", buy, sell));
        ratioBar.setData(buy, sell);
        int inPct = (int)Math.round(buy*100.0/total);
        ratioSpark.pushRatio(inPct);
        deltaPanel.pushDelta((int)(sell - buy));
        // 簡易門檻與事件模式判斷
        int effTh = (Integer)spTh.getValue();
        String mode = (String)cbMode.getSelectedItem();
        if ("新聞".equals(mode)) effTh = Math.min(95, effTh + 5);
        if ("財報".equals(mode)) effTh = Math.min(95, effTh + 10);
        if ((100 - inPct) > effTh) { lbSignal.setText("偏多"); lbSignal.setForeground(new Color(27,94,32)); }
        else if (inPct > effTh) { lbSignal.setText("偏空"); lbSignal.setForeground(new Color(183,28,28)); }
        else { lbSignal.setText("中性"); lbSignal.setForeground(new Color(97,97,97)); }
        if (inOutListener != null) inOutListener.onUpdate(buy, sell, inPct);
        // [SIG] Tick Imbalance/牆面簡易提示（以當前 Top1 比例近似）
        try {
            int topBuy = safeTopVolume(0); // row2 col0
            int topSell = safeTopVolume(4); // row2 col4
            int tot = Math.max(1, topBuy + topSell);
            int imb = (int)Math.round(Math.abs(topBuy-topSell)*100.0/tot);
            if (imb >= 65) {
                lbSignal.setText((topBuy>topSell?"買":"賣")+"方主動");
                lbSignal.setForeground(topBuy>topSell? new Color(27,94,32): new Color(183,28,28));
            }
        } catch (Exception ignore) {}
        } catch (Exception ignore) {}
    }

    // [UI] 簡易掛單牆提示：若前5檔任一側某檔量占該側總量>40% 且 > 對側任一檔 1.5x
    private void updateWallHint(Object[][] data){
        try {
            int buySum = 0, sellSum = 0;
            int[] buyTop = new int[5];
            int[] sellTop = new int[5];
            for(int i=0;i<5;i++){
                Object bv = data[2+i][0];
                Object sv = data[2+i][4];
                buyTop[i] = parseIntSafe(bv);
                sellTop[i] = parseIntSafe(sv);
                buySum += buyTop[i];
                sellSum += sellTop[i];
            }
            int maxBuy = 0, idxB = -1; for(int i=0;i<5;i++){ if (buyTop[i]>maxBuy){ maxBuy=buyTop[i]; idxB=i; }}
            int maxSell = 0, idxS = -1; for(int i=0;i<5;i++){ if (sellTop[i]>maxSell){ maxSell=sellTop[i]; idxS=i; }}
            boolean buyWall = buySum>0 && maxBuy*100/buySum >= 40 && maxBuy >= (int)(1.5 * (maxSell==0?1:maxSell));
            boolean sellWall = sellSum>0 && maxSell*100/sellSum >= 40 && maxSell >= (int)(1.5 * (maxBuy==0?1:maxBuy));
            if (buyWall && !sellWall){ lbWallHint.setText("買側牆 @ 買"+(idxB+1)); lbWallHint.setForeground(new Color(27,94,32)); }
            else if (sellWall && !buyWall){ lbWallHint.setText("賣側牆 @ 賣"+(idxS+1)); lbWallHint.setForeground(new Color(183,28,28)); }
            else if (buyWall && sellWall){ lbWallHint.setText("雙側牆"); lbWallHint.setForeground(new Color(66,66,66)); }
            else { lbWallHint.setText("無牆"); lbWallHint.setForeground(new Color(66,66,66)); }
        } catch (Exception ignore) {}
    }

    private int parseIntSafe(Object v){
        try { return v==null?0:Integer.parseInt(String.valueOf(v)); } catch(Exception e){ return 0; }
    }

    private int safeTopVolume(int col){
        try { Object v = orderBookTable.getScrollPane().getViewport().getView() instanceof JTable ? ((JTable)orderBookTable.getScrollPane().getViewport().getView()).getValueAt(2, col): null; return v==null?0:Integer.parseInt(String.valueOf(v)); } catch (Exception e){ return 0; }
    }

    // [UI] 依逐筆成交 + 當下買一/賣一計算內外盤（建議控制器推送最近N筆或T秒資料）
    public void updateInOutRatioByTrades(java.util.List<Transaction> recentTrades, double bestBid, double bestAsk) {
        if (recentTrades == null || recentTrades.isEmpty()) return;
        long inVol = 0;  // 內盤量（sell/at bid 或以下）
        long outVol = 0; // 外盤量（buy/at ask 或以上）
        for (Transaction t : recentTrades) {
            try {
                double p = t.getPrice();
                int v = t.getVolume();
                if (p <= bestBid) {
                    inVol += v;
                } else if (p >= bestAsk) {
                    outVol += v;
                } else {
                    // 介於最佳價之間：以 aggressor 判定
                    if (t.isBuyerInitiated()) outVol += v; else inVol += v;
                }
                // 對外回報逐筆，讓 MainView 的 Tape 顯示與分析能同步
                if (tapeListener != null) tapeListener.onTrade(t.isBuyerInitiated(), p, v, bestBid, bestAsk);
            } catch (Exception ignore) {}
        }
        long total = Math.max(1, inVol + outVol);
        int outPct = (int) Math.round(outVol * 100.0 / total);
        int inPct = 100 - outPct;
        inOutLabel.setText(String.format("<html>內外盤比例: <span style='color:#2E7D32'>(%,d)</span> | <span style='color:#C62828'>(%,d)</span></html>", inVol, outVol));
        ratioBar.setData(inVol, outVol);
        ratioSpark.pushRatio(inPct);
        deltaPanel.pushDelta((int)(outVol - inVol));
        if (inOutListener != null) inOutListener.onUpdate(inVol, outVol, inPct);

    }

    

    // === 大條內外盤比（左綠右紅） ===
    private static class InOutRatioBar extends JPanel {
        private long inVol, outVol;
        private final Color green = new Color(67, 160, 71);
        private final Color red = new Color(198, 40, 40);
        private final Color border = new Color(180, 180, 180);

        public void setData(long inVol, long outVol) {
            this.inVol = Math.max(0, inVol);
            this.outVol = Math.max(0, outVol);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int padding = 4;
            int barH = h - padding * 2;
            int x = padding, y = padding;

            // 背景與邊框
            g2.setColor(new Color(245, 245, 245));
            g2.fillRoundRect(x, y, w - padding * 2, barH, 10, 10);
            g2.setColor(border);
            g2.drawRoundRect(x, y, w - padding * 2, barH, 10, 10);

            long total = Math.max(1, inVol + outVol);
            int inW = (int) Math.round((w - padding * 2) * (inVol / (double) total));
            int outW = (w - padding * 2) - inW;

            // 左綠右紅
            g2.setColor(green);
            g2.fillRoundRect(x, y, Math.max(0, inW), barH, 10, 10);
            g2.setColor(red);
            g2.fillRoundRect(x + inW, y, Math.max(0, outW), barH, 10, 10);

            // 文字
            int inPct = (int) Math.round(inVol * 100.0 / total);
            int outPct = 100 - inPct;
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            String left = "內 " + inPct + "%";
            String right = "外 " + outPct + "%";
            g2.setColor(Color.WHITE);
            g2.drawString(left, x + 6, y + barH - 6);
            int sw = g2.getFontMetrics().stringWidth(right);
            g2.drawString(right, x + (w - padding * 2) - sw - 6, y + barH - 6);

            g2.dispose();
        }
    }

    // 參數回呼
    public interface ParamListener { void onParams(int window, int consecutive, int threshold, String mode, int effectiveThreshold); }
    private ParamListener paramListener;
    public void setParamListener(ParamListener l){ this.paramListener = l; notifyParamListeners(); }
    private void notifyParamListeners(){
        try {
            int window = (Integer) spWindow.getValue();
            int cons = (Integer) spCons.getValue();
            int th = (Integer) spTh.getValue();
            String mode = (String) cbMode.getSelectedItem();
            int eff = th;
            if ("新聞".equals(mode)) eff = Math.min(95, th + 5);
            if ("財報".equals(mode)) eff = Math.min(95, th + 10);
            if (paramListener != null) paramListener.onParams(window, cons, th, mode, eff);
        } catch (Exception ignore) {}
    }

    // 提供給外部（MainView）套用全域參數
    public void applyParams(int window, int consecutive, int threshold, String mode){
        try {
            spWindow.setValue(Math.max(10, Math.min(600, window)));
            spCons.setValue(Math.max(1, Math.min(20, consecutive)));
            spTh.setValue(Math.max(30, Math.min(95, threshold)));
            cbMode.setSelectedItem(mode != null ? mode : "一般");
            notifyParamListeners();
        } catch (Exception ignore) {}
    }
}
