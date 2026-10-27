// =========================================================
// Small helpers shared by every page: formatting, HTML snippets,
// pop-up messages, tabs, page buttons and the stock search box.
// =========================================================
import { api } from './api.js';

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

/** Makes text safe to put inside HTML (stops names like "<script>" from running) */
export function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ---------- numbers ----------
const inrFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const numFormat = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const intFormat = new Intl.NumberFormat('en-IN');
const blank = (v) => v === null || v === undefined || v === '';

/** ₹1,23,456.78 (Indian digit grouping) */
export const inr = (v) => (blank(v) ? '–' : inrFormat.format(Number(v)));
/** 1,23,456.78 */
export const num = (v) => (blank(v) ? '–' : numFormat.format(Number(v)));
export const int = (v) => (blank(v) ? '–' : intFormat.format(Number(v)));
/** +1.23% / -0.40% */
export const pct = (v) => (blank(v) ? '–' : `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(2)}%`);
/** +₹12.30 / -₹4.00 */
export function signedInr(v) {
  if (blank(v)) return '–';
  const n = Number(v);
  return `${n > 0 ? '+' : n < 0 ? '-' : ''}${inrFormat.format(Math.abs(n))}`;
}
/** CSS class for a gain, a loss or no change */
export const tone = (v) => (Number(v ?? 0) > 0 ? 'up' : Number(v ?? 0) < 0 ? 'down' : 'flat');
/** Round to NSE's 5-paise tick */
export const toTick = (p) => Math.round(p / 0.05) * 0.05;

// ---------- dates (the backend already sends Indian time) ----------
export function dateTime(v) {
  if (!v) return '–';
  const d = new Date(String(v).replace(' ', 'T'));
  return Number.isNaN(d.getTime()) ? esc(v) : d.toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}
