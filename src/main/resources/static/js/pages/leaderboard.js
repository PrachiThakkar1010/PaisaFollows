// Leaderboard (GET /api/leaderboard)
import { api } from '../api.js';
import { initLayout } from '../layout.js';
import { $, change, errorBox, esc, inr, spinner, table } from '../util.js';

const me = await initLayout('leaderboard');
const MEDALS = ['🥇', '🥈', '🥉'];

$('#board').innerHTML = spinner();
try {
  const rows = await api.leaderboard(25);
  $('#board').innerHTML = table(['Rank', 'Investor', 'Total value', 'Return'], rows.map((r) => `
    <tr class="${r.name === me.name ? 'me' : ''}">
      <td class="strong">${MEDALS[r.rank - 1] ?? `#${r.rank}`}</td>
      <td style="text-align:left">${esc(r.name)}${r.name === me.name ? ' <span class="small link">(you)</span>' : ''}</td>
      <td>${inr(r.totalValue)}</td>
      <td>${change(r.returnPct)}</td>
    </tr>`).join(''));
} catch (e) {
  $('#board').innerHTML = errorBox(e.message);
}