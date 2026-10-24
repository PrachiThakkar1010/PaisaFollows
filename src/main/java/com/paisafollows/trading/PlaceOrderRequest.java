package com.paisafollows.trading;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** JSON body of POST /api/orders */
public record PlaceOrderRequest(
        @NotBlank String symbol,
        @NotNull Side side,                       // BUY or SELL
        @NotNull OrderType type,                  // MARKET now; LIMIT and STOP_LOSS in Module 9
        @NotNull @Positive @Max(100000) Integer quantity,
        @Positive BigDecimal price,               // limit price (Module 9)
        @Positive BigDecimal triggerPrice         // stop-loss trigger (Module 9)
) {
}