package com.paisafollows.trading;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** One stock in a basket: always a MARKET order. */
public record BasketLeg(
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull @Positive @Max(100000) Integer quantity
) {
}