<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage, ElNotification } from "element-plus";
import AccountPanel from "@/components/player/AccountPanel.vue";
import AuthCard from "@/components/player/AuthCard.vue";
import ChartCard from "@/components/player/ChartCard.vue";
import HistoryPanel from "@/components/player/HistoryPanel.vue";
import LeaderboardPanel from "@/components/player/LeaderboardPanel.vue";
import OrderBookCard from "@/components/player/OrderBookCard.vue";
import OrderFormCard from "@/components/player/OrderFormCard.vue";
import TradeTapeCard from "@/components/player/TradeTapeCard.vue";
import TradingToolsCard from "@/components/player/TradingToolsCard.vue";
import { formatMoney, formatPercent, formatPrice, roleLabel } from "@/services/formatters";
import { useAuthStore } from "@/stores/auth";
import { useMarketStore } from "@/stores/market";
import { usePortfolioStore } from "@/stores/portfolio";
import { usePreferencesStore } from "@/stores/preferences";

const authStore = useAuthStore();
const marketStore = useMarketStore();
const portfolioStore = usePortfolioStore();
const preferencesStore = usePreferencesStore();

const workspaceTab = ref("portfolio");

const quoteTone = computed(() => ((marketStore.quote?.change || 0) >= 0 ? "positive" : "negative"));
const symbol = computed(() => marketStore.quote?.symbol || portfolioStore.account?.marketSymbol || "DEMO");
const connectionLabel = computed(() => {
  if (marketStore.connectionStatus === "connected") return "即時連線中";
  if (marketStore.connectionStatus === "connecting") return "連線建立中";
  return "連線中斷";
});

const workspaceTabs = computed(() => {
  if (authStore.isAuthenticated) {
    return [
      { key: "portfolio", label: "持倉" },
      { key: "history", label: "歷史" },
      { key: "trades", label: "成交" },
      { key: "leaderboard", label: "排行" },
      { key: "tools", label: "工具" }
    ];
  }

  return [
    { key: "trades", label: "成交" },
    { key: "leaderboard", label: "排行" }
  ];
});

watch(
  () => authStore.isAuthenticated,
  (isAuthenticated) => {
    workspaceTab.value = isAuthenticated ? "portfolio" : "trades";
  },
  { immediate: true }
);

