<script setup>
import { formatMoney, formatPrice, formatQty, formatTime } from "@/services/formatters";

defineProps({
  trades: {
    type: Array,
    default: () => []
  }
});
</script>

<template>
  <section class="glass-panel min-w-0 p-5">
    <div class="mb-4">
      <p class="section-title">逐筆成交</p>
      <h3 class="mt-2 text-lg font-semibold text-white">最新成交明細</h3>
    </div>

    <div class="panel-scroll space-y-2">
      <div class="grid grid-cols-[0.95fr_0.9fr_0.7fr_0.95fr] gap-2 px-3 text-xs uppercase tracking-[0.2em] text-slate-500">
        <span>時間</span>
        <span>價格</span>
        <span class="text-right">數量</span>
        <span class="text-right">金額</span>
      </div>

      <div
        v-for="trade in trades.slice(0, 20)"
        :key="trade.transactionId || `${trade.executedAt}-${trade.price}`"
        class="rounded-2xl border border-white/10 bg-white/5 px-3 py-3"
      >
        <div class="grid grid-cols-[0.95fr_0.9fr_0.7fr_0.95fr] gap-2 text-sm">
          <span class="number-fit text-slate-400">{{ formatTime(trade.executedAt) }}</span>
          <span class="number-fit" :class="trade.buyerInitiated ? 'positive' : 'negative'">{{ formatPrice(trade.price) }}</span>
          <span class="number-fit text-right text-slate-300">{{ formatQty(trade.quantity) }}</span>
          <span class="number-fit text-right text-slate-300">{{ formatMoney(trade.amount) }}</span>
        </div>
      </div>
    </div>
  </section>
</template>
