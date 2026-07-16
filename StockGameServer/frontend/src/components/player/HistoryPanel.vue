<script setup>
import {
  formatDateTime,
  formatMoney,
  formatPercent,
  formatPrice,
  formatQty,
  formatSignedMoney,
  sideClass,
  sideLabel,
  statusLabel,
  typeLabel
} from "@/services/formatters";

defineProps({
  account: {
    type: Object,
    default: null
  },
  history: {
    type: Array,
    default: () => []
  },
  showSummary: {
    type: Boolean,
    default: true
  }
});
</script>

<template>
  <section class="space-y-4 min-w-0">
    <div v-if="showSummary" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <div class="metric-card">
        <div class="section-title">初始資金</div>
        <div class="metric-value mt-2">{{ account ? formatMoney(account.initialCash) : "--" }}</div>
      </div>
      <div class="metric-card">
        <div class="section-title">總資產</div>
        <div class="metric-value mt-2">{{ account ? formatMoney(account.totalAssets) : "--" }}</div>
      </div>
      <div class="metric-card">
        <div class="section-title">總損益</div>
        <div class="metric-value mt-2" :class="account && account.totalPnl >= 0 ? 'positive' : 'negative'">
          {{ account ? formatSignedMoney(account.totalPnl) : "--" }}
        </div>
      </div>
      <div class="metric-card">
        <div class="section-title">報酬率</div>
        <div class="metric-value mt-2" :class="account && account.returnRate >= 0 ? 'positive' : 'negative'">
          {{ account ? formatPercent(account.returnRate) : "--" }}
        </div>
      </div>
    </div>

    <section class="glass-panel min-w-0 overflow-hidden">
      <div class="border-b border-white/10 px-5 py-4">
        <p class="section-title">委託歷史</p>
        <h3 class="mt-2 text-lg font-semibold text-white">最近委託與成交狀態</h3>
      </div>

      <div class="max-h-[420px] overflow-auto">
        <table class="min-w-full table-fixed text-sm">
          <thead class="bg-white/5 text-left text-xs uppercase tracking-[0.2em] text-slate-500">
            <tr>
              <th class="w-[22%] px-4 py-3">時間</th>
              <th class="w-[12%] px-4 py-3">方向</th>
              <th class="w-[12%] px-4 py-3">類型</th>
              <th class="w-[18%] px-4 py-3">價格</th>
              <th class="w-[12%] px-4 py-3">數量</th>
              <th class="w-[12%] px-4 py-3">成交</th>
              <th class="w-[12%] px-4 py-3">狀態</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in history" :key="order.id" class="border-t border-white/5 text-slate-300">
              <td class="px-4 py-3 text-xs sm:text-sm">{{ formatDateTime(order.createdAt) }}</td>
              <td class="px-4 py-3" :class="sideClass(order.side)">{{ sideLabel(order.side) }}</td>
              <td class="px-4 py-3">{{ typeLabel(order.type) }}</td>
              <td class="px-4 py-3"><span class="number-fit block">{{ order.type === "MARKET" ? "市價" : formatPrice(order.price) }}</span></td>
              <td class="px-4 py-3"><span class="number-fit block">{{ formatQty(order.quantity) }}</span></td>
              <td class="px-4 py-3"><span class="number-fit block">{{ formatQty(order.filledQuantity) }}</span></td>
              <td class="px-4 py-3">{{ statusLabel(order.status) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>