/** yyyy-mm-dd for <input type="date"> */
export function isoDate(d) {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

// ---------- HTML snippets ----------
export const spinner = (text = 'Loading…') => `<div class="spinner">${esc(text)}</div>`;
export const empty = (html) => `<div class="empty">${html}</div>`;
export const errorBox = (text) => `<p class="error">${esc(text)}</p>`;

const STATUS_COLORS = {
  FILLED: 'green', OPEN: 'blue', CANCELLED: '', REJECTED: 'red', EXPIRED: 'amber',
  ACTIVE: 'blue', TRIGGERED: 'green', ROLLED_BACK: 'red', SKIPPED: 'amber',
};
export const statusBadge = (s) => `<span class="badge ${STATUS_COLORS[s] ?? ''}">${esc(String(s).replace('_', ' '))}</span>`;
export const sideBadge = (s) => `<span class="badge ${s === 'BUY' ? 'green' : 'red'}">${esc(s)}</span>`;
export const change = (v) => `<span class="num chg ${tone(v)}">${pct(v)}</span>`;

/** A price that live.js keeps updating (and flashing) every second */
export const livePrice = (symbol, fallback) => `<span class="num" data-price="${esc(symbol)}">${num(fallback)}</span>`;
/** A % change that live.js keeps updating */
export const liveChange = (symbol, fallback) => `<span class="num chg ${tone(fallback)}" data-change="${esc(symbol)}">${pct(fallback)}</span>`;

/** Link to a stock page */
export const stockLink = (symbol) => `<a class="strong plain" href="/stock.html?symbol=${encodeURIComponent(symbol)}">${esc(symbol)}</a>`;
export const stockUrl = (symbol) => `/stock.html?symbol=${encodeURIComponent(symbol)}`;

/** <table> from a list of column titles and the rows' HTML */
export function table(head, rowsHtml) {
  return `<div class="table-wrap"><table class="table"><thead><tr>${head.map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${rowsHtml}</tbody></table></div>`;
}

export function errorMessage(e) {
  return e?.message ?? 'Something went wrong';
}

// ---------- pop-up messages (bottom right) ----------
export function toast(kind, title, body = '') {
  let box = $('#toasts');
  if (!box) {
    box = document.createElement('div');
    box.id = 'toasts';
    document.body.appendChild(box);
  }
  const el = document.createElement('div');
  el.className = `toast ${kind}`;
  el.setAttribute('role', 'status');
  el.innerHTML = `<p class="title">${esc(title)}</p>${body ? `<p class="body">${esc(body)}</p>` : ''}`;
  box.appendChild(el);
  while (box.children.length > 4) box.firstChild.remove();
  setTimeout(() => el.remove(), kind === 'error' ? 7000 : 5000);
}

// ---------- tabs ----------
/** Draws tab buttons into `el`; calls onChange(value) when one is clicked */
export function tabs(el, options, value, onChange) {
  const draw = (current) => {
    el.className = 'tabs';
    el.innerHTML = options.map((o) => `<button type="button" data-v="${esc(o.value)}" class="${o.value === current ? 'active' : ''}">${esc(o.label)}</button>`).join('');
  };
  draw(value);
  el.onclick = (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    draw(b.dataset.v);
    onChange(b.dataset.v);
  };
}

// ---------- page buttons ----------
export function pager(el, page, totalPages, onChange) {
  if (totalPages <= 1) { el.innerHTML = ''; return; }
  el.className = 'pager';
  el.innerHTML = `<span class="muted">Page ${page + 1} of ${totalPages}</span>
    <div class="row"><button class="btn btn-secondary btn-sm" data-p="${page - 1}" ${page === 0 ? 'disabled' : ''}>Previous</button>
    <button class="btn btn-secondary btn-sm" data-p="${page + 1}" ${page + 1 >= totalPages ? 'disabled' : ''}>Next</button></div>`;
  el.onclick = (e) => {
    const b = e.target.closest('button[data-p]');
    if (b && !b.disabled) onChange(Number(b.dataset.p));
  };
}

// ---------- stock search box (GET /api/stocks/search uses SQL LIKE) ----------
export function stockSearch(el, onSelect, placeholder = 'Search stocks, e.g. tata') {
  el.classList.add('search');
  el.innerHTML = `<input class="input" placeholder="${esc(placeholder)}" autocomplete="off"><ul class="search-results hidden"></ul>`;
  const input = $('input', el);
  const list = $('ul', el);
  let results = [];
  let active = 0;
  let timer;

  const close = () => list.classList.add('hidden');
  const draw = () => {
    list.innerHTML = results.map((r, i) => `<li><button type="button" data-i="${i}" class="${i === active ? 'active' : ''}">
      <span class="strong">${esc(r.symbol)}</span><span class="small muted truncate">${esc(r.name)}</span></button></li>`).join('');
    list.classList.toggle('hidden', results.length === 0);
  };
  const choose = (i) => {
    if (!results[i]) return;
    onSelect(results[i].symbol);
    input.value = '';
    results = [];
    close();
  };

  input.addEventListener('input', () => {
    clearTimeout(timer);
    const q = input.value.trim();
    if (!q) { results = []; close(); return; }
    // wait 200 ms after the last key press before asking the server
    timer = setTimeout(async () => {
      try { results = (await api.search(q)).slice(0, 8); active = 0; draw(); } catch { results = []; close(); }
    }, 200);
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); active = Math.min(active + 1, results.length - 1); draw(); }
    if (e.key === 'ArrowUp') { e.preventDefault(); active = Math.max(active - 1, 0); draw(); }
    if (e.key === 'Enter') { e.preventDefault(); choose(active); }
    if (e.key === 'Escape') close();
  });
  list.addEventListener('mousedown', (e) => {
    const b = e.target.closest('button');
    if (b) { e.preventDefault(); choose(Number(b.dataset.i)); }
  });
  document.addEventListener('mousedown', (e) => { if (!el.contains(e.target)) close(); });
}