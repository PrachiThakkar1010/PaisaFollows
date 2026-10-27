// Markets: screener, "what can I afford?", sector ranking
import { api } from '../api.js';
import { initLayout } from '../layout.js';
import { $, change, empty, errorBox, esc, inr, int, liveChange, livePrice, pager, spinner, stockLink, stockUrl, table, tabs } from '../util.js';

await initLayout('markets');

// ---------- switch between the three views ----------
const views = { screener: '#screener-view', affordable: '#affordable-view', sectors: '#sectors-view' };
tabs($('#view-tabs'), [
  { value: 'screener', label: 'Screener' },
  { value: 'affordable', label: 'What can I afford?' },
  { value: 'sectors', label: 'Sectors' },
], 'screener', (v) => {
  Object.entries(views).forEach(([k, sel]) => $(sel).classList.toggle('hidden', k !== v));
  if (v === 'affordable') loadAffordable();
  if (v === 'sectors') loadSectors();
});

// ---------- screener ----------
const filters = {
  sectors: new URLSearchParams(location.search).get('sector') ? [new URLSearchParams(location.search).get('sector')] : [],
  page: 0,
};

async function drawChips() {
  const all = await api.sectors();
  const draw = () => {
    $('#sector-chips').innerHTML = all.map((s) => `<button type="button" class="chip ${filters.sectors.includes(s) ? 'active' : ''}" data-s="${esc(s)}">${esc(s)}</button>`).join('')
      + (filters.sectors.length ? '<button type="button" class="btn btn-ghost btn-sm" data-clear="1">Clear</button>' : '');
  };
  draw();
  $('#sector-chips').onclick = (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    if (b.dataset.clear) filters.sectors = [];
    else filters.sectors = filters.sectors.includes(b.dataset.s) ? filters.sectors.filter((x) => x !== b.dataset.s) : [...filters.sectors, b.dataset.s];
    filters.page = 0;
    draw();
    loadScreener();
  };
}

async function loadScreener() {
  $('#screener-table').innerHTML = spinner();
  try {
    const res = await api.screener({
      sectors: filters.sectors,
      minPrice: $('#min-price').value || undefined,
      maxPrice: $('#max-price').value || undefined,
      sort: $('#sort').value,
      dir: $('#dir').value,
      page: filters.page,
      size: 15,
    });
    $('#screener-table').innerHTML = !res.items.length ? empty('No stocks match these filters.') :
      table(['Stock', 'Sector', 'LTP ₹', 'Prev. close', 'Change'], res.items.map((r) => `
        <tr class="clickable" data-href="${stockUrl(r.symbol)}">
          <td><span class="strong">${esc(r.symbol)}</span><span class="sub">${esc(r.name)}</span></td>
          <td class="small muted">${esc(r.sector)}</td>
          <td>${livePrice(r.symbol, r.ltp)}</td>
          <td>${inr(r.previousClose)}</td>
          <td>${liveChange(r.symbol, r.changePct)}</td>
        </tr>`).join(''));
    pager($('#screener-pager'), res.page, res.totalPages, (p) => { filters.page = p; loadScreener(); });
  } catch (e) {
    $('#screener-table').innerHTML = errorBox(e.message);
  }
}

// a click anywhere on a row opens that stock
$('#screener-table').addEventListener('click', (e) => {
  const tr = e.target.closest('tr[data-href]');
  if (tr) window.location.href = tr.dataset.href;
});
['#min-price', '#max-price', '#sort', '#dir'].forEach((id) => $(id).addEventListener('change', () => { filters.page = 0; loadScreener(); }));

drawChips();
loadScreener();

// ---------- what can I afford? ----------
async function loadAffordable() {
  $('#affordable-table').innerHTML = spinner();
  try {
    const rows = await api.affordable(50);
    $('#affordable-table').innerHTML = table(['Stock', 'LTP ₹', 'Max shares'], rows.map((r) => `<tr>
      <td>${stockLink(r.symbol)} <span class="small muted">${esc(r.name)}</span></td>
      <td>${livePrice(r.symbol, r.ltp)}</td>
      <td class="strong ${r.maxQuantity === 0 ? 'faint' : ''}">${int(r.maxQuantity)}</td></tr>`).join(''));
  } catch (e) { $('#affordable-table').innerHTML = errorBox(e.message); }
}

// ---------- sector ranking ----------
async function loadSectors() {
  $('#sectors-table').innerHTML = spinner();
  try {
    const rows = await api.sectorPerformance($('#min-stocks').value);
    $('#sectors-table').innerHTML = table(['Sector', 'Stocks', 'Avg. change', 'Best stock'], rows.map((s) => `<tr>
      <td>${esc(s.sector)}</td><td>${s.stocks}</td><td>${change(s.avgChangePct)}</td><td>${change(s.bestChangePct)}</td></tr>`).join(''));
  } catch (e) { $('#sectors-table').innerHTML = errorBox(e.message); }
}
$('#min-stocks').addEventListener('change', loadSectors);