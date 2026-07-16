import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client/dist/sockjs";
import { buildSocketUrl } from "@/services/api";

export function createMarketSocket(callbacks) {
  const client = new Client({
    webSocketFactory: () => new SockJS(buildSocketUrl("/ws")),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      callbacks.onConnectionChange?.("connected");

      client.subscribe("/topic/market/quote", (message) => {
        callbacks.onQuote?.(JSON.parse(message.body));
      });

      client.subscribe("/topic/market/orderbook", (message) => {
        callbacks.onOrderBook?.(JSON.parse(message.body));
      });

      client.subscribe("/topic/market/trades", (message) => {
        callbacks.onTrade?.(JSON.parse(message.body));
      });

      client.subscribe("/topic/market/candle", (message) => {
        callbacks.onCandle?.(JSON.parse(message.body));
      });

      client.subscribe("/topic/system/announcement", (message) => {
        callbacks.onAnnouncement?.(message.body);
      });
    },
    onStompError: () => {
      callbacks.onConnectionChange?.("disconnected");
    },
    onWebSocketClose: () => {
      callbacks.onConnectionChange?.("disconnected");
    }
  });

  return client;
}
