// Candle chart (TradingView Lightweight Charts, loaded from the CDN as the global LightweightCharts).
// History comes from GET /api/candles (SQL GROUP BY time bucket); the live price then updates
// the newest candle every half second, so the chart moves in real time.
import { api } from './api.js';
import { getQuote, onPrices } from './live.js';
import { tabs } from './util.js';

const INTERVALS = { '1m': 60, '5m': 300, '15m': 900, '1h': 3600, '1d': 86400 };
const IST = 19800; // the library shows UTC, so add 5 h 30 min to display Indian time

export function candleChart(el, tabsEl, noteEl, symbol) {
  const { createChart, CandlestickSeries, ColorType } = window.LightweightCharts;
  const chart = createChart(el, {
    autoSize: true,
    layout: { background: { type: ColorType.Solid, color: '#ffffff' }, textColor: '#475569', fontSize: 11 },
    grid: { vertLines: { color: '#f1f5f9' }, horzLines: { color: '#f1f5f9' } },
    rightPriceScale: { borderColor: '#e2e8f0' },
    timeScale: { borderColor: '#e2e8f0', timeVisible: true, secondsVisible: false },
  });
  const series = chart.addSeries(CandlestickSeries, {
    upColor: '#059669', downColor: '#dc2626', borderVisible: false, wickUpColor: '#059669', wickDownColor: '#dc2626',
  });

  let interval = '1m';
  let last = null; // newest candle on screen

  async function load() {
    try {
      const res = await api.candles(symbol, interval);
      const data = res.candles.map((c) => ({
        time: c.time + IST, open: Number(c.open), high: Number(c.high), low: Number(c.low), close: Number(c.close),
      }));
      series.setData(data);
      last = data.length ? { ...data[data.length - 1] } : null;
      noteEl.textContent = data.length ? 'Times in IST' : 'No history yet: candles appear after a few minutes';
      chart.timeScale().fitContent();
    } catch {
      noteEl.textContent = 'Could not load chart';
    }
  }

  tabs(tabsEl, Object.keys(INTERVALS).map((k) => ({ value: k, label: k === '1d' ? '1D' : k })), interval, (v) => { interval = v; load(); });
  load();
  setInterval(load, 30000);

  // live price -> update (or start) the current candle
  onPrices(() => {
    const q = getQuote(symbol);
    if (!q) return;
    const size = INTERVALS[interval];
    const t = Math.floor((q.timestamp / 1000 + IST) / size) * size;
    const p = Number(q.price);
    if (last && t < last.time) return;
    last = last && last.time === t
      ? { ...last, high: Math.max(last.high, p), low: Math.min(last.low, p), close: p }
      : { time: t, open: p, high: p, low: p, close: p };
    series.update(last);
  });
}