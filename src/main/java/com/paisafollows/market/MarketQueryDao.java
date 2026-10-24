package com.paisafollows.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.market.MarketViews.AffordableStock;
import com.paisafollows.market.MarketViews.MarketOverview;
import com.paisafollows.market.MarketViews.MostHeld;
import com.paisafollows.market.MarketViews.SearchResult;
import com.paisafollows.market.MarketViews.SectorPerformance;
import com.paisafollows.market.MarketViews.SectorSummary;
import com.paisafollows.market.MarketViews.SimilarStock;
import com.paisafollows.market.MarketViews.StockDetail;
import com.paisafollows.market.MarketViews.StockRow;

/** All SQL behind the market screens. */
@Repository
public class MarketQueryDao {

    // ---------- LIKE: search box ----------
    private static final String SEARCH =
            "SELECT symbol, name FROM INSTRUMENT "
          + "WHERE status = 'ACTIVE' AND instrument_type = 'STOCK' "
          + "  AND (symbol LIKE ? OR name LIKE ?) "
          + "ORDER BY symbol "
          + "LIMIT 10";

    // ---------- DISTINCT: sector dropdown ----------
    private static final String SECTORS =
            "SELECT DISTINCT s.sector "
          + "FROM STOCK s JOIN INSTRUMENT i ON i.instrument_id = s.instrument_id "
          + "WHERE i.status = 'ACTIVE' AND s.sector IS NOT NULL "
          + "ORDER BY s.sector";

    // ---------- VIEW: movers (direction is added from a fixed list, see movers()) ----------
    private static final String MOVERS =
            "SELECT instrument_id, symbol, name, sector, ltp, previous_close, change_amount, change_pct "
          + "FROM v_market_movers "
          + "ORDER BY change_pct %s "
          + "LIMIT ?";

    // ---------- Screener base (WHERE / IN / ORDER BY / LIMIT / OFFSET are added in screener()) ----------
    private static final String SCREENER_SELECT =
            "SELECT instrument_id, symbol, name, sector, ltp, previous_close, change_amount, change_pct "
          + "FROM v_market_movers ";

    private static final String SCREENER_COUNT =
            "SELECT COUNT(*) FROM v_market_movers ";

    // ---------- NATURAL JOIN: stock page facts ----------
    private static final String DETAIL =
            "SELECT symbol, name, company_name, sector, face_value, exchange, "
          + "       ROUND(c.last_traded_price, 2) AS ltp, ROUND(c.previous_close, 2) AS previous_close "
          + "FROM INSTRUMENT NATURAL JOIN STOCK "
          + "JOIN CURRENT_MARKET_PRICE c USING (instrument_id) "
          + "WHERE symbol = ?";

    // ---------- Subqueries: scalar, correlated, ALL ----------
    private static final String DETAIL_STATS =
            "SELECT m.change_pct, "
          + "       (SELECT COUNT(*) FROM HOLDING h WHERE h.instrument_id = m.instrument_id) AS holders, "
          + "       m.ltp > (SELECT AVG(m2.ltp) FROM v_market_movers m2 WHERE m2.sector = m.sector) "
          + "           AS above_sector_avg, "
          + "       m.change_pct >= ALL (SELECT m3.change_pct FROM v_market_movers m3 WHERE m3.sector = m.sector) "
          + "           AS sector_leader "
          + "FROM v_market_movers m "
          + "WHERE m.symbol = ?";

    // ---------- SELF JOIN: other stocks in the same sector ----------
    private static final String SIMILAR =
            "SELECT ib.symbol, ib.name, ROUND(cb.last_traded_price, 2) AS ltp "
          + "FROM STOCK sa "
          + "JOIN INSTRUMENT ia ON ia.instrument_id = sa.instrument_id "
          + "JOIN STOCK sb      ON sb.sector = sa.sector AND sb.instrument_id <> sa.instrument_id "
          + "JOIN INSTRUMENT ib ON ib.instrument_id = sb.instrument_id AND ib.status = 'ACTIVE' "
          + "LEFT JOIN CURRENT_MARKET_PRICE cb ON cb.instrument_id = sb.instrument_id "
          + "WHERE ia.symbol = ? "
          + "ORDER BY ib.symbol";

