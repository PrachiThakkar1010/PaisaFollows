package com.paisafollows.trading;

import java.math.BigDecimal;
import java.util.UUID;

/** Reply to POST /api/orders */
public record OrderResponse(
        UUID orderId,
        String symbol,
        Side side,
        OrderType type,
        int quantity,
        OrderStatus status,
        BigDecimal fillPrice,
        BigDecimal totalValue,      // quantity x fill price
        BigDecimal realisedPnl,     // only for sells
        String message
) {
}