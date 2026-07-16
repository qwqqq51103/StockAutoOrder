import { defineStore } from "pinia";

const STORAGE_KEY = "stockgame.preferences";

function defaultQuickTrades() {
  return [
    { id: 1, side: "BUY", quantity: 50 },
    { id: 2, side: "BUY", quantity: 100 },
    { id: 3, side: "SELL", quantity: 50 },
    { id: 4, side: "SELL", quantity: 100 }
  ];
}

export const usePreferencesStore = defineStore("preferences", {
  state: () => ({
    quickTrades: defaultQuickTrades(),
    priceAlerts: [],
    notificationPermission: typeof Notification !== "undefined" ? Notification.permission : "default"
  }),
  actions: {
    hydrate() {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;

      try {
        const saved = JSON.parse(raw);
        this.quickTrades = saved.quickTrades?.length ? saved.quickTrades : defaultQuickTrades();
        this.priceAlerts = saved.priceAlerts || [];
      } catch {
        localStorage.removeItem(STORAGE_KEY);
      }
    },
    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          quickTrades: this.quickTrades,
          priceAlerts: this.priceAlerts
        })
      );
    },
    async ensureNotificationPermission() {
      if (typeof Notification === "undefined") return;
      if (Notification.permission === "granted" || Notification.permission === "denied") {
        this.notificationPermission = Notification.permission;
        return;
      }
      this.notificationPermission = await Notification.requestPermission();
    },
    saveQuickTrade(preset) {
      const nextId = this.quickTrades.length ? Math.max(...this.quickTrades.map((item) => item.id)) + 1 : 1;
      this.quickTrades = [...this.quickTrades, { id: nextId, ...preset }].slice(0, 8);
      this.persist();
    },
    removeQuickTrade(id) {
      this.quickTrades = this.quickTrades.filter((item) => item.id !== id);
      this.persist();
    },
    addAlert(alert) {
      const nextId = this.priceAlerts.length ? Math.max(...this.priceAlerts.map((item) => item.id)) + 1 : 1;
      this.priceAlerts = [
        ...this.priceAlerts,
        { id: nextId, triggered: false, createdAt: Date.now(), ...alert }
      ];
      this.persist();
    },
    removeAlert(id) {
      this.priceAlerts = this.priceAlerts.filter((item) => item.id !== id);
      this.persist();
    },
    resetTriggeredAlert(id) {
      this.priceAlerts = this.priceAlerts.map((item) =>
        item.id === id ? { ...item, triggered: false } : item
      );
      this.persist();
    },
    checkAlerts(price) {
      const triggered = [];
      this.priceAlerts = this.priceAlerts.map((alert) => {
        if (alert.triggered) {
          return alert;
        }

        const hit =
          (alert.direction === "above" && price >= alert.price) ||
          (alert.direction === "below" && price <= alert.price);

        if (!hit) {
          return alert;
        }

        triggered.push(alert);
        return { ...alert, triggered: true };
      });

      if (triggered.length) {
        this.persist();
      }

      return triggered;
    }
  }
});
