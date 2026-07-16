import { defineStore } from "pinia";
import { apiPost } from "@/services/api";

const STORAGE_KEY = "stockgame.auth";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: "",
    username: "",
    role: "PLAYER",
    status: "idle",
    error: ""
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    isAdmin: (state) => state.role === "ADMIN"
  },
  actions: {
    hydrate() {
      const raw = localStorage.getItem(STORAGE_KEY);

      if (!raw) return;

      try {
        const saved = JSON.parse(raw);
        this.token = saved.token || "";
        this.username = saved.username || "";
        this.role = saved.role || "PLAYER";
      } catch {
        localStorage.removeItem(STORAGE_KEY);
      }
    },
    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          token: this.token,
          username: this.username,
          role: this.role
        })
      );
    },
    setSession(response) {
      this.token = response.token;
      this.username = response.username;
      this.role = response.role || "PLAYER";
      this.error = "";
      this.persist();
    },
    async login(payload) {
      this.status = "loading";
      this.error = "";

      try {
        const response = await apiPost("/api/auth/login", payload);
        this.setSession(response);
        return response;
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.status = "idle";
      }
    },
    async register(payload) {
      this.status = "loading";
      this.error = "";

      try {
        const response = await apiPost("/api/auth/register", payload);
        this.setSession(response);
        return response;
      } catch (error) {
        this.error = error.message;
        throw error;
      } finally {
        this.status = "idle";
      }
    },
    logout() {
      this.token = "";
      this.username = "";
      this.role = "PLAYER";
      this.error = "";
      localStorage.removeItem(STORAGE_KEY);
    }
  }
});
