package StockMainAction.view;

import Core.MatchingMode;
import Core.Order;
import Core.OrderBook;
import StockMainAction.model.StockMarketModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 主視圖類別 - 負責顯示圖表和數據 作為MVC架構中的View組件
 */
public class MainView extends JFrame {

    // 圖表
    private JFreeChart priceChart;
    private JFreeChart volatilityChart;
    private JFreeChart rsiChart;
    private JFreeChart volumeChart;
    private JFreeChart wapChart;
    private JFreeChart retailProfitChart;
    private JFreeChart mainForceProfitChart;

    // 圖表數據
    private XYSeries priceSeries;
    private XYSeries smaSeries;
    private XYSeries volatilitySeries;
    private XYSeries rsiSeries;
    private XYSeries wapSeries;
    private DefaultCategoryDataset volumeDataset;
    private List<Color> colorList = new ArrayList<>();

    // UI組件
    private JLabel stockPriceLabel, retailCashLabel, retailStocksLabel, mainForceCashLabel, mainForceStocksLabel,
            targetPriceLabel, averageCostPriceLabel, fundsLabel, inventoryLabel, weightedAveragePriceLabel;
    private JTextArea infoTextArea;
    private OrderBookView orderBookView;
    private JTabbedPane tabbedPane;

    // 儲存最後一次更新的時間步長
    private int lastTimeStep = -1;

    /**
     * 構造函數
     */
    public MainView() {
        initializeChartData();
        initializeUI();
    }

    /**
     * 初始化圖表數據
     */
    private void initializeChartData() {
        // 初始化數據系列
        priceSeries = new XYSeries("股價");
        smaSeries = new XYSeries("SMA");
        volatilitySeries = new XYSeries("波動性");
        rsiSeries = new XYSeries("RSI");
        wapSeries = new XYSeries("加權平均價格");

        // 初始化成交量數據集
        volumeDataset = new DefaultCategoryDataset();
    }

