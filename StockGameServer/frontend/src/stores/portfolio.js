import { defineStore } from "pinia";
import { apiDelete, apiGet, apiPost } from "@/services/api";
import { useAuthStore } from "@/stores/auth";

export const usePortfolioStore = defineStore("portfolio", {
  state: () => ({
    account: null,
    activeOrders: [],
    orderHistory: [],
    status: "idle",
    error: "",
    realtimeLoading: false,
    lastRealtimeRefreshAt: 0,
    lastHistoryRefreshAt: 0
  }),
  actions: {
    reset() {
      this.account = null;
      this.activeOrders = [];
      this.orderHistory = [];
      this.error = "";
      this.status = "idle";
      this.realtimeLoading = false;
      this.lastRealtimeRefreshAt = 0;
      this.lastHistoryRefreshAt = 0;
    },
    async bootstrap() {
      const authStore = useAuthStore();

      if (!authStore.token) {
        this.reset();
        return;
      }

      await Promise.all([this.loadAccount(), this.loadActiveOrders(), this.loadHistory()]);
    },
    async loadAccount() {
      const authStore = useAuthStore();
      this.account = await apiGet("/api/account/info", authStore.token);
    },
    async loadActiveOrders() {
      const authStore = useAuthStore();
      this.activeOrders = await apiGet("/api/orders/active", authStore.token);
    },
    async loadHistory() {
      const authStore = useAuthStore();
      this.orderHistory = await apiGet("/api/orders/history", authStore.token);
    },
    async refreshRealtime() {
      const authStore = useAuthStore();
      if (!authStore.token || this.realtimeLoading) return;

      const now = Date.now();
      if (now - this.lastRealtimeRefreshAt < 1000) return;

      this.realtimeLoading = true;
      this.lastRealtimeRefreshAt = now;

      try {
        const jobs = [this.loadAccount(), this.loadActiveOrders()];
        if (now - this.lastHistoryRefreshAt >= 5000) {
          this.lastHistoryRefreshAt = now;
          jobs.push(this.loadHistory());
        }
        await Promise.all(jobs);
      } catch (error) {
        this.error = error.message;
      } finally {
        this.realtimeLoading = false;
      }
    },
    async placeOrder(payload) {
      const authStore = useAuthStore();
      this.status = "loading";
      this.error = "";

      try {
        const result = await apiPost("/api/orders", payload, authStore.token);
        await this.bootstrap();
        return result;
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.status = "idle";
      }
    },
    async cancelOrder(orderId) {
      const authStore = useAuthStore();
      this.status = "loading";
      this.error = "";

      try {
        await apiDelete(`/api/orders/${orderId}`, authStore.token);
        await this.bootstrap();
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.status = "idle";
      }
    }
  }
});
