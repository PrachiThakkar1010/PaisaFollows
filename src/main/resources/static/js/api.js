// =========================================================
// Every call to the Spring Boot backend lives in this file.
// The page and the API come from the same server (port 8080),
// so the login cookie (JSESSIONID) is sent automatically.
// =========================================================

/** Every backend error looks like {"error": "INSUFFICIENT_FUNDS", "message": "..."} */
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/** Pages that work without being logged in (never redirect away from these) */
const PUBLIC_PAGES = ['/', '/index.html', '/register.html'];

async function request(method, path, body) {
  const options = { method, credentials: 'same-origin', headers: {} };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  const res = await fetch(path, options);
  if (res.status === 204) return null;

  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }

  if (!res.ok) {
    // Not logged in (or the session expired): go to the login page,
    // unless we are already on the login / sign-up page (that would reload it forever)
    if (res.status === 401 && !path.startsWith('/api/auth/') && !PUBLIC_PAGES.includes(window.location.pathname)) {
      window.location.href = '/index.html';
    }
    throw new ApiError(res.status, data?.error ?? 'ERROR', data?.message ?? `Request failed (${res.status})`);
  }
  return data;
}

const get = (path) => request('GET', path);
const post = (path, body = {}) => request('POST', path, body);
const put = (path, body) => request('PUT', path, body);
const del = (path) => request('DELETE', path);

/** Builds "?a=1&b=2", skipping empty values */
function qs(params) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  }
  const s = p.toString();
  return s ? `?${s}` : '';
}

const enc = encodeURIComponent;

export const api = {
  // ---- auth
  register: (body) => post('/api/auth/register', body),
  login: (email, password) => post('/api/auth/login', { email, password }),
  logout: () => post('/api/auth/logout'),
  me: () => get('/api/me'),

  // ---- market data
  quotes: () => get('/api/quotes'),
  search: (q) => get(`/api/stocks/search${qs({ q })}`),
  sectors: () => get('/api/stocks/sectors'),
  screener: ({ sectors, minPrice, maxPrice, sort, dir, page, size }) =>
    get(`/api/stocks${qs({ sectors: sectors?.length ? sectors.join(',') : undefined, minPrice, maxPrice, sort, dir, page, size })}`),
  movers: (type, limit = 5) => get(`/api/stocks/movers${qs({ type, limit })}`),
  affordable: (limit = 50) => get(`/api/stocks/affordable${qs({ limit })}`),
  stock: (symbol) => get(`/api/stocks/${enc(symbol)}`),
  similar: (symbol) => get(`/api/stocks/${enc(symbol)}/similar`),
  candles: (symbol, interval) => get(`/api/candles/${enc(symbol)}${qs({ interval })}`),
  sectorSummary: () => get('/api/market/sectors'),
  sectorPerformance: (minStocks = 3) => get(`/api/market/sector-performance${qs({ minStocks })}`),
  overview: () => get('/api/market/overview'),

  // ---- trading
  placeOrder: (body) => post('/api/orders', body),
  cancelOrder: (orderId) => del(`/api/orders/${enc(orderId)}`),
  basket: (legs) => post('/api/orders/basket', { legs }),
  orders: (status, page, size = 10) => get(`/api/orders${qs({ status, page, size })}`),

  // ---- portfolio
  portfolio: () => get('/api/portfolio'),
  statement: (from, to) => get(`/api/statement${qs({ from, to })}`),
  walletHistory: (limit = 50) => get(`/api/wallet/history${qs({ limit })}`),
  leaderboard: (limit = 25) => get(`/api/leaderboard${qs({ limit })}`),

  // ---- watchlists and "my stocks"
  watchlists: () => get('/api/watchlists'),
  createWatchlist: (name) => post('/api/watchlists', { name }),
  deleteWatchlist: (id) => del(`/api/watchlists/${enc(id)}`),
  watchlistItems: (id) => get(`/api/watchlists/${enc(id)}/items`),
  addToWatchlist: (id, symbol) => post(`/api/watchlists/${enc(id)}/items`, { symbol }),
  removeFromWatchlist: (id, symbol) => del(`/api/watchlists/${enc(id)}/items/${enc(symbol)}`),
  suggestions: () => get('/api/watchlists/suggestions'),
  myStocks: () => get('/api/my-stocks'),
  watchedAndOwned: () => get('/api/my-stocks/watched-and-owned'),
  watchOnly: () => get('/api/my-stocks/watch-only'),
  activity: (limit = 30) => get(`/api/my-stocks/activity${qs({ limit })}`),
  ideas: () => get('/api/my-stocks/ideas'),

  // ---- alerts
  alerts: (status) => get(`/api/alerts${qs({ status })}`),
  createAlert: (symbol, direction, targetPrice) => post('/api/alerts', { symbol, direction, targetPrice }),
  updateAlert: (id, targetPrice) => put(`/api/alerts/${enc(id)}`, { targetPrice }),
  deleteAlert: (id) => del(`/api/alerts/${enc(id)}`),

  // ---- admin
  archives: () => get('/api/admin/price-history/archives'),
  archiveHistory: () => post('/api/admin/price-history/archive'),
  dropArchive: (table) => del(`/api/admin/price-history/archives/${enc(table)}`),
  resetHistory: () => post('/api/admin/price-history/reset'),
  renameSymbol: (symbol, newSymbol) => post(`/api/admin/instruments/${enc(symbol)}/rename`, { newSymbol }),
  delist: (symbol) => del(`/api/admin/instruments/${enc(symbol)}`),
  activate: (symbol) => post(`/api/admin/instruments/${enc(symbol)}/activate`),
};