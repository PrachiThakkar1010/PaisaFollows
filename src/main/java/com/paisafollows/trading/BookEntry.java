package com.paisafollows.trading;

import java.math.BigDecimal;
import java.util.UUID;

/** One waiting order in ORDER_BOOK. */
public record BookEntry(UUID orderId, UUID instrumentId, Side side, int quantity, BigDecimal price) {
}