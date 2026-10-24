package com.paisafollows.portfolio;

import java.math.BigDecimal;
import java.util.List;

/** Reply of GET /api/portfolio */
public record PortfolioSummary(
        BigDecimal availableCash,
        BigDecimal reservedCash,
        BigDecimal invested,         // what the current holdings cost
        BigDecimal holdingsValue,    // what they are worth now
        BigDecimal unrealisedPnl,    // holdingsValue - invested
        BigDecimal realisedPnl,      // profit/loss already booked by sells
        BigDecimal dayChange,        // change since the previous close
        BigDecimal totalValue,       // cash + reserved + holdings
        List<HoldingView> holdings
) {
}