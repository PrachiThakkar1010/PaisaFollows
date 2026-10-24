package com.paisafollows.trading;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record Order(
        UUID orderId,
        UUID accountId,
        UUID portfolioId,
        UUID instrumentId,
        OrderType orderType,
        Side side,
        int quantity,
        BigDecimal price,          // limit price (null for MARKET)
        BigDecimal triggerPrice,   // stop-loss trigger (null otherwise)
        OrderStatus status,
        BigDecimal fillPrice,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime filledAt
) {
}