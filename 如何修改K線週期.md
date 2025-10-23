# 如何修改K線蠟燭圖時間週期

## 📍 修改位置

**檔案：** `D:\自動掛單版src\StockMainAction\view\MainView.java`

---

## 🔧 修改方法

### 1️⃣ 修改可用的時間週期選項（第 210-211 行）

```java
// K線多週期管理（新增 10秒、30秒 以秒為單位以 0.x 表示，內部會換算）
private final int[] klineMinutes = new int[]{1, 5, 10, 30, 60};
private final int[] klineSeconds = new int[]{10, 30, 60};
```

#### 說明：
- **`klineSeconds`**：秒級K線週期（如 10秒、30秒、60秒）
- **`klineMinutes`**：分鐘級K線週期（如 1分、5分、10分、30分、60分）

#### 範例修改：

**新增 5秒、15秒、120秒週期：**
```java
private final int[] klineSeconds = new int[]{5, 10, 15, 30, 60, 120};
```

**新增 15分、45分週期：**
```java
private final int[] klineMinutes = new int[]{1, 5, 10, 15, 30, 45, 60};
```

**移除某些週期（例如只保留 10秒、1分、5分）：**
```java
private final int[] klineSeconds = new int[]{10};
private final int[] klineMinutes = new int[]{1, 5};
```

---

### 2️⃣ 修改預設顯示的K線週期（第 1789-1790 行）

在 `createCharts()` 方法中找到：

```java
// 預設使用 10 秒 K 線
currentKlineMinutes = -10;
```

#### 說明：
- **負數值**：表示秒級週期（如 `-10` = 10秒，`-30` = 30秒）
- **正數值**：表示分鐘級週期（如 `1` = 1分鐘，`5` = 5分鐘）

#### 範例修改：

**預設使用 1分鐘 K線：**
```java
currentKlineMinutes = 1;
```

**預設使用 30秒 K線：**
```java
currentKlineMinutes = -30;
```

**預設使用 5分鐘 K線：**
```java
currentKlineMinutes = 5;
```

---

### 3️⃣ 修改每個週期保留的K線根數（第 1775、1783 行）

在 `createCharts()` 方法中：

```java
// 秒級K線
for (int s : klineSeconds) {
    OHLCSeries srs = new OHLCSeries("K線(" + s + "秒)");
    try { srs.setMaximumItemCount(30); } catch (Exception ignore) {}  // ← 這裡設定最大根數
    // ...
}

// 分鐘級K線
for (int m : klineMinutes) {
    OHLCSeries s = new OHLCSeries("K線(" + m + "分)");
    try { s.setMaximumItemCount(30); } catch (Exception ignore) {}  // ← 這裡設定最大根數
    // ...
}
```

#### 範例修改：

**秒級K線顯示最多 60 根：**
```java
try { srs.setMaximumItemCount(60); } catch (Exception ignore) {}
```

**分鐘級K線顯示最多 100 根：**
```java
try { s.setMaximumItemCount(100); } catch (Exception ignore) {}
```

---

## 📊 完整範例：自訂K線週期配置

### 範例 1：短線交易者配置（秒級為主）

```java
// 第 210-211 行
private final int[] klineSeconds = new int[]{5, 10, 15, 30, 60};  // 5秒到60秒
private final int[] klineMinutes = new int[]{1, 3, 5};            // 1分、3分、5分

// 第 1789-1790 行
currentKlineMinutes = -5;  // 預設顯示 5秒 K線

// 第 1775 行
try { srs.setMaximumItemCount(120); } catch (Exception ignore) {}  // 秒級K線顯示120根

// 第 1783 行
try { s.setMaximumItemCount(60); } catch (Exception ignore) {}  // 分鐘級K線顯示60根
```

### 範例 2：波段交易者配置（分鐘級為主）

```java
// 第 210-211 行
private final int[] klineSeconds = new int[]{30, 60};               // 只保留30秒、60秒
private final int[] klineMinutes = new int[]{1, 5, 15, 30, 60, 120};  // 1分到120分

// 第 1789-1790 行
currentKlineMinutes = 5;  // 預設顯示 5分鐘 K線

// 第 1775 行
try { srs.setMaximumItemCount(30); } catch (Exception ignore) {}  // 秒級K線顯示30根

// 第 1783 行
try { s.setMaximumItemCount(200); } catch (Exception ignore) {}  // 分鐘級K線顯示200根
```

### 範例 3：當沖交易者配置（極短線）

```java
// 第 210-211 行
private final int[] klineSeconds = new int[]{3, 5, 10, 20, 30};  // 3秒、5秒、10秒、20秒、30秒
private final int[] klineMinutes = new int[]{1, 2, 3, 5};        // 1分、2分、3分、5分

// 第 1789-1790 行
currentKlineMinutes = -3;  // 預設顯示 3秒 K線

// 第 1775 行
try { srs.setMaximumItemCount(200); } catch (Exception ignore) {}  // 秒級K線顯示200根

// 第 1783 行
try { s.setMaximumItemCount(100); } catch (Exception ignore) {}  // 分鐘級K線顯示100根
```

