# 信號形狀更新與買賣盤失衡觸發機制說明

## 📅 更新時間
2025-10-23

---

## ✅ 已完成修改

### 1. 多頭信號形狀：正三角形 ▲（紅色）
```java
// 多頭信號：正三角形（指向上）▲
java.awt.Polygon upTriangle = new java.awt.Polygon();
upTriangle.addPoint(0, -5);   // 頂點
upTriangle.addPoint(-4, 3);   // 左下
upTriangle.addPoint(4, 3);    // 右下

rSignals.setSeriesShape(0, upTriangle);     // 多頭：紅色正三角形
rSignals.setSeriesPaint(0, new Color(239, 83, 80));  // 紅色 #EF5350
```

### 2. 空頭信號形狀：倒三角形 ▼（綠色）
```java
// 空頭信號：倒三角形（指向下）▼
java.awt.Polygon downTriangle = new java.awt.Polygon();
downTriangle.addPoint(0, 5);    // 底部頂點
downTriangle.addPoint(-4, -3);  // 左上
downTriangle.addPoint(4, -3);   // 右上

rSignals.setSeriesShape(1, downTriangle);   // 空頭：綠色倒三角形
rSignals.setSeriesPaint(1, new Color(38, 166, 154));  // 綠色 #26A69A
```

### 3. 信號標記對照表

| 信號類型 | 形狀 | 顏色 | RGB | HEX | 視覺標記 |
|---------|------|------|-----|-----|---------|
| **多頭信號** | 正三角形 ▲ | 🔴 紅色 | `239, 83, 80` | `#EF5350` | 🔴 ▲ |
| **空頭信號** | 倒三角形 ▼ | 🟢 綠色 | `38, 166, 154` | `#26A69A` | 🟢 ▼ |
| **大買單** | 圓形 ● | 🔴 紅色 | `239, 83, 80` | `#EF5350` | 🔴 ● |
| **大賣單** | 圓形 ● | 🟢 綠色 | `38, 166, 154` | `#26A69A` | 🟢 ● |
| **買盤失衡** | 圓形 ● | 🟠 橙色 | `255, 152, 0` | `#FF9800` | 🟠 ↑ |
| **賣盤失衡** | 圓形 ● | 🟣 紫色 | `156, 39, 176` | `#9C27B0` | 🟣 ↓ |

---

## 📊 買賣盤失衡觸發機制詳解

### 計算原理

買賣盤失衡（Tick Imbalance）是通過分析最近 N 筆交易中買方主動與賣方主動的筆數差異來判斷市場短期供需失衡狀態。

### 核心計算邏輯

**位置：** `StockMarketModel.java` - `getRecentTickImbalance(int n)` 方法

```java
public double getRecentTickImbalance(int n) {
    try {
        // 取得最近 N 筆交易記錄
        List<Transaction> recent = getRecentTransactions(Math.max(1, n));
        if (recent.isEmpty()) return 0.0;
        
        // 統計買賣主動筆數
        int buy = 0, sell = 0;
        for (Transaction t : recent) {
            if (t.isBuyerInitiated())  // 買方主動成交
                buy++;
            else                       // 賣方主動成交
                sell++;
        }
        
        // 計算失衡度：(買-賣)/(買+賣)
        int tot = Math.max(1, buy + sell);
        return (buy - sell) / (double) tot;  // 範圍：[-1, 1]
    } catch (Exception e) {
        return 0.0;
    }
}
```

### 失衡度判讀

| 失衡度範圍 | 市場狀態 | 信號類型 | 含義 |
|-----------|---------|---------|------|
| `> 0.25` | **強烈買盤失衡** | 🟠 買盤失衡 ↑ | 最近 N 筆交易中，買方主動的筆數遠多於賣方，顯示積極買盤湧入 |
| `0.05 ~ 0.25` | 輕微偏多 | - | 買盤稍微活躍 |
| `-0.05 ~ 0.05` | 均衡 | - | 買賣力道相當 |
| `-0.25 ~ -0.05` | 輕微偏空 | - | 賣盤稍微活躍 |
| `< -0.25` | **強烈賣盤失衡** | 🟣 賣盤失衡 ↓ | 最近 N 筆交易中，賣方主動的筆數遠多於買方，顯示恐慌賣壓 |

### 主力策略中的應用

**位置：** `MainForceStrategyWithOrderBook.java`

主力在做決策時會參考 `tickImbalance`：

```java
// 買盤失衡度計算（取最近 50 筆交易）
InOutTapeSignal sig = computeInOutAndSpeed();

// 做多條件（範例）
if (recentTrend > 0.02 || sig.outPct >= 65 || sig.tickImbalance > 0.25) {
    // 當趨勢向上、外盤佔比高、或買盤失衡度 > 0.25 時，考慮做多
}

// 做空條件（範例）
if (recentTrend < -0.03 || sig.inPct >= 65 || sig.tickImbalance < -0.25) {
    // 當趨勢向下、內盤佔比高、或賣盤失衡度 < -0.25 時，考慮做空
}
```

### 失衡度計算細節（MainForceStrategyWithOrderBook）

