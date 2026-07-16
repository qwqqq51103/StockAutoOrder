<script setup>
import { computed, onMounted, onUnmounted, watch } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";
import { roleLabel } from "@/services/formatters";
import { useAdminStore } from "@/stores/admin";
import { useAuthStore } from "@/stores/auth";
import { useMarketStore } from "@/stores/market";
import { usePortfolioStore } from "@/stores/portfolio";

const route = useRoute();
const authStore = useAuthStore();
const marketStore = useMarketStore();
const portfolioStore = usePortfolioStore();
const adminStore = useAdminStore();

const statusLabel = computed(() => {
  if (marketStore.connectionStatus === "connected") return "即時連線中";
  if (marketStore.connectionStatus === "connecting") return "正在連線";
  return "離線";
});

async function loadPrivateData() {
  try {
    await portfolioStore.bootstrap();

    if (authStore.isAdmin) {
      await adminStore.bootstrap();
      return;
    }

    adminStore.reset();
  } catch {
    authStore.logout();
    portfolioStore.reset();
    adminStore.reset();
  }
}

async function refreshRealtimeData() {
  if (!authStore.isAuthenticated) return;

  await portfolioStore.refreshRealtime();

  if (authStore.isAdmin) {
    await adminStore.refreshRealtime();
  }
}

onMounted(async () => {
  authStore.hydrate();
  await marketStore.bootstrap();
  marketStore.connect();

  if (authStore.isAuthenticated) {
    await loadPrivateData();
  }
});

watch(
  () => [authStore.isAuthenticated, authStore.role],
  async ([isAuthenticated]) => {
    if (isAuthenticated) {
      await loadPrivateData();
      return;
    }

    portfolioStore.reset();
    adminStore.reset();
  }
);

watch(
  () => marketStore.realtimeVersion,
  () => {
    refreshRealtimeData();
  }
);

onUnmounted(() => {
  marketStore.disconnect();
});
</script>

<template>
  <div class="min-h-screen">
    <header class="border-b border-white/10 bg-slate-950/80 backdrop-blur">
      <div class="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-4 sm:px-6">
        <div class="flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl border border-emerald-400/20 bg-emerald-500/10 text-lg font-bold text-emerald-300">
            SG
          </div>
          <div>
            <p class="text-xs uppercase tracking-[0.28em] text-slate-500">StockGameServer</p>
            <h1 class="text-lg font-semibold text-slate-100">模擬交易平台</h1>
          </div>
        </div>

        <nav class="flex items-center gap-2 rounded-2xl border border-white/10 bg-white/5 p-1">
          <RouterLink
            to="/"
            class="rounded-xl px-4 py-2 text-sm transition"
            :class="route.path === '/' ? 'bg-emerald-500 text-slate-950' : 'text-slate-300 hover:bg-white/5'"
          >
            前台遊戲
          </RouterLink>
          <RouterLink
            to="/admin"
            class="rounded-xl px-4 py-2 text-sm transition"
            :class="route.path === '/admin' ? 'bg-sky-400 text-slate-950' : 'text-slate-300 hover:bg-white/5'"
          >
            後台管理
          </RouterLink>
        </nav>

        <div class="flex items-center gap-3 text-sm">
          <span class="hidden text-slate-400 sm:inline">{{ statusLabel }}</span>
          <span
            class="h-2.5 w-2.5 rounded-full"
            :class="marketStore.connectionStatus === 'connected' ? 'bg-emerald-400' : marketStore.connectionStatus === 'connecting' ? 'bg-amber-300' : 'bg-rose-400'"
          />
          <div class="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-slate-300">
            {{ authStore.isAuthenticated ? `${authStore.username} / ${roleLabel(authStore.role)}` : "尚未登入" }}
          </div>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-7xl px-4 py-6 sm:px-6">
      <RouterView />
    </main>
  </div>
</template>
