package com.paisafollows.market;

import java.math.BigDecimal;
import java.util.List;

/** Reply shapes for GET /api/candles/{symbol} */
public final class CandleViews {

    private CandleViews() {
    }

    /**
     * One candle. time = start of the period in Unix seconds (what chart libraries expect);
     * label = the same moment as readable IST text.
     */
    public record Candle(long time, String label, BigDecimal open, BigDecimal high,
                         BigDecimal low, BigDecimal close, long ticks) {
    }

    public record CandleSeries(String symbol, String interval, int hours, List<Candle> candles) {
    }
}