```java
private InOutTapeSignal computeInOutAndSpeed() {
    InOutTapeSignal s = new InOutTapeSignal();
    List<Transaction> recent = model.getRecentTransactions(50);  // 取最近 50 筆
    
    int buyTicks = 0, sellTicks = 0;
    for (Transaction t : recent) {
        if (t.isBuyerInitiated()) {
            buyTicks++;   // 買方主動成交（通常以賣價成交）
        } else {
            sellTicks++;  // 賣方主動成交（通常以買價成交）
        }
    }
    
    int n = Math.max(1, buyTicks + sellTicks);
    s.tickImbalance = (buyTicks - sellTicks) / (double) n;
    
    return s;
}
```

---

## 🔍 買賣主動判定

### 什麼是「買方主動」與「賣方主動」？

在撮合交易中：

| 成交類型 | 定義 | 掛單簿狀態 | 市場心理 |
|---------|------|-----------|---------|
| **買方主動** | 市價買單或限價買單「吃掉」賣單 | 以賣一價格成交 | 積極買盤，願意付出更高價格 |
| **賣方主動** | 市價賣單或限價賣單「吃掉」買單 | 以買一價格成交 | 急於出脫，願意接受較低價格 |

### 範例場景

**掛單簿狀態：**
```
賣五: 100.50  (50張)
賣四: 100.45  (100張)
賣三: 100.40  (150張)
賣二: 100.35  (200張)
賣一: 100.30  (300張)  ← 最佳賣價
─────────────────────
買一: 100.25  (250張)  ← 最佳買價
買二: 100.20  (180張)
買三: 100.15  (120張)
```

1. **買方主動成交：**
   - 投資人 A 掛「市價買入 100 張」
   - 系統自動以「賣一價 100.30」成交 100 張
   - 記錄為 `isBuyerInitiated = true`
   - 統計為「外盤」成交

2. **賣方主動成交：**
   - 投資人 B 掛「市價賣出 80 張」
   - 系統自動以「買一價 100.25」成交 80 張
   - 記錄為 `isBuyerInitiated = false`
   - 統計為「內盤」成交

---

## ⚠️ 目前系統狀態

### ✅ 已實作功能

1. **買賣盤失衡計算邏輯**：
   - `StockMarketModel.getRecentTickImbalance(n)` 方法已實作
   - 主力策略中已使用此指標做決策

2. **圖表資料結構**：
   - `tickImbBuySeries` 和 `tickImbSellSeries` 已宣告
   - 已加入到 `dsSignals` 資料集中
   - 渲染器 `rSignals` 已設定橙色和紫色

3. **信號指示器面板**：
   - `SignalIndicatorPanel` 已建立
   - 可顯示買賣盤失衡的計數

4. **✅ K線圖自動標記（已啟用）**：
   - 已在 `updatePriceChart` 方法中添加買賣盤失衡檢測
   - 自動在K線圖上標記買賣盤失衡點
   - 同步更新信號指示器面板計數

---

## 🎯 已啟用的買賣盤失衡標記邏輯

**位置：** `MainView.java` - `updatePriceChart()` 方法（第 2573-2607 行）

```java
// [CHART] 買賣盤失衡檢測（Tick Imbalance）
try {
    if (model != null) {
        // 取得最近 60 筆交易的買賣盤失衡度
        double tickImb = model.getRecentTickImbalance(60);
        
        long xMs;
        if (currentKlineMinutes < 0) 
            xMs = ((Second) period).getFirstMillisecond(); 
        else 
            xMs = ((Minute) period).getFirstMillisecond();
        
        // 買盤失衡：失衡度 > 0.25（買方主動筆數遠多於賣方）
        if (tickImb > 0.25) {
            int idx = tickImbBuySeries.indexOf(xMs);
            if (idx >= 0) {
                tickImbBuySeries.updateByIndex(idx, price);
            } else {
                tickImbBuySeries.add(xMs, price);
            }
            keepSeriesWithinLimit(tickImbBuySeries, 100);
        }
        
        // 賣盤失衡：失衡度 < -0.25（賣方主動筆數遠多於買方）
        else if (tickImb < -0.25) {
            int idx = tickImbSellSeries.indexOf(xMs);
            if (idx >= 0) {
                tickImbSellSeries.updateByIndex(idx, price);
            } else {
                tickImbSellSeries.add(xMs, price);
            }
            keepSeriesWithinLimit(tickImbSellSeries, 100);
        }
    }
} catch (Exception ignore) {}
```

### 實作特點

1. **即時檢測**：每次價格更新時自動檢測失衡狀態
2. **動態閾值**：失衡度 > 0.25 或 < -0.25 時觸發標記
3. **去重機制**：同一時間點只保留一個標記（避免重複）
4. **數量限制**：最多保留 100 個標記點（避免圖表過於擁擠）
5. **自動更新**：信號指示器面板會同步顯示最新計數

---

## 📝 總結

| 項目 | 狀態 | 說明 |
|-----|------|------|
| 多頭信號形狀 | ✅ 完成 | 正三角形 ▲ 紅色 |
| 空頭信號形狀 | ✅ 完成 | 倒三角形 ▼ 綠色 |
| 買賣盤失衡計算 | ✅ 已實作 | `getRecentTickImbalance()` 方法 |
| 主力策略使用失衡 | ✅ 已實作 | 決策邏輯中已參考 |
| **K線圖失衡標記** | ✅ **已啟用** | **自動標記買賣盤失衡點** |
| 信號面板失衡計數 | ✅ 已完成 | UI 會同步更新計數 |

### 🎉 所有買賣盤失衡功能已全面啟用！

---

**文件版本：** 1.0  
**最後更新：** 2025-10-23

