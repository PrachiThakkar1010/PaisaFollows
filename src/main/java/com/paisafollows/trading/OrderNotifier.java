package com.paisafollows.trading;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.paisafollows.common.UuidUtil;

/** Pushes order updates to the owner's browser, but only after the database change is COMMITTED. */
@Component
public class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Who owns this order, and which stock is it? (4-table join) */
    private static final String FIND_OWNER_AND_SYMBOL =
            "SELECT u.email, i.symbol "
          + "FROM orders o "
          + "JOIN ACCOUNT a    ON a.account_id    = o.account_id "
          + "JOIN `USER` u     ON u.user_id       = a.user_id "
          + "JOIN INSTRUMENT i ON i.instrument_id = o.instrument_id "
          + "WHERE o.order_id = ?";

    /** What the browser receives on /user/queue/orders */
    public record OrderNotification(UUID orderId, String symbol, Side side, OrderType type, int quantity,
                                    OrderStatus status, BigDecimal fillPrice, String message, String at) {
    }

    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate messaging;

    public OrderNotifier(JdbcTemplate jdbc, SimpMessagingTemplate messaging) {
        this.jdbc = jdbc;
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderEvent(OrderEvent e) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(FIND_OWNER_AND_SYMBOL, UuidUtil.toBytes(e.orderId()));
            if (rows.isEmpty()) {
                return;
            }
            String email = (String) rows.get(0).get("email");
            String symbol = (String) rows.get(0).get("symbol");
            OrderNotification n = new OrderNotification(e.orderId(), symbol, e.side(), e.type(), e.quantity(),
                    e.status(), e.fillPrice(), e.message(), LocalDateTime.now(IST).format(TIME));

            // Delivered to /user/queue/orders of every browser tab logged in with this email
            messaging.convertAndSendToUser(email, "/queue/orders", n);
        } catch (RuntimeException ex) {
            // A failed notification must never affect trading; the order is already saved
            log.warn("Could not notify about order {}: {}", e.orderId(), ex.getMessage());
        }
    }
}