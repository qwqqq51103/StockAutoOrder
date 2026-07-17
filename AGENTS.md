# Project Codex 工作規範

本檔只補充「自動掛單版本」目前唯一專案 `StockMainAction` 的專案知識；通用流程沿用 Global `AGENTS.md`、Global Agents 與 Global Skills。

## 專案定位

- 目前專案主體只剩 `StockMainAction/`。
- 這是 Java Swing 股市模擬與自動掛單專案，採用 MVC-ish 分層：model 保存市場/帳戶/撮合狀態，controller 協調模擬與 UI，view 提供 Swing 畫面。
- 主要風險集中在撮合邏輯、帳戶資金/持股一致性、委託生命週期、交易紀錄、K 線/圖表資料、Swing EDT 更新與日誌輸出。
- 修改交易、撮合、帳戶、策略、K 線、Swing UI 更新或日誌前，先使用 Project Skill：`stock-main-action-domain`。

## 技術棧

- 語言：Java 17。
- Build/Test：Maven。
- UI：Java Swing、FlatLaf。
- 圖表：JFreeChart。
- 測試：JUnit 4、Surefire。
- 外部服務：目前沒有 Spring Boot、Vue、WebSocket、JWT、MySQL 或前後端分離流程。

## 主要目錄

- `StockMainAction/pom.xml`：Maven 專案設定。
- `StockMainAction/src/main/java/StockMainAction/StockMarketSimulation.java`：Swing 應用入口。
- `StockMainAction/src/main/java/StockMainAction/model/core/`：委託、撮合、成交、股票、交易紀錄與核心資料結構。
- `StockMainAction/src/main/java/StockMainAction/model/account/`：帳戶流水、資金/持股快照與帳務異動。
- `StockMainAction/src/main/java/StockMainAction/model/strategy/`：策略訊號、風控、意圖執行與掛單年齡追蹤。
- `StockMainAction/src/main/java/StockMainAction/model/`：市場模型、AI 交易者、市場行為與分析器。
- `StockMainAction/src/main/java/StockMainAction/controller/`：市場控制器、快速交易、價格提醒、技術指標與交易協調。
- `StockMainAction/src/main/java/StockMainAction/service/`：個人交易服務與市場干預服務。
- `StockMainAction/src/main/java/StockMainAction/view/`：Swing 主畫面、委託簿、委託/成交視圖、控制視圖與元件。
- `StockMainAction/src/main/java/StockMainAction/util/logging/`：市場日誌、審計、非同步日誌與 Log Viewer。
- `StockMainAction/src/test/java/StockMainAction/`：單元測試與壓力/併發/圖表/UI 輔助測試。
- `說明/`：既有設計說明、K 線/圖表/流程圖與修復紀錄。

## 建置與測試

- 編譯：在 `StockMainAction/` 執行 `mvn -q -DskipTests compile`。
- 測試：在 `StockMainAction/` 執行 `mvn -q test`。
- 測試會透過 Surefire 設定 `java.awt.headless=true`，並把 `user.home` 指到 `StockMainAction/target/test-home`。
- 若修改 Swing 視覺或互動流程，單元測試只能涵蓋工具類與部分行為，仍需要啟動應用做手動 UI 檢查。

## 修改原則

- 不讀取或修改 `target/`、`logs/`、`lib/`，除非任務明確要求或需要檢查既有測試報告。
- 不回復 Git 中已刪除的 `StockGameServer` 舊模組，除非使用者明確要求恢復。
- 修改撮合或帳戶邏輯時，同步檢查 `OrderBook`、`AccountLedger`、`TransactionJournal`、`StockMarketModel` 與相關測試。
- 修改策略或 AI 行為時，同步檢查 `model/strategy/`、`RetailInvestorAI`、`MainForceStrategyWithOrderBook`、`NoiseTraderAI` 與市場模型呼叫點。
- 修改 Swing UI 時，注意 Event Dispatch Thread、更新節流、視窗尺寸與大型 `MainView` 的既有結構，避免未要求的大型重構。
- 修改圖表或 K 線時，同步檢查 `view/chart/`、`TechnicalIndicatorsCalculator`、`TransactionHistoryViewer` 與 `說明/` 中相關文件。
- 專案目前可能存在使用者未提交變更；不得 revert、移動或清理非本次任務產生的檔案。

## Project Agents

- 本專案目前不新增 Project Agent。
- 搜尋、規劃、除錯與審查沿用 Global Agents：`explorer`、`planner`、`debugger`、`reviewer`、`architect`。
- 只有當未來出現多次重複的 `StockMainAction` 專屬工作流程，且 Global Agents 無法清楚承載時，才考慮新增 Project Agent。

## Project Skills

- `stock-main-action-domain`：`StockMainAction` 專案架構、撮合/帳務一致性、策略流程、Swing UI 與 Maven 驗證流程。