---

## 🔍 程式碼解析

### 週期儲存機制

系統使用 `Map` 來儲存不同週期的K線數據：

```java
private final Map<Integer, OHLCSeries> minuteToSeries = new HashMap<>();
```

- **Key 為正數**：表示分鐘週期（例如 `1` = 1分鐘）
- **Key 為負數**：表示秒週期（例如 `-10` = 10秒）

### 時間桶對齊

在 `updatePriceChart()` 方法中（第 2412-2426 行），系統會根據當前週期計算時間桶：

```java
if (currentKlineMinutes < 0) {
    int s = -currentKlineMinutes; // 秒
    long bucketMs = 1000L * s;
    aligned = now - (now % bucketMs);
    period = new Second(new java.util.Date(aligned));
} else {
    int m = currentKlineMinutes;  // 分
    long bucketMs = 60_000L * m;
    aligned = now - (now % bucketMs);
    period = new Minute(new java.util.Date(aligned));
}
```

**例如：**
- 10秒K線：每10秒對齊一次（10:30:00、10:30:10、10:30:20...）
- 1分鐘K線：每1分鐘對齊一次（10:30:00、10:31:00、10:32:00...）
- 5分鐘K線：每5分鐘對齊一次（10:30:00、10:35:00、10:40:00...）

---

## ⚠️ 注意事項

### 1. 週期值限制

- **秒級週期**：建議範圍 1 ~ 120 秒（不建議超過2分鐘，否則應改用分鐘級）
- **分鐘級週期**：建議範圍 1 ~ 240 分鐘（1分鐘到4小時）

### 2. 記憶體使用

K線根數越多，記憶體使用越大：

| 週期類型 | 建議最大根數 | 記憶體佔用 |
|---------|-------------|-----------|
| **秒級** | 30-120 根 | 較低 |
| **分鐘級** | 60-200 根 | 中等 |
| **小時級** | 100-500 根 | 較高 |

### 3. 效能影響

- **過多週期選項**：會增加初始化時間和記憶體使用
- **過多K線根數**：會影響圖表渲染效能
- **建議配置**：3-6個常用週期，每個週期保留30-100根K線

### 4. 數據同步

系統在 `updatePriceChart()` 中會同時更新多個週期的K線：

```java
// [CHART] 另外更新多週期資料（30秒、60秒、10分、30分）
int[] extraKeys = new int[]{-30, -60, 10, 30};
for (int key : extraKeys) {
    if (key == currentKlineMinutes) continue;
    try { updateOhlcForKey(price, now, key); } catch (Exception ignore) {}
}
```

如果您新增了新的週期，可以考慮將其加入 `extraKeys` 陣列以保持數據同步。

---

## 🎯 快速修改步驟

### 如果您只想改變預設週期：

1. 找到第 **1789-1790** 行
2. 修改 `currentKlineMinutes` 的值：
   - 改為 `1` → 預設1分鐘
   - 改為 `5` → 預設5分鐘
   - 改為 `-30` → 預設30秒
3. 儲存並重新編譯

### 如果您想新增新的週期選項：

1. 找到第 **210-211** 行
2. 在 `klineSeconds` 或 `klineMinutes` 陣列中新增數值
3. 例如新增 2分鐘週期：
   ```java
   private final int[] klineMinutes = new int[]{1, 2, 5, 10, 30, 60};
   ```
4. 儲存並重新編譯

---

## 📚 相關檔案

- **主要檔案**：`MainView.java`（行數：210-211、1773-1790、2412-2463）
- **週期切換邏輯**：`updatePriceChart()` 方法
- **週期數據更新**：`updateOhlcForKey()` 方法

---

## ❓ 常見問題

### Q1: 可以新增小時級K線嗎？

**A:** 可以！只需在 `klineMinutes` 中新增 60 的倍數即可：

```java
private final int[] klineMinutes = new int[]{1, 5, 15, 30, 60, 120, 240};
// 60 = 1小時, 120 = 2小時, 240 = 4小時
```

### Q2: 為什麼秒級週期用負數表示？

**A:** 這是程式設計上的技巧，用正負值區分「分鐘」和「秒」兩種單位，方便在同一個 Map 中儲存，避免使用兩個獨立的 Map。

### Q3: 修改後需要清除舊數據嗎？

**A:** 不需要。重新啟動程式後，所有K線數據會重新初始化。

### Q4: 可以動態切換週期嗎？

**A:** 目前系統支援多週期並存，但切換週期的UI控件可能需要另外實作。現有的 `currentKlineMinutes` 變數控制當前顯示的週期。

---

## 🎉 總結

修改K線週期只需要簡單的三個步驟：

1. ✅ 修改可用週期陣列（第 210-211 行）
2. ✅ 修改預設週期（第 1789-1790 行）  
3. ✅ 調整K線根數（第 1775、1783 行，可選）

修改完成後，重新編譯執行即可！

---

**文件版本：** 1.0  
**最後更新：** 2025-10-23  
**作者：** AI Assistant

