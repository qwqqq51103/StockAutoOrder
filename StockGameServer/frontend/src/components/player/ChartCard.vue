<script setup>
import { createChart, CrosshairMode } from "lightweight-charts";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { formatPrice } from "@/services/formatters";
import {
  aggregateCandles,
  calculateBollingerBands,
  calculateMacd,
  calculateRsi
} from "@/services/indicators";

const props = defineProps({
  candles: {
    type: Array,
    default: () => []
  },
  quote: {
    type: Object,
    default: null
  }
});

const timeframes = [
  { label: "1m", value: 1 },
  { label: "5m", value: 5 },
  { label: "15m", value: 15 },
  { label: "30m", value: 30 }
];

const selectedTimeframe = ref(1);
const showBb = ref(true);
const showRsi = ref(true);
const showMacd = ref(false);

const mainChartRef = ref(null);
const rsiChartRef = ref(null);
const macdChartRef = ref(null);

const aggregatedCandles = computed(() => aggregateCandles(props.candles, selectedTimeframe.value));
const lastCandle = computed(() => aggregatedCandles.value[aggregatedCandles.value.length - 1] || null);
const bollingerBands = computed(() => calculateBollingerBands(aggregatedCandles.value));
const rsiData = computed(() => calculateRsi(aggregatedCandles.value));
const macdData = computed(() => calculateMacd(aggregatedCandles.value));

let mainChart;
let rsiChart;
let macdChart;
let candleSeries;
let volumeSeries;
let bbUpperSeries;
let bbMiddleSeries;
let bbLowerSeries;
let rsiSeries;
let rsiUpperGuide;
let rsiLowerGuide;
let macdSeries;
let macdSignalSeries;
let macdHistogramSeries;

function createBaseChart(container, height) {
  return createChart(container, {
    width: container.clientWidth,
    height,
    layout: {
      background: { color: "transparent" },
      textColor: "#94a3b8"
    },
    grid: {
      vertLines: { color: "rgba(148, 163, 184, 0.08)" },
      horzLines: { color: "rgba(148, 163, 184, 0.08)" }
    },
    crosshair: { mode: CrosshairMode.Normal },
    rightPriceScale: {
      borderColor: "rgba(148, 163, 184, 0.12)"
    },
    timeScale: {
      borderColor: "rgba(148, 163, 184, 0.12)",
      timeVisible: true
    }
  });
}

function syncVisibleRange(sourceChart, targetChart) {
  sourceChart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
    targetChart.timeScale().setVisibleLogicalRange(range);
  });
}

function renderCharts() {
  if (!mainChart) return;

  const candles = aggregatedCandles.value;

  candleSeries.setData(candles.map((item) => ({
    time: item.time,
    open: item.open,
    high: item.high,
    low: item.low,
    close: item.close
  })));

  volumeSeries.setData(candles.map((item) => ({
    time: item.time,
    value: item.volume,
    color: item.close >= item.open ? "rgba(52, 211, 153, 0.45)" : "rgba(251, 113, 133, 0.45)"
  })));

  bbUpperSeries.applyOptions({ visible: showBb.value });
  bbMiddleSeries.applyOptions({ visible: showBb.value });
  bbLowerSeries.applyOptions({ visible: showBb.value });
  bbUpperSeries.setData(bollingerBands.value.upper);
  bbMiddleSeries.setData(bollingerBands.value.middle);
  bbLowerSeries.setData(bollingerBands.value.lower);

  rsiSeries.applyOptions({ visible: showRsi.value });
  rsiUpperGuide.applyOptions({ visible: showRsi.value });
  rsiLowerGuide.applyOptions({ visible: showRsi.value });
  rsiSeries.setData(rsiData.value);
  rsiUpperGuide.setData(candles.map((item) => ({ time: item.time, value: 70 })));
  rsiLowerGuide.setData(candles.map((item) => ({ time: item.time, value: 30 })));

  macdSeries.applyOptions({ visible: showMacd.value });
  macdSignalSeries.applyOptions({ visible: showMacd.value });
  macdHistogramSeries.applyOptions({ visible: showMacd.value });
  macdSeries.setData(macdData.value.macd);
  macdSignalSeries.setData(macdData.value.signal);
  macdHistogramSeries.setData(macdData.value.histogram);

  mainChart.timeScale().fitContent();
  rsiChart.timeScale().fitContent();
  macdChart.timeScale().fitContent();
}

function resizeCharts() {
  if (mainChart && mainChartRef.value) {
    mainChart.applyOptions({ width: mainChartRef.value.clientWidth, height: mainChartRef.value.clientHeight });
  }
  if (rsiChart && rsiChartRef.value) {
    rsiChart.applyOptions({ width: rsiChartRef.value.clientWidth, height: rsiChartRef.value.clientHeight });
  }
  if (macdChart && macdChartRef.value) {
    macdChart.applyOptions({ width: macdChartRef.value.clientWidth, height: macdChartRef.value.clientHeight });
  }
}

