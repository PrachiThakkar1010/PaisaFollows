// Dashboard: totals, holdings, market breadth, movers, sector heatmap
import { api } from '../api.js';
import { initLayout, session } from '../layout.js';
import { onOrderEvent, onPrices } from '../live.js';
import { liveTotals } from '../portfolio-math.js';
import { $, change, empty, errorBox, esc, inr, liveChange, livePrice, pct, signedInr, spinner, stockLink, stockUrl, table, tone } from '../util.js';

const me = await initLayout('dashboard');
$('#hello').textContent = `Hello, ${me.name.split(' ')[0]}`;

let portfolio = null;

// ---------- stat cards + holdings (redrawn as prices move) ----------
function stat(label, value, sub, cls = '') {
  return `<section class="card stat"><p class="label">${label}</p><p class="value ${cls}">${value}</p><p class="sub">${sub}</p></section>`;
}

function drawPortfolio() {
  if (!portfolio) return;
  const t = liveTotals(portfolio);
  const m = session.me;
  $('#stats').innerHTML =
    stat('Total value', inr(t.totalValue), 'Cash + reserved + holdings') +
    stat('Available cash', inr(m.availableBalance), `Reserved for orders: ${inr(m.reservedBalance)}`) +
    stat('Holdings value', inr(t.holdingsValue), `Invested ${inr(t.invested)}`) +
    stat('Unrealised P&amp;L', signedInr(t.unrealisedPnl),
      `${pct(t.unrealisedPct)} · today <span class="${tone(t.dayChange)}">${signedInr(t.dayChange)}</span> · booked ${signedInr(portfolio.realisedPnl)}`,
      tone(t.unrealisedPnl));

  if (!t.holdings.length) {
    $('#holdings').innerHTML = empty('No holdings yet. <a class="link" href="/markets.html">Find a stock to buy</a>.');
    return;
  }
  $('#holdings').innerHTML = table(['Stock', 'Qty', 'Avg. price', 'LTP', 'Value', 'P&amp;L'],
    t.holdings.slice(0, 6).map((h) => `<tr>
      <td>${stockLink(h.symbol)}</td>
      <td>${h.quantity}</td>
      <td>${inr(h.averageBuyPrice)}</td>
      <td>${livePrice(h.symbol, h.ltp)}</td>
      <td>${inr(h.currentValue)}</td>
      <td class="${tone(h.unrealisedPnl)}">${signedInr(h.unrealisedPnl)} <span class="small">(${pct(h.pnlPct)})</span></td>
    </tr>`).join(''));
}

async function loadPortfolio() {
  try { portfolio = await api.portfolio(); drawPortfolio(); }
  catch (e) { $('#holdings').innerHTML = errorBox(e.message); }
}

$('#stats').innerHTML = spinner();
$('#holdings').innerHTML = spinner();
loadPortfolio();
onPrices(drawPortfolio);
onOrderEvent(loadPortfolio);

// ---------- movers ----------
function moversList(rows) {
  return `<ul class="list">${rows.map((r) => `<li><a class="list-row" href="${stockUrl(r.symbol)}">
      <span><span class="strong">${esc(r.symbol)}</span><br><span class="small muted">${esc(r.name)}</span></span>
      <span class="right">${livePrice(r.symbol, r.ltp)}<br>${liveChange(r.symbol, r.changePct)}</span>
    </a></li>`).join('')}</ul>`;
}
$('#gainers').innerHTML = spinner();
$('#losers').innerHTML = spinner();
api.movers('gainers', 5).then((r) => { $('#gainers').innerHTML = moversList(r); }).catch((e) => { $('#gainers').innerHTML = errorBox(e.message); });
api.movers('losers', 5).then((r) => { $('#losers').innerHTML = moversList(r); }).catch((e) => { $('#losers').innerHTML = errorBox(e.message); });

// ---------- market breadth ----------
$('#breadth').innerHTML = spinner();
api.overview().then((o) => {
  const w = (n) => `${(n / Math.max(o.stocks, 1)) * 100}%`;
  $('#breadth').innerHTML = `<div class="card-body">
    <div class="breadth"><div style="width:${w(o.gainers)};background:var(--up)"></div><div style="width:${w(o.unchanged)};background:#cbd5e1"></div><div style="width:${w(o.losers)};background:var(--down)"></div></div>
    <div class="row" style="justify-content:space-between;margin-top:12px"><span class="up">${o.gainers} up</span><span class="muted">${o.unchanged} flat</span><span class="down">${o.losers} down</span></div>
    <div class="form-grid" style="margin-top:16px;padding-top:16px;border-top:1px solid var(--border-soft)">
      <div><p class="small muted">Average move</p>${change(o.avgChangePct)}</div>
      <div><p class="small muted">Average price</p><p class="num">${inr(o.avgPrice)}</p></div>
    </div>
    <div style="margin-top:16px;padding-top:16px;border-top:1px solid var(--border-soft)">
      <p class="small muted" style="margin-bottom:8px">Most held by investors</p>
      <div class="chips">${o.mostHeld.slice(0, 6).map((m) => `<a class="tag" href="${stockUrl(m.symbol)}">${esc(m.symbol)} <span class="faint">· ${m.holders}</span></a>`).join('')}</div>
    </div></div>`;
}).catch((e) => { $('#breadth').innerHTML = errorBox(e.message); });

// ---------- sector heatmap (materialized view MV_SECTOR_SUMMARY) ----------
function heat(v) {
  if (v >= 1) return 'heat-3';
  if (v >= 0.3) return 'heat-2';
  if (v > 0) return 'heat-1';
  if (v === 0) return 'heat-0';
  if (v > -0.3) return 'heat-n1';
  if (v > -1) return 'heat-n2';
  return 'heat-n3';
}
$('#heatmap').innerHTML = spinner();
api.sectorSummary().then((rows) => {
  $('#heatmap').innerHTML = !rows.length ? empty('The sector summary has not been refreshed yet.') :
    `<div class="heatmap">${rows.map((s) => `<a class="tile ${heat(Number(s.avgChangePct))}" href="/markets.html?sector=${encodeURIComponent(s.sector)}">
      <p class="name">${esc(s.sector)}</p><p class="pct">${pct(s.avgChangePct)}</p>
      <p class="meta">${s.stocks} stocks · ${s.gainers}↑ ${s.losers}↓</p></a>`).join('')}</div>`;
}).catch((e) => { $('#heatmap').innerHTML = errorBox(e.message); });