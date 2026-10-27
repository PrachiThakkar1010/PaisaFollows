// Stock page: live price, candle chart, buy/sell box, price alert, add to watchlist
// URL: /stock.html?symbol=RELIANCE
import { api } from '../api.js';
import { candleChart } from '../chart.js';
import { initLayout, refreshMe, session } from '../layout.js';
import { getQuote, onOrderEvent, onPrices } from '../live.js';
import { $, $$, errorMessage, esc, inr, livePrice, num, pct, signedInr, toast, tone, toTick } from '../util.js';

await initLayout('markets');
const symbol = (new URLSearchParams(location.search).get('symbol') ?? '').toUpperCase();
document.title = `${symbol} · PaisaFollows`;

// ---------- stock details ----------
let detail;
try {
  detail = await api.stock(symbol);
} catch (e) {
  $('#stock-page').classList.add('hidden');
  $('#not-found').classList.remove('hidden');
  $('#not-found').innerHTML = `<p class="muted">${esc(e.message)}</p><a class="link" href="/markets.html" style="display:inline-block;margin-top:12px">Back to markets</a>`;
  throw e;
}

$('#symbol').textContent = detail.symbol;
$('#company').textContent = detail.companyName || detail.name;
$('#badges').innerHTML = `<span class="badge">${esc(detail.exchange)}</span><span class="badge brand">${esc(detail.sector)}</span>`
  + (detail.sectorLeader ? '<span class="badge green">Sector leader today</span>' : '');
$('#price').dataset.price = symbol;
$('#price').textContent = num(detail.ltp);
$('#facts').innerHTML = `
  <div><dt>Previous close</dt><dd class="num">${inr(detail.previousClose)}</dd></div>
  <div><dt>Face value</dt><dd class="num">${inr(detail.faceValue)}</dd></div>
  <div><dt>Investors holding</dt><dd class="num">${detail.holders}</dd></div>
  <div><dt>vs sector average price</dt><dd>${detail.aboveSectorAverage ? 'Above' : 'Below'}</dd></div>`;

function drawDayChange() {
  const q = getQuote(symbol);
  const diff = q ? Number(q.change) : Number(detail.ltp) - Number(detail.previousClose);
  const p = q ? q.changePct : detail.changePct;
  $('#day-change').className = `num ${tone(diff)}`;
  $('#day-change').textContent = `${signedInr(diff)} (${pct(p)})`;
  $('#closed-note').classList.toggle('hidden', !q || q.marketOpen);
}
drawDayChange();
onPrices(drawDayChange);

// ---------- chart ----------
candleChart($('#chart'), $('#interval-tabs'), $('#chart-note'), symbol);

// ---------- similar stocks (SELF JOIN) ----------
$('#similar-title').textContent = `Similar stocks in ${detail.sector}`;
api.similar(symbol).then((rows) => {
  $('#similar').innerHTML = rows.length ? rows.map((s) => `<a class="stock-pill" href="/stock.html?symbol=${encodeURIComponent(s.symbol)}">
    <span class="strong">${esc(s.symbol)}</span> ${livePrice(s.symbol, s.ltp)}</a>`).join('') : '<p class="muted">No other stocks in this sector.</p>';
});

// ---------- my position in this stock ----------
let held = 0;
async function loadPosition() {
  try {
    const p = await api.portfolio();
    const h = p.holdings.find((x) => x.symbol === symbol);
    held = h ? h.quantity : 0;
    $('#my-position').classList.toggle('hidden', !h);
    if (h) {
      const ltp = Number(getQuote(symbol)?.price ?? h.ltp);
      const pnl = (ltp - Number(h.averageBuyPrice)) * h.quantity;
      $('#my-position').innerHTML = `You hold <b>${h.quantity}</b> at avg. ${inr(h.averageBuyPrice)} · P&amp;L <span class="${tone(pnl)}">${signedInr(pnl)}</span>`;
    }
    drawTicket();
  } catch { /* ignore */ }
}
loadPosition();
onOrderEvent(loadPosition);

// ---------- buy / sell box (POST /api/orders) ----------
const HELP = {
  MARKET: 'Fills now at the live price (+0.05% slippage).',
  LIMIT: 'Waits until the price reaches your limit. A buy reserves the money.',
  STOP_LOSS: 'Sells automatically if the price falls to your trigger.',
};
let side = 'BUY';
let type = 'MARKET';

function suggestPrice() {
  const q = getQuote(symbol);
  if (!q || type === 'MARKET') return;
  const p = Number(q.price);
  const s = type === 'STOP_LOSS' ? p * 0.98 : side === 'BUY' ? p * 0.995 : p * 1.005;
  $('#order-price').value = toTick(s).toFixed(2);
}

