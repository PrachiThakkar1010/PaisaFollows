// Re-values holdings with the live price (the server's numbers are a few seconds old)
import { getQuote } from './live.js';

export function liveHolding(h) {
  const ltp = Number(getQuote(h.symbol)?.price ?? h.ltp);
  const currentValue = ltp * h.quantity;
  const invested = Number(h.invested);
  const unrealisedPnl = currentValue - invested;
  return {
    ...h,
    ltp,
    currentValue,
    unrealisedPnl,
    pnlPct: invested ? (unrealisedPnl * 100) / invested : 0,
    dayChange: (ltp - Number(h.previousClose)) * h.quantity,
  };
}

export function liveTotals(p) {
  const holdings = p.holdings.map(liveHolding);
  const holdingsValue = holdings.reduce((s, h) => s + h.currentValue, 0);
  const invested = holdings.reduce((s, h) => s + Number(h.invested), 0);
  const dayChange = holdings.reduce((s, h) => s + h.dayChange, 0);
  const cash = Number(p.availableCash) + Number(p.reservedCash);
  return {
    holdings,
    holdingsValue,
    invested,
    unrealisedPnl: holdingsValue - invested,
    unrealisedPct: invested ? ((holdingsValue - invested) * 100) / invested : 0,
    dayChange,
    totalValue: cash + holdingsValue,
  };
}