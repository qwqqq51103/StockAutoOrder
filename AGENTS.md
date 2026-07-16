# Project Codex 工作規範

本檔只補充「自動掛單版本」專案知識；通用流程沿用 Global `AGENTS.md`、Global Agents 與 Global Skills。

## 專案定位

- 這是股市模擬/自動掛單專案，包含 Spring Boot 線上遊戲伺服器、Vue 前端與 Java Swing 本機模擬端。
- 主要風險集中在撮合邏輯、帳戶資金/持股一致性、即時 WebSocket 推播、JWT/權限、K 線與交易資料同步。
- 修改交易、撮合、帳戶、K 線、WebSocket 或前後端資料契約前，先使用 Project Skill：`stock-game-domain`。

## 技術棧

- 後端：Java 17、Spring Boot 3.2.5、Spring Security、Spring Data JPA、WebSocket/STOMP、JWT、MySQL/H2。
- 前端：Vue 3、Vite 5、Pinia、Vue Router、Element Plus、Tailwind CSS、lightweight-charts、STOMP/SockJS。
- 本機模擬端：Java Swing，位於 `StockMainAction/`。
- 說明文件與流程圖位於 `說明/`。

## 主要目錄

- `StockGameServer/`：Spring Boot 後端、前端、靜態資源與啟動腳本。
- `StockGameServer/src/main/java/com/stockgame/server/engine/`：伺服器撮合與市場模擬核心。
- `StockGameServer/src/main/java/com/stockgame/server/service/`：帳戶、下單、市場與管理服務。
- `StockGameServer/src/main/java/com/stockgame/server/controller/`：REST API。
- `StockGameServer/src/main/java/com/stockgame/server/websocket/`：即時推播。
- `StockGameServer/frontend/src/`：Vue 前端。
- `StockMainAction/`：Swing 版本的模型、控制器、視圖與撮合邏輯。

## 建置與測試

- 後端編譯：在 `StockGameServer/` 執行 `mvn -q -DskipTests compile`。
- 後端測試：在 `StockGameServer/` 執行 `mvn -q test`。
- 前端建置：在 `StockGameServer/frontend/` 執行 `npm run build`。
- 前端同步到 Spring Boot static：在 `StockGameServer/frontend/` 執行 `npm run sync:static`。
- 一鍵前端建置同步：在 `StockGameServer/` 執行 `.\build-frontend-and-sync.cmd` 或 `.\建置前端並同步.cmd`。
- 開發模式啟動：在 `StockGameServer/` 執行 `.\啟動前後端_開發模式.cmd`。
- 正式模式啟動：在 `StockGameServer/` 執行 `.\啟動前後端_正式模式.cmd`。

## 修改原則

- 不讀取或修改 `node_modules/`、`dist/`、`target/`、`logs/`、`lib/`，除非任務明確要求。
- 不把密碼、JWT secret、資料庫憑證或個人環境值新增到版本化檔案。
- 修改 API DTO、WebSocket payload 或前端 store 時，要同步檢查後端 controller/service、前端 services/stores/views 與相關測試。
- 修改撮合或帳戶邏輯時，要檢查資金、持股、委託狀態、成交紀錄與排行資料是否保持一致。
- 修改 Swing `StockMainAction/` 與伺服器 `StockGameServer/` 類似邏輯時，先判斷是否需要兩邊同步，避免兩套行為分歧。
- 專案目前可能存在使用者未提交變更；不得 revert、移動或清理非本次任務產生的檔案。

## Project Agents

- 本專案目前不新增 Project Agent。
- 搜尋、規劃、除錯與審查沿用 Global Agents：`explorer`、`planner`、`debugger`、`reviewer`、`architect`。
- 只有當未來出現多次重複的本專案專屬工作流程，且 Global Agents 無法清楚承載時，才考慮新增 Project Agent。

## Project Skills

- `stock-game-domain`：股市遊戲專案架構、撮合/帳戶一致性、前後端契約與驗證流程。
