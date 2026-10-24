package com.paisafollows.trading.dao;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.trading.Holding;

@Repository
public class HoldingDao {

    private static final String LOCK_HOLDING =
            "SELECT holding_id, portfolio_id, instrument_id, quantity, average_buy_price "
          + "FROM HOLDING WHERE portfolio_id = ? AND instrument_id = ? FOR UPDATE";

    private static final String INSERT_HOLDING =
            "INSERT INTO HOLDING (holding_id, portfolio_id, instrument_id, quantity, average_buy_price) "
          + "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE_HOLDING =
            "UPDATE HOLDING SET quantity = ?, average_buy_price = ? WHERE holding_id = ?";

    private static final String DELETE_HOLDING =
            "DELETE FROM HOLDING WHERE holding_id = ?";

    private final JdbcTemplate jdbc;

    public HoldingDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lock order: wallet first, then this. Empty if the portfolio does not own this stock. */
    public Optional<Holding> lock(UUID portfolioId, UUID instrumentId) {
        return jdbc.query(LOCK_HOLDING, (rs, n) -> new Holding(
                        UuidUtil.fromBytes(rs.getBytes("holding_id")),
                        UuidUtil.fromBytes(rs.getBytes("portfolio_id")),
                        UuidUtil.fromBytes(rs.getBytes("instrument_id")),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("average_buy_price")),
                UuidUtil.toBytes(portfolioId), UuidUtil.toBytes(instrumentId)).stream().findFirst();
    }

    public void insert(UUID portfolioId, UUID instrumentId, int quantity, BigDecimal averageBuyPrice) {
        jdbc.update(INSERT_HOLDING, UuidUtil.toBytes(UuidUtil.newId()), UuidUtil.toBytes(portfolioId),
                UuidUtil.toBytes(instrumentId), quantity, averageBuyPrice);
    }

    public void update(UUID holdingId, int quantity, BigDecimal averageBuyPrice) {
        jdbc.update(UPDATE_HOLDING, quantity, averageBuyPrice, UuidUtil.toBytes(holdingId));
    }

    public void delete(UUID holdingId) {
        jdbc.update(DELETE_HOLDING, UuidUtil.toBytes(holdingId));
    }
}