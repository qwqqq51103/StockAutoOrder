---
name: stock-main-action-domain
description: StockMainAction 專案專屬知識。當任務涉及 Java Swing 股市模擬、自動掛單、撮合引擎、帳戶資金/持股、委託生命週期、交易紀錄、策略/AI、K 線/圖表、Swing UI、日誌或 Maven 測試時使用。
---

# Stock Main Action Domain

## 專案地圖

- `StockMainAction/pom.xml`：Java 17 Maven 設定，依賴 JFreeChart、FlatLaf、JUnit 4。
- `StockMainAction/src/main/java/StockMainAction/StockMarketSimulation.java`：Swing 應用入口。
- `model/core/`：委託、撮合、成交、股票、交易紀錄與核心資料結構。
- `model/account/`：帳戶流水、資金/持股快照與帳務異動。
- `model/strategy/`：策略訊號、風控、掛單意圖與掛單年齡追蹤。
- `model/`：市場模型、AI 交易者、市場行為與分析器。
- `controller/`：市場控制、快速交易、價格提醒、技術指標與交易協調。
- `service/`：個人交易服務與市場干預服務。
- `view/`：Swing 主畫面、委託簿、委託/成交視圖、控制視圖與元件。
- `view/chart/`：圖表資料限制與成交帶指標。
- `view/swing/`：Swing 更新節流與視窗尺寸工具。
- `util/logging/`：市場日誌、審計、非同步日誌與 Log Viewer。
- `src/test/java/StockMainAction/`：核心、帳務、策略、服務、圖表與 Swing 工具測試。

## 分析流程

1. 先判斷任務屬於撮合核心、帳務、策略/AI、Swing UI、圖表/K 線、日誌或 Maven/測試設定。
2. 對交易任務，同時檢查委託建立、撮合、成交紀錄、帳戶資金、持股、委託狀態與統計更新。
3. 對帳務任務，優先檢查 `AccountLedger`、`UserAccount`、`OrderBook`、`TransactionJournal` 與帳務測試。
4. 對策略/AI 任務，同時檢查 `model/strategy/`、`RetailInvestorAI`、`MainForceStrategyWithOrderBook`、`NoiseTraderAI` 與 `StockMarketModel` 呼叫點。
5. 對 Swing UI 任務，檢查事件來源、listener、EDT、更新節流、視窗尺寸與大型 `MainView` 的既有結構。
6. 對 K 線、圖表或成交帶任務，檢查 `view/chart/`、`TechnicalIndicatorsCalculator`、`TransactionHistoryViewer` 與 `說明/` 中相關文件。
7. 對日誌任務，檢查 `MarketLogger`、`AsyncMarketLogger`、`LogicAudit`、`LoggerConfig` 與測試時的 `market.log.console` 設定。

## 驗證建議

1. 一般 Java 變更：在 `StockMainAction/` 執行 `mvn -q -DskipTests compile`。
2. 撮合、帳務、策略、圖表或服務變更：在 `StockMainAction/` 執行 `mvn -q test`。
3. Swing UI 視覺或互動變更：先執行相關測試，再啟動應用做手動 UI 檢查。
4. 需要查看既有測試結果時，只讀 `StockMainAction/target/surefire-reports/`，不要把 `target/` 當原始碼來源。

## 注意事項

- 不讀取或修改 `target/`、`logs/`、`lib/`，除非任務明確要求或需要檢查既有測試報告。
- 不恢復 Git 中已刪除的 `StockGameServer` 舊模組，除非使用者明確要求。
- 不把本機路徑、使用者環境值或暫存輸出硬編到版本化檔案。
- 修改核心交易資料結構時，要保留資料一致性與回歸測試優先。
