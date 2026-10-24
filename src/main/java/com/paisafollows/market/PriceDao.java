package com.paisafollows.market;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.Tick;
import com.paisafollows.common.UuidUtil;

@Repository
public class PriceDao {

    /** Last saved price of every stock (to restart from where we left off). */
    private static final String FIND_SAVED_PRICES =
            "SELECT instrument_id, last_traded_price, previous_close FROM CURRENT_MARKET_PRICE";

    /** Insert the row if the stock has none yet, otherwise update it ("upsert"). */
    private static final String UPSERT_CURRENT_PRICE =
            "INSERT INTO CURRENT_MARKET_PRICE "
          + "  (instrument_id, last_traded_price, previous_close, exchange_timestamp, received_at) "
          + "VALUES (?, ?, ?, ?, NOW()) AS new "
          + "ON DUPLICATE KEY UPDATE "
          + "  last_traded_price  = new.last_traded_price, "
          + "  previous_close     = new.previous_close, "
          + "  exchange_timestamp = new.exchange_timestamp, "
          + "  received_at        = new.received_at";

    /** One snapshot row per stock, appended every minute (for charts later). */
    private static final String INSERT_HISTORY =
            "INSERT INTO MARKET_PRICE_HISTORY "
          + "  (price_update_id, instrument_id, last_traded_price, exchange_timestamp, received_at, event_type) "
          + "VALUES (?, ?, ?, ?, NOW(), 'SNAPSHOT')";

    private final JdbcTemplate jdbc;

    public PriceDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Map of instrument id -> [last price, previous close]. Missing stocks are simply not in the map. */
    public Map<UUID, BigDecimal[]> findSavedPrices() {
        Map<UUID, BigDecimal[]> saved = new HashMap<>();
        jdbc.query(FIND_SAVED_PRICES, rs -> {
            saved.put(UuidUtil.fromBytes(rs.getBytes("instrument_id")),
                    new BigDecimal[] { rs.getBigDecimal("last_traded_price"), rs.getBigDecimal("previous_close") });
        });
        return saved;
    }

    public void upsertCurrentPrices(Collection<Tick> ticks, Map<UUID, BigDecimal> previousCloses) {
        List<Tick> list = List.copyOf(ticks);
        jdbc.batchUpdate(UPSERT_CURRENT_PRICE, list, list.size(), (ps, tick) -> {
            ps.setBytes(1, UuidUtil.toBytes(tick.instrumentId()));
            ps.setBigDecimal(2, tick.price());
            ps.setBigDecimal(3, previousCloses.getOrDefault(tick.instrumentId(), tick.price()));
            ps.setTimestamp(4, Timestamp.from(tick.timestamp()));
        });
    }

    public void insertHistory(Collection<Tick> ticks) {
        List<Tick> list = List.copyOf(ticks);
        jdbc.batchUpdate(INSERT_HISTORY, list, list.size(), (ps, tick) -> {
            ps.setBytes(1, UuidUtil.toBytes(UuidUtil.newId()));
            ps.setBytes(2, UuidUtil.toBytes(tick.instrumentId()));
            ps.setBigDecimal(3, tick.price());
            ps.setTimestamp(4, Timestamp.from(tick.timestamp()));
        });
    }
}