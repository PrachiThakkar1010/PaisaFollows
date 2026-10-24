package com.paisafollows.trading;

import java.util.UUID;

/** What trading needs to know about a stock before accepting an order. */
public record InstrumentRef(UUID id, String symbol, String name, String type, boolean active) {

    /** Which ACCOUNT_SEGMENT is needed to trade this kind of instrument. */
    public String requiredSegment() {
        return switch (type) {
            case "COMMODITY" -> "COMMODITY";
            case "FUTURE", "OPTION" -> "F_AND_O";
            default -> "EQUITY";   // STOCK, ETF, INDEX, MF, IPO
        };
    }
}