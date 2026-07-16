<script setup>
import { computed } from "vue";
import {
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

const props = defineProps({
  account: {
    type: Object,
    default: null
  },
  activeOrders: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["cancel"]);

const positions = computed(() => props.account?.positions || []);

function fillRatio(order) {
  if (!order?.quantity) return 0;
  return Math.min(100, Math.round((Number(order.filledQuantity || 0) / Number(order.quantity || 0)) * 100));
}
</script>

<template>
  <section class="glass-panel min-w-0 p-5">
    <div class="mb-4 flex items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="section-title">持倉與委託</p>
        <h3 class="mt-2 text-lg font-semibold text-white">資產總覽</h3>
      </div>
      <div class="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-slate-400">
        {{ loading ? "同步中" : "已更新" }}
      </div>
    </div>

    <div v-if="account" class="space-y-4">
      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div class="metric-card">
          <div class="section-title">總資產</div>
          <div class="metric-value mt-2">{{ formatMoney(account.totalAssets) }}</div>
        </div>
        <div class="metric-card">
          <div class="section-title">總損益</div>
          <div class="metric-value mt-2" :class="account.totalPnl >= 0 ? 'positive' : 'negative'">
            {{ formatSignedMoney(account.totalPnl) }}
          </div>
        </div>
        <div class="metric-card">
          <div class="section-title">可用現金</div>
          <div class="metric-value mt-2">{{ formatMoney(account.availableCash) }}</div>
        </div>
        <div class="metric-card">
          <div class="section-title">報酬率</div>
          <div class="metric-value mt-2" :class="account.returnRate >= 0 ? 'positive' : 'negative'">
            {{ formatPercent(account.returnRate) }}
          </div>
        </div>
      </div>

      <div class="grid gap-4 xl:grid-cols-[minmax(0,1.1fr)_320px]">
        <section class="min-w-0 rounded-2xl border border-white/10 bg-white/5 p-4">
          <div class="mb-3 flex items-center justify-between gap-3">
            <div class="min-w-0">
              <div class="text-sm font-semibold text-white">目前持倉</div>
              <div class="text-xs text-slate-500">標的、數量、均價、現值與未實現損益</div>
            </div>
          </div>

          <div v-if="positions.length" class="space-y-3 panel-scroll">
            <div
              v-for="position in positions"
              :key="position.symbol"
              class="rounded-2xl border border-white/10 bg-slate-950/40 p-4"
            >
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="text-sm font-semibold text-white">{{ position.symbol }}</div>
                  <div class="mt-1 text-xs text-slate-500">
                    可用 {{ formatQty(position.availableQuantity) }} 股 / 凍結 {{ formatQty(position.frozenQuantity) }} 股
                  </div>
                </div>
                <div class="min-w-0 text-right">
                  <div class="text-xs text-slate-500">未實現損益</div>
                  <div class="number-fit text-sm font-semibold" :class="position.unrealizedPnl >= 0 ? 'positive' : 'negative'">
                    {{ formatSignedMoney(position.unrealizedPnl) }}
                  </div>
                </div>
              </div>

              <div class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <div class="rounded-2xl border border-white/10 bg-white/5 p-3">
                  <div class="section-title">總股數</div>
                  <div class="number-fit mt-2 text-lg font-semibold text-white">{{ formatQty(position.totalQuantity) }} 股</div>
                </div>
                <div class="rounded-2xl border border-white/10 bg-white/5 p-3">
                  <div class="section-title">均價 / 現價</div>
                  <div class="number-fit mt-2 text-sm text-white">
                    {{ formatPrice(position.avgCostPrice) }} / {{ formatPrice(position.lastPrice) }}
                  </div>
                </div>
                <div class="rounded-2xl border border-white/10 bg-white/5 p-3">
                  <div class="section-title">股票現值</div>
                  <div class="number-fit mt-2 text-lg font-semibold text-white">{{ formatMoney(position.marketValue) }}</div>
                </div>
                <div class="rounded-2xl border border-white/10 bg-white/5 p-3">
                  <div class="section-title">資產占比</div>
                  <div class="number-fit mt-2 text-lg font-semibold text-white">{{ formatPercent(position.allocationPct) }}</div>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="rounded-2xl border border-dashed border-white/10 px-4 py-10 text-center text-sm text-slate-500">
            目前沒有持倉。
          </div>
        </section>

        <section class="min-w-0 rounded-2xl border border-white/10 bg-white/5 p-4">
          <div class="mb-3 flex items-center justify-between gap-3">
            <div class="min-w-0">
              <div class="text-sm font-semibold text-white">進行中委託</div>
              <div class="text-xs text-slate-500">即時查看成交進度</div>
            </div>
            <div class="rounded-full border border-white/10 bg-slate-950/50 px-3 py-1 text-xs text-slate-400">
              {{ activeOrders.length }} 筆
            </div>
          </div>

          <div v-if="activeOrders.length" class="space-y-3 panel-scroll">
            <div
              v-for="order in activeOrders"
              :key="order.id"
              class="rounded-2xl border border-white/10 bg-slate-950/40 p-3"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="text-sm font-semibold" :class="sideClass(order.side)">{{ sideLabel(order.side) }}</span>
                    <span class="rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-[11px] text-slate-300">
                      {{ typeLabel(order.type) }}
                    </span>
                  </div>
                  <div class="mt-1 number-fit text-xs text-slate-500">
                    {{ order.type === "MARKET" ? "市價" : formatPrice(order.price) }} / {{ statusLabel(order.status) }}
                  </div>
                </div>
                <button
                  class="shrink-0 rounded-full border border-rose-300/20 bg-rose-400/10 px-3 py-1 text-xs text-rose-200 transition hover:bg-rose-400/20"
                  @click="emit('cancel', order.id)"
                >
                  取消
                </button>
              </div>

              <div class="mt-3">
                <div class="mb-1 flex items-center justify-between text-xs text-slate-400">
                  <span>成交進度</span>
                  <span class="number-fit">{{ formatQty(order.filledQuantity) }} / {{ formatQty(order.quantity) }} 股</span>
                </div>
                <div class="h-2 overflow-hidden rounded-full bg-slate-900/90">
                  <div
                    class="h-full rounded-full"
                    :class="order.side === 'BUY' ? 'bg-emerald-400' : 'bg-rose-400'"
                    :style="{ width: `${fillRatio(order)}%` }"
                  />
                </div>
              </div>
            </div>
          </div>

          <div v-else class="rounded-2xl border border-dashed border-white/10 px-4 py-10 text-center text-sm text-slate-500">
            目前沒有進行中的委託。
          </div>
        </section>
      </div>
    </div>

    <div v-else class="rounded-2xl border border-dashed border-white/10 px-4 py-10 text-center text-sm text-slate-500">
      登入後可查看持倉與委託。
    </div>
  </section>
</template>
