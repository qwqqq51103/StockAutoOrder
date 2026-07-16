export function aggregateCandles(candles, timeframeMinutes) {
  const bucketSize = timeframeMinutes * 60;
  const buckets = new Map();

  for (const candle of candles || []) {
    const bucketTime = Math.floor(candle.time / bucketSize) * bucketSize;
    const existing = buckets.get(bucketTime);

    if (!existing) {
      buckets.set(bucketTime, {
        time: bucketTime,
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
        volume: candle.volume || 0,
        buyVolume: candle.buyVolume || 0,
        sellVolume: candle.sellVolume || 0
      });
      continue;
    }

    existing.high = Math.max(existing.high, candle.high);
    existing.low = Math.min(existing.low, candle.low);
    existing.close = candle.close;
    existing.volume += candle.volume || 0;
    existing.buyVolume += candle.buyVolume || 0;
    existing.sellVolume += candle.sellVolume || 0;
  }

  return [...buckets.values()].sort((a, b) => a.time - b.time);
}

export function calculateBollingerBands(candles, period = 20, multiplier = 2) {
  const closes = candles.map((item) => item.close);
  const upper = [];
  const middle = [];
  const lower = [];

  for (let i = 0; i < closes.length; i += 1) {
    if (i + 1 < period) continue;
    const slice = closes.slice(i + 1 - period, i + 1);
    const avg = slice.reduce((sum, value) => sum + value, 0) / period;
    const variance = slice.reduce((sum, value) => sum + (value - avg) ** 2, 0) / period;
    const deviation = Math.sqrt(variance);
    const time = candles[i].time;
    upper.push({ time, value: avg + deviation * multiplier });
    middle.push({ time, value: avg });
    lower.push({ time, value: avg - deviation * multiplier });
  }

  return { upper, middle, lower };
}

export function calculateRsi(candles, period = 14) {
  if (!candles.length) return [];

  const closes = candles.map((item) => item.close);
  const result = [];
  let gains = 0;
  let losses = 0;

  for (let i = 1; i < closes.length; i += 1) {
    const change = closes[i] - closes[i - 1];
    gains += Math.max(change, 0);
    losses += Math.max(-change, 0);

    if (i < period) continue;

    if (i === period) {
      gains /= period;
      losses /= period;
    } else {
      gains = (gains * (period - 1) + Math.max(change, 0)) / period;
      losses = (losses * (period - 1) + Math.max(-change, 0)) / period;
    }

    const rs = losses === 0 ? 100 : gains / losses;
    const rsi = 100 - 100 / (1 + rs);
    result.push({ time: candles[i].time, value: rsi });
  }

  return result;
}

function ema(values, period) {
  const multiplier = 2 / (period + 1);
  const result = [];
  let current = values[0];
  result.push(current);

  for (let i = 1; i < values.length; i += 1) {
    current = (values[i] - current) * multiplier + current;
    result.push(current);
  }

  return result;
}

export function calculateMacd(candles, shortPeriod = 12, longPeriod = 26, signalPeriod = 9) {
  if (!candles.length) {
    return { macd: [], signal: [], histogram: [] };
  }

  const closes = candles.map((item) => item.close);
  const shortEma = ema(closes, shortPeriod);
  const longEma = ema(closes, longPeriod);
  const macdValues = closes.map((_, index) => shortEma[index] - longEma[index]);
  const signalValues = ema(macdValues, signalPeriod);

  const macd = [];
  const signal = [];
  const histogram = [];

  for (let i = 0; i < candles.length; i += 1) {
    const time = candles[i].time;
    macd.push({ time, value: macdValues[i] });
    signal.push({ time, value: signalValues[i] });
    histogram.push({
      time,
      value: macdValues[i] - signalValues[i],
      color: macdValues[i] - signalValues[i] >= 0 ? "rgba(52, 211, 153, 0.55)" : "rgba(251, 113, 133, 0.55)"
    });
  }

  return { macd, signal, histogram };
}
