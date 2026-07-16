<script setup>
import { computed, reactive, watch } from "vue";
import { formatMoney, formatPrice, formatQty, sideLabel, typeLabel } from "@/services/formatters";

const props = defineProps({
  quote: {
    type: Object,
    default: null
  },
  account: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["submit"]);

const form = reactive({
  side: "BUY",
  type: "LIMIT",
  price: "",
  quantity: 100
});

const referencePrice = computed(() => Number(props.quote?.lastPrice || 0));
const effectivePrice = computed(() => (form.type === "MARKET" ? referencePrice.value : Number(form.price || 0)));
const estimatedAmount = computed(() => effectivePrice.value * Number(form.quantity || 0));
const maxBuyQuantity = computed(() => {
  if (!effectivePrice.value || !props.account?.availableCash) return 0;
  return Math.floor(Number(props.account.availableCash) / effectivePrice.value);
});

watch(
  () => props.quote?.lastPrice,
  (price) => {
    if (!price) return;
    if (!form.price || Math.abs(Number(form.price) - Number(price)) > Number(price) * 0.1) {
      form.price = Number(price).toFixed(2);
    }
  },
  { immediate: true }
);

function applyPrice(type) {
  if (!props.quote) return;

  if (type === "bid" && props.quote.bestBid > 0) {
    form.price = Number(props.quote.bestBid).toFixed(2);
    return;
  }
  if (type === "ask" && props.quote.bestAsk > 0) {
    form.price = Number(props.quote.bestAsk).toFixed(2);
    return;
  }
  if (props.quote.lastPrice > 0) {
    form.price = Number(props.quote.lastPrice).toFixed(2);
  }
}

function applyQuantity(mode) {
  const availableStocks = Number(props.account?.availableStocks || 0);

  if (form.side === "BUY") {
    if (!effectivePrice.value) return;
    const baseQuantity = maxBuyQuantity.value;
    if (mode === "all") {
      form.quantity = Math.max(1, baseQuantity);
      return;
    }
    const ratio = mode === "half" ? 0.5 : 0.25;
    form.quantity = Math.max(1, Math.floor(baseQuantity * ratio));
    return;
  }

  if (availableStocks <= 0) {
    form.quantity = 1;
    return;
  }
  if (mode === "all") {
    form.quantity = availableStocks;
    return;
  }
  const ratio = mode === "half" ? 0.5 : 0.25;
  form.quantity = Math.max(1, Math.floor(availableStocks * ratio));
}

function quickFill(side, quantity) {
  form.side = side;
  form.type = "MARKET";
  form.quantity = quantity;
}

function submit() {
  emit("submit", {
    side: form.side,
    type: form.type,
    price: form.type === "MARKET" ? 0 : Number(form.price || 0),
    quantity: Number(form.quantity || 0)
  });
}
</script>

<template>
  <section class="glass-panel min-w-0 p-5">
    <div class="mb-4 flex items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="section-title">下單面板</p>
        <h3 class="mt-2 text-lg font-semibold text-white">送出委託</h3>
      </div>
      <div class="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-slate-400">
        {{ quote?.symbol || account?.marketSymbol || "DEMO" }}
      </div>
    </div>

    <div class="mb-4 grid grid-cols-2 gap-2 rounded-2xl border border-white/10 bg-white/5 p-1">
      <button
        class="rounded-xl px-4 py-3 text-sm font-medium transition"
        :class="form.side === 'BUY' ? 'bg-emerald-400 text-slate-950' : 'text-slate-300'"
        @click="form.side = 'BUY'"
      >
        買進
      </button>
      <button
        class="rounded-xl px-4 py-3 text-sm font-medium transition"
        :class="form.side === 'SELL' ? 'bg-rose-400 text-slate-950' : 'text-slate-300'"
        @click="form.side = 'SELL'"
      >
        賣出
      </button>
    </div>

    <div class="grid gap-4 lg:grid-cols-2">
      <div class="space-y-4 min-w-0">
        <label class="block space-y-2">
          <span class="text-sm text-slate-300">委託類型</span>
          <select
            v-model="form.type"
            class="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm outline-none"
          >
            <option value="LIMIT">限價</option>
            <option value="MARKET">市價</option>
            <option value="FOK">FOK</option>
          </select>
        </label>

        <label v-if="form.type !== 'MARKET'" class="block space-y-2">
          <div class="flex items-center justify-between gap-2">
            <span class="text-sm text-slate-300">委託價格</span>
            <div class="flex gap-2 text-xs">
              <button class="rounded-full border border-white/10 px-2 py-1 text-slate-400" @click="applyPrice('bid')">買一</button>
              <button class="rounded-full border border-white/10 px-2 py-1 text-slate-400" @click="applyPrice('last')">現價</button>
              <button class="rounded-full border border-white/10 px-2 py-1 text-slate-400" @click="applyPrice('ask')">賣一</button>
            </div>
          </div>
          <input
            v-model="form.price"
            class="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm outline-none"
            type="number"
            step="0.01"
            min="0.01"
          />
        </label>

        <label class="block space-y-2">
          <div class="flex items-center justify-between gap-2">
            <span class="text-sm text-slate-300">委託數量</span>
            <div class="flex gap-2 text-xs">
              <button class="rounded-full border border-white/10 px-2 py-1 text-slate-400" @click="applyQuantity('quarter')">25%</button>
              <button class="rounded-full border border-white/10 px-2 py-1 text-slate-400" @click="applyQuantity('half')">50%</button>
              <button class="rounded-full border border-white/10 px-2 py-1 text-slate-400" @click="applyQuantity('all')">全部</button>
            </div>
          </div>
          <input
            v-model="form.quantity"
            class="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm outline-none"
            type="number"
            min="1"
          />
        </label>
      </div>

      <div class="min-w-0 space-y-3">
        <div class="rounded-2xl border border-white/10 bg-white/5 p-4 text-sm">
          <div class="flex items-center justify-between gap-3 text-slate-400">
            <span>現價</span>
            <span class="number-fit text-white">{{ quote ? formatPrice(quote.lastPrice) : "--" }}</span>
          </div>
          <div class="mt-2 flex items-center justify-between gap-3 text-slate-400">
            <span>預估金額</span>
            <span class="number-fit text-white">{{ formatMoney(estimatedAmount) }}</span>
          </div>
          <div class="mt-2 flex items-center justify-between gap-3 text-slate-400">
            <span>委託摘要</span>
            <span class="number-fit text-white">{{ sideLabel(form.side) }} / {{ typeLabel(form.type) }}</span>
          </div>
        </div>

        <div class="rounded-2xl border border-white/10 bg-white/5 p-4 text-sm">
          <div class="flex items-center justify-between gap-3 text-slate-400">
            <span>可用現金</span>
            <span class="number-fit text-white">{{ account ? formatMoney(account.availableCash) : "--" }}</span>
          </div>
          <div class="mt-2 flex items-center justify-between gap-3 text-slate-400">
            <span>可賣股數</span>
            <span class="number-fit text-white">{{ account ? `${formatQty(account.availableStocks)} 股` : "--" }}</span>
          </div>
          <div class="mt-2 flex items-center justify-between gap-3 text-slate-400">
            <span>最多可買</span>
            <span class="number-fit text-white">{{ `${formatQty(maxBuyQuantity)} 股` }}</span>
          </div>
        </div>

        <div class="grid grid-cols-3 gap-2">
          <button class="rounded-2xl border border-emerald-400/20 bg-emerald-400/10 px-3 py-2 text-sm text-emerald-300" @click="quickFill('BUY', 50)">買 50</button>
          <button class="rounded-2xl border border-emerald-400/20 bg-emerald-400/10 px-3 py-2 text-sm text-emerald-300" @click="quickFill('BUY', 100)">買 100</button>
          <button class="rounded-2xl border border-emerald-400/20 bg-emerald-400/10 px-3 py-2 text-sm text-emerald-300" @click="quickFill('BUY', 200)">買 200</button>
          <button class="rounded-2xl border border-rose-400/20 bg-rose-400/10 px-3 py-2 text-sm text-rose-300" @click="quickFill('SELL', 50)">賣 50</button>
          <button class="rounded-2xl border border-rose-400/20 bg-rose-400/10 px-3 py-2 text-sm text-rose-300" @click="quickFill('SELL', 100)">賣 100</button>
          <button class="rounded-2xl border border-rose-400/20 bg-rose-400/10 px-3 py-2 text-sm text-rose-300" @click="quickFill('SELL', 200)">賣 200</button>
        </div>
      </div>
    </div>

    <button
      class="mt-5 w-full rounded-2xl px-4 py-3 text-sm font-semibold text-slate-950 transition disabled:cursor-not-allowed disabled:opacity-60"
      :class="form.side === 'BUY' ? 'bg-emerald-400 hover:bg-emerald-300' : 'bg-rose-400 hover:bg-rose-300'"
      :disabled="loading"
      @click="submit"
    >
      {{ loading ? "送出中..." : `${form.side === "BUY" ? "送出買進" : "送出賣出"}委託` }}
    </button>
  </section>
</template>
