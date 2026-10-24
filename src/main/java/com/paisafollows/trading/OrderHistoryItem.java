package com.paisafollows.trading;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderHistoryItem(
        UUID orderId,
        String symbol,
        OrderType type,
        Side side,
        int quantity,
        BigDecimal price,
        BigDecimal triggerPrice,
        OrderStatus status,
        BigDecimal fillPrice,
        BigDecimal realisedPnl,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime filledAt
) {
}