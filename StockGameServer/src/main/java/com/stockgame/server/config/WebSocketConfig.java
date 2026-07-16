package com.stockgame.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 設定。
 *
 * 連線方式（客戶端）：
 *   ws://localhost:8080/ws  (SockJS fallback 亦支援)
 *
 * 訂閱主題：
 *   /topic/market/quote     — 每 tick 廣播行情快照（所有客戶端）
 *   /topic/market/orderbook — 訂單簿快照（所有客戶端）
 *   /topic/market/trades    — 成交逐筆（所有客戶端）
 *   /user/queue/account     — 個人帳戶更新（登入玩家）
 *   /user/queue/orders      — 個人委託狀態更新（登入玩家）
 *
 * 客戶端可向以下 destination 送訊息（透過 @MessageMapping）：
 *   /app/ping               — 心跳
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // 生產請縮限前端域名
                .withSockJS();                  // 瀏覽器 fallback
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /topic → 廣播給所有訂閱者（公開市場資料）
        // /user  → 點對點推送（個人帳戶/委託更新）
        config.enableSimpleBroker("/topic", "/user");

        // 客戶端送訊息的前綴
        config.setApplicationDestinationPrefixes("/app");

        // /user/... 的路由前綴
        config.setUserDestinationPrefix("/user");
    }
}
