package com.paisafollows.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.admin.AdminViews.ArchiveInfo;
import com.paisafollows.admin.AdminViews.LeaderboardEntry;

/** SQL for admin maintenance. Table names built here come only from code, never from user input. */
@Repository
public class AdminDao {

    /** Only names like MARKET_PRICE_HISTORY_20261025_023000 are archives */
    public static final Pattern ARCHIVE_NAME = Pattern.compile("^MARKET_PRICE_HISTORY_[0-9]{8}_[0-9]{6}$");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ---------- UPDATE with cascade ----------
    private static final String RENAME_SYMBOL =
            "UPDATE INSTRUMENT SET symbol = ? WHERE symbol = ?";

    private static final String COUNT_ALERTS_FOR_SYMBOL =
            "SELECT COUNT(*) FROM PRICE_ALERT WHERE symbol = ?";

    // ---------- DELETE without cascade (expected to be refused) ----------
    private static final String DELETE_INSTRUMENT =
            "DELETE FROM INSTRUMENT WHERE symbol = ?";

    private static final String SET_STATUS =
            "UPDATE INSTRUMENT SET status = ? WHERE symbol = ?";

    // ---------- Archive: CREATE ... LIKE, ALTER, RENAME (each DDL statement commits on its own) ----------
    private static final String DROP_LEFTOVER_NEW =
            "DROP TABLE IF EXISTS MARKET_PRICE_HISTORY_NEW";

    private static final String CREATE_NEW_LIKE =
            "CREATE TABLE MARKET_PRICE_HISTORY_NEW LIKE MARKET_PRICE_HISTORY";

    /** CREATE ... LIKE copies columns, keys and indexes but NOT foreign keys, so add it back */
    private static final String ADD_FK_TO_NEW =
            "ALTER TABLE MARKET_PRICE_HISTORY_NEW "
          + "ADD FOREIGN KEY (instrument_id) REFERENCES INSTRUMENT (instrument_id)";

    /** Both renames happen together, atomically: there is never a moment with no history table */
    private static final String SWAP_TABLES =
            "RENAME TABLE MARKET_PRICE_HISTORY TO %s, MARKET_PRICE_HISTORY_NEW TO MARKET_PRICE_HISTORY";

    private static final String LIST_ARCHIVES =
            "SELECT table_name FROM information_schema.tables "
          + "WHERE table_schema = DATABASE() "
          + "  AND table_name REGEXP '^MARKET_PRICE_HISTORY_[0-9]{8}_[0-9]{6}$' "
          + "ORDER BY table_name DESC";

    private static final String COUNT_ROWS = "SELECT COUNT(*) FROM %s";

    // ---------- DROP and TRUNCATE ----------
    private static final String DROP_ARCHIVE = "DROP TABLE %s";

    private static final String TRUNCATE_HISTORY = "TRUNCATE TABLE MARKET_PRICE_HISTORY";

    // ---------- Leaderboard: derived table + function + subquery ----------
    private static final String LEADERBOARD =
            "SELECT ranked.name, ranked.total_value, ranked.return_pct "
          + "FROM ( "
          + "    SELECT u.name, "
          + "           w.available_balance + w.reserved_balance + fn_portfolio_value(p.portfolio_id) AS total_value, "
          + "           ROUND((w.available_balance + w.reserved_balance + fn_portfolio_value(p.portfolio_id) - d.deposits) "
          + "                 * 100 / NULLIF(d.deposits, 0), 2) AS return_pct "
          + "    FROM `USER` u "
          + "    JOIN ACCOUNT a   ON a.user_id    = u.user_id "
          + "    JOIN WALLET w    ON w.account_id = a.account_id "
          + "    JOIN PORTFOLIO p ON p.account_id = a.account_id AND p.name = 'Main' "
          + "    JOIN (SELECT wallet_id, SUM(amount) AS deposits "
          + "          FROM WALLET_TRANSACTION WHERE transaction_type = 'DEPOSIT' "
          + "          GROUP BY wallet_id) AS d ON d.wallet_id = w.wallet_id "
          + ") AS ranked "
          + "ORDER BY ranked.total_value DESC "
          + "LIMIT ?";

    private final JdbcTemplate jdbc;

    public AdminDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int renameSymbol(String oldSymbol, String newSymbol) {
        return jdbc.update(RENAME_SYMBOL, newSymbol, oldSymbol);
    }

    public long alertsFor(String symbol) {
        Long n = jdbc.queryForObject(COUNT_ALERTS_FOR_SYMBOL, Long.class, symbol);
        return n == null ? 0 : n;
    }

    /** Throws DataIntegrityViolationException when other rows still point to this stock. */
    public int deleteInstrument(String symbol) {
        return jdbc.update(DELETE_INSTRUMENT, symbol);
    }

    public int setStatus(String symbol, String status) {
        return jdbc.update(SET_STATUS, status, symbol);
    }

    /** Swaps in an empty history table and keeps the old one under a dated name. Returns that name. */
    public String archiveHistory() {
        String archive = "MARKET_PRICE_HISTORY_" + LocalDateTime.now().format(STAMP);
        jdbc.execute(DROP_LEFTOVER_NEW);
        jdbc.execute(CREATE_NEW_LIKE);
        jdbc.execute(ADD_FK_TO_NEW);
        jdbc.execute(String.format(SWAP_TABLES, archive));
        return archive;
    }

    public List<ArchiveInfo> listArchives() {
        return jdbc.queryForList(LIST_ARCHIVES, String.class).stream()
                .map(name -> new ArchiveInfo(name, countRows(name)))
                .toList();
    }

    public long countRows(String table) {
        if (!table.equals("MARKET_PRICE_HISTORY") && !ARCHIVE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a price-history table: " + table);
        }
        Long n = jdbc.queryForObject(String.format(COUNT_ROWS, table), Long.class);
        return n == null ? 0 : n;
    }

    public void dropArchive(String table) {
        if (!ARCHIVE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Not an archive table: " + table);
        }
        jdbc.execute(String.format(DROP_ARCHIVE, table));
    }

    public void truncateHistory() {
        jdbc.execute(TRUNCATE_HISTORY);
    }

    public List<LeaderboardEntry> leaderboard(int limit) {
        int[] rank = {0};
        return jdbc.query(LEADERBOARD, (rs, n) -> new LeaderboardEntry(
                ++rank[0],
                rs.getString("name"),
                rs.getBigDecimal("total_value").setScale(2, java.math.RoundingMode.HALF_UP),
                rs.getBigDecimal("return_pct") == null ? BigDecimal.ZERO : rs.getBigDecimal("return_pct")),
                limit);
    }
}