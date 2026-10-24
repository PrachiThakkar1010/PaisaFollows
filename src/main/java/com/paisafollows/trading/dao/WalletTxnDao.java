package com.paisafollows.trading.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;

/** Writes one WALLET_TRANSACTION row for every movement of money. */
@Repository
public class WalletTxnDao {

    private static final String INSERT_TXN =
            "INSERT INTO WALLET_TRANSACTION "
          + "  (transaction_id, wallet_id, transaction_type, amount, status, reference_id, description, processed_at) "
          + "VALUES (?, ?, ?, ?, 'COMPLETED', ?, ?, NOW())";

    private final JdbcTemplate jdbc;

    public WalletTxnDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param type        ORDER_RESERVE, ORDER_RELEASE, TRADE_DEBIT or TRADE_CREDIT
     * @param referenceId the order this money movement belongs to
     */
    public void record(UUID walletId, String type, BigDecimal amount, UUID referenceId, String description) {
        if (amount.signum() <= 0) {
            return;   // the table only allows amount > 0; a zero movement needs no record
        }
        jdbc.update(INSERT_TXN,
                UuidUtil.toBytes(UuidUtil.newId()), UuidUtil.toBytes(walletId), type, amount,
                referenceId == null ? null : UuidUtil.toBytes(referenceId), description);
    }
}