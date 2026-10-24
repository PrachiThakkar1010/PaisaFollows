package com.paisafollows.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.alert.AlertViews.AlertView;
import com.paisafollows.common.UuidUtil;

/** All price-alert SQL. Firing alerts is NOT here: the trigger trg_price_alert_fire does it. */
@Repository
public class AlertDao {

    /** INSERT ... SELECT: only an existing, ACTIVE stock can get an alert */
    private static final String CREATE_ALERT =
            "INSERT INTO PRICE_ALERT (alert_id, account_id, symbol, direction, target_price) "
          + "SELECT ?, ?, symbol, ?, ? FROM INSTRUMENT WHERE symbol = ? AND status = 'ACTIVE'";

    /** My alerts with the current price (LEFT JOIN: a stock may have no price yet) */
    private static final String FIND_ALERTS =
            "SELECT pa.alert_id, pa.symbol, pa.direction, pa.target_price, "
          + "       ROUND(c.last_traded_price, 2) AS current_price, "
          + "       pa.status, pa.triggered_price, pa.triggered_at, pa.created_at "
          + "FROM PRICE_ALERT pa "
          + "JOIN INSTRUMENT i ON i.symbol = pa.symbol "
          + "LEFT JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id "
          + "WHERE pa.account_id = ? AND (? IS NULL OR pa.status = ?) "
          + "ORDER BY pa.status, pa.created_at DESC";

    /** UPDATE without cascade: change the target of one of MY ACTIVE alerts */
    private static final String UPDATE_TARGET =
            "UPDATE PRICE_ALERT SET target_price = ? "
          + "WHERE alert_id = ? AND account_id = ? AND status = 'ACTIVE'";

    private static final String DELETE_ALERT =
            "DELETE FROM PRICE_ALERT WHERE alert_id = ? AND account_id = ?";

    private final JdbcTemplate jdbc;

    public AlertDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the new id, or null if the symbol does not exist (a duplicate throws DuplicateKeyException) */
    public UUID create(UUID accountId, String symbol, String direction, BigDecimal target) {
        UUID id = UuidUtil.newId();
        int rows = jdbc.update(CREATE_ALERT, UuidUtil.toBytes(id), UuidUtil.toBytes(accountId),
                direction, target, symbol.trim().toUpperCase());
        return rows == 1 ? id : null;
    }

    public List<AlertView> findAll(UUID accountId, String status) {
        return jdbc.query(FIND_ALERTS, (rs, n) -> new AlertView(
                UuidUtil.fromBytes(rs.getBytes("alert_id")),
                rs.getString("symbol"),
                rs.getString("direction"),
                rs.getBigDecimal("target_price"),
                rs.getBigDecimal("current_price"),
                rs.getString("status"),
                rs.getBigDecimal("triggered_price"),
                rs.getObject("triggered_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)),
                UuidUtil.toBytes(accountId), status, status);
    }

    public int updateTarget(UUID alertId, UUID accountId, BigDecimal target) {
        return jdbc.update(UPDATE_TARGET, target, UuidUtil.toBytes(alertId), UuidUtil.toBytes(accountId));
    }

    public int delete(UUID alertId, UUID accountId) {
        return jdbc.update(DELETE_ALERT, UuidUtil.toBytes(alertId), UuidUtil.toBytes(accountId));
    }
}