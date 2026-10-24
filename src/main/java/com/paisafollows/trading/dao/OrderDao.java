package com.paisafollows.trading.dao;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.trading.Order;
import com.paisafollows.trading.OrderStatus;
import com.paisafollows.trading.OrderType;
import com.paisafollows.trading.Side;

@Repository
public class OrderDao {

    private static final String INSERT_ORDER =
            "INSERT INTO orders (order_id, account_id, portfolio_id, instrument_id, order_type, transaction_type, "
          + "                    quantity, price, trigger_price, status, reject_reason) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_ORDER =
            "SELECT order_id, account_id, portfolio_id, instrument_id, order_type, transaction_type, quantity, "
          + "       price, trigger_price, status, fill_price, reject_reason, created_at, filled_at "
          + "FROM orders ";

    private static final String FIND_BY_ID = SELECT_ORDER + "WHERE order_id = ?";

    private static final String LOCK_BY_ID = FIND_BY_ID + " FOR UPDATE";

    private static final String FIND_BY_ACCOUNT =
            SELECT_ORDER + "WHERE account_id = ? ORDER BY created_at DESC LIMIT 100";

    private static final String FIND_BY_ACCOUNT_AND_STATUS =
            SELECT_ORDER + "WHERE account_id = ? AND status = ? ORDER BY created_at DESC";

    /** Only an OPEN order can be filled (protects against filling twice) */
    private static final String MARK_FILLED =
            "UPDATE orders SET status = 'FILLED', fill_price = ?, filled_at = NOW() "
          + "WHERE order_id = ? AND status = 'OPEN'";

    /** OPEN -> CANCELLED or EXPIRED */
    private static final String MARK_CLOSED =
            "UPDATE orders SET status = ? WHERE order_id = ? AND status = 'OPEN'";

    /** Shares already promised to waiting sell orders (cannot be sold twice) */
    private static final String OPEN_SELL_QUANTITY =
            "SELECT COALESCE(SUM(quantity), 0) FROM orders "
          + "WHERE portfolio_id = ? AND instrument_id = ? AND transaction_type = 'SELL' AND status = 'OPEN'";

    private final JdbcTemplate jdbc;

    public OrderDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Order o) {
        jdbc.update(INSERT_ORDER,
                UuidUtil.toBytes(o.orderId()), UuidUtil.toBytes(o.accountId()), UuidUtil.toBytes(o.portfolioId()),
                UuidUtil.toBytes(o.instrumentId()), o.orderType().name(), o.side().name(), o.quantity(),
                o.price(), o.triggerPrice(), o.status().name(), o.rejectReason());
    }

    public Optional<Order> findById(UUID orderId) {
        return jdbc.query(FIND_BY_ID, this::map, UuidUtil.toBytes(orderId)).stream().findFirst();
    }

    /** Lock order: wallet first, then this. */
    public Optional<Order> lock(UUID orderId) {
        return jdbc.query(LOCK_BY_ID, this::map, UuidUtil.toBytes(orderId)).stream().findFirst();
    }

    public List<Order> findByAccount(UUID accountId) {
        return jdbc.query(FIND_BY_ACCOUNT, this::map, UuidUtil.toBytes(accountId));
    }

    public List<Order> findByAccountAndStatus(UUID accountId, OrderStatus status) {
        return jdbc.query(FIND_BY_ACCOUNT_AND_STATUS, this::map, UuidUtil.toBytes(accountId), status.name());
    }

    /** @return false if the order was no longer OPEN */
    public boolean markFilled(UUID orderId, BigDecimal fillPrice) {
        return jdbc.update(MARK_FILLED, fillPrice, UuidUtil.toBytes(orderId)) == 1;
    }

    public boolean markClosed(UUID orderId, OrderStatus newStatus) {
        return jdbc.update(MARK_CLOSED, newStatus.name(), UuidUtil.toBytes(orderId)) == 1;
    }

    public int openSellQuantity(UUID portfolioId, UUID instrumentId) {
        Integer qty = jdbc.queryForObject(OPEN_SELL_QUANTITY, Integer.class,
                UuidUtil.toBytes(portfolioId), UuidUtil.toBytes(instrumentId));
        return qty == null ? 0 : qty;
    }

    /** Turns one result row into an Order. */
    private Order map(ResultSet rs, int rowNum) throws SQLException {
        return new Order(
                UuidUtil.fromBytes(rs.getBytes("order_id")),
                UuidUtil.fromBytes(rs.getBytes("account_id")),
                UuidUtil.fromBytes(rs.getBytes("portfolio_id")),
                UuidUtil.fromBytes(rs.getBytes("instrument_id")),
                OrderType.valueOf(rs.getString("order_type")),
                Side.valueOf(rs.getString("transaction_type")),
                rs.getInt("quantity"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("trigger_price"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("fill_price"),
                rs.getString("reject_reason"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("filled_at", LocalDateTime.class));
    }
}