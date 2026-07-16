# 股市遊戲伺服器 — 環境安裝與啟動說明

> 支援 Windows 10 / 11，在別台電腦上照此步驟操作即可正常運作。

---

## 一、前置需求

### 1. Java 17（必要）

1. 前往下載：https://www.oracle.com/java/technologies/downloads/#java17
   - 選擇 **Windows x64 Installer**（`.exe` 檔）
2. 安裝完後，開啟命令提示字元（cmd）確認：
   ```
   java -version
   ```
   應顯示 `java version "17.x.x"` 或以上版本。

> ⚠️ 若安裝後還是找不到 `java`，請手動設定環境變數：
> - `JAVA_HOME` = `C:\Program Files\Java\jdk-17`（依你的安裝路徑）
> - `Path` 加入 `%JAVA_HOME%\bin`

---

### 2. MySQL 8.0（必要）

1. 前往下載：https://dev.mysql.com/downloads/mysql/
   - 選擇 **MySQL Installer for Windows**（建議選 Full）
2. 安裝時記下你設定的 **root 密碼**
3. 安裝完後進入 MySQL，建立資料庫：
   ```sql
   CREATE DATABASE stockgame CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
   > 可使用 MySQL Workbench（安裝時一起裝）或命令列執行上面這行

---

### 3. Git（建議，用於下載程式碼）

1. 前往下載：https://git-scm.com/download/win
2. 安裝完後在命令提示字元執行：
   ```
   git --version
   ```

---

## 二、下載程式碼

開啟命令提示字元，執行：

```bash
git clone https://github.com/qwqqq51103/StockAutoOrder.git
cd StockAutoOrder\StockGameServer
```

---

## 三、設定資料庫連線

開啟 `src\main\resources\application.yml`，找到以下區塊並修改：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/stockgame?useSSL=false&serverTimezone=Asia/Taipei&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
    username: root
    password: ""       # ← 改成你的 MySQL root 密碼
```

> 如果你的 MySQL 不是用 root，請一併修改 `username`。

---

## 四、啟動伺服器

### 方法 A：直接啟動（推薦）

雙擊資料夾內的 **`啟動伺服器.cmd`** 即可。

第一次啟動會自動下載 Maven 依賴（需要網路），約 1～3 分鐘，之後啟動只需 10 秒。

### 方法 B：使用 GUI 控制台

雙擊 **`啟動GUI控制台.cmd`**，會開啟 Swing 管理介面，可以：
- 按鈕啟動 / 停止伺服器
- 即時查看伺服器 log
- 直接開啟遊戲網頁

### 方法 C：手動用 Maven 啟動

```bash
cd StockAutoOrder\StockGameServer
mvnw.cmd clean spring-boot:run
```

---

## 五、開始遊玩

伺服器啟動後，看到以下訊息代表成功：

```
Started StockGameServerApplication in X.XXX seconds
市場初始化完成，參考價：100.00
伺服器啟動成功！監聽 port 8080
```

打開瀏覽器，前往：

```
http://localhost:8080
```

即可看到遊戲登入畫面。

---

## 六、遊戲功能說明

| 功能 | 說明 |
|------|------|
| 帳號系統 | 註冊 / 登入，初始資金 **100 萬元** |
| 即時撮合 | 限價單、市價單、FOK，與 AI 玩家撮合 |
| K 線圖 | 1m / 5m / 15m 時間框架，支援布林帶、RSI、MACD |
| 委託簿 | 即時更新買賣五檔 |
| 成交帶 | 即時顯示最新成交明細 |
| 帳戶資訊 | 現金、持股市值、損益、報酬率、內外盤比例 |
| 快捷交易 | 自訂一鍵市價下單按鈕 |
| 價格提醒 | 設定目標價，觸發瀏覽器通知 |
| 成交歷史 | 查看所有委託紀錄與個人績效統計 |
| 排行榜 | 查看所有玩家損益排名 |

---

## 七、常見問題

### Q：啟動時出現 `Communications link failure`
代表無法連接 MySQL。請確認：
1. MySQL 服務是否已啟動（服務名稱通常是 `MySQL80`）
2. `application.yml` 中的密碼是否正確
3. 資料庫 `stockgame` 是否已建立

### Q：啟動時出現 `Port 8080 was already in use`
代表 8080 port 被佔用。執行以下命令找出佔用程序並結束它：
```
netstat -ano | findstr :8080
taskkill /PID <PID號碼> /F
```

### Q：第一次啟動下載 Maven 依賴很慢
需要網路連線，初次下載約需 200MB 的 library。可使用手機熱點，完成後依賴會快取在本機。

### Q：瀏覽器開啟後頁面是空白
等待伺服器完全啟動（看到 `Started StockGameServerApplication`）後再重新整理頁面。

### Q：忘記密碼 / 想重置資料
進入 MySQL 執行：
```sql
DROP DATABASE stockgame;
CREATE DATABASE stockgame CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
下次啟動伺服器時會自動重新建立所有資料表。

---

## 八、系統需求

| 項目 | 最低需求 |
|------|---------|
| 作業系統 | Windows 10 / 11（64位元） |
| Java | 17 或以上 |
| MySQL | 8.0 或以上 |
| RAM | 512 MB 以上（建議 1 GB） |
| 磁碟空間 | 500 MB（含 Maven 依賴） |
| 網路 | 初次啟動需要網路下載依賴 |
