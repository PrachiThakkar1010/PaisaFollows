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

  // Chart colours come from the same CSS variables as the rest of the page (light or dark theme)
  const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  const colours = () => ({
    layout: { background: { type: ColorType.Solid, color: css('--card') }, textColor: css('--text-2'), fontSize: 11 },
    grid: { vertLines: { color: css('--border-soft') }, horzLines: { color: css('--border-soft') } },
    rightPriceScale: { borderColor: css('--border') },
    timeScale: { borderColor: css('--border'), timeVisible: true, secondsVisible: false },
  });
  const candleColours = () => ({
    upColor: css('--up'), downColor: css('--down'), wickUpColor: css('--up'), wickDownColor: css('--down'),
  });

  const chart = createChart(el, { autoSize: true, ...colours() });
  const series = chart.addSeries(CandlestickSeries, { borderVisible: false, ...candleColours() });

  // Light/dark switch clicked: repaint the chart in the new colours
  window.addEventListener('pf-theme-change', () => {
    chart.applyOptions(colours());
    series.applyOptions(candleColours());
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