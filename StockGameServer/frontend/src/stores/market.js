import { defineStore } from "pinia";
import { apiGet } from "@/services/api";
import { createMarketSocket } from "@/services/ws";

function createEmptyOrderBook() {
  return {
    bids: [],
    asks: []
  };
}

export const useMarketStore = defineStore("market", {
  state: () => ({
    quote: null,
    orderBook: createEmptyOrderBook(),
    candles: [],
    recentTrades: [],
    leaderboard: [],
    latestAnnouncement: "",
    announcements: [],
    realtimeVersion: 0,
    lastLeaderboardRefreshAt: 0,
    connectionStatus: "disconnected",
    socketClient: null
  }),
  actions: {
    async bootstrap() {
      await Promise.all([
        this.fetchQuote(),
        this.fetchOrderBook(),
        this.fetchCandles(),
        this.fetchTrades(),
        this.fetchLeaderboard()
      ]);
    },
    async fetchQuote() {
      this.quote = await apiGet("/api/market/quote").catch(() => this.quote);
    },
    async fetchOrderBook() {
      this.orderBook = (await apiGet("/api/market/orderbook").catch(() => this.orderBook)) || createEmptyOrderBook();
    },
    async fetchCandles() {
      this.candles = (await apiGet("/api/market/candles").catch(() => [])) || [];
    },
    async fetchTrades() {
      this.recentTrades = (await apiGet("/api/market/trades").catch(() => []))?.slice(0, 80) || [];
    },
    async fetchLeaderboard() {
      this.leaderboard = (await apiGet("/api/account/leaderboard").catch(() => [])) || [];
    },
    markRealtimeEvent() {
      this.realtimeVersion += 1;
    },
    refreshLeaderboardSoon() {
      const now = Date.now();
      if (now - this.lastLeaderboardRefreshAt < 3000) {
        return;
      }

      this.lastLeaderboardRefreshAt = now;
      this.fetchLeaderboard();
    },
    connect() {
      if (this.socketClient?.active) {
        return;
      }

      this.connectionStatus = "connecting";
      this.socketClient = createMarketSocket({
        onConnectionChange: (status) => {
          this.connectionStatus = status;
        },
        onQuote: (quote) => {
          this.quote = quote;
          this.markRealtimeEvent();
        },
        onOrderBook: (orderBook) => {
          this.orderBook = orderBook;
          this.markRealtimeEvent();
        },
        onTrade: (trade) => {
          this.recentTrades = [trade, ...this.recentTrades].slice(0, 80);
          this.markRealtimeEvent();
          this.refreshLeaderboardSoon();
        },
        onCandle: (candle) => {
          const index = this.candles.findIndex((item) => item.time === candle.time);

          if (index >= 0) {
            this.candles.splice(index, 1, candle);
            this.markRealtimeEvent();
            return;
          }

          this.candles.push(candle);
          this.candles = this.candles.slice(-500);
          this.markRealtimeEvent();
        },
        onAnnouncement: (message) => {
          this.latestAnnouncement = message;
          this.announcements = [{ message, at: Date.now() }, ...this.announcements].slice(0, 20);
          this.markRealtimeEvent();
        }
      });

      this.socketClient.activate();
    },
    disconnect() {
      if (!this.socketClient) return;

      this.socketClient.deactivate();
      this.socketClient = null;
      this.connectionStatus = "disconnected";
    }
  }
});
