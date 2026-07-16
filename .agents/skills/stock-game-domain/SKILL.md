---
name: stock-game-domain
description: 自動掛單版本專案專屬知識。當任務涉及股市模擬、撮合引擎、帳戶資金/持股、K 線、WebSocket、Spring Boot API、Vue 前端交易畫面或 Swing 模擬端時使用。
---

# Stock Game Domain

## 使用時機

1. 修改或分析 `StockGameServer/` 的 API、service、engine、entity、repository、WebSocket 或 static/frontend 同步流程。
2. 修改或分析 `StockGameServer/frontend/src/` 的交易畫面、store、API client、WebSocket client、圖表或管理介面。
3. 修改或分析 `StockMainAction/` 的 Swing 模擬端、AI 交易者、OrderBook、K 線或統計畫面。
4. 調查撮合、成交、帳戶餘額、持股、排行、K 線、即時推播或登入權限問題。

## 專案地圖

- `StockGameServer/src/main/java/com/stockgame/server/engine/`：伺服器撮合、市場模擬與 order book。
- `StockGameServer/src/main/java/com/stockgame/server/service/`：交易、帳戶、市場、管理與登入流程。
- `StockGameServer/src/main/java/com/stockgame/server/entity/`：JPA persisted state。
- `StockGameServer/src/main/java/com/stockgame/server/dto/`：前後端 API 與 WebSocket 契約。
- `StockGameServer/frontend/src/services/`：HTTP 與 WebSocket client。
- `StockGameServer/frontend/src/stores/`：Pinia 狀態，通常是畫面資料來源。
- `StockGameServer/frontend/src/components/player/` 與 `views/`：玩家與管理畫面。
- `StockMainAction/`：本機 Swing 版本，可能保留與伺服器類似的交易邏輯。

## 分析流程

1. 先判斷任務屬於後端、前端、Swing，或跨邊界資料契約。
2. 對跨邊界任務，同時檢查 DTO/API/WebSocket payload、前端 service/store/view 與後端 controller/service。
3. 對交易任務，同時檢查委託建立、撮合、成交紀錄、帳戶資金、持股、排行與推播更新。
4. 對 K 線或圖表任務，同時檢查資料聚合來源、時間週期、前端 chart adapter 與既有 `說明/` 文件。
5. 明確區分伺服器版 `StockGameServer/` 與 Swing 版 `StockMainAction/`；只有有實際共用行為需求時才同步修改。

## 驗證建議

1. 後端核心變更：在 `StockGameServer/` 執行 `mvn -q test`。
2. 後端非測試可編譯變更：在 `StockGameServer/` 執行 `mvn -q -DskipTests compile`。
3. 前端變更：在 `StockGameServer/frontend/` 執行 `npm run build`。
4. 前端需要進入 Spring Boot static 時：先 `npm run build`，再 `npm run sync:static`。
5. UI 或 WebSocket 行為變更：必要時啟動後端與 Vite dev server，手動或瀏覽器檢查玩家頁與管理頁。

## 注意事項

- 不把環境密碼、JWT secret、MySQL 憑證或本機路徑硬編到新檔案。
- 避免掃描第三方與建置產物：`node_modules/`、`dist/`、`target/`、`logs/`、`lib/`。
- `application.yml` 目前含開發用設定；正式環境應使用環境變數與 `application-prod.yml`。
- 修改交易資料結構時，要保留資料一致性與回歸測試優先。
