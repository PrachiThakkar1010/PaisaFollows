package com.paisafollows.trading.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.trading.BookEntry;
import com.paisafollows.trading.OrderType;
import com.paisafollows.trading.Side;

/** ORDER_BOOK = orders waiting for the price to reach them (limit and stop-loss). */
@Repository
public class OrderBookDao {

    private static final String INSERT_ENTRY =
            "INSERT INTO ORDER_BOOK (order_book_id, order_id, instrument_id, side, quantity, price) "
          + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String DELETE_BY_ORDER =
            "DELETE FROM ORDER_BOOK WHERE order_id = ?";

    /** Every waiting order, with its type from the orders table (needed to know limit vs stop-loss). */
    private static final String FIND_ALL_OPEN =
            "SELECT b.order_id, b.instrument_id, o.order_type, b.side, b.quantity, b.price "
          + "FROM ORDER_BOOK b "
          + "JOIN orders o ON o.order_id = b.order_id "
          + "WHERE o.status = 'OPEN'";

    private final JdbcTemplate jdbc;

    public OrderBookDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(UUID orderId, UUID instrumentId, Side side, int quantity, BigDecimal price) {
        jdbc.update(INSERT_ENTRY, UuidUtil.toBytes(UuidUtil.newId()), UuidUtil.toBytes(orderId),
                UuidUtil.toBytes(instrumentId), side.name(), quantity, price);
    }

    public void remove(UUID orderId) {
        jdbc.update(DELETE_BY_ORDER, UuidUtil.toBytes(orderId));
    }

    public List<BookEntry> findAll() {
        return jdbc.query(FIND_ALL_OPEN, (rs, n) -> new BookEntry(
                UuidUtil.fromBytes(rs.getBytes("order_id")),
                UuidUtil.fromBytes(rs.getBytes("instrument_id")),
                OrderType.valueOf(rs.getString("order_type")),
                Side.valueOf(rs.getString("side")),
                rs.getInt("quantity"),
                rs.getBigDecimal("price")));
    }
}