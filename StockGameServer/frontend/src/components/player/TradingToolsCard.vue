<script setup>
import { computed, reactive } from "vue";
import { formatPrice } from "@/services/formatters";
import { usePreferencesStore } from "@/stores/preferences";

defineProps({
  lastPrice: {
    type: Number,
    default: 0
  },
  latestAnnouncement: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["quick-order"]);
const preferencesStore = usePreferencesStore();

const quickTradeForm = reactive({
  side: "BUY",
  quantity: 100
});

const alertForm = reactive({
  direction: "above",
  price: ""
});

const sortedAlerts = computed(() =>
  [...preferencesStore.priceAlerts].sort((a, b) => a.price - b.price)
);

function addQuickTrade() {
  const quantity = Number(quickTradeForm.quantity || 0);
  if (quantity <= 0) return;
  preferencesStore.saveQuickTrade({
    side: quickTradeForm.side,
    quantity
  });
  quickTradeForm.quantity = 100;
}

function addAlert() {
  const price = Number(alertForm.price || 0);
  if (price <= 0) return;
  preferencesStore.addAlert({
    direction: alertForm.direction,
    price
  });
  alertForm.price = "";
}
</script>

<template>
  <section class="glass-panel p-5">
    <div class="mb-5">
      <p class="section-title">交易工具</p>
      <h3 class="mt-2 text-lg font-semibold text-white">價格提醒與快捷交易</h3>
    </div>

    <div v-if="latestAnnouncement" class="mb-5 rounded-2xl border border-sky-300/20 bg-sky-400/10 p-4 text-sm text-sky-100">
      <div class="mb-1 text-xs uppercase tracking-[0.2em] text-sky-200">最新公告</div>
      {{ latestAnnouncement }}
    </div>

    <div>
      <div class="mb-3 flex items-center justify-between">
        <h4 class="text-sm font-semibold text-white">快捷交易預設</h4>
        <span class="text-xs text-slate-500">{{ preferencesStore.quickTrades.length }}/8</span>
      </div>

      <div class="grid grid-cols-2 gap-2">
        <button
          v-for="preset in preferencesStore.quickTrades"
          :key="preset.id"
          class="rounded-2xl px-3 py-2 text-sm transition"
          :class="preset.side === 'BUY' ? 'bg-emerald-400/10 text-emerald-300' : 'bg-rose-400/10 text-rose-300'"
          @click="emit('quick-order', preset)"
        >
          {{ preset.side === "BUY" ? "買進" : "賣出" }} {{ preset.quantity }}
        </button>
      </div>

      <div class="mt-4 grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
        <select
          v-model="quickTradeForm.side"
          class="rounded-2xl border border-white/10 bg-slate-950/60 px-3 py-2 text-sm outline-none"
        >
          <option value="BUY">買進</option>
          <option value="SELL">賣出</option>
        </select>
        <input
          v-model="quickTradeForm.quantity"
          class="rounded-2xl border border-white/10 bg-slate-950/60 px-3 py-2 text-sm outline-none"
          type="number"
          min="1"
        />
        <button class="rounded-2xl bg-white/10 px-3 py-2 text-sm text-white" @click="addQuickTrade">
          新增
        </button>
      </div>

      <div class="mt-3 space-y-2">
        <div
          v-for="preset in preferencesStore.quickTrades"
          :key="`manage-${preset.id}`"
          class="flex items-center justify-between rounded-2xl border border-white/10 bg-white/5 px-3 py-2 text-sm"
        >
          <span :class="preset.side === 'BUY' ? 'positive' : 'negative'">
            {{ preset.side === "BUY" ? "買進" : "賣出" }} {{ preset.quantity }} 股
          </span>
          <button class="text-xs text-slate-400 hover:text-white" @click="preferencesStore.removeQuickTrade(preset.id)">
            移除
          </button>
        </div>
      </div>
    </div>

    <div class="mt-6">
      <div class="mb-3 flex items-center justify-between">
        <h4 class="text-sm font-semibold text-white">價格提醒</h4>
        <span class="text-xs text-slate-500">現價 {{ formatPrice(lastPrice) }}</span>
      </div>

      <div class="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
        <select
          v-model="alertForm.direction"
          class="rounded-2xl border border-white/10 bg-slate-950/60 px-3 py-2 text-sm outline-none"
        >
          <option value="above">突破高於</option>
          <option value="below">跌破低於</option>
        </select>
        <input
          v-model="alertForm.price"
          class="rounded-2xl border border-white/10 bg-slate-950/60 px-3 py-2 text-sm outline-none"
          type="number"
          step="0.01"
          min="0.01"
        />
        <button class="rounded-2xl bg-sky-400/20 px-3 py-2 text-sm text-sky-100" @click="addAlert">
          新增
        </button>
      </div>

      <div class="mt-3 space-y-2">
        <div
          v-for="alert in sortedAlerts"
          :key="alert.id"
          class="flex items-center justify-between rounded-2xl border border-white/10 bg-white/5 px-3 py-2 text-sm"
        >
          <div>
            <div class="text-white">
              {{ alert.direction === "above" ? "突破高於" : "跌破低於" }} {{ formatPrice(alert.price) }}
            </div>
            <div class="text-xs" :class="alert.triggered ? 'text-amber-300' : 'text-slate-500'">
              {{ alert.triggered ? "已觸發" : "等待中" }}
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button
              v-if="alert.triggered"
              class="text-xs text-sky-300 hover:text-sky-100"
              @click="preferencesStore.resetTriggeredAlert(alert.id)"
            >
              重設
            </button>
            <button class="text-xs text-slate-400 hover:text-white" @click="preferencesStore.removeAlert(alert.id)">
              移除
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
