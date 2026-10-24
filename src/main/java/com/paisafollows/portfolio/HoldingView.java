package com.paisafollows.portfolio;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of v_portfolio_holdings. */
public record HoldingView(
        UUID instrumentId,
        String symbol,
        String name,
        String sector,
        int quantity,
        BigDecimal averageBuyPrice,
        BigDecimal ltp,
        BigDecimal previousClose,
        BigDecimal invested,
        BigDecimal currentValue,
        BigDecimal unrealisedPnl,
        BigDecimal pnlPct,
        BigDecimal dayChange
) {
}
