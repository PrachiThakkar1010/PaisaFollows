// Portfolio: holdings at live prices, sector allocation, statement, balance history
import { api } from '../api.js';
import { initLayout, session } from '../layout.js';
import { onOrderEvent, onPrices } from '../live.js';
import { liveTotals } from '../portfolio-math.js';
import { $, dateTime, empty, errorBox, esc, inr, isoDate, livePrice, pct, signedInr, spinner, stockLink, table, tabs, tone } from '../util.js';

await initLayout('portfolio');

function cashLine() {
  $('#cash-line').textContent = `Available ${inr(session.me.availableBalance)} · reserved ${inr(session.me.reservedBalance)}`;
}
cashLine();

// ---------- switch views ----------
const views = { holdings: '#holdings-view', statement: '#statement-view', balance: '#balance-view' };
tabs($('#view-tabs'), [
  { value: 'holdings', label: 'Holdings' }, { value: 'statement', label: 'Statement' }, { value: 'balance', label: 'Balance history' },
], 'holdings', (v) => {
  Object.entries(views).forEach(([k, sel]) => $(sel).classList.toggle('hidden', k !== v));
  if (v === 'statement') loadStatement();
  if (v === 'balance') loadBalance();
});

// ---------- holdings ----------
const COLORS = ['#0f766e', '#0284c7', '#7c3aed', '#db2777', '#ea580c', '#ca8a04', '#16a34a', '#475569', '#0891b2', '#9333ea'];
let portfolio = null;

const stat = (label, value, sub, cls = '') =>
  `<section class="card stat"><p class="label">${label}</p><p class="value ${cls}">${value}</p><p class="sub">${sub}</p></section>`;

function draw() {
  if (!portfolio) return;
  const t = liveTotals(portfolio);
  const cash = Number(portfolio.availableCash) + Number(portfolio.reservedCash);
  $('#stats').innerHTML =
    stat('Total value', inr(t.totalValue), `Cash ${inr(cash)}`) +
    stat('Invested', inr(t.invested), `Now worth ${inr(t.holdingsValue)}`) +
    stat('Unrealised P&amp;L', signedInr(t.unrealisedPnl), `${pct(t.unrealisedPct)} · today ${signedInr(t.dayChange)}`, tone(t.unrealisedPnl)) +
    stat('Realised P&amp;L', signedInr(portfolio.realisedPnl), 'Booked by your sells', tone(portfolio.realisedPnl));

  // allocation bar: share of holdings value per sector
  const bySector = new Map();
  t.holdings.forEach((h) => bySector.set(h.sector, (bySector.get(h.sector) ?? 0) + h.currentValue));
  const sectors = [...bySector.entries()].sort((a, b) => b[1] - a[1]);
  $('#alloc-card').classList.toggle('hidden', !sectors.length);
  $('#alloc').innerHTML = sectors.map(([s, v], i) => `<div title="${esc(s)}" style="width:${(v / t.holdingsValue) * 100}%;background:${COLORS[i % COLORS.length]}"></div>`).join('');
  $('#legend').innerHTML = sectors.map(([s, v], i) => `<span><i style="background:${COLORS[i % COLORS.length]}"></i>${esc(s)} <span class="faint num">${((v / t.holdingsValue) * 100).toFixed(1)}%</span></span>`).join('');

  $('#holdings').innerHTML = !t.holdings.length ? empty('No holdings yet. <a class="link" href="/markets.html">Browse the market</a>.') :
    table(['Stock', 'Qty', 'Avg. price', 'LTP', 'Invested', 'Current value', 'P&amp;L', 'Day change'], t.holdings.map((h) => `<tr>
      <td>${stockLink(h.symbol)}<span class="sub">${esc(h.sector)}</span></td>
      <td>${h.quantity}</td>
      <td>${inr(h.averageBuyPrice)}</td>
      <td>${livePrice(h.symbol, h.ltp)}</td>
      <td>${inr(h.invested)}</td>
      <td>${inr(h.currentValue)}</td>
      <td class="${tone(h.unrealisedPnl)}">${signedInr(h.unrealisedPnl)}<span class="sub ${tone(h.unrealisedPnl)}">${pct(h.pnlPct)}</span></td>
      <td class="${tone(h.dayChange)}">${signedInr(h.dayChange)}</td></tr>`).join(''));
}

async function loadPortfolio() {
  try { portfolio = await api.portfolio(); draw(); cashLine(); }
  catch (e) { $('#holdings').innerHTML = errorBox(e.message); }
}
$('#stats').innerHTML = spinner();
loadPortfolio();
onPrices(draw);
onOrderEvent(loadPortfolio);

// ---------- statement (CALL sp_account_statement) ----------
const today = new Date();
$('#to').value = isoDate(today);
$('#from').value = isoDate(new Date(today.getTime() - 30 * 86400000));

async function loadStatement() {
  $('#statement').innerHTML = spinner();
  try {
    const s = await api.statement($('#from').value, $('#to').value);
    $('#totals').innerHTML = `<span>Money in <span class="num strong up">${inr(s.totalIn)}</span></span>
      <span>Money out <span class="num strong down">${inr(s.totalOut)}</span></span>`;
    $('#statement').innerHTML = !s.entries.length ? empty('No transactions in this period.') :
      table(['Date', 'Type', 'Description', 'Amount'], s.entries.map((e) => `<tr>
        <td class="small muted">${dateTime(e.createdAt)}</td>
        <td class="small">${esc(e.type.replace(/_/g, ' '))}</td>
        <td class="muted" style="text-align:left;white-space:normal;max-width:320px">${esc(e.description)}</td>
        <td class="strong ${e.flow === 'IN' ? 'up' : e.flow === 'OUT' ? 'down' : 'muted'}">${e.flow === 'IN' ? '+' : e.flow === 'OUT' ? '−' : ''}${inr(e.amount)}${e.flow === 'HOLD' ? '<span class="sub tiny">reserve / release</span>' : ''}</td>
      </tr>`).join(''));
  } catch (e) { $('#statement').innerHTML = errorBox(e.message); }
}
$('#show-statement').onclick = loadStatement;

// ---------- balance history (rows from the trigger) ----------
async function loadBalance() {
  $('#balance').innerHTML = spinner();
  try {
    const rows = await api.walletHistory(50);
    $('#balance').innerHTML = !rows.length ? empty('No balance changes yet.') :
      table(['When', 'Available before', 'Available after', 'Change', 'Reserved after'], rows.map((r) => {
        const diff = Number(r.newAvailable) - Number(r.oldAvailable);
        return `<tr><td class="small muted">${dateTime(r.changedAt)}</td><td>${inr(r.oldAvailable)}</td><td>${inr(r.newAvailable)}</td>
          <td class="${tone(diff)}">${signedInr(diff)}</td><td>${inr(r.newReserved)}</td></tr>`;
      }).join(''));
  } catch (e) { $('#balance').innerHTML = errorBox(e.message); }
}