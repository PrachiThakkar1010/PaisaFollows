package com.paisafollows.trading.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.trading.OrderHistoryItem;
import com.paisafollows.trading.OrderStatus;
import com.paisafollows.trading.OrderType;
import com.paisafollows.trading.Side;

@Repository
public class OrderHistoryDao {

    /** One page of a user's orders, newest first, with the stock symbol and the profit of sells. */
    private static final String FIND_PAGE =
            "SELECT o.order_id, i.symbol, o.order_type, o.transaction_type, o.quantity, o.price, "
          + "       o.trigger_price, o.status, o.fill_price, t.realised_pnl, o.reject_reason, "
          + "       o.created_at, o.filled_at "
          + "FROM orders o "
          + "JOIN INSTRUMENT i  ON i.instrument_id  = o.instrument_id "
          + "LEFT JOIN TRADE t  ON t.sell_order_id  = o.order_id "
          + "WHERE o.account_id = ? AND (? IS NULL OR o.status = ?) "
          + "ORDER BY o.created_at DESC "
          + "LIMIT ? OFFSET ?";

    private static final String COUNT =
            "SELECT COUNT(*) FROM orders o WHERE o.account_id = ? AND (? IS NULL OR o.status = ?)";

    private final JdbcTemplate jdbc;

    public OrderHistoryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OrderHistoryItem> findPage(UUID accountId, OrderStatus status, int page, int size) {
        String s = status == null ? null : status.name();
        return jdbc.query(FIND_PAGE, (rs, n) -> new OrderHistoryItem(
                        UuidUtil.fromBytes(rs.getBytes("order_id")),
                        rs.getString("symbol"),
                        OrderType.valueOf(rs.getString("order_type")),
                        Side.valueOf(rs.getString("transaction_type")),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("trigger_price"),
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("fill_price"),
                        rs.getBigDecimal("realised_pnl"),
                        rs.getString("reject_reason"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("filled_at", LocalDateTime.class)),
                UuidUtil.toBytes(accountId), s, s, size, page * size);
    }

    public long count(UUID accountId, OrderStatus status) {
        String s = status == null ? null : status.name();
        Long total = jdbc.queryForObject(COUNT, Long.class, UuidUtil.toBytes(accountId), s, s);
        return total == null ? 0 : total;
    }
}