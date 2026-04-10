package com.stockgame.server.websocket;

import com.stockgame.server.dto.AccountInfoDto;
import com.stockgame.server.dto.CandleDto;
import com.stockgame.server.dto.MarketQuoteDto;
import com.stockgame.server.dto.OrderBookDto;
import com.stockgame.server.dto.TradeEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket STOMP 廣播服務。
 *
 * 公開廣播主題（所有連線客戶端接收）：
 *   /topic/market/quote     — 行情快照（每 tick）
 *   /topic/market/orderbook — 訂單簿快照（每 tick）
 *   /topic/market/trades    — 成交逐筆（有成交時）
 *
 * 個人推送（僅推給指定玩家）：
 *   /user/{username}/queue/account  — 帳戶變動通知
 *   /user/{username}/queue/orders   — 委託狀態更新
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketBroadcastService {

    private final SimpMessagingTemplate messaging;

    // ── 公開廣播 ─────────────────────────────────────────────────────────────

    public void broadcastQuote(MarketQuoteDto quote) {
        messaging.convertAndSend("/topic/market/quote", quote);
    }

    public void broadcastOrderBook(OrderBookDto orderBook) {
        messaging.convertAndSend("/topic/market/orderbook", orderBook);
    }

    public void broadcastTrade(TradeEventDto trade) {
        messaging.convertAndSend("/topic/market/trades", trade);
        log.debug("廣播成交：{} @ {}", trade.getQuantity(), trade.getPrice());
    }

    public void broadcastCandle(CandleDto candle) {
        messaging.convertAndSend("/topic/market/candle", candle);
    }

    // ── 個人推送 ─────────────────────────────────────────────────────────────

    /** 推送帳戶更新給指定玩家 */
    public void pushAccountUpdate(String username, AccountInfoDto account) {
        messaging.convertAndSendToUser(username, "/queue/account", account);
    }

    /** 推送委託狀態更新給指定玩家 */
    public void pushOrderUpdate(String username, Object orderUpdate) {
        messaging.convertAndSendToUser(username, "/queue/orders", orderUpdate);
    }

    /** 系統公告（全頻道） */
    public void broadcastAnnouncement(String message) {
        messaging.convertAndSend("/topic/system/announcement", message);
    }
}
