package com.paisafollows.trading;

import java.math.BigDecimal;
import java.util.UUID;

/** One waiting order in ORDER_BOOK. price = limit price, or trigger price for STOP_LOSS. */
public record BookEntry(UUID orderId, UUID instrumentId, OrderType type, Side side, int quantity, BigDecimal price) {
}