<script setup>
import { computed } from "vue";
import { formatPrice, formatQty } from "@/services/formatters";

const props = defineProps({
  orderBook: {
    type: Object,
    default: () => ({ bids: [], asks: [] })
  }
});

const asks = computed(() => [...(props.orderBook?.asks || [])].slice(0, 5).reverse());
const bids = computed(() => [...(props.orderBook?.bids || [])].slice(0, 5));
const maxDepth = computed(() => {
  const quantities = [...asks.value, ...bids.value].map((item) => Number(item.quantity || 0));
  return Math.max(...quantities, 1);
});
const spread = computed(() => {
  const bestAsk = props.orderBook?.asks?.[0]?.price;
  const bestBid = props.orderBook?.bids?.[0]?.price;
  if (!bestAsk || !bestBid) return "--";
  return formatPrice(bestAsk - bestBid);
});

function depthStyle(quantity) {
  const ratio = (Number(quantity || 0) / maxDepth.value) * 100;
  return { width: `${Math.max(8, Math.min(100, ratio))}%` };
}
</script>

<template>
  <section class="glass-panel min-w-0 p-5">
    <div class="mb-4 flex items-end justify-between gap-3">
      <div class="min-w-0">
        <p class="section-title">委託簿</p>
        <h3 class="mt-2 text-lg font-semibold text-white">即時五檔</h3>
      </div>
      <div class="rounded-full border border-amber-300/20 bg-amber-400/10 px-3 py-1 text-xs text-amber-200">
        價差 {{ spread }}
      </div>
    </div>

    <div class="space-y-2 text-sm">
      <div class="grid grid-cols-[1fr_0.9fr_0.6fr] gap-2 px-3 text-xs uppercase tracking-[0.2em] text-slate-500">
        <span>價格</span>
        <span class="text-right">數量</span>
        <span class="text-right">筆數</span>
      </div>

      <div class="space-y-1">
        <div
          v-for="ask in asks"
          :key="`ask-${ask.price}`"
          class="relative overflow-hidden rounded-2xl border border-rose-400/10 bg-rose-500/8 px-3 py-2"
        >
          <div class="absolute inset-y-0 right-0 bg-rose-400/10" :style="depthStyle(ask.quantity)" />
          <div class="relative grid grid-cols-[1fr_0.9fr_0.6fr] gap-2">
            <span class="number-fit negative">{{ formatPrice(ask.price) }}</span>
            <span class="number-fit text-right text-slate-300">{{ formatQty(ask.quantity) }}</span>
            <span class="number-fit text-right text-slate-500">{{ ask.orderCount }}</span>
          </div>
        </div>
      </div>

      <div class="rounded-2xl border border-white/10 bg-white/5 px-3 py-2 text-center text-xs text-slate-400">
        最佳價差 {{ spread }}
      </div>

      <div class="space-y-1">
        <div
          v-for="bid in bids"
          :key="`bid-${bid.price}`"
          class="relative overflow-hidden rounded-2xl border border-emerald-400/10 bg-emerald-500/8 px-3 py-2"
        >
          <div class="absolute inset-y-0 left-0 bg-emerald-400/10" :style="depthStyle(bid.quantity)" />
          <div class="relative grid grid-cols-[1fr_0.9fr_0.6fr] gap-2">
            <span class="number-fit positive">{{ formatPrice(bid.price) }}</span>
            <span class="number-fit text-right text-slate-300">{{ formatQty(bid.quantity) }}</span>
            <span class="number-fit text-right text-slate-500">{{ bid.orderCount }}</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
