package com.paisafollows.market;

import java.util.UUID;

/** One tradable stock, as stored in INSTRUMENT. */
public record Instrument(UUID id, String symbol, String name) {
}