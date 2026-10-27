// Price alerts: create, edit target (UPDATE), delete. A database trigger marks them TRIGGERED.
import { api } from '../api.js';
import { initLayout } from '../layout.js';
import { $, dateTime, empty, errorBox, errorMessage, esc, livePrice, num, spinner, statusBadge, stockLink, stockSearch, table, tabs, toast } from '../util.js';

await initLayout('alerts');

let filter = '';
let symbol = '';

// ---------- choose a stock ----------
function showChosen() {
  $('#chosen').classList.toggle('hidden', !symbol);
  $('#alert-search').classList.toggle('hidden', !!symbol);
  $('#chosen').innerHTML = symbol ? `<span><span class="strong">${esc(symbol)}</span> · ₹${livePrice(symbol)}</span>
    <button class="btn btn-ghost btn-sm" type="button" id="change-stock">Change</button>` : '';
}
stockSearch($('#alert-search'), (s) => { symbol = s; showChosen(); });
$('#chosen').addEventListener('click', (e) => { if (e.target.id === 'change-stock') { symbol = ''; showChosen(); } });
showChosen();

// ---------- list ----------
function draw(rows) {
  const shown = rows.filter((a) => !filter || a.status === filter);
  $('#alerts').innerHTML = !shown.length ? empty('No alerts here.') :
    table(['Stock', 'Condition', 'Target ₹', 'Live ₹', 'Status', ''], shown.map((a) => `<tr>
      <td>${stockLink(a.symbol)}</td>
      <td class="small">${a.direction === 'ABOVE' ? '↑ above' : '↓ below'}</td>
      <td>${num(a.targetPrice)} ${a.status === 'ACTIVE' ? `<button class="btn btn-ghost btn-sm" data-edit="${esc(a.alertId)}" data-target="${a.targetPrice}" type="button">Edit</button>` : ''}</td>
      <td>${livePrice(a.symbol, a.currentPrice)}</td>
      <td>${statusBadge(a.status)}${a.triggeredAt ? `<span class="sub tiny">at ₹${num(a.triggeredPrice)} · ${dateTime(a.triggeredAt)}</span>` : ''}</td>
      <td><button class="btn btn-ghost btn-sm" data-delete="${esc(a.alertId)}" type="button">Delete</button></td></tr>`).join(''));
}

async function load() {
  try { draw(await api.alerts()); } catch (e) { $('#alerts').innerHTML = errorBox(e.message); }
}

tabs($('#status-tabs'), [{ value: '', label: 'All' }, { value: 'ACTIVE', label: 'Active' }, { value: 'TRIGGERED', label: 'Triggered' }],
  '', (v) => { filter = v; load(); });

$('#alerts').innerHTML = spinner();
load();
setInterval(load, 10000); // the trigger flips alerts to TRIGGERED in the database, so check again every 10 s

// ---------- create ----------
$('#create').onclick = async () => {
  $('#create-error').textContent = '';
  const target = Number($('#target').value);
  if (!symbol) { $('#create-error').textContent = 'Choose a stock'; return; }
  if (!target) { $('#create-error').textContent = 'Enter a target price'; return; }
  try {
    draw(await api.createAlert(symbol, $('#direction').value, target));
    toast('success', 'Alert created', `${symbol} ${$('#direction').value.toLowerCase()} ₹${num(target)}`);
    $('#target').value = '';
  } catch (e) { $('#create-error').textContent = errorMessage(e); }
};

// ---------- edit (UPDATE without cascade) and delete ----------
$('#alerts').addEventListener('click', async (e) => {
  const edit = e.target.closest('[data-edit]');
  const del = e.target.closest('[data-delete]');
  try {
    if (edit) {
      const value = prompt('New target price (₹)', edit.dataset.target);
      if (value === null) return;
      draw(await api.updateAlert(edit.dataset.edit, Number(value)));
      toast('success', 'Alert updated');
    }
    if (del) draw(await api.deleteAlert(del.dataset.delete));
  } catch (err) { toast('error', 'Could not change the alert', errorMessage(err)); }
});