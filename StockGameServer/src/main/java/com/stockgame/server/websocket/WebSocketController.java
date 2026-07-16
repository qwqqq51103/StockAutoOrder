package com.stockgame.server.websocket;

import com.stockgame.server.dto.MarketQuoteDto;
import com.stockgame.server.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

/**
 * WebSocket STOMP 訊息路由。
 * 客戶端可透過 /app/ping 發送心跳，伺服器回應目前行情。
 */
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final MarketService marketService;

    /**
     * 心跳：客戶端送 /app/ping，伺服器廣播目前報價至 /topic/market/quote。
     */
    @MessageMapping("/ping")
    @SendTo("/topic/market/quote")
    public MarketQuoteDto handlePing(Principal principal) {
        return marketService.getQuote();
    }
}
