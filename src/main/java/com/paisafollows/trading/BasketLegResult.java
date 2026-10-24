package com.paisafollows.trading;

/** What happened to one leg: FILLED, ROLLED_BACK (to its savepoint) or SKIPPED (unknown stock). */
public record BasketLegResult(
        int leg,
        String symbol,
        Side side,
        int quantity,
        String outcome,
        String savepoint,
        OrderResponse order,
        String reason
) {
}