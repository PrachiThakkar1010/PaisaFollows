package com.paisafollows.market;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.market.CandleViews.Candle;

@Repository
public class CandleDao {

    private static final String STOCK_EXISTS =
            "SELECT COUNT(*) FROM INSTRUMENT WHERE symbol = ?";

    /**
     * Groups price snapshots into time buckets and summarises each bucket as a candle.
     * Inner query (derived table b): which bucket each snapshot falls into.
     * Outer query: GROUP BY bucket -> open (first), high (MAX), low (MIN), close (last), count.
     * +19800 / -19800 = 5 h 30 min, so hourly and daily buckets start on IST boundaries, not UTC.
     */
    private static final String CANDLES =
            "SELECT b.bucket, "
          + "       FROM_UNIXTIME(b.bucket, '%Y-%m-%d %H:%i') AS label, "
          + "       CAST(SUBSTRING_INDEX(GROUP_CONCAT(b.price ORDER BY b.ts ASC),  ',', 1) AS DECIMAL(18,2)) AS open_price, "
          + "       ROUND(MAX(b.price), 2) AS high_price, "
          + "       ROUND(MIN(b.price), 2) AS low_price, "
          + "       CAST(SUBSTRING_INDEX(GROUP_CONCAT(b.price ORDER BY b.ts DESC), ',', 1) AS DECIMAL(18,2)) AS close_price, "
          + "       COUNT(*) AS ticks "
          + "FROM ( "
          + "    SELECT FLOOR((UNIX_TIMESTAMP(h.exchange_timestamp) + 19800) / ?) * ? - 19800 AS bucket, "
          + "           h.exchange_timestamp AS ts, "
          + "           h.last_traded_price  AS price "
          + "    FROM MARKET_PRICE_HISTORY h "
          + "    JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id "
          + "    WHERE i.symbol = ? "
          + "      AND h.exchange_timestamp >= NOW() - INTERVAL ? HOUR "
          + ") AS b "
          + "GROUP BY b.bucket "
          + "ORDER BY b.bucket";

    private final JdbcTemplate jdbc;

    public CandleDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(String symbol) {
        Long n = jdbc.queryForObject(STOCK_EXISTS, Long.class, symbol);
        return n != null && n > 0;
    }

    public List<Candle> candles(String symbol, int bucketSeconds, int hours) {
        return jdbc.query(CANDLES, (rs, n) -> new Candle(
                rs.getLong("bucket"),
                rs.getString("label"),
                rs.getBigDecimal("open_price"),
                rs.getBigDecimal("high_price"),
                rs.getBigDecimal("low_price"),
                rs.getBigDecimal("close_price"),
                rs.getLong("ticks")),
                bucketSeconds, bucketSeconds, symbol, hours);
    }
}