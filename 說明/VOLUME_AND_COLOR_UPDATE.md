# K線與成交量顏色規則及均線更新

## 更新日期
2025-10-23

## 更新內容

### 1. K線紅漲綠跌配色

**修改位置**: `MainView.java` 第1809-1812行

**修改內容**:
- 上漲K線：紅色 `Color(239, 83, 80)`
- 下跌K線：綠色 `Color(38, 166, 154)`

```java
// TradingView 配色：紅漲綠跌（中國習慣）
candleRenderer.setUpPaint(new Color(239, 83, 80));       // 紅色上漲
candleRenderer.setDownPaint(new Color(38, 166, 154));    // 綠色下跌
```

**效果**: 符合中國大陸股市習慣，紅色代表上漲，綠色代表下跌。

---

### 2. 成交量柱紅漲綠跌配色

**修改位置**: `MainView.java` 第1964-1990行

**修改內容**:
- 創建自定義 `XYBarRenderer`
- 根據對應K線的漲跌決定成交量柱顏色
- 上漲：紅色 `Color(239, 83, 80, 180)` (帶透明度)
- 下跌：綠色 `Color(38, 166, 154, 180)` (帶透明度)

```java
XYBarRenderer volumeBarRenderer = new XYBarRenderer(0.2) {
    @Override
    public Paint getItemPaint(int series, int item) {
        // 根據對應K線的漲跌決定成交量柱顏色
        try {
            if (finalOhlcSeries != null && item < finalOhlcSeries.getItemCount()) {
                OHLCItem ohlcItem = (OHLCItem) finalOhlcSeries.getDataItem(item);
                if (ohlcItem != null) {
                    double close = ohlcItem.getCloseValue();
                    double open = ohlcItem.getOpenValue();
                    if (close >= open) {
                        return new Color(239, 83, 80, 180);  // 紅色上漲
                    } else {
                        return new Color(38, 166, 154, 180); // 綠色下跌
                    }
                }
            }
        } catch (Exception ignore) {}
        return new Color(100, 181, 246, 180); // 默認藍色
    }
};
```

**效果**: 成交量柱顏色與對應K線顏色保持一致，直觀反映市場買賣力量。

---

### 3. 成交量MA5和MA10均線

**新增字段**: `MainView.java` 第205-206行
```java
private XYSeries volumeMA5Series;  // 成交量MA5
private XYSeries volumeMA10Series; // 成交量MA10
```

**初始化**: `MainView.java` 第1947-2007行

**配色方案**:
- MA5: 橙色 `Color(255, 165, 0)` - 線寬1.2f
- MA10: 紫色 `Color(138, 43, 226)` - 線寬1.2f

```java
// 創建成交量MA系列
volumeMA5Series = new XYSeries("VOL MA5");
volumeMA5Series.setMaximumItemCount(2000);
volumeMA10Series = new XYSeries("VOL MA10");
volumeMA10Series.setMaximumItemCount(2000);

// 添加成交量MA5和MA10
XYSeriesCollection volumeMA5Dataset = new XYSeriesCollection(volumeMA5Series);
XYSeriesCollection volumeMA10Dataset = new XYSeriesCollection(volumeMA10Series);

XYLineAndShapeRenderer volumeMA5Renderer = new XYLineAndShapeRenderer(true, false);
volumeMA5Renderer.setSeriesPaint(0, new Color(255, 165, 0));  // 橙色
volumeMA5Renderer.setSeriesStroke(0, new BasicStroke(1.2f));

XYLineAndShapeRenderer volumeMA10Renderer = new XYLineAndShapeRenderer(true, false);
volumeMA10Renderer.setSeriesPaint(0, new Color(138, 43, 226)); // 紫色
volumeMA10Renderer.setSeriesStroke(0, new BasicStroke(1.2f));

volumePlot.setDataset(1, volumeMA5Dataset);
volumePlot.setRenderer(1, volumeMA5Renderer);
volumePlot.setDataset(2, volumeMA10Dataset);
volumePlot.setRenderer(2, volumeMA10Renderer);
```

**計算邏輯**: `MainView.java` 第2892-2933行

