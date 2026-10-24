package com.paisafollows.market;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.paisafollows.common.Tick;

/** A price plus how much it moved since the previous close. Sent over WebSocket and by /api/quotes. */
public record Quote(
        String symbol,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePct,
        boolean marketOpen,
        long timestamp
) {
    public static Quote of(Tick tick, BigDecimal previousClose, boolean marketOpen) {
        BigDecimal change = tick.price().subtract(previousClose);
        BigDecimal pct = previousClose.signum() == 0
                ? BigDecimal.ZERO
                : change.multiply(BigDecimal.valueOf(100)).divide(previousClose, 2, RoundingMode.HALF_UP);
        return new Quote(tick.symbol(), tick.price(), previousClose, change, pct,
                marketOpen, tick.timestamp().toEpochMilli());
    }
}