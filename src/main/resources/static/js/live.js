// =========================================================
// Live data over ONE WebSocket (STOMP):
//   /topic/ticks        every price, every second
//   /user/queue/orders  my waiting orders filling / cancelling / expiring
//
// Prices are kept in a Map. Twice a second, every element on the page with
//   data-price="SYMBOL"   gets the newest price (and flashes green/red)
//   data-change="SYMBOL"  gets the newest % change
// so pages never have to update prices themselves.
// Needs stomp.umd.min.js loaded first (it creates the global StompJs).
// =========================================================
import { api } from './api.js';
import { $, $$, num, pct, toast } from './util.js';

const prices = new Map();       // symbol -> latest quote
const priceListeners = new Set();
const orderListeners = new Set();
let dirty = false;
let connected = false;

export const getQuote = (symbol) => prices.get(String(symbol ?? '').toUpperCase());
export const allQuotes = () => [...prices.values()].sort((a, b) => a.symbol.localeCompare(b.symbol));
export const isConnected = () => connected;

/** fn() runs (at most twice a second) whenever any price changed */
export function onPrices(fn) { priceListeners.add(fn); }
/** fn(notification) runs whenever one of my orders filled / was cancelled / expired */
export function onOrderEvent(fn) { orderListeners.add(fn); }

function setQuote(q) {
  prices.set(q.symbol, q);
  dirty = true;
}

/** Copies the newest prices into every data-price / data-change element on the page */
export function refreshPrices() {
  for (const el of $$('[data-price]')) {
    const q = getQuote(el.dataset.price);
    if (!q) continue;
    const text = num(q.price);
    if (el.textContent !== text) {
      const old = Number(el.dataset.last ?? q.price);
      el.textContent = text;
      el.dataset.last = q.price;
      if (old !== Number(q.price)) {
        el.classList.remove('flash-up', 'flash-down');
        void el.offsetWidth; // restart the animation
        el.classList.add(Number(q.price) > old ? 'flash-up' : 'flash-down');
      }
    }
  }
  for (const el of $$('[data-change]')) {
    const q = getQuote(el.dataset.change);
    if (!q) continue;
    el.textContent = pct(q.changePct);
    el.classList.remove('up', 'down', 'flat');
    el.classList.add(q.changePct > 0 ? 'up' : q.changePct < 0 ? 'down' : 'flat');
  }
}

setInterval(() => {
  if (!dirty) return;
  dirty = false;
  refreshPrices();
  priceListeners.forEach((fn) => fn());
}, 500);

/** Called once by layout.js after login is confirmed */
export async function startLive() {
  try { (await api.quotes()).forEach(setQuote); } catch { /* the WebSocket will fill them */ }
  refreshPrices();
  priceListeners.forEach((fn) => fn());

  if (!window.StompJs) return;
  const client = new StompJs.Client({
    brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });

  client.onConnect = () => {
    connected = true;
    $('#live-dot')?.classList.add('on');
    client.subscribe('/topic/ticks', (m) => setQuote(JSON.parse(m.body)));
    client.subscribe('/user/queue/orders', (m) => {
      const n = JSON.parse(m.body);
      const title = `${n.status === 'FILLED' ? 'Order filled' : `Order ${n.status.toLowerCase()}`} · ${n.side} ${n.quantity} ${n.symbol}`;
      toast(n.status === 'FILLED' ? 'success' : 'info', title, n.message);
      orderListeners.forEach((fn) => fn(n));
    });
  };
  client.onWebSocketClose = () => {
    connected = false;
    $('#live-dot')?.classList.remove('on');
  };
  client.activate();
}