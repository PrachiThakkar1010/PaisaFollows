package com.paisafollows.market;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;

@Repository
public class InstrumentDao {

    private static final String FIND_ACTIVE_STOCKS =
            "SELECT instrument_id, symbol, name "
          + "FROM INSTRUMENT "
          + "WHERE instrument_type = 'STOCK' AND status = 'ACTIVE' "
          + "ORDER BY symbol";

    private final JdbcTemplate jdbc;

    public InstrumentDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Instrument> findActiveStocks() {
        return jdbc.query(FIND_ACTIVE_STOCKS, (rs, rowNum) -> new Instrument(
                UuidUtil.fromBytes(rs.getBytes("instrument_id")),
                rs.getString("symbol"),
                rs.getString("name")));
    }
}