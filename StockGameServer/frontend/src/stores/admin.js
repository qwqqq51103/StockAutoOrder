import { defineStore } from "pinia";
import { apiGet, apiPost } from "@/services/api";
import { useAuthStore } from "@/stores/auth";

export const useAdminStore = defineStore("admin", {
  state: () => ({
    status: null,
    accounts: [],
    loading: false,
    error: "",
    realtimeLoading: false,
    lastRealtimeRefreshAt: 0,
    lastAccountsRefreshAt: 0
  }),
  actions: {
    reset() {
      this.status = null;
      this.accounts = [];
      this.loading = false;
      this.error = "";
      this.realtimeLoading = false;
      this.lastRealtimeRefreshAt = 0;
      this.lastAccountsRefreshAt = 0;
    },
    async bootstrap() {
      const authStore = useAuthStore();
      if (!authStore.isAdmin) {
        this.reset();
        return;
      }

      await Promise.all([this.loadStatus(), this.loadAccounts()]);
    },
    async loadStatus() {
      const authStore = useAuthStore();
      this.status = await apiGet("/api/admin/status", authStore.token);
    },
    async loadAccounts() {
      const authStore = useAuthStore();
      this.accounts = await apiGet("/api/admin/accounts", authStore.token);
    },
    async refreshRealtime() {
      const authStore = useAuthStore();
      if (!authStore.isAdmin || this.realtimeLoading) return;

      const now = Date.now();
      if (now - this.lastRealtimeRefreshAt < 1000) return;

      this.realtimeLoading = true;
      this.lastRealtimeRefreshAt = now;

      try {
        const jobs = [this.loadStatus()];
        if (now - this.lastAccountsRefreshAt >= 3000) {
          this.lastAccountsRefreshAt = now;
          jobs.push(this.loadAccounts());
        }
        await Promise.all(jobs);
      } catch (error) {
        this.error = error.message;
      } finally {
        this.realtimeLoading = false;
      }
    },
    async setMarket(open) {
      const authStore = useAuthStore();
      this.loading = true;
      this.error = "";
      try {
        this.status = await apiPost(`/api/admin/market/${open ? "open" : "close"}`, {}, authStore.token);
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async triggerTick() {
      const authStore = useAuthStore();
      this.loading = true;
      this.error = "";
      try {
        this.status = await apiPost("/api/admin/market/tick", {}, authStore.token);
        await this.loadAccounts();
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async updateSimulatorConfig(payload) {
      const authStore = useAuthStore();
      this.loading = true;
      this.error = "";
      try {
        this.status = await apiPost("/api/admin/simulator/config", payload, authStore.token);
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async sendAnnouncement(message) {
      const authStore = useAuthStore();
      this.loading = true;
      this.error = "";
      try {
        await apiPost("/api/admin/announcement", { message }, authStore.token);
        await this.loadStatus();
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async setUserEnabled(username, enabled) {
      const authStore = useAuthStore();
      this.loading = true;
      this.error = "";
      try {
        await apiPost(`/api/admin/users/${encodeURIComponent(username)}/${enabled ? "enable" : "disable"}`, {}, authStore.token);
        await this.loadAccounts();
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async resetAccount(username) {
      const authStore = useAuthStore();
      this.loading = true;
      this.error = "";
      try {
        await apiPost(`/api/admin/accounts/${encodeURIComponent(username)}/reset`, {}, authStore.token);
        await Promise.all([this.loadStatus(), this.loadAccounts()]);
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.loading = false;
      }
    }
  }
});
