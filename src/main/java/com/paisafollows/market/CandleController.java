package com.paisafollows.market;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.market.CandleViews.CandleSeries;

@RestController
public class CandleController {

    /** The ONLY intervals allowed: name -> seconds per candle */
    private static final Map<String, Integer> SECONDS = Map.of(
            "1m", 60, "5m", 300, "15m", 900, "1h", 3600, "1d", 86400);

    /** How far back to look when ?hours is not given */
    private static final Map<String, Integer> DEFAULT_HOURS = Map.of(
            "1m", 2, "5m", 8, "15m", 24, "1h", 168, "1d", 2160);

    private static final int MAX_HOURS = 2160;   // 90 days

    private final CandleDao dao;

    public CandleController(CandleDao dao) {
        this.dao = dao;
    }

    /** GET /api/candles/RELIANCE?interval=5m&hours=8 */
    @GetMapping("/api/candles/{symbol}")
    public CandleSeries candles(@PathVariable String symbol,
                                @RequestParam(defaultValue = "1m") String interval,
                                @RequestParam(required = false) Integer hours) {
        String s = symbol.toUpperCase();
        Integer seconds = SECONDS.get(interval);
        if (seconds == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "interval must be one of 1m, 5m, 15m, 1h, 1d");
        }
        if (!dao.exists(s)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + s);
        }
        int h = hours == null ? DEFAULT_HOURS.get(interval) : Math.min(Math.max(hours, 1), MAX_HOURS);
        return new CandleSeries(s, interval, h, dao.candles(s, seconds, h));
    }
}