async function submitOrder(payload) {
  if (!authStore.isAuthenticated) {
    ElMessage.warning("請先登入再送出委託。");
    return;
  }

  try {
    await portfolioStore.placeOrder(payload);
    ElMessage.success("委託已送出。");
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function cancelOrder(orderId) {
  try {
    await portfolioStore.cancelOrder(orderId);
    ElMessage.success("委託已取消。");
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function submitQuickOrder(preset) {
  await submitOrder({
    side: preset.side,
    type: "MARKET",
    price: 0,
    quantity: preset.quantity
  });
}

function notifyAlert(alert, price) {
  const message = `${alert.direction === "above" ? "已突破高於" : "已跌破低於"} ${formatPrice(alert.price)}，目前價格 ${formatPrice(price)}。`;

  ElNotification({
    title: "價格提醒",
    message,
    type: "warning",
    duration: 5000
  });

  if (preferencesStore.notificationPermission === "granted" && typeof Notification !== "undefined") {
    new Notification("StockGameServer 價格提醒", { body: message });
  }
}

function logout() {
  authStore.logout();
}

onMounted(async () => {
  preferencesStore.hydrate();
  await preferencesStore.ensureNotificationPermission();
});

watch(
  () => marketStore.quote?.lastPrice,
  (price) => {
    if (!price) return;
    const triggered = preferencesStore.checkAlerts(Number(price));
    triggered.forEach((alert) => notifyAlert(alert, Number(price)));
  }
);
</script>

<template>
  <section class="space-y-5">
    <div class="glass-panel overflow-hidden p-5 sm:p-6">
      <div class="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-full border border-emerald-300/20 bg-emerald-400/10 px-3 py-1 text-xs font-semibold tracking-[0.25em] text-emerald-200">
              {{ symbol }}
            </span>
            <span class="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-slate-300">
              {{ connectionLabel }}
            </span>
          </div>

          <h2 class="mt-3 text-2xl font-semibold leading-tight text-white sm:text-3xl">
            交易工作台
          </h2>
          <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-400">
            把看盤、下單、持倉與成交追蹤放在同一頁，縮短捲動距離。
          </p>
        </div>

        <div v-if="authStore.isAuthenticated" class="grid w-full gap-3 sm:grid-cols-2 xl:w-[360px]">
          <div class="metric-card">
            <div class="section-title">玩家</div>
            <div class="metric-value mt-2">{{ authStore.username }}</div>
            <div class="mt-1 text-xs text-slate-400">{{ roleLabel(authStore.role) }}</div>
          </div>
          <div class="metric-card">
            <div class="section-title">總資產</div>
            <div class="metric-value mt-2">{{ portfolioStore.account ? formatMoney(portfolioStore.account.totalAssets) : "--" }}</div>
          </div>
          <div class="metric-card">
            <div class="section-title">可用現金</div>
            <div class="metric-value mt-2">{{ portfolioStore.account ? formatMoney(portfolioStore.account.availableCash) : "--" }}</div>
          </div>
          <button
            class="rounded-2xl border border-white/15 bg-white/5 px-4 py-3 text-sm text-white transition hover:bg-white/10"
            @click="logout"
          >
            登出
          </button>
        </div>

        <div v-else class="w-full xl:w-[360px]">
          <AuthCard />
        </div>
      </div>

      <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div class="metric-card">
          <div class="section-title">最新成交</div>
          <div class="metric-value mt-2">{{ marketStore.quote ? formatPrice(marketStore.quote.lastPrice) : "--" }}</div>
        </div>
        <div class="metric-card">
          <div class="section-title">漲跌幅</div>
          <div class="metric-value mt-2" :class="quoteTone">
            {{ marketStore.quote ? formatPercent(marketStore.quote.changePct) : "--" }}
          </div>
        </div>
        <div class="metric-card">
          <div class="section-title">成交量</div>
          <div class="metric-value mt-2">
            {{ marketStore.quote ? marketStore.quote.totalVolume.toLocaleString("zh-TW") : "--" }}
          </div>
        </div>
        <div class="metric-card">
          <div class="section-title">最佳價差</div>
          <div class="metric-value mt-2">
            {{
              marketStore.quote?.bestAsk && marketStore.quote?.bestBid
                ? formatPrice(marketStore.quote.bestAsk - marketStore.quote.bestBid)
                : "--"
            }}
          </div>
        </div>
      </div>

      <div v-if="marketStore.latestAnnouncement" class="mt-4 rounded-2xl border border-sky-300/20 bg-sky-400/10 px-4 py-3 text-sm text-sky-100">
        <span class="mr-2 text-xs uppercase tracking-[0.2em] text-sky-200">公告</span>
        {{ marketStore.latestAnnouncement }}
      </div>
    </div>

    <div class="grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_360px]">
      <div class="min-w-0">
        <ChartCard :candles="marketStore.candles" :quote="marketStore.quote" />
      </div>

      <div class="min-w-0">
        <OrderFormCard
          v-if="authStore.isAuthenticated"
          :quote="marketStore.quote"
          :account="portfolioStore.account"
          :loading="portfolioStore.status === 'loading'"
          @submit="submitOrder"
        />
        <div v-else class="glass-panel flex h-full items-center justify-center p-8 text-center text-sm text-slate-400">
          登入後可直接在這裡下單。
        </div>
      </div>
    </div>

    <div class="grid gap-5 xl:grid-cols-[300px_minmax(0,1fr)]">
      <div class="min-w-0">
        <OrderBookCard :order-book="marketStore.orderBook" />
      </div>

      <div class="min-w-0 space-y-3">
        <div class="flex flex-wrap gap-2">
          <button
            v-for="tab in workspaceTabs"
            :key="tab.key"
            class="rounded-full px-4 py-2 text-sm transition"
            :class="workspaceTab === tab.key ? 'bg-emerald-400 text-slate-950' : 'bg-white/5 text-slate-300'"
            @click="workspaceTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <AccountPanel
          v-if="workspaceTab === 'portfolio' && authStore.isAuthenticated"
          :account="portfolioStore.account"
          :active-orders="portfolioStore.activeOrders"
          :loading="portfolioStore.status === 'loading' || portfolioStore.realtimeLoading"
          @cancel="cancelOrder"
        />

        <HistoryPanel
          v-else-if="workspaceTab === 'history' && authStore.isAuthenticated"
          :account="portfolioStore.account"
          :history="portfolioStore.orderHistory"
          :show-summary="false"
        />

        <TradeTapeCard
          v-else-if="workspaceTab === 'trades'"
          :trades="marketStore.recentTrades"
        />

        <LeaderboardPanel
          v-else-if="workspaceTab === 'leaderboard'"
          :leaderboard="marketStore.leaderboard"
        />

        <TradingToolsCard
          v-else-if="workspaceTab === 'tools' && authStore.isAuthenticated"
          :last-price="marketStore.quote?.lastPrice || 0"
          :latest-announcement="marketStore.latestAnnouncement"
          @quick-order="submitQuickOrder"
        />
      </div>
    </div>
  </section>
</template>
