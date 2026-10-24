package com.paisafollows.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One price update for one stock. */
public record Tick(UUID instrumentId, String symbol, BigDecimal price, Instant timestamp) {
}