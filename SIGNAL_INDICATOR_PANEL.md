# 信號指示器面板說明

## 概述

在成交量圖表下方新增了一個實時信號指示器面板，用於顯示市場中的各種交易信號和大單活動。

## 功能說明

### 📊 顯示位置
信號指示器面板位於組合圖表（K線+成交量）的下方，高度為45像素。

### 🎯 顯示信號

信號面板實時顯示以下6種信號的數量：

#### 1. 🔺 多頭信號 (Bull Signal)
- **顏色**: 紅色 `#EF5350`
- **說明**: 外盤比例連續高於門檻且價格創新高時產生
- **用途**: 判斷市場多頭力量

#### 2. 🔻 空頭信號 (Bear Signal)  
- **顏色**: 綠色 `#26A69A`
- **說明**: 內盤比例連續高於門檻且價格創新低時產生
- **用途**: 判斷市場空頭力量

#### 3. 💰 大買單 (Big Buy)
- **顏色**: 紅色 `#EF5350`
- **說明**: 成交量超過大單門檻（默認500）且為買方主動
- **用途**: 監控大資金買入動作

#### 4. 💸 大賣單 (Big Sell)
- **顏色**: 綠色 `#26A69A`
- **說明**: 成交量超過大單門檻（默認500）且為賣方主動
- **用途**: 監控大資金賣出動作

#### 5. 📈 買盤失衡 (Tick Buy Imbalance)
- **顏色**: 橙色 `#FF9800`
- **說明**: 短期內買方主動成交量明顯大於賣方
- **用途**: 識別買盤壓力

#### 6. 📉 賣盤失衡 (Tick Sell Imbalance)
- **顏色**: 紫色 `#9C27B0`
- **說明**: 短期內賣方主動成交量明顯大於買方
- **用途**: 識別賣盤壓力

---

## 視覺佈局

```
┌────────────────────────────────────────────────────────────────────────┐
│                        K線圖 (70%)                                     │
│                                                                         │
│         ▓▓  ▓  ▓▓                                                      │
│        ▓  ▓  ▓▓  ▓                                                     │
│       ▓        ▓                                                        │
│                                                                         │
├────────────────────────────────────────────────────────────────────────┤
│                      成交量圖 (30%)                                     │
│                                                                         │
│    ▓     ▓     ─── MA5 (橙色)                                          │
│    █  ▓  █  ▓  ─── MA10 (紫色)                                         │
│    █  █  █  █                                                           │
│                                                                         │
├────────────────────────────────────────────────────────────────────────┤
│ 🔺 多頭信號 3  │ 🔻 空頭信號 1  │ 💰 大買單 5  │ 💸 大賣單 2  │     │
│                │                │              │              │         │
│ 📈 買盤失衡 8  │ 📉 賣盤失衡 4                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 技術實現

### 類別定義

```java
class SignalIndicatorPanel extends JPanel {
    private final JLabel bullSignalLabel;
    private final JLabel bearSignalLabel;
    private final JLabel bigBuyLabel;
    private final JLabel bigSellLabel;
    private final JLabel tickBuyImbLabel;
    private final JLabel tickSellImbLabel;
    
    // 更新方法
    public void updateBullSignal(int count)
    public void updateBearSignal(int count)
    public void updateBigBuy(int count)
    public void updateBigSell(int count)
    public void updateTickBuyImb(int count)
    public void updateTickSellImb(int count)
    public void updateAllSignals(int bull, int bear, int bigBuy, 
                                  int bigSell, int tickBuy, int tickSell)
}
```

### 自動更新機制

信號面板在以下情況自動更新：

1. **價格更新時** (`updatePriceChart`)
   - 自動統計各類信號的數量
   - 更新面板顯示

2. **手動調用** (`updateSignalIndicators`)
   - 提供公開方法供外部調用
   - 可用於強制刷新顯示

### 數據源

| 信號類型 | 數據源 | 更新時機 |
|---------|--------|---------|
| 多頭信號 | `bullSignals` (XYSeries) | 價格創新高且外盤連續 |
| 空頭信號 | `bearSignals` (XYSeries) | 價格創新低且內盤連續 |
| 大買單 | `bigBuySeries` (XYSeries) | 成交量>門檻且買方主動 |
| 大賣單 | `bigSellSeries` (XYSeries) | 成交量>門檻且賣方主動 |
| 買盤失衡 | `tickImbBuySeries` (XYSeries) | Tick級買賣失衡檢測 |
| 賣盤失衡 | `tickImbSellSeries` (XYSeries) | Tick級買賣失衡檢測 |

---

## 樣式設計

### 面板樣式
- **背景色**: `#FAFAFA` (淺灰)
- **高度**: 45px
- **邊框**: 頂部1px灰線分隔
- **內邊距**: 上下5px，左右10px