    /**
     * 初始化UI組件
     */
    private void initializeUI() {
        // 設置視窗基本屬性
        setTitle("股票市場模擬 (MVC版)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);

        // 創建主分頁面板
        tabbedPane = new JTabbedPane();

        // 創建圖表
        createCharts();

        // 創建主分頁
        JPanel mainPanel = createMainPanel();
        tabbedPane.addTab("市場圖表", mainPanel);

        // 創建損益表分頁
        JPanel profitPanel = createProfitPanel();
        tabbedPane.addTab("損益表", profitPanel);

        // 創建技術指標分頁
        JPanel indicatorsPanel = createIndicatorsPanel();
        tabbedPane.addTab("技術指標", indicatorsPanel);

        // 添加分頁面板到視窗
        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        // 設置關閉事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                System.exit(0);
            }
        });
    }

    /**
     * 創建主分頁面板
     */
    private JPanel createMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 創建圖表面板
        JPanel chartPanel = new JPanel(new GridLayout(4, 1));
        chartPanel.add(new ChartPanel(priceChart));
        chartPanel.add(new ChartPanel(volatilityChart));
        chartPanel.add(new ChartPanel(rsiChart));
        chartPanel.add(new ChartPanel(volumeChart));

        // 創建標籤面板
        JPanel labelPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        initializeLabels(labelPanel);

        // 創建訂單簿視圖
        orderBookView = new OrderBookView();

        // 創建信息面板
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoTextArea = new JTextArea(8, 30);
        infoTextArea.setEditable(false);
        JScrollPane infoScrollPane = new JScrollPane(infoTextArea);

        // 將訂單簿和信息區域組合
        JSplitPane infoSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                orderBookView.getScrollPane(),
                infoScrollPane);
        infoSplitPane.setResizeWeight(0.7);

        // 將圖表和標籤區域組合
        JSplitPane chartLabelSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                chartPanel,
                labelPanel);
        chartLabelSplitPane.setResizeWeight(0.7);

        // 將左右兩部分組合
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                chartLabelSplitPane,
                infoSplitPane);
        mainSplitPane.setResizeWeight(0.8);

        mainPanel.add(mainSplitPane, BorderLayout.CENTER);

        return mainPanel;
    }

    /**
     * 創建損益表分頁
     */
    private JPanel createProfitPanel() {
        JPanel profitPanel = new JPanel(new GridLayout(2, 1));

        retailProfitChart = createProfitChart("散戶損益", "散戶", 1);
        mainForceProfitChart = createProfitChart("主力損益", "主力", 1);

        profitPanel.add(new ChartPanel(retailProfitChart));
        profitPanel.add(new ChartPanel(mainForceProfitChart));

        return profitPanel;
    }

    /**
     * 創建技術指標分頁
     */
    private JPanel createIndicatorsPanel() {
        JPanel indicatorsPanel = new JPanel(new GridLayout(3, 1));

        indicatorsPanel.add(new ChartPanel(volatilityChart));
        indicatorsPanel.add(new ChartPanel(rsiChart));
        indicatorsPanel.add(new ChartPanel(wapChart));

        return indicatorsPanel;
    }

    /**
     * 初始化UI標籤
     */
    private void initializeLabels(JPanel panel) {
        stockPriceLabel = createLabel(panel, "股票價格: 0.00");
        retailCashLabel = createLabel(panel, "散戶平均現金: 0.00");
        retailStocksLabel = createLabel(panel, "散戶平均持股: 0");
        mainForceCashLabel = createLabel(panel, "主力現金: 0.00");
        mainForceStocksLabel = createLabel(panel, "主力持有籌碼: 0");
        targetPriceLabel = createLabel(panel, "主力目標價位: 0.00");
        averageCostPriceLabel = createLabel(panel, "主力平均成本: 0.00");
        fundsLabel = createLabel(panel, "市場可用資金: 0.00");
        inventoryLabel = createLabel(panel, "市場庫存: 0");
        weightedAveragePriceLabel = createLabel(panel, "加權平均價格: 0.00");
    }

    /**
     * 創建標籤
     */
    private JLabel createLabel(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        panel.add(label);
        return label;
    }

    /**
     * 創建所有圖表
     */
    private void createCharts() {
        // 創建股價圖
        XYSeriesCollection priceDataset = new XYSeriesCollection();
        priceDataset.addSeries(priceSeries);
        priceDataset.addSeries(smaSeries);
        priceChart = createXYChart("股價走勢", "時間", "價格", priceDataset);

        // 創建波動性圖
        XYSeriesCollection volatilityDataset = new XYSeriesCollection();
        volatilityDataset.addSeries(volatilitySeries);
        volatilityChart = createXYChart("市場波動性", "時間", "波動性", volatilityDataset);

        // 創建RSI圖
        XYSeriesCollection rsiDataset = new XYSeriesCollection();
        rsiDataset.addSeries(rsiSeries);
        rsiChart = createXYChart("相對強弱指數 (RSI)", "時間", "RSI", rsiDataset);

        // 設置RSI圖的額外屬性
        XYPlot rsiPlot = rsiChart.getXYPlot();
        NumberAxis rsiRangeAxis = (NumberAxis) rsiPlot.getRangeAxis();
        rsiRangeAxis.setRange(0.0, 100.0);
        rsiPlot.addRangeMarker(new ValueMarker(70.0, Color.RED, new BasicStroke(1.0f)));
        rsiPlot.addRangeMarker(new ValueMarker(30.0, Color.GREEN, new BasicStroke(1.0f)));

        // 創建加權平均價格圖
        XYSeriesCollection wapDataset = new XYSeriesCollection();
        wapDataset.addSeries(wapSeries);
        wapChart = createXYChart("加權平均價格 (WAP)", "時間", "WAP", wapDataset);

        // 創建成交量圖
        volumeChart = ChartFactory.createBarChart(
                "成交量", "時間", "成交量", volumeDataset,
                PlotOrientation.VERTICAL, false, true, false
        );

        // 設置成交量圖的渲染器
        CategoryPlot volumePlot = volumeChart.getCategoryPlot();
        BarRenderer volumeRenderer = new BarRenderer() {
            @Override
            public Paint getItemPaint(int row, int column) {
                if (column < colorList.size()) {
                    return colorList.get(column);
                }
                return Color.BLUE;
            }
        };
        volumeRenderer.setBarPainter(new StandardBarPainter());
        volumePlot.setRenderer(volumeRenderer);

        // 設置圖表字體
        setChartFont(volumeChart);
        setChartFont(priceChart);
        setChartFont(volatilityChart);
        setChartFont(rsiChart);
        setChartFont(wapChart);
    }

    /**
     * 創建XY折線圖
     */
    private JFreeChart createXYChart(String title, String xAxisLabel, String yAxisLabel, XYSeriesCollection dataset) {
        JFreeChart chart = ChartFactory.createXYLineChart(
                title, xAxisLabel, yAxisLabel, dataset,
                PlotOrientation.VERTICAL, true, true, false
        );

        // 設置圖表字體和樣式
        setChartFont(chart);

        return chart;
    }

    /**
     * 創建損益柱狀圖
     */
    private JFreeChart createProfitChart(String title, String categoryPrefix, int count) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        // 初始化數據
        for (int i = 1; i <= count; i++) {
            dataset.addValue(0, "現金", categoryPrefix + i);
            dataset.addValue(0, "持股", categoryPrefix + i);
            dataset.addValue(0, "損益", categoryPrefix + i);
        }

        JFreeChart chart = ChartFactory.createBarChart(
                title, "分類", "數值", dataset,
                PlotOrientation.HORIZONTAL, true, true, false
        );

        // 設置字體
        setChartFont(chart);

        // 設置渲染器顏色
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);    // 現金
        renderer.setSeriesPaint(1, Color.GREEN);   // 持股
        renderer.setSeriesPaint(2, Color.ORANGE);  // 損益

        return chart;
    }

    /**
     * 設置圖表字體
     */
    private void setChartFont(JFreeChart chart) {
        Font titleFont = new Font("Microsoft JhengHei", Font.BOLD, 18);
        Font axisFont = new Font("Microsoft JhengHei", Font.PLAIN, 12);
        chart.getTitle().setFont(titleFont);

        // 設置坐標軸字體
        if (chart.getPlot() instanceof XYPlot) {
            XYPlot plot = (XYPlot) chart.getPlot();
            plot.getDomainAxis().setLabelFont(axisFont);
            plot.getDomainAxis().setTickLabelFont(axisFont);
            plot.getRangeAxis().setLabelFont(axisFont);
            plot.getRangeAxis().setTickLabelFont(axisFont);
        } else if (chart.getPlot() instanceof CategoryPlot) {
            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            plot.getDomainAxis().setLabelFont(axisFont);
            plot.getDomainAxis().setTickLabelFont(axisFont);
            plot.getRangeAxis().setLabelFont(axisFont);
            plot.getRangeAxis().setTickLabelFont(axisFont);

            // 設置圖例字體
            if (chart.getLegend() != null) {
                chart.getLegend().setItemFont(axisFont);
            }
        }
    }

    /**
     * 更新價格圖表
     */
    public void updatePriceChart(int timeStep, double price, double sma) {
        SwingUtilities.invokeLater(() -> {
            if (!Double.isNaN(price)) {
                priceSeries.add(timeStep, price);
                keepSeriesWithinLimit(priceSeries, 100);
            }

            if (!Double.isNaN(sma)) {
                smaSeries.add(timeStep, sma);
                keepSeriesWithinLimit(smaSeries, 100);
            }
        });
    }

    /**
     * 更新技術指標
     */
    public void updateTechnicalIndicators(int timeStep, double volatility, double rsi, double wap) {
        SwingUtilities.invokeLater(() -> {
            if (!Double.isNaN(volatility)) {
                volatilitySeries.add(timeStep, volatility);
                keepSeriesWithinLimit(volatilitySeries, 100);
            }

            if (!Double.isNaN(rsi)) {
                rsiSeries.add(timeStep, rsi);
                keepSeriesWithinLimit(rsiSeries, 100);
            }

            if (!Double.isNaN(wap)) {
                wapSeries.add(timeStep, wap);
                keepSeriesWithinLimit(wapSeries, 100);
            }
        });
    }

    /**
     * 更新成交量圖
     */
    public void updateVolumeChart(int timeStep, int volume) {
        SwingUtilities.invokeLater(() -> {
            // 只在時間步長變化時添加新的數據點
            if (timeStep > lastTimeStep) {
                // 移除舊數據，保持最多30個數據點
                while (volumeDataset.getColumnCount() >= 30) {
                    String firstKey = (String) volumeDataset.getColumnKeys().get(0);
                    volumeDataset.removeColumn(firstKey);
                    if (!colorList.isEmpty()) {
                        colorList.remove(0);
                    }
                }

                // 添加新數據
                volumeDataset.addValue(volume, "Volume", String.valueOf(timeStep));

                // 獲取價格變化來決定顏色
                double currentPrice = 0;
                double previousPrice = 0;

                if (priceSeries.getItemCount() > 0) {
                    currentPrice = priceSeries.getY(priceSeries.getItemCount() - 1).doubleValue();
                }

                if (priceSeries.getItemCount() > 1) {
                    previousPrice = priceSeries.getY(priceSeries.getItemCount() - 2).doubleValue();
                }

                // 根據價格變動確定顏色
                Color color = Color.BLUE; // 預設為藍色
                if (currentPrice > previousPrice) {
                    color = Color.GREEN; // 價格上漲 → 綠色
                } else if (currentPrice < previousPrice) {
                    color = Color.RED; // 價格下跌 → 紅色
                }

                colorList.add(color);
                lastTimeStep = timeStep;
            } else {
                // 更新現有數據
                String columnKey = String.valueOf(timeStep);
                if (volumeDataset.getColumnKeys().contains(columnKey)) {
                    Number existingValue = volumeDataset.getValue("Volume", columnKey);
                    int newValue = existingValue != null ? existingValue.intValue() + volume : volume;
                    volumeDataset.setValue(newValue, "Volume", columnKey);
                }
            }
        });
    }

    /**
     * 更新損益表
     */
    public void updateProfitChart(double retailCash, int retailStocks, double stockPrice, double initialRetailCash,
            double mainForceCash, int mainForceStocks, double initialMainForceCash) {
        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset retailDataset = (DefaultCategoryDataset) retailProfitChart.getCategoryPlot().getDataset();
            DefaultCategoryDataset mainForceDataset = (DefaultCategoryDataset) mainForceProfitChart.getCategoryPlot().getDataset();

            // 更新散戶數據
            String retailCategory = "散戶1";
            retailDataset.setValue(retailCash, "現金", retailCategory);
            retailDataset.setValue(retailStocks, "持股", retailCategory);
            double retailProfit = (retailStocks * stockPrice) + retailCash - initialRetailCash;
            retailDataset.setValue(retailProfit, "損益", retailCategory);

            // 更新主力數據
            String mainCategory = "主力1";
            mainForceDataset.setValue(mainForceCash, "現金", mainCategory);
            mainForceDataset.setValue(mainForceStocks, "持股", mainCategory);
            double mainForceProfit = (mainForceStocks * stockPrice) + mainForceCash - initialMainForceCash;
            mainForceDataset.setValue(mainForceProfit, "損益", mainCategory);
        });
    }

    /**
     * 更新市場狀態標籤
     */
    public void updateMarketStateLabels(double price, double retailCash, int retailStocks,
            double mainForceCash, int mainForceStocks,
            double targetPrice, double avgCostPrice,
            double funds, int inventory,
            double wap) {
        SwingUtilities.invokeLater(() -> {
            stockPriceLabel.setText("股票價格: " + String.format("%.2f", price));
            retailCashLabel.setText("散戶平均現金: " + String.format("%.2f", retailCash));
            retailStocksLabel.setText("散戶平均持股: " + retailStocks);
            mainForceCashLabel.setText("主力現金: " + String.format("%.2f", mainForceCash));
            mainForceStocksLabel.setText("主力持有籌碼: " + mainForceStocks);
            targetPriceLabel.setText("主力目標價位: " + String.format("%.2f", targetPrice));
            averageCostPriceLabel.setText("主力平均成本: " + String.format("%.2f", avgCostPrice));
            fundsLabel.setText("市場可用資金: " + String.format("%.2f", funds));
            inventoryLabel.setText("市場庫存: " + inventory);
            weightedAveragePriceLabel.setText("加權平均價格: " + String.format("%.2f", wap));
        });
    }

    /**
     * 更新訂單簿顯示
     */
    public void updateOrderBookDisplay(OrderBook orderBook) {
        SwingUtilities.invokeLater(() -> {
            orderBookView.updateOrderBookDisplay(orderBook);
        });
    }

    /**
     * 添加信息到文本區域
     */
    public void appendToInfoArea(String message) {
        SwingUtilities.invokeLater(() -> {
            infoTextArea.append(message + "\n");
            // 自動滾動到最新內容
            infoTextArea.setCaretPosition(infoTextArea.getDocument().getLength());
        });
    }

    /**
     * 顯示輸入對話框
     */
    public String showInputDialog(String message, String title, int messageType) {
        return JOptionPane.showInputDialog(this, message, title, messageType);
    }

    /**
     * 顯示確認對話框
     */
    public int showConfirmDialog(String message, String title, int optionType) {
        return JOptionPane.showConfirmDialog(this, message, title, optionType);
    }

    /**
     * 顯示錯誤消息
     */
    public void showErrorMessage(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * 顯示信息消息
     */
    public void showInfoMessage(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 限制數據點數量，只保留最新的一定數量的數據
     */
    private void keepSeriesWithinLimit(XYSeries series, int maxPoints) {
        while (series.getItemCount() > maxPoints) {
            series.remove(0);
        }
    }
}