    // ---------- CROSS JOIN: my cash x every stock ----------
    private static final String AFFORDABLE =
            "SELECT m.symbol, m.name, m.ltp, FLOOR(w.available_balance / m.ltp) AS max_quantity "
          + "FROM v_market_movers m "
          + "CROSS JOIN (SELECT available_balance FROM WALLET WHERE wallet_id = ?) AS w "
          + "WHERE m.ltp > 0 "
          + "ORDER BY max_quantity DESC, m.symbol "
          + "LIMIT ?";

    // ---------- MATERIALIZED VIEW: read the stored summary ----------
    private static final String SECTOR_SUMMARY =
            "SELECT sector, stocks, avg_change_pct, gainers, losers, total_holders, total_invested, refreshed_at "
          + "FROM MV_SECTOR_SUMMARY "
          + "ORDER BY avg_change_pct DESC";

    private static final String REFRESH_SECTOR_SUMMARY =
            "CALL sp_refresh_mv_sector_summary()";

    // ---------- GROUP BY + HAVING: live sector ranking ----------
    private static final String SECTOR_PERFORMANCE =
            "SELECT sector, COUNT(*) AS stocks, ROUND(AVG(change_pct), 2) AS avg_change_pct, "
          + "       MAX(change_pct) AS best_change_pct "
          + "FROM v_market_movers "
          + "GROUP BY sector "
          + "HAVING COUNT(*) >= ? "
          + "ORDER BY avg_change_pct DESC";

    // ---------- Aggregates + scalar subquery: market breadth ----------
    private static final String BREADTH =
            "SELECT COUNT(*) AS stocks, "
          + "       SUM(change_pct > 0) AS gainers, "
          + "       SUM(change_pct < 0) AS losers, "
          + "       SUM(change_pct = 0) AS unchanged, "
          + "       ROUND(AVG(change_pct), 2) AS avg_change_pct, "
          + "       (SELECT ROUND(AVG(ltp), 2) FROM v_market_movers) AS avg_price "
          + "FROM v_market_movers";

    // ---------- Scalar subquery in WHERE ----------
    private static final String ABOVE_AVERAGE_PRICE =
            "SELECT symbol FROM v_market_movers "
          + "WHERE ltp > (SELECT AVG(ltp) FROM v_market_movers) "
          + "ORDER BY ltp DESC";

    // ---------- RIGHT JOIN: every stock, held or not ----------
    private static final String MOST_HELD =
            "SELECT i.symbol, COUNT(h.holding_id) AS holders, COALESCE(SUM(h.quantity), 0) AS shares_held "
          + "FROM HOLDING h "
          + "RIGHT JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id "
          + "WHERE i.instrument_type = 'STOCK' AND i.status = 'ACTIVE' "
          + "GROUP BY i.symbol "
          + "ORDER BY holders DESC, i.symbol "
          + "LIMIT 10";

    private final JdbcTemplate jdbc;

    public MarketQueryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SearchResult> search(String text) {
        String safe = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbc.query(SEARCH, (rs, n) -> new SearchResult(rs.getString("symbol"), rs.getString("name")),
                safe.toUpperCase() + "%", "%" + safe + "%");
    }

    public List<String> sectors() {
        return jdbc.queryForList(SECTORS, String.class);
    }

    public List<StockRow> movers(boolean gainers, int limit) {
        return jdbc.query(String.format(MOVERS, gainers ? "DESC" : "ASC"), this::mapStockRow, limit);
    }

    /** Screener. sortColumn must come from the fixed list in the controller, never from the user directly. */
    public List<StockRow> screener(List<String> sectors, BigDecimal minPrice, BigDecimal maxPrice,
                                   String sortColumn, boolean descending, int page, int size) {
        List<Object> args = new ArrayList<>();
        String sql = SCREENER_SELECT + where(sectors, minPrice, maxPrice, args)
                + " ORDER BY " + sortColumn + (descending ? " DESC" : " ASC") + ", symbol"
                + " LIMIT ? OFFSET ?";
        args.add(size);
        args.add(page * size);
        return jdbc.query(sql, this::mapStockRow, args.toArray());
    }

