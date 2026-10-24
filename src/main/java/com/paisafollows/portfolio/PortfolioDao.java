package com.paisafollows.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;

/** All SQL for the portfolio, statement and balance-history screens. */
@Repository
public class PortfolioDao {

    /** VIEW: holdings with live value, biggest first */
    private static final String FIND_HOLDINGS =
            "SELECT instrument_id, symbol, name, sector, quantity, average_buy_price, ltp, previous_close, "
          + "       invested, current_value, unrealised_pnl, pnl_pct, day_change "
          + "FROM v_portfolio_holdings "
          + "WHERE portfolio_id = ? "
          + "ORDER BY current_value DESC";

    /** AGGREGATES over the view */
    private static final String HOLDING_TOTALS =
            "SELECT COALESCE(SUM(invested), 0)       AS invested, "
          + "       COALESCE(SUM(unrealised_pnl), 0) AS unrealised_pnl, "
          + "       COALESCE(SUM(day_change), 0)     AS day_change "
          + "FROM v_portfolio_holdings "
          + "WHERE portfolio_id = ?";

    /** FUNCTION */
    private static final String PORTFOLIO_VALUE =
            "SELECT fn_portfolio_value(?)";

    /** Profit/loss already booked: sum over this portfolio's sell trades */
    private static final String REALISED_PNL =
            "SELECT COALESCE(SUM(t.realised_pnl), 0) "
          + "FROM TRADE t "
          + "JOIN orders o ON o.order_id = t.sell_order_id "
          + "WHERE o.portfolio_id = ?";

    private static final String WALLET_BALANCE =
            "SELECT available_balance, reserved_balance FROM WALLET WHERE wallet_id = ?";

    /** STORED PROCEDURE */
    private static final String CALL_STATEMENT =
            "CALL sp_account_statement(?, ?, ?)";

    /** Rows written by the TRIGGER, newest first */
    private static final String BALANCE_HISTORY =
            "SELECT old_available, new_available, old_reserved, new_reserved, changed_at "
          + "FROM WALLET_AUDIT "
          + "WHERE wallet_id = ? "
          + "ORDER BY audit_id DESC "
          + "LIMIT ?";

    private final JdbcTemplate jdbc;

    public PortfolioDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HoldingView> findHoldings(UUID portfolioId) {
        return jdbc.query(FIND_HOLDINGS, (rs, n) -> new HoldingView(
                UuidUtil.fromBytes(rs.getBytes("instrument_id")),
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("sector"),
                rs.getInt("quantity"),
                rs.getBigDecimal("average_buy_price"),
                rs.getBigDecimal("ltp"),
                rs.getBigDecimal("previous_close"),
                rs.getBigDecimal("invested"),
                rs.getBigDecimal("current_value"),
                rs.getBigDecimal("unrealised_pnl"),
                rs.getBigDecimal("pnl_pct"),
                rs.getBigDecimal("day_change")), UuidUtil.toBytes(portfolioId));
    }

    public Map<String, Object> holdingTotals(UUID portfolioId) {
        return jdbc.queryForMap(HOLDING_TOTALS, UuidUtil.toBytes(portfolioId));
    }

    public BigDecimal portfolioValue(UUID portfolioId) {
        return jdbc.queryForObject(PORTFOLIO_VALUE, BigDecimal.class, UuidUtil.toBytes(portfolioId));
    }

    public BigDecimal realisedPnl(UUID portfolioId) {
        return jdbc.queryForObject(REALISED_PNL, BigDecimal.class, UuidUtil.toBytes(portfolioId));
    }

    public Map<String, Object> walletBalance(UUID walletId) {
        return jdbc.queryForMap(WALLET_BALANCE, UuidUtil.toBytes(walletId));
    }

    public List<StatementEntry> statement(UUID walletId, LocalDate from, LocalDate to) {
        return jdbc.query(CALL_STATEMENT, (rs, n) -> new StatementEntry(
                rs.getString("transaction_type"),
                rs.getString("flow"),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getObject("created_at", LocalDateTime.class)), UuidUtil.toBytes(walletId), from, to);
    }

    public List<BalanceChange> balanceHistory(UUID walletId, int limit) {
        return jdbc.query(BALANCE_HISTORY, (rs, n) -> new BalanceChange(
                rs.getBigDecimal("old_available"),
                rs.getBigDecimal("new_available"),
                rs.getBigDecimal("old_reserved"),
                rs.getBigDecimal("new_reserved"),
                rs.getObject("changed_at", LocalDateTime.class)), UuidUtil.toBytes(walletId), limit);
    }
}