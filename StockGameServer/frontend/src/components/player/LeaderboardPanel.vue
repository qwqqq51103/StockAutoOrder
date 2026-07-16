<script setup>
import { formatMoney, formatQty, formatSignedMoney } from "@/services/formatters";

defineProps({
  leaderboard: {
    type: Array,
    default: () => []
  }
});
</script>

<template>
  <section class="glass-panel min-w-0 overflow-hidden">
    <div class="border-b border-white/10 px-5 py-4">
      <p class="section-title">排行榜</p>
      <h3 class="mt-2 text-lg font-semibold text-white">玩家已實現損益排名</h3>
    </div>

    <div class="max-h-[420px] overflow-auto">
      <table class="min-w-full table-fixed text-sm">
        <thead class="bg-white/5 text-left text-xs uppercase tracking-[0.2em] text-slate-500">
          <tr>
            <th class="w-[14%] px-4 py-3">名次</th>
            <th class="w-[22%] px-4 py-3">玩家</th>
            <th class="w-[24%] px-4 py-3">已實現損益</th>
            <th class="w-[24%] px-4 py-3">可用現金</th>
            <th class="w-[16%] px-4 py-3">持股</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in leaderboard" :key="entry.rank" class="border-t border-white/5 text-slate-300">
            <td class="px-4 py-4">
              <span
                class="inline-flex h-8 w-8 items-center justify-center rounded-full border text-sm font-semibold"
                :class="entry.rank === 1 ? 'border-amber-300/40 bg-amber-400/10 text-amber-200' : entry.rank === 2 ? 'border-slate-300/30 bg-slate-200/10 text-slate-200' : entry.rank === 3 ? 'border-orange-300/30 bg-orange-400/10 text-orange-200' : 'border-white/10 bg-white/5 text-slate-300'"
              >
                {{ entry.rank }}
              </span>
            </td>
            <td class="px-4 py-4"><span class="number-fit block text-white">{{ entry.username }}</span></td>
            <td class="px-4 py-4"><span class="number-fit block font-medium" :class="entry.realizedPnl >= 0 ? 'positive' : 'negative'">{{ formatSignedMoney(entry.realizedPnl) }}</span></td>
            <td class="px-4 py-4"><span class="number-fit block">{{ formatMoney(entry.availableCash) }}</span></td>
            <td class="px-4 py-4"><span class="number-fit block">{{ formatQty(entry.totalStocks) }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
