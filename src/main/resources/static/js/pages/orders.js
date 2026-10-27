// Orders: history with filters and cancel, plus the basket order
import { api } from '../api.js';
import { initLayout, refreshMe } from '../layout.js';
import { onOrderEvent } from '../live.js';
import { $, $$, dateTime, empty, errorBox, errorMessage, esc, inr, num, pager, sideBadge, signedInr, spinner, statusBadge, stockLink, table, tabs, toast, tone } from '../util.js';

await initLayout('orders');

// ---------- history ----------
let status = '';
let page = 0;

tabs($('#status-tabs'), [
  { value: '', label: 'All' }, { value: 'OPEN', label: 'Open' }, { value: 'FILLED', label: 'Filled' },
  { value: 'CANCELLED', label: 'Cancelled' }, { value: 'REJECTED', label: 'Rejected' }, { value: 'EXPIRED', label: 'Expired' },
], '', (v) => { status = v; page = 0; loadOrders(); });

async function loadOrders() {
  if (!$('#orders').innerHTML) $('#orders').innerHTML = spinner();
  try {
    const res = await api.orders(status || undefined, page, 10);
    $('#orders').innerHTML = !res.items.length ? empty('No orders here yet.') :
      table(['Placed', 'Stock', 'Side', 'Type', 'Qty', 'Price', 'Status', 'Fill', 'P&amp;L', ''], res.items.map((o) => `<tr>
        <td class="small muted">${dateTime(o.createdAt)}</td>
        <td>${stockLink(o.symbol)}</td>
        <td>${sideBadge(o.side)}</td>
        <td class="small">${esc(o.type.replace('_', ' '))}</td>
        <td>${o.quantity}</td>
        <td>${o.type === 'STOP_LOSS' ? `<span title="Trigger price">▼ ${num(o.triggerPrice)}</span>` : num(o.price)}</td>
        <td>${statusBadge(o.status)}${o.rejectReason ? `<span class="sub tiny" title="${esc(o.rejectReason)}">${esc(o.rejectReason)}</span>` : ''}</td>
        <td>${o.fillPrice !== null ? `${num(o.fillPrice)}<span class="sub tiny">${dateTime(o.filledAt)}</span>` : '–'}</td>
        <td class="${tone(o.realisedPnl)}">${o.realisedPnl !== null ? signedInr(o.realisedPnl) : '–'}</td>
        <td>${o.status === 'OPEN' ? `<button class="btn btn-danger btn-sm" data-cancel="${esc(o.orderId)}" type="button">Cancel</button>` : ''}</td>
      </tr>`).join(''));
    pager($('#orders-pager'), res.page, res.totalPages, (p) => { page = p; loadOrders(); });
  } catch (e) { $('#orders').innerHTML = errorBox(e.message); }
}

// Cancel button (DELETE /api/orders/{id})
$('#orders').addEventListener('click', async (e) => {
  const b = e.target.closest('[data-cancel]');
  if (!b) return;
  b.disabled = true;
  try {
    await api.cancelOrder(b.dataset.cancel);
    toast('info', 'Order cancelled');
    refreshMe();
    loadOrders();
  } catch (err) {
    toast('error', 'Could not cancel', errorMessage(err));
    b.disabled = false;
  }
});

loadOrders();
onOrderEvent(loadOrders); // a waiting order filled or expired

// ---------- basket ----------
function legRow(symbol = '', side = 'BUY', qty = 1) {
  const div = document.createElement('div');
  div.className = 'row leg';
  div.innerHTML = `
    <input class="input grow leg-symbol" placeholder="Symbol" value="${esc(symbol)}" style="text-transform:uppercase;flex:1;min-width:0">
    <select class="input leg-side" style="width:90px"><option ${side === 'BUY' ? 'selected' : ''}>BUY</option><option ${side === 'SELL' ? 'selected' : ''}>SELL</option></select>
    <input class="input leg-qty" type="number" min="1" value="${qty}" style="width:90px">
    <button class="btn btn-ghost leg-remove" type="button" aria-label="Remove">✕</button>`;
  return div;
}
const legs = $('#legs');
legs.append(legRow('ITC', 'BUY', 2), legRow('SBIN', 'BUY', 1));
legs.addEventListener('click', (e) => {
  if (e.target.closest('.leg-remove') && $$('.leg', legs).length > 1) e.target.closest('.leg').remove();
});
$('#add-leg').onclick = () => { if ($$('.leg', legs).length < 10) legs.append(legRow()); };

$('#place-basket').onclick = async () => {
  $('#basket-error').textContent = '';
  $('#basket-result').innerHTML = '';
  const body = $$('.leg', legs).map((l) => ({
    symbol: $('.leg-symbol', l).value.trim().toUpperCase(),
    side: $('.leg-side', l).value,
    quantity: Math.floor(Number($('.leg-qty', l).value)),
  }));
  $('#place-basket').disabled = true;
  try {
    const res = await api.basket(body);
    $('#basket-result').innerHTML = `<p class="small muted" style="padding:12px 20px 0">${res.filled} filled · ${res.rolledBack} rolled back</p>
      <ul class="list">${res.legs.map((l) => `<li class="list-row">
        <span><span class="tiny faint">#${l.leg}</span> ${sideBadge(l.side)} ${l.quantity} <span class="strong">${esc(l.symbol)}</span></span>
        <span class="right">${statusBadge(l.outcome)}<br><span class="tiny muted">${l.outcome === 'FILLED' ? `@ ${inr(l.order?.fillPrice)}` : esc(l.reason ?? '')}</span></span>
      </li>`).join('')}</ul>`;
    refreshMe();
    loadOrders();
  } catch (e) {
    $('#basket-error').textContent = errorMessage(e);
  } finally {
    $('#place-basket').disabled = false;
  }
};