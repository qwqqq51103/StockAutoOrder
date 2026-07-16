package com.stockgame.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 線上股市遊戲後端伺服器啟動入口
 *
 * 主要功能模組：
 *   1. REST API  — 認證、下單、帳戶查詢
 *   2. WebSocket — STOMP 即時行情、成交廣播
 *   3. JPA/H2    — 玩家帳號、委託、成交持久化
 *   4. 撮合引擎  — 移植自桌面版 OrderBook，以 Server 為唯一撮合中心
 *   5. AI 市場   — 主力、散戶、噪音交易者在 Server 端持續運作
 */
@SpringBootApplication
@EnableScheduling
public class StockGameServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockGameServerApplication.class, args);
    }
}