    public long screenerCount(List<String> sectors, BigDecimal minPrice, BigDecimal maxPrice) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject(SCREENER_COUNT + where(sectors, minPrice, maxPrice, args),
                Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    /** Builds "WHERE ltp BETWEEN ? AND ? AND sector IN (?, ?, ...)" with one ? per sector. */
    private String where(List<String> sectors, BigDecimal minPrice, BigDecimal maxPrice, List<Object> args) {
        StringBuilder sql = new StringBuilder("WHERE ltp BETWEEN ? AND ?");
        args.add(minPrice);
        args.add(maxPrice);
        if (!sectors.isEmpty()) {
            sql.append(" AND sector IN (").append(String.join(", ", Collections.nCopies(sectors.size(), "?"))).append(")");
            args.addAll(sectors);
        }
        return sql.toString();
    }

    public Optional<StockDetail> detail(String symbol) {
        List<Map<String, Object>> facts = jdbc.queryForList(DETAIL, symbol.toUpperCase());
        List<Map<String, Object>> stats = jdbc.queryForList(DETAIL_STATS, symbol.toUpperCase());
        if (facts.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> f = facts.get(0);
        Map<String, Object> s = stats.isEmpty() ? Map.of() : stats.get(0);
        return Optional.of(new StockDetail(
                (String) f.get("symbol"),
                (String) f.get("name"),
                (String) f.get("company_name"),
                (String) f.get("sector"),
                (BigDecimal) f.get("face_value"),
                (String) f.get("exchange"),
                (BigDecimal) f.get("ltp"),
                (BigDecimal) f.get("previous_close"),
                (BigDecimal) s.get("change_pct"),
                toLong(s.get("holders")),
                toLong(s.get("above_sector_avg")) == 1,
                toLong(s.get("sector_leader")) == 1));
    }

    public List<SimilarStock> similar(String symbol) {
        return jdbc.query(SIMILAR, (rs, n) -> new SimilarStock(
                rs.getString("symbol"), rs.getString("name"), rs.getBigDecimal("ltp")), symbol.toUpperCase());
    }

    public List<AffordableStock> affordable(UUID walletId, int limit) {
        return jdbc.query(AFFORDABLE, (rs, n) -> new AffordableStock(
                rs.getString("symbol"), rs.getString("name"), rs.getBigDecimal("ltp"), rs.getLong("max_quantity")),
                UuidUtil.toBytes(walletId), limit);
    }

    public List<SectorSummary> sectorSummary() {
        return jdbc.query(SECTOR_SUMMARY, (rs, n) -> new SectorSummary(
                rs.getString("sector"),
                rs.getInt("stocks"),
                rs.getBigDecimal("avg_change_pct"),
                rs.getInt("gainers"),
                rs.getInt("losers"),
                rs.getInt("total_holders"),
                rs.getBigDecimal("total_invested"),
                rs.getObject("refreshed_at", java.time.LocalDateTime.class)));
    }

    public void refreshSectorSummary() {
        jdbc.execute(REFRESH_SECTOR_SUMMARY);
    }

    public List<SectorPerformance> sectorPerformance(int minStocks) {
        return jdbc.query(SECTOR_PERFORMANCE, (rs, n) -> new SectorPerformance(
                rs.getString("sector"),
                rs.getLong("stocks"),
                rs.getBigDecimal("avg_change_pct"),
                rs.getBigDecimal("best_change_pct")), minStocks);
    }

    public MarketOverview overview() {
        Map<String, Object> b = jdbc.queryForMap(BREADTH);
        List<String> above = jdbc.queryForList(ABOVE_AVERAGE_PRICE, String.class);
        List<MostHeld> mostHeld = jdbc.query(MOST_HELD, (rs, n) -> new MostHeld(
                rs.getString("symbol"), rs.getLong("holders"), rs.getLong("shares_held")));
        return new MarketOverview(
                toLong(b.get("stocks")),
                toLong(b.get("gainers")),
                toLong(b.get("losers")),
                toLong(b.get("unchanged")),
                (BigDecimal) b.get("avg_change_pct"),
                (BigDecimal) b.get("avg_price"),
                above,
                mostHeld);
    }

    private StockRow mapStockRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new StockRow(
                UuidUtil.fromBytes(rs.getBytes("instrument_id")),
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("sector"),
                rs.getBigDecimal("ltp"),
                rs.getBigDecimal("previous_close"),
                rs.getBigDecimal("change_amount"),
                rs.getBigDecimal("change_pct"));
    }

    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}