### 信號項樣式
- **標籤字體**: Microsoft JhengHei, 12px
- **數值字體**: Microsoft JhengHei Bold, 13px
- **間距**: 項目間15px
- **分隔線**: 垂直線，1px寬，25px高

### 數值高亮
當信號數量 > 0 時：
- 字體加粗
- 字號放大到 14px
- 更醒目地提示交易者

---

## 使用場景

### 1. 日內交易
- 監控大買單/大賣單，判斷主力動向
- 觀察多空信號，把握趨勢轉折點

### 2. 高頻監控
- 買賣盤失衡信號提示短期壓力
- 結合K線和成交量確認入場時機

### 3. 風險控制
- 大賣單頻繁出現時謹慎追高
- 大買單頻繁出現時謹慎做空

---

## 配置選項

### 大單門檻調整
```java
private int bigOrderThreshold = 500; // 可調整
```

默認為500，可根據市場特性調整：
- **小市值股票**: 建議降低門檻 (200-300)
- **大市值股票**: 建議提高門檻 (1000-2000)
- **期貨市場**: 根據合約規模調整

### 多空信號參數
```java
private int consecutiveRequired = 2;  // 連續窗口數
private int effectiveThreshold = 65;  // 內外盤門檻%
```

在頂部工具列"事件"欄可動態調整：
- **一般模式**: 65%
- **新聞模式**: 70% (+5%)
- **財報模式**: 75% (+10%)

---

## API接口

### 公開方法

```java
// 手動更新所有信號指示器
public void updateSignalIndicators()
```

**使用示例**:
```java
mainView.updateSignalIndicators();
```

---

## 顏色方案總結

| 信號 | 顏色 | RGB | 用途 |
|------|------|-----|------|
| 🔺 多頭 | 紅色 | `239, 83, 80` | 上漲/做多 |
| 🔻 空頭 | 綠色 | `38, 166, 154` | 下跌/做空 |
| 💰 大買 | 紅色 | `239, 83, 80` | 買入壓力 |
| 💸 大賣 | 綠色 | `38, 166, 154` | 賣出壓力 |
| 📈 買失衡 | 橙色 | `255, 152, 0` | 買盤主導 |
| 📉 賣失衡 | 紫色 | `156, 39, 176` | 賣盤主導 |

---

## 更新歷史

- **2025-10-23**: 初始版本
  - 創建 SignalIndicatorPanel 類
  - 實現6種信號的實時顯示
  - 集成到主視圖底部
  - 添加自動更新機制

---

## 相關文件

- `MainView.java`: 主視圖類，第4215-4355行為 SignalIndicatorPanel 定義
- `VOLUME_AND_COLOR_UPDATE.md`: 成交量和顏色更新文檔
- `COMBINED_CHART_UPDATE.md`: 組合圖表整合文檔
- `TRADINGVIEW_STYLE_IMPROVEMENTS.md`: TradingView風格改進文檔

---

## 注意事項

1. **性能考慮**: 信號數量統計使用 `XYSeries.getItemCount()`，效率很高
2. **線程安全**: 所有UI更新都在 EDT (Event Dispatch Thread) 中執行
3. **異常處理**: 所有更新操作都有 try-catch 保護，不會影響主程序
4. **內存管理**: 各XYSeries都設置了最大點數限制，防止內存溢出

---

## 擴展建議

### 未來可添加的功能

1. **信號歷史**
   - 點擊信號顯示歷史詳情
   - 圖表上標記信號出現時間

2. **信號統計**
   - 顯示信號勝率
   - 計算信號平均持續時間

3. **聲音提示**
   - 重要信號出現時播放提示音
   - 可配置開關

4. **信號組合**
   - 多個信號同時出現時高亮顯示
   - 提供組合信號建議

5. **自定義門檻**
   - UI界面直接調整各信號門檻
   - 保存配置到文件