```java
// === [TradingView] 計算成交量MA5和MA10 ===
try {
    if (volumeXYSeries != null && volumeXYSeries.getItemCount() > 0) {
        // 清空MA系列
        volumeMA5Series.clear();
        volumeMA10Series.clear();
        
        int count = volumeXYSeries.getItemCount();
        
        // 計算MA5
        for (int i = 0; i < count; i++) {
            double sum = 0;
            int n = 0;
            for (int j = Math.max(0, i - 4); j <= i; j++) {
                sum += volumeXYSeries.getY(j).doubleValue();
                n++;
            }
            double ma5 = sum / n;
            volumeMA5Series.add(volumeXYSeries.getX(i), ma5);
        }
        
        // 計算MA10
        for (int i = 0; i < count; i++) {
            double sum = 0;
            int n = 0;
            for (int j = Math.max(0, i - 9); j <= i; j++) {
                sum += volumeXYSeries.getY(j).doubleValue();
                n++;
            }
            double ma10 = sum / n;
            volumeMA10Series.add(volumeXYSeries.getX(i), ma10);
        }
        
        // 限制MA系列數據點
        while (volumeMA5Series.getItemCount() > 600) {
            volumeMA5Series.remove(0);
        }
        while (volumeMA10Series.getItemCount() > 600) {
            volumeMA10Series.remove(0);
        }
    }
} catch (Exception ignore) {}
```

**效果**: 
- 成交量MA5顯示近5個週期的平均成交量
- 成交量MA10顯示近10個週期的平均成交量
- 有助於判斷成交量的趨勢變化

---

## 視覺效果

### 組合圖表佈局 (70% K線 + 30% 成交量)

```
┌─────────────────────────────────────────────────────┐
│  K線圖 (70%)                                        │
│  ┌────────────────────────────────────────┐         │
│  │ OHLC信息面板                           │         │
│  │ HH:mm:ss  +0.32 (+3.17%)               │         │
│  │ O: 10.00  H: 10.32  L: 10.00  C: 10.32 │         │
│  └────────────────────────────────────────┘         │
│                                                      │
│       ▓▓                                             │
│     ▓▓  ▓                                            │
│    ▓      ▓    ▓▓                                    │
│   ▓        ▓  ▓  ▓                                   │
│  ▓          ▓▓    ▓                                  │
│                    ▓                                 │
│  紅色=上漲  綠色=下跌                                │
│                                                      │
├─────────────────────────────────────────────────────┤
│  成交量圖 (30%)                                      │
│                                                      │
│  ▓     ▓                                             │
│  █  ▓  █  ▓                                          │
│  █  █  █  █  ▓  ─── MA5 (橙色)                       │
│  █  █  █  █  █  ─── MA10 (紫色)                      │
│                                                      │
│  紅柱=上漲  綠柱=下跌                                │
└─────────────────────────────────────────────────────┘
```

---

## 配色總結

| 元素 | 上漲顏色 | 下跌顏色 | 備註 |
|------|---------|---------|------|
| K線實體 | 紅色 #EF5350 | 綠色 #26A69A | 實心蠟燭 |
| 成交量柱 | 紅色 #EF5350 (透明度180) | 綠色 #26A69A (透明度180) | 根據K線漲跌 |
| 成交量MA5 | 橙色 #FFA500 | 橙色 #FFA500 | 線寬1.2f |
| 成交量MA10 | 紫色 #8A2BE2 | 紫色 #8A2BE2 | 線寬1.2f |

---

## 技術要點

### 1. 動態顏色渲染
- 使用自定義 `XYBarRenderer` 覆寫 `getItemPaint()` 方法
- 實時查詢對應K線數據判斷漲跌
- 異常處理確保渲染穩定性

### 2. 均線計算優化
- 每次成交量更新時重新計算MA
- 使用滑動窗口算法
- 限制數據點數量防止內存溢出

### 3. 數據同步
- K線和成交量使用相同的時間對齊機制
- 確保顏色和數據的一致性
- 支持秒級和分鐘級週期

---

## 使用說明

1. **K線圖**: 
   - 紅色K線表示該週期收盤價高於開盤價（上漲）
   - 綠色K線表示該週期收盤價低於開盤價（下跌）

2. **成交量柱**:
   - 紅色柱子表示該週期價格上漲
   - 綠色柱子表示該週期價格下跌
   - 柱子高度表示成交量大小

3. **成交量均線**:
   - 橙色線（MA5）：近5個週期的平均成交量
   - 紫色線（MA10）：近10個週期的平均成交量
   - 均線上穿表示成交量放大趨勢
   - 均線下穿表示成交量萎縮趨勢

---

## 相關文件

- `MainView.java`: 主視圖類，包含所有圖表邏輯
- `COMBINED_CHART_UPDATE.md`: 組合圖表整合文檔
- `TRADINGVIEW_STYLE_IMPROVEMENTS.md`: TradingView風格改進文檔
- `BUGFIX_NULLPOINTER.md`: 空指針修復文檔

---

## 更新歷史

- **2025-10-23**: 初始版本
  - 實現K線紅漲綠跌配色
  - 實現成交量柱紅漲綠跌配色
  - 添加成交量MA5和MA10均線