function setupCharts() {
  if (!mainChartRef.value || !rsiChartRef.value || !macdChartRef.value) return;

  mainChart = createBaseChart(mainChartRef.value, 360);
  rsiChart = createBaseChart(rsiChartRef.value, 140);
  macdChart = createBaseChart(macdChartRef.value, 160);

  candleSeries = mainChart.addCandlestickSeries({
    upColor: "#10b981",
    downColor: "#fb7185",
    borderVisible: false,
    wickUpColor: "#34d399",
    wickDownColor: "#f43f5e"
  });

  volumeSeries = mainChart.addHistogramSeries({ priceFormat: { type: "volume" }, priceScaleId: "" });
  volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });

  bbUpperSeries = mainChart.addLineSeries({ color: "#38bdf8", lineWidth: 1, visible: showBb.value });
  bbMiddleSeries = mainChart.addLineSeries({ color: "#fbbf24", lineWidth: 1, visible: showBb.value });
  bbLowerSeries = mainChart.addLineSeries({ color: "#38bdf8", lineWidth: 1, visible: showBb.value });

  rsiSeries = rsiChart.addLineSeries({ color: "#a78bfa", lineWidth: 2, visible: showRsi.value });
  rsiUpperGuide = rsiChart.addLineSeries({ color: "rgba(251, 191, 36, 0.45)", lineWidth: 1, visible: showRsi.value });
  rsiLowerGuide = rsiChart.addLineSeries({ color: "rgba(251, 191, 36, 0.45)", lineWidth: 1, visible: showRsi.value });

  macdSeries = macdChart.addLineSeries({ color: "#38bdf8", lineWidth: 2, visible: showMacd.value });
  macdSignalSeries = macdChart.addLineSeries({ color: "#f59e0b", lineWidth: 2, visible: showMacd.value });
  macdHistogramSeries = macdChart.addHistogramSeries({ priceFormat: { type: "price", precision: 2 }, visible: showMacd.value });

  syncVisibleRange(mainChart, rsiChart);
  syncVisibleRange(mainChart, macdChart);
  syncVisibleRange(rsiChart, mainChart);
  syncVisibleRange(macdChart, mainChart);

  renderCharts();
}

onMounted(() => {
  setupCharts();
  window.addEventListener("resize", resizeCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  mainChart?.remove();
  rsiChart?.remove();
  macdChart?.remove();
});

watch([aggregatedCandles, showBb, showRsi, showMacd], () => {
  renderCharts();
}, { deep: true });
</script>

<template>
  <section class="glass-panel overflow-hidden p-5">
    <div class="mb-4 flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div>
        <p class="section-title">行情圖表</p>
        <h3 class="mt-2 text-xl font-semibold text-white">多週期圖表與技術指標</h3>
      </div>

      <div class="grid gap-3 sm:grid-cols-2 xl:text-right">
        <div>
          <p class="text-xs uppercase tracking-[0.18em] text-slate-500">最新價</p>
          <p class="text-xl font-semibold text-white">{{ quote ? formatPrice(quote.lastPrice) : "--" }}</p>
        </div>
        <div v-if="lastCandle" class="text-sm text-slate-300">
          <div>開 {{ formatPrice(lastCandle.open) }}</div>
          <div>高 {{ formatPrice(lastCandle.high) }}</div>
          <div>低 {{ formatPrice(lastCandle.low) }}</div>
          <div>收 {{ formatPrice(lastCandle.close) }}</div>
        </div>
      </div>
    </div>

    <div class="mb-4 flex flex-wrap items-center gap-3">
      <div class="flex flex-wrap gap-2">
        <button
          v-for="item in timeframes"
          :key="item.value"
          class="rounded-full px-3 py-1.5 text-sm transition"
          :class="selectedTimeframe === item.value ? 'bg-emerald-400 text-slate-950' : 'bg-white/5 text-slate-300'"
          @click="selectedTimeframe = item.value"
        >
          {{ item.label }}
        </button>
      </div>

      <div class="ml-auto flex flex-wrap items-center gap-2 text-sm">
        <button class="rounded-full px-3 py-1.5 transition" :class="showBb ? 'bg-sky-400/20 text-sky-200' : 'bg-white/5 text-slate-400'" @click="showBb = !showBb">布林通道</button>
        <button class="rounded-full px-3 py-1.5 transition" :class="showRsi ? 'bg-violet-400/20 text-violet-200' : 'bg-white/5 text-slate-400'" @click="showRsi = !showRsi">RSI</button>
        <button class="rounded-full px-3 py-1.5 transition" :class="showMacd ? 'bg-amber-400/20 text-amber-200' : 'bg-white/5 text-slate-400'" @click="showMacd = !showMacd">MACD</button>
      </div>
    </div>

    <div ref="mainChartRef" class="h-[360px] w-full" />

    <div v-show="showRsi" class="mt-4">
      <div class="mb-2 text-xs uppercase tracking-[0.2em] text-slate-500">RSI 14</div>
      <div ref="rsiChartRef" class="h-[140px] w-full" />
    </div>

    <div v-show="showMacd" class="mt-4">
      <div class="mb-2 text-xs uppercase tracking-[0.2em] text-slate-500">MACD 12 / 26 / 9</div>
      <div ref="macdChartRef" class="h-[160px] w-full" />
    </div>
  </section>
</template>
