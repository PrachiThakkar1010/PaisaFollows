// Watchlists and "My stocks"
import { api } from '../api.js';
import { initLayout } from '../layout.js';
import { $, change, dateTime, empty, errorBox, errorMessage, esc, inr, liveChange, livePrice, spinner, stockLink, stockSearch, stockUrl, table, tabs, toast } from '../util.js';

await initLayout('watchlists');

let lists = [];
let current = null;

// ---------- the list of watchlists ----------
async function loadLists(selectId) {
  lists = await api.watchlists();
  current = selectId ?? (lists.some((l) => l.watchlistId === current) ? current : lists[0]?.watchlistId ?? null);
  $('#lists').innerHTML = lists.map((l) => `<li><button type="button" data-id="${esc(l.watchlistId)}" class="${l.watchlistId === current ? 'active' : ''}">
    <span class="truncate">${esc(l.name)}</span><span class="small faint">${l.items}</span></button></li>`).join('');
  $('#list-name').textContent = lists.find((l) => l.watchlistId === current)?.name ?? 'Watchlist';
  $('#delete-list').classList.toggle('hidden', !current);
}

$('#lists').addEventListener('click', (e) => {
  const b = e.target.closest('button[data-id]');
  if (!b) return;
  current = b.dataset.id;
  $('#add-error').textContent = '';
  loadLists(current);
  loadItems();
});

$('#create').onclick = async () => {
  const name = $('#new-name').value.trim();
  if (!name) return;
  try {
    const w = await api.createWatchlist(name);
    $('#new-name').value = '';
    await loadLists(w.watchlistId);
    loadItems();
  } catch (e) { toast('error', 'Could not create', errorMessage(e)); }
};
$('#new-name').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('#create').click(); });

// DELETE with cascade: the list's items go automatically
$('#delete-list').onclick = async () => {
  const name = lists.find((l) => l.watchlistId === current)?.name;
  if (!confirm(`Delete the watchlist "${name}" and all its stocks?`)) return;
  try {
    const r = await api.deleteWatchlist(current);
    toast('info', `Deleted "${name}"`, `${r.itemsRemoved} item(s) removed automatically by ON DELETE CASCADE`);
    current = null;
    await loadLists();
    loadItems();
  } catch (e) { toast('error', 'Could not delete', errorMessage(e)); }
};

// ---------- items of the selected list ----------
function drawItems(items) {
  $('#items').innerHTML = !items.length ? empty('This list is empty. Search above to add stocks.') :
    table(['Stock', 'LTP ₹', 'Change', 'Added', ''], items.map((i) => `<tr>
      <td>${stockLink(i.symbol)} ${i.owned ? '<span class="badge brand">Owned</span>' : ''}<span class="sub">${esc(i.name)}</span></td>
      <td>${livePrice(i.symbol, i.ltp)}</td>
      <td>${liveChange(i.symbol, i.changePct)}</td>
      <td class="small muted">${dateTime(i.addedAt)}</td>
      <td><button class="btn btn-ghost btn-sm" data-remove="${esc(i.symbol)}" type="button">Remove</button></td></tr>`).join(''));
}

async function loadItems() {
  if (!current) { $('#items').innerHTML = empty('Create a watchlist to start.'); return; }
  $('#items').innerHTML = spinner();
  try { drawItems(await api.watchlistItems(current)); } catch (e) { $('#items').innerHTML = errorBox(e.message); }
  loadSuggestions();
}

async function add(symbol) {
  $('#add-error').textContent = '';
  try {
    drawItems(await api.addToWatchlist(current, symbol)); // INSERT ... SELECT
    loadLists(current);
    loadSuggestions();
  } catch (e) { $('#add-error').textContent = errorMessage(e); }
}

$('#items').addEventListener('click', async (e) => {
  const b = e.target.closest('[data-remove]');
  if (!b) return;
  try { drawItems(await api.removeFromWatchlist(current, b.dataset.remove)); loadLists(current); }
  catch (err) { toast('error', 'Could not remove', errorMessage(err)); }
});

stockSearch($('#add-search'), add, 'Add a stock to this list');

// popular stocks I am not watching yet (NOT EXISTS)
async function loadSuggestions() {
  try {
    const s = await api.suggestions();
    $('#suggestions').innerHTML = s.length ? '<span class="muted">Popular, not watched yet:</span>'
      + s.slice(0, 8).map((x) => `<button class="tag" type="button" data-add="${esc(x.symbol)}">+ ${esc(x.symbol)}</button>`).join('') : '';
  } catch { $('#suggestions').innerHTML = ''; }
}
$('#suggestions').addEventListener('click', (e) => {
  const b = e.target.closest('[data-add]');
  if (b) add(b.dataset.add);
});

await loadLists();
loadItems();

// ---------- my stocks ----------
const pills = (symbols, emptyText) => !symbols.length ? empty(emptyText) :
  `<div class="card-body chips">${symbols.map((s) => `<a class="stock-pill" href="${stockUrl(s)}"><span class="strong">${esc(s)}</span> ${livePrice(s)} ${liveChange(s)}</a>`).join('')}</div>`;

const MY = {
  all: async () => {
    const rows = await api.myStocks();
    return !rows.length ? empty('Nothing watched or owned yet.') : table(['Stock', 'Watched', 'Shares held', 'LTP ₹'], rows.map((s) => `<tr>
      <td>${stockLink(s.symbol)}</td><td>${s.watched ? '✓' : '–'}</td><td>${s.heldQuantity ?? '–'}</td><td>${livePrice(s.symbol, s.ltp)}</td></tr>`).join(''));
  },
  both: async () => pills(await api.watchedAndOwned(), 'No stock is both in a watchlist and in your holdings.'),
  watchOnly: async () => pills(await api.watchOnly(), 'You own everything you watch.'),
  activity: async () => {
    const rows = await api.activity(30);
    return !rows.length ? empty('No activity yet.') : `<ul class="list">${rows.map((a) => `<li class="list-row">
      <span><span class="badge ${a.activity.includes('BUY') ? 'green' : a.activity.includes('SELL') ? 'red' : ''}">${esc(a.activity.replace(/_/g, ' '))}</span>
        ${stockLink(a.symbol)}${a.quantity !== null ? ` <span class="muted">· ${a.quantity} @ ${inr(a.price)}</span>` : ''}</span>
      <span class="small muted">${dateTime(a.happenedAt)}</span></li>`).join('')}</ul>`;
  },
  ideas: async () => {
    const rows = await api.ideas();
    return !rows.length ? empty('Buy a stock first: ideas are stocks cheaper than one you already own.') :
      table(['Idea', 'LTP ₹', 'Change today'], rows.map((s) => `<tr><td>${stockLink(s.symbol)}</td><td>${livePrice(s.symbol, s.ltp)}</td><td>${change(s.changePct)}</td></tr>`).join(''));
  },
};

async function loadMy(view) {
  $('#my-stocks').innerHTML = spinner();
  try { $('#my-stocks').innerHTML = await MY[view](); } catch (e) { $('#my-stocks').innerHTML = errorBox(e.message); }
}
tabs($('#my-tabs'), [
  { value: 'all', label: 'Watched or owned' }, { value: 'both', label: 'Both' }, { value: 'watchOnly', label: 'Watched only' },
  { value: 'activity', label: 'Activity' }, { value: 'ideas', label: 'Ideas' },
], 'all', loadMy);
loadMy('all');