function drawTicket() {
  const buy = side === 'BUY';
  $$('.toggle button').forEach((b) => b.classList.toggle('active', b.dataset.side === side));
  $$('#types button').forEach((b) => b.classList.toggle('active', b.dataset.type === type));
  $('#types [data-type="STOP_LOSS"]').classList.toggle('hidden', buy); // stop-loss is only for selling
  $('#type-help').textContent = HELP[type];
  $('#price-label').textContent = type === 'MARKET' ? 'Price (live)' : type === 'LIMIT' ? 'Limit price ₹' : 'Trigger price ₹';
  $('#order-price').disabled = type === 'MARKET';
  if (type === 'MARKET') $('#order-price').value = getQuote(symbol) ? Number(getQuote(symbol).price).toFixed(2) : '';

  const qty = Math.max(0, Math.floor(Number($('#qty').value) || 0));
  const unit = Number($('#order-price').value) || 0;
  const estimate = unit * qty;
  const cash = Number(session.me.availableBalance);
  $('#estimate-label').textContent = buy ? 'Approx. cost' : 'Approx. proceeds';
  $('#estimate').textContent = inr(estimate);
  $('#have-label').textContent = buy ? 'Available cash' : 'You hold';
  $('#have').textContent = buy ? inr(cash) : `${held} shares`;
  $('#have').className = `num ${buy && estimate > cash ? 'down' : ''}`;
  $('#place').className = `btn btn-block ${buy ? 'btn-buy' : 'btn-sell'}`;
  $('#place').textContent = `${buy ? 'Buy' : 'Sell'} ${qty || ''} ${symbol}`;
}

$('.toggle').onclick = (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  side = b.dataset.side;
  if (side === 'BUY' && type === 'STOP_LOSS') type = 'MARKET';
  suggestPrice();
  drawTicket();
};
$('#types').onclick = (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  type = b.dataset.type;
  suggestPrice();
  drawTicket();
};
$('#qty').addEventListener('input', drawTicket);
$('#order-price').addEventListener('input', drawTicket);
onPrices(() => { if (type === 'MARKET') drawTicket(); });
drawTicket();

$('#place').onclick = async () => {
  $('#order-error').textContent = '';
  const quantity = Math.floor(Number($('#qty').value));
  if (!(quantity >= 1)) { $('#order-error').textContent = 'Enter a quantity of at least 1'; return; }
  const price = Number($('#order-price').value);
  $('#place').disabled = true;
  try {
    const res = await api.placeOrder({
      symbol, side, type, quantity,
      price: type === 'LIMIT' ? price : undefined,
      triggerPrice: type === 'STOP_LOSS' ? price : undefined,
    });
    if (res.status === 'FILLED') {
      toast('success', `${side === 'BUY' ? 'Bought' : 'Sold'} ${quantity} ${symbol}`,
        `at ₹${num(res.fillPrice)}${res.realisedPnl !== null && res.realisedPnl !== undefined ? ` · P&L ${inr(res.realisedPnl)}` : ''}`);
    } else {
      toast('info', `${type === 'LIMIT' ? 'Limit' : 'Stop-loss'} order placed`, `${side} ${quantity} ${symbol} @ ₹${num(price)}. You'll be notified when it fills.`);
    }
    await refreshMe();
    await loadPosition();
  } catch (e) {
    $('#order-error').textContent = errorMessage(e);
  } finally {
    $('#place').disabled = false;
  }
};

// ---------- add to watchlist ----------
api.watchlists().then((lists) => {
  if (!lists.length) { $('#watch-box').classList.add('hidden'); return; }
  $('#watch-select').innerHTML = lists.map((l) => `<option value="${esc(l.watchlistId)}">${esc(l.name)}</option>`).join('');
});
$('#watch-add').onclick = async () => {
  const sel = $('#watch-select');
  try {
    await api.addToWatchlist(sel.value, symbol);
    toast('success', `${symbol} added`, `to ${sel.options[sel.selectedIndex].text}`);
  } catch (e) { toast('error', 'Could not add', errorMessage(e)); }
};

// ---------- price alert ----------
function alertPlaceholder() {
  const q = getQuote(symbol);
  if (q) $('#alert-target').placeholder = toTick(Number(q.price) * ($('#alert-dir').value === 'ABOVE' ? 1.01 : 0.99)).toFixed(2);
}
alertPlaceholder();
$('#alert-dir').addEventListener('change', alertPlaceholder);
$('#alert-add').onclick = async () => {
  $('#alert-error').textContent = '';
  const target = Number($('#alert-target').value);
  if (!target) { $('#alert-error').textContent = 'Enter a target price'; return; }
  try {
    await api.createAlert(symbol, $('#alert-dir').value, target);
    toast('success', 'Alert created', `${symbol} ${$('#alert-dir').value.toLowerCase()} ₹${num(target)}`);
    $('#alert-target').value = '';
  } catch (e) { $('#alert-error').textContent = errorMessage(e); }
};