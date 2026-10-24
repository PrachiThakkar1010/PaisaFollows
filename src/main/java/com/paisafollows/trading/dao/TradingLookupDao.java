package com.paisafollows.trading.dao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.trading.InstrumentRef;

/** Read-only checks done before accepting an order. */
@Repository
public class TradingLookupDao {

    private static final String FIND_INSTRUMENT_BY_SYMBOL =
            "SELECT instrument_id, symbol, name, instrument_type, status FROM INSTRUMENT WHERE symbol = ?";

    private static final String HAS_SEGMENT =
            "SELECT COUNT(*) FROM ACCOUNT_SEGMENT WHERE account_id = ? AND segment = ?";

    private final JdbcTemplate jdbc;

    public TradingLookupDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<InstrumentRef> findInstrument(String symbol) {
        return jdbc.query(FIND_INSTRUMENT_BY_SYMBOL, (rs, n) -> new InstrumentRef(
                UuidUtil.fromBytes(rs.getBytes("instrument_id")),
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("instrument_type"),
                "ACTIVE".equals(rs.getString("status"))), symbol.toUpperCase()).stream().findFirst();
    }

    public boolean hasSegment(UUID accountId, String segment) {
        Integer count = jdbc.queryForObject(HAS_SEGMENT, Integer.class, UuidUtil.toBytes(accountId), segment);
        return count != null && count > 0;
    }
}