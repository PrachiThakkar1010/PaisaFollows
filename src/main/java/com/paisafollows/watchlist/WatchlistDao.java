package com.paisafollows.watchlist;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.watchlist.WatchlistViews.Suggestion;
import com.paisafollows.watchlist.WatchlistViews.WatchlistItem;
import com.paisafollows.watchlist.WatchlistViews.WatchlistSummary;

/** All watchlist SQL. */
@Repository
public class WatchlistDao {

    /** LEFT JOIN: every watchlist of the account, including EMPTY ones (count = 0) */
    private static final String FIND_WATCHLISTS =
            "SELECT w.watchlist_id, w.name, w.created_at, COUNT(wi.watchlist_item_id) AS items "
          + "FROM WATCHLIST w "
          + "LEFT JOIN WATCHLIST_ITEM wi ON wi.watchlist_id = w.watchlist_id "
          + "WHERE w.account_id = ? "
          + "GROUP BY w.watchlist_id, w.name, w.created_at "
          + "ORDER BY w.created_at";

    private static final String INSERT_WATCHLIST =
            "INSERT INTO WATCHLIST (watchlist_id, account_id, name) VALUES (?, ?, ?)";

    private static final String OWNS_WATCHLIST =
            "SELECT COUNT(*) FROM WATCHLIST WHERE watchlist_id = ? AND account_id = ?";

    /** LEFT JOIN for the price (a stock may have no price yet) + EXISTS for the "owned" badge */
    private static final String FIND_ITEMS =
            "SELECT i.symbol, i.name, "
          + "       ROUND(c.last_traded_price, 2) AS ltp, "
          + "       ROUND((c.last_traded_price - c.previous_close) * 100 / NULLIF(c.previous_close, 0), 2) AS change_pct, "
          + "       EXISTS (SELECT 1 FROM HOLDING h "
          + "               WHERE h.portfolio_id = ? AND h.instrument_id = wi.instrument_id) AS owned, "
          + "       wi.added_at "
          + "FROM WATCHLIST_ITEM wi "
          + "JOIN INSTRUMENT i ON i.instrument_id = wi.instrument_id "
          + "LEFT JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = wi.instrument_id "
          + "WHERE wi.watchlist_id = ? "
          + "ORDER BY wi.added_at DESC";

    /** INSERT ... SELECT: turns the symbol into its instrument_id in the same statement */
    private static final String ADD_ITEM =
            "INSERT INTO WATCHLIST_ITEM (watchlist_item_id, watchlist_id, instrument_id) "
          + "SELECT ?, ?, instrument_id FROM INSTRUMENT WHERE symbol = ? AND status = 'ACTIVE'";

    /** Multi-table DELETE: find the item through its stock's symbol */
    private static final String REMOVE_ITEM =
            "DELETE wi FROM WATCHLIST_ITEM wi "
          + "JOIN INSTRUMENT i ON i.instrument_id = wi.instrument_id "
          + "WHERE wi.watchlist_id = ? AND i.symbol = ?";

    private static final String COUNT_ITEMS =
            "SELECT COUNT(*) FROM WATCHLIST_ITEM WHERE watchlist_id = ?";

    /** DELETE with CASCADE: its WATCHLIST_ITEM rows are removed automatically */
    private static final String DELETE_WATCHLIST =
            "DELETE FROM WATCHLIST WHERE watchlist_id = ? AND account_id = ?";

    /** NOT EXISTS: stocks other investors hold that are in none of my watchlists */
    private static final String SUGGESTIONS =
            "SELECT i.symbol, COUNT(*) AS holders "
          + "FROM HOLDING h "
          + "JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id "
          + "WHERE i.status = 'ACTIVE' "
          + "  AND NOT EXISTS (SELECT 1 FROM WATCHLIST_ITEM wi "
          + "                  JOIN WATCHLIST w ON w.watchlist_id = wi.watchlist_id "
          + "                  WHERE w.account_id = ? AND wi.instrument_id = h.instrument_id) "
          + "GROUP BY i.symbol "
          + "ORDER BY holders DESC, i.symbol "
          + "LIMIT 5";

    private final JdbcTemplate jdbc;

    public WatchlistDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WatchlistSummary> findAll(UUID accountId) {
        return jdbc.query(FIND_WATCHLISTS, (rs, n) -> new WatchlistSummary(
                UuidUtil.fromBytes(rs.getBytes("watchlist_id")),
                rs.getString("name"),
                rs.getLong("items"),
                rs.getObject("created_at", LocalDateTime.class)), UuidUtil.toBytes(accountId));
    }

    public UUID create(UUID accountId, String name) {
        UUID id = UuidUtil.newId();
        jdbc.update(INSERT_WATCHLIST, UuidUtil.toBytes(id), UuidUtil.toBytes(accountId), name.trim());
        return id;
    }

    public boolean owns(UUID watchlistId, UUID accountId) {
        Integer n = jdbc.queryForObject(OWNS_WATCHLIST, Integer.class,
                UuidUtil.toBytes(watchlistId), UuidUtil.toBytes(accountId));
        return n != null && n > 0;
    }

    public List<WatchlistItem> items(UUID watchlistId, UUID portfolioId) {
        return jdbc.query(FIND_ITEMS, (rs, n) -> new WatchlistItem(
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getBigDecimal("ltp"),
                rs.getBigDecimal("change_pct"),
                rs.getBoolean("owned"),
                rs.getObject("added_at", LocalDateTime.class)),
                UuidUtil.toBytes(portfolioId), UuidUtil.toBytes(watchlistId));
    }

    /** @return 1 if added, 0 if the symbol does not exist (a duplicate throws DuplicateKeyException) */
    public int addItem(UUID watchlistId, String symbol) {
        return jdbc.update(ADD_ITEM, UuidUtil.toBytes(UuidUtil.newId()), UuidUtil.toBytes(watchlistId),
                symbol.trim().toUpperCase());
    }

    public int removeItem(UUID watchlistId, String symbol) {
        return jdbc.update(REMOVE_ITEM, UuidUtil.toBytes(watchlistId), symbol.trim().toUpperCase());
    }

    public long countItems(UUID watchlistId) {
        Long n = jdbc.queryForObject(COUNT_ITEMS, Long.class, UuidUtil.toBytes(watchlistId));
        return n == null ? 0 : n;
    }

    public int delete(UUID watchlistId, UUID accountId) {
        return jdbc.update(DELETE_WATCHLIST, UuidUtil.toBytes(watchlistId), UuidUtil.toBytes(accountId));
    }

    public List<Suggestion> suggestions(UUID accountId) {
        return jdbc.query(SUGGESTIONS, (rs, n) -> new Suggestion(rs.getString("symbol"), rs.getLong("holders")),
                UuidUtil.toBytes(accountId));
    }
}