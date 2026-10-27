// Admin tools (only for emails in app.admin-emails; everyone else gets 403 and is sent away)
import { api } from '../api.js';
import { initLayout } from '../layout.js';
import { $, $$, empty, errorBox, errorMessage, esc, int, spinner, table, toast } from '../util.js';

await initLayout('admin');

/** Runs one admin action, shows the result, and refreshes the archive list */
async function run(action, title, describe) {
  $$('button').forEach((b) => { b.disabled = true; });
  try {
    const result = await action();
    toast('success', title, describe(result));
    loadArchives();
  } catch (e) {
    toast('error', `${title} failed`, errorMessage(e));
  } finally {
    $$('button').forEach((b) => { b.disabled = false; });
  }
}

const value = (id) => $(id).value.trim().toUpperCase();

$('#rename').onclick = () => {
  const from = value('#old-symbol');
  const to = value('#new-symbol');
  if (from && to) run(() => api.renameSymbol(from, to), `${from} → ${to}`, (r) => r.message);
};
$('#delist').onclick = () => {
  const s = value('#list-symbol');
  if (s) run(() => api.delist(s), `Delist ${s}`, (r) => r.reason);
};
$('#activate').onclick = () => {
  const s = value('#list-symbol');
  if (s) run(() => api.activate(s), `Activate ${s}`, (r) => r.reason);
};
$('#archive').onclick = () => run(() => api.archiveHistory(), 'History archived', (r) => `${r.archiveTable}: ${int(r.rowsArchived)} rows kept`);
$('#reset').onclick = () => {
  if (confirm('Empty MARKET_PRICE_HISTORY? Charts will be blank for a few minutes.')) {
    run(() => api.resetHistory(), 'History reset', (r) => `${int(r.rowsRemoved)} rows removed`);
  }
};

async function loadArchives() {
  try {
    const rows = await api.archives();
    $('#archives').innerHTML = !rows.length ? empty('No archives yet.') :
      table(['Table', 'Rows', ''], rows.map((a) => `<tr><td class="mono">${esc(a.table)}</td><td>${int(a.rows)}</td>
        <td><button class="btn btn-danger btn-sm" data-drop="${esc(a.table)}" type="button">Drop</button></td></tr>`).join(''));
  } catch (e) { $('#archives').innerHTML = errorBox(e.message); }
}
$('#archives').addEventListener('click', (e) => {
  const b = e.target.closest('[data-drop]');
  if (b && confirm(`DROP TABLE ${b.dataset.drop}?`)) run(() => api.dropArchive(b.dataset.drop), 'Archive dropped', () => b.dataset.drop);
});
$('#archives').innerHTML = spinner();
loadArchives();