// =========================================================
// Quick trade: small Buy / Sell buttons next to a stock name.
// Clicking one opens a pop-up to buy or sell that stock without leaving the page
// (same POST /api/orders as the stock page).
//
// Use in a page:
//   import { tradeButtons } from '../quick-trade.js';
//   `... ${tradeButtons('RELIANCE')} ...`
// Any element with data-trade="BUY|SELL" and data-symbol="..." opens the pop-up.
// =========================================================
import { api } from './api.js';
import { refreshMe, session } from './layout.js';
import { getQuote, onPrices } from './live.js';
import { $, $$, errorMessage, esc, inr, livePrice, num, stockUrl, toast, toTick } from './util.js';

// Load this feature's styles once (keeps style.css unchanged)
if (!document.querySelector('link[href="/css/quick-trade.css"]')) {
  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = '/css/quick-trade.css';
  document.head.appendChild(link);
}

/** The two little buttons, as HTML */
export function tradeButtons(symbol) {
  const s = esc(symbol);
  return `<span class="trade-btns">
    <button type="button" class="trade-btn b" data-trade="BUY" data-symbol="${s}" title="Buy ${s}" aria-label="Buy ${s}">Buy</button>
    <button type="button" class="trade-btn s" data-trade="SELL" data-symbol="${s}" title="Sell ${s}" aria-label="Sell ${s}">Sell</button>
  </span>`;
}

// One click handler for the whole page. Runs before row/link clicks (capture phase),
// so pressing Buy or Sell never also opens the stock page.
document.addEventListener('click', (e) => {
  const btn = e.target.closest('[data-trade]');
  if (!btn) return;
  e.preventDefault();
  e.stopPropagation();
  openQuickTrade(btn.dataset.symbol, btn.dataset.trade);
}, true);

let box = null;          // the open pop-up (only one at a time)
let state = null;        // { symbol, side, type, held }

/** Opens the pop-up for one stock, with BUY or SELL pre-selected */
export async function openQuickTrade(symbol, side) {
  closeQuickTrade();
  state = { symbol: symbol.toUpperCase(), side, type: 'MARKET', held: 0 };

  box = document.createElement('div');
  box.className = 'qt-backdrop';
  box.innerHTML = `
    <div class="qt-box" role="dialog" aria-modal="true" aria-label="Quick trade ${esc(state.symbol)}">
      <div class="qt-head">
        <div>
          <h2>${esc(state.symbol)}</h2>
          <p class="small muted">Live ₹${livePrice(state.symbol, getQuote(state.symbol)?.price)}</p>
        </div>
        <button type="button" class="qt-close" aria-label="Close">×</button>
      </div>
      <div class="qt-body ticket">
        <div class="toggle">
          <button type="button" class="buy" data-side="BUY">Buy</button>
          <button type="button" class="sell" data-side="SELL">Sell</button>
        </div>
        <div class="types">
          <button type="button" data-type="MARKET">Market</button>
          <button type="button" data-type="LIMIT">Limit</button>
        </div>
        <div class="form-grid">
          <label class="field"><span>Quantity</span><input class="input" type="number" min="1" step="1" value="1" id="qt-qty"></label>
          <label class="field"><span id="qt-price-label">Price (live)</span><input class="input" type="number" min="0.05" step="0.05" id="qt-price"></label>
        </div>
        <div class="summary">
          <div><span class="muted" id="qt-est-label">Approx. cost</span><span class="num strong" id="qt-est"></span></div>
          <div><span class="muted" id="qt-have-label">Available cash</span><span class="num" id="qt-have"></span></div>
        </div>
        <p class="error" id="qt-error"></p>
        <button type="button" class="btn btn-block" id="qt-place" style="padding:10px"></button>
        <p class="qt-foot"><a class="link" href="${stockUrl(state.symbol)}">Open full stock page →</a></p>
      </div>
    </div>`;
  document.body.appendChild(box);

  // close: × button, click outside the box, or Escape
  $('.qt-close', box).onclick = closeQuickTrade;
  box.addEventListener('mousedown', (e) => { if (e.target === box) closeQuickTrade(); });
  document.addEventListener('keydown', onEscape);

  // controls
  $('.toggle', box).onclick = (e) => {
    const b = e.target.closest('button');
    if (b) { state.side = b.dataset.side; suggestPrice(); draw(); }
  };
  $('.types', box).onclick = (e) => {
    const b = e.target.closest('button');
    if (b) { state.type = b.dataset.type; suggestPrice(); draw(); }
  };
  $('#qt-qty', box).addEventListener('input', draw);
  $('#qt-price', box).addEventListener('input', draw);
  $('#qt-place', box).onclick = place;

  draw();
  $('#qt-qty', box).focus();

  // how many shares I hold (for selling)
  try {
    const p = await api.portfolio();
    state.held = p.holdings.find((h) => h.symbol === state.symbol)?.quantity ?? 0;
    draw();
  } catch { /* ignore */ }
}

export function closeQuickTrade() {
  if (box) box.remove();
  box = null;
  state = null;
  document.removeEventListener('keydown', onEscape);
}

function onEscape(e) {
  if (e.key === 'Escape') closeQuickTrade();
}

// keep the market price and estimate live while the pop-up is open
onPrices(() => { if (box && state.type === 'MARKET') draw(); });

function suggestPrice() {
  const q = getQuote(state.symbol);
  if (!q || state.type === 'MARKET') return;
  const p = Number(q.price);
  $('#qt-price', box).value = toTick(state.side === 'BUY' ? p * 0.995 : p * 1.005).toFixed(2);
}

/** Redraws the pop-up from `state` */
function draw() {
  if (!box) return;
  const buy = state.side === 'BUY';
  const q = getQuote(state.symbol);

  $$('.toggle button', box).forEach((b) => b.classList.toggle('active', b.dataset.side === state.side));
  $$('.types button', box).forEach((b) => b.classList.toggle('active', b.dataset.type === state.type));
  $('#qt-price-label', box).textContent = state.type === 'MARKET' ? 'Price (live)' : 'Limit price ₹';
  const priceInput = $('#qt-price', box);
  priceInput.disabled = state.type === 'MARKET';
  if (state.type === 'MARKET') priceInput.value = q ? Number(q.price).toFixed(2) : '';

  const qty = Math.max(0, Math.floor(Number($('#qt-qty', box).value) || 0));
  const estimate = (Number(priceInput.value) || 0) * qty;
  const cash = Number(session.me?.availableBalance ?? 0);
  $('#qt-est-label', box).textContent = buy ? 'Approx. cost' : 'Approx. proceeds';
  $('#qt-est', box).textContent = inr(estimate);
  $('#qt-have-label', box).textContent = buy ? 'Available cash' : 'You hold';
  $('#qt-have', box).textContent = buy ? inr(cash) : `${state.held} shares`;
  $('#qt-have', box).className = `num ${buy && estimate > cash ? 'down' : ''}`;

  const placeBtn = $('#qt-place', box);
  placeBtn.className = `btn btn-block ${buy ? 'btn-buy' : 'btn-sell'}`;
  placeBtn.textContent = `${buy ? 'Buy' : 'Sell'} ${qty || ''} ${state.symbol}`;
}

/** POST /api/orders */
async function place() {
  const err = $('#qt-error', box);
  err.textContent = '';
  const quantity = Math.floor(Number($('#qt-qty', box).value));
  if (!(quantity >= 1)) { err.textContent = 'Enter a quantity of at least 1'; return; }
  const price = Number($('#qt-price', box).value);
  const { symbol, side, type } = state;
  const btn = $('#qt-place', box);
  btn.disabled = true;
  try {
    const res = await api.placeOrder({ symbol, side, type, quantity, price: type === 'LIMIT' ? price : undefined });
    if (res.status === 'FILLED') {
      toast('success', `${side === 'BUY' ? 'Bought' : 'Sold'} ${quantity} ${symbol}`,
        `at ₹${num(res.fillPrice)}${res.realisedPnl !== null && res.realisedPnl !== undefined ? ` · P&L ${inr(res.realisedPnl)}` : ''}`);
    } else {
      toast('info', 'Limit order placed', `${side} ${quantity} ${symbol} @ ₹${num(price)}. You'll be notified when it fills.`);
    }
    closeQuickTrade();
    await refreshMe();
    document.dispatchEvent(new CustomEvent('pf-order-placed', { detail: { symbol } })); // pages can reload their data
  } catch (e) {
    err.textContent = errorMessage(e);
    btn.disabled = false;
  }
}