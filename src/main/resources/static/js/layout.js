// =========================================================
// The part every logged-in page shares: login check, header,
// menu, search box, balance, scrolling ticker and live prices.
// Each page calls:   const me = await initLayout('dashboard');
// =========================================================
import { api } from './api.js';
import { allQuotes, onOrderEvent, onPrices, refreshPrices, startLive } from './live.js';
import { $, esc, inr, pct, stockSearch, stockUrl } from './util.js';

const NAV = [
  ['dashboard', 'Dashboard', '/dashboard.html'],
  ['markets', 'Markets', '/markets.html'],
  ['portfolio', 'Portfolio', '/portfolio.html'],
  ['orders', 'Orders', '/orders.html'],
  ['watchlists', 'Watchlists', '/watchlists.html'],
  ['alerts', 'Alerts', '/alerts.html'],
  ['leaderboard', 'Leaderboard', '/leaderboard.html'],
];

/** The logged-in user (filled by initLayout, kept fresh by refreshMe) */
export const session = { me: null, isAdmin: false };

export async function initLayout(active) {
  // 1. Logged in? (a 401 sends the browser to the login page)
  session.me = await api.me();

  // 2. Admin? The login reply does not say, so ask an admin-only URL: 200 = admin, 403 = not
  try { await api.archives(); session.isAdmin = true; } catch { session.isAdmin = false; }
  if (active === 'admin' && !session.isAdmin) {
    window.location.href = '/dashboard.html';
    return session.me;
  }

  // 3. Draw the header at the top of the page
  const nav = session.isAdmin ? [...NAV, ['admin', 'Admin', '/admin.html']] : NAV;
  const links = nav.map(([key, label, href]) => `<a href="${href}" class="${key === active ? 'active' : ''}">${label}</a>`).join('');
  const header = document.createElement('header');
  header.className = 'site-header';
  header.innerHTML = `
    <div class="header-row">
      <a href="/dashboard.html" class="brand"><img src="/favicon.svg" alt=""><span>Paisa<span>Follows</span></span></a>
      <nav class="main-nav">${links}</nav>
      <div id="header-search" class="header-search"></div>
      <div class="user-menu">
        <button class="user-button" id="user-button" type="button">
          <span class="live-dot" id="live-dot" title="Live prices"></span>
          <span class="who"><span class="strong" id="me-name"></span><br><span class="num muted" id="me-cash"></span></span>
        </button>
        <div class="dropdown hidden" id="user-dropdown">
          <div class="info">
            <p class="strong" id="me-name-2"></p>
            <p class="small muted truncate">${esc(session.me.email)}</p>
            <p class="small muted">Reserved: <span class="num" id="me-reserved"></span></p>
          </div>
          <div class="mobile-nav">${links}</div>
          <button id="logout" class="down" type="button">Log out</button>
        </div>
      </div>
    </div>
    <div class="ticker"><div class="ticker-track" id="ticker"></div></div>`;
  document.body.prepend(header);
  showMe();

  // 4. Menu, logout and the search box
  $('#user-button').onclick = () => $('#user-dropdown').classList.toggle('hidden');
  document.addEventListener('click', (e) => { if (!e.target.closest('.user-menu')) $('#user-dropdown')?.classList.add('hidden'); });
  $('#logout').onclick = async () => {
    try { await api.logout(); } finally { window.location.href = '/index.html'; }
  };
  stockSearch($('#header-search'), (symbol) => { window.location.href = stockUrl(symbol); }, 'Search stocks');

  // 5. Live prices: ticker strip + balance refresh when an order fills
  onPrices(drawTicker);
  onOrderEvent(() => refreshMe());
  startLive();

  return session.me;
}

/** Re-reads the balance (after a trade) and updates the header */
export async function refreshMe() {
  try { session.me = await api.me(); showMe(); } catch { /* 401 handled in api.js */ }
  return session.me;
}

function showMe() {
  const me = session.me;
  $('#me-name').textContent = me.name;
  $('#me-name-2').textContent = me.name;
  $('#me-cash').textContent = inr(me.availableBalance);
  $('#me-reserved').textContent = inr(me.reservedBalance);
}

let tickerBuilt = false;
function drawTicker() {
  const quotes = allQuotes();
  const track = $('#ticker');
  if (!track || !quotes.length) return;
  if (!tickerBuilt) {
    // Two copies side by side, so the scrolling loops without a gap
    const items = [...quotes, ...quotes].map((q) => `<a href="${stockUrl(q.symbol)}">
      <span class="strong">${esc(q.symbol)}</span>
      <span class="num" data-price="${esc(q.symbol)}">${Number(q.price).toFixed(2)}</span>
      <span class="num" data-change="${esc(q.symbol)}">${pct(q.changePct)}</span></a>`).join('');
    track.innerHTML = items;
    tickerBuilt = true;
    refreshPrices(); // colour the % changes straight away
  }
}