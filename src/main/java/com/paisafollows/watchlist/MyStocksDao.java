package com.paisafollows.watchlist;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.watchlist.WatchlistViews.Activity;
import com.paisafollows.watchlist.WatchlistViews.Idea;
import com.paisafollows.watchlist.WatchlistViews.MyStock;

/** "My stocks" screens: what I watch vs what I own. */
@Repository
public class MyStocksDao {

    /** Symbols in any of my watchlists (used by INTERSECT / EXCEPT) */
    private static final String MY_WATCHED_SYMBOLS =
            "SELECT i.symbol FROM WATCHLIST_ITEM wi "
          + "JOIN WATCHLIST w   ON w.watchlist_id  = wi.watchlist_id "
          + "JOIN INSTRUMENT i  ON i.instrument_id = wi.instrument_id "
          + "WHERE w.account_id = ? ";

    /** Symbols I hold (used by INTERSECT / EXCEPT) */
    private static final String MY_HELD_SYMBOLS =
            "SELECT i.symbol FROM HOLDING h "
          + "JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id "
          + "WHERE h.portfolio_id = ? ";

    /** INTERSECT: in a watchlist AND owned */
    private static final String WATCHED_AND_OWNED =
            MY_WATCHED_SYMBOLS + "INTERSECT " + MY_HELD_SYMBOLS + "ORDER BY symbol";

    /** EXCEPT: in a watchlist but NOT owned */
    private static final String WATCH_ONLY =
            MY_WATCHED_SYMBOLS + "EXCEPT " + MY_HELD_SYMBOLS + "ORDER BY symbol";

    /** FULL OUTER JOIN, emulated: LEFT JOIN  UNION  RIGHT JOIN */
    private static final String MY_STOCKS =
            "WITH watched AS ( "
          + "    SELECT DISTINCT wi.instrument_id FROM WATCHLIST_ITEM wi "
          + "    JOIN WATCHLIST w ON w.watchlist_id = wi.watchlist_id "
          + "    WHERE w.account_id = ? "
          + "), held AS ( "
          + "    SELECT instrument_id, quantity FROM HOLDING WHERE portfolio_id = ? "
          + ") "
          + "SELECT i.symbol, TRUE AS watched, h.quantity AS held_quantity, ROUND(c.last_traded_price, 2) AS ltp "
          + "FROM watched w "
          + "LEFT JOIN held h ON h.instrument_id = w.instrument_id "
          + "JOIN INSTRUMENT i ON i.instrument_id = w.instrument_id "
          + "LEFT JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = w.instrument_id "
          + "UNION "
          + "SELECT i.symbol, w.instrument_id IS NOT NULL, h.quantity, ROUND(c.last_traded_price, 2) "
          + "FROM watched w "
          + "RIGHT JOIN held h ON h.instrument_id = w.instrument_id "
          + "JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id "
          + "LEFT JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = h.instrument_id "
          + "ORDER BY symbol";

    /** UNION ALL: buys, sells and watchlist additions in one timeline */
    private static final String ACTIVITY =
            "SELECT 'BUY' AS activity, i.symbol, t.quantity, t.execution_price AS price, t.trade_time AS happened_at "
          + "FROM TRADE t "
          + "JOIN orders o     ON o.order_id      = t.buy_order_id "
          + "JOIN INSTRUMENT i ON i.instrument_id = t.instrument_id "
          + "WHERE o.account_id = ? "
          + "UNION ALL "
          + "SELECT 'SELL', i.symbol, t.quantity, t.execution_price, t.trade_time "
          + "FROM TRADE t "
          + "JOIN orders o     ON o.order_id      = t.sell_order_id "
          + "JOIN INSTRUMENT i ON i.instrument_id = t.instrument_id "
          + "WHERE o.account_id = ? "
          + "UNION ALL "
          + "SELECT 'WATCH', i.symbol, NULL, NULL, wi.added_at "
          + "FROM WATCHLIST_ITEM wi "
          + "JOIN WATCHLIST w  ON w.watchlist_id  = wi.watchlist_id "
          + "JOIN INSTRUMENT i ON i.instrument_id = wi.instrument_id "
          + "WHERE w.account_id = ? "
          + "ORDER BY happened_at DESC "
          + "LIMIT ?";

    /** ANY + NOT EXISTS: stocks I don't own, cheaper than at least one stock I do own */
    private static final String IDEAS =
            "SELECT m.symbol, m.ltp, m.change_pct "
          + "FROM v_market_movers m "
          + "WHERE m.ltp < ANY (SELECT c.last_traded_price FROM HOLDING h "
          + "                   JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = h.instrument_id "
          + "                   WHERE h.portfolio_id = ?) "
          + "  AND NOT EXISTS (SELECT 1 FROM HOLDING h2 "
          + "                  WHERE h2.portfolio_id = ? AND h2.instrument_id = m.instrument_id) "
          + "ORDER BY m.change_pct DESC "
          + "LIMIT 5";

    private final JdbcTemplate jdbc;

    public MyStocksDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MyStock> myStocks(UUID accountId, UUID portfolioId) {
        return jdbc.query(MY_STOCKS, (rs, n) -> new MyStock(
                rs.getString("symbol"),
                rs.getBoolean("watched"),
                (Integer) rs.getObject("held_quantity", Integer.class),
                rs.getBigDecimal("ltp")),
                UuidUtil.toBytes(accountId), UuidUtil.toBytes(portfolioId));
    }

    public List<String> watchedAndOwned(UUID accountId, UUID portfolioId) {
        return jdbc.queryForList(WATCHED_AND_OWNED, String.class,
                UuidUtil.toBytes(accountId), UuidUtil.toBytes(portfolioId));
    }

    public List<String> watchOnly(UUID accountId, UUID portfolioId) {
        return jdbc.queryForList(WATCH_ONLY, String.class,
                UuidUtil.toBytes(accountId), UuidUtil.toBytes(portfolioId));
    }

    public List<Activity> activity(UUID accountId, int limit) {
        byte[] acct = UuidUtil.toBytes(accountId);
        return jdbc.query(ACTIVITY, (rs, n) -> new Activity(
                rs.getString("activity"),
                rs.getString("symbol"),
                (Integer) rs.getObject("quantity", Integer.class),
                rs.getBigDecimal("price"),
                rs.getObject("happened_at", LocalDateTime.class)), acct, acct, acct, limit);
    }

    public List<Idea> ideas(UUID portfolioId) {
        byte[] pf = UuidUtil.toBytes(portfolioId);
        return jdbc.query(IDEAS, (rs, n) -> new Idea(
                rs.getString("symbol"), rs.getBigDecimal("ltp"), rs.getBigDecimal("change_pct")), pf, pf);
    }
}