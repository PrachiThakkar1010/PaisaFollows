package com.paisafollows.trading.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;

/** One TRADE row per fill. Paper trades have only one side (see TRADE_chk_3). */
@Repository
public class TradeDao {

    private static final String INSERT_TRADE =
            "INSERT INTO TRADE (trade_id, buy_order_id, sell_order_id, instrument_id, quantity, execution_price, realised_pnl) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;

    public TradeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordBuy(UUID orderId, UUID instrumentId, int quantity, BigDecimal price) {
        jdbc.update(INSERT_TRADE, UuidUtil.toBytes(UuidUtil.newId()), UuidUtil.toBytes(orderId), null,
                UuidUtil.toBytes(instrumentId), quantity, price, null);
    }

    public void recordSell(UUID orderId, UUID instrumentId, int quantity, BigDecimal price, BigDecimal realisedPnl) {
        jdbc.update(INSERT_TRADE, UuidUtil.toBytes(UuidUtil.newId()), null, UuidUtil.toBytes(orderId),
                UuidUtil.toBytes(instrumentId), quantity, price, realisedPnl);
    }
}