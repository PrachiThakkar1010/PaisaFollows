package com.paisafollows.trading;

import java.math.BigDecimal;
import java.util.UUID;

/** "Something happened to this waiting order". Published inside the transaction, delivered after COMMIT. */
public record OrderEvent(
        UUID orderId,
        Side side,
        OrderType type,
        int quantity,
        OrderStatus status,
        BigDecimal fillPrice,    // null unless FILLED
        String message
) {
}