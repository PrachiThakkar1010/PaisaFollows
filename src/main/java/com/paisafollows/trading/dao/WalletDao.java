package com.paisafollows.trading.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.paisafollows.common.UuidUtil;
import com.paisafollows.trading.Wallet;

/** All wallet SQL. Every method must run inside a @Transactional service method. */
@Repository
public class WalletDao {

    private static final String LOCK_WALLET =
            "SELECT wallet_id, account_id, available_balance, reserved_balance "
          + "FROM WALLET WHERE wallet_id = ? FOR UPDATE";

    /** available -> reserved, only if enough is available */
    private static final String RESERVE =
            "UPDATE WALLET SET available_balance = available_balance - ?, reserved_balance = reserved_balance + ? "
          + "WHERE wallet_id = ? AND available_balance >= ?";

    /** reserved -> available (order cancelled, expired, or leftover after a fill) */
    private static final String RELEASE =
            "UPDATE WALLET SET available_balance = available_balance + ?, reserved_balance = reserved_balance - ? "
          + "WHERE wallet_id = ? AND reserved_balance >= ?";

    /** Money leaves the wallet from the reserved part (a limit buy is filled) */
    private static final String SPEND_RESERVED =
            "UPDATE WALLET SET reserved_balance = reserved_balance - ? "
          + "WHERE wallet_id = ? AND reserved_balance >= ?";

    /** Money leaves straight from available (a market buy is paid instantly) */
    private static final String DEBIT_AVAILABLE =
            "UPDATE WALLET SET available_balance = available_balance - ? "
          + "WHERE wallet_id = ? AND available_balance >= ?";

    /** Money comes in (a sell is filled) */
    private static final String CREDIT =
            "UPDATE WALLET SET available_balance = available_balance + ? WHERE wallet_id = ?";

    private final JdbcTemplate jdbc;

    public WalletDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Reads the wallet and locks it until the transaction ends. Always lock the wallet FIRST. */
    public Wallet lock(UUID walletId) {
        return jdbc.queryForObject(LOCK_WALLET, (rs, n) -> new Wallet(
                UuidUtil.fromBytes(rs.getBytes("wallet_id")),
                UuidUtil.fromBytes(rs.getBytes("account_id")),
                rs.getBigDecimal("available_balance"),
                rs.getBigDecimal("reserved_balance")), UuidUtil.toBytes(walletId));
    }

    /** @return false if there was not enough available balance (nothing changed) */
    public boolean reserve(UUID walletId, BigDecimal amount) {
        return jdbc.update(RESERVE, amount, amount, UuidUtil.toBytes(walletId), amount) == 1;
    }

    public boolean release(UUID walletId, BigDecimal amount) {
        return jdbc.update(RELEASE, amount, amount, UuidUtil.toBytes(walletId), amount) == 1;
    }

    public boolean spendReserved(UUID walletId, BigDecimal amount) {
        return jdbc.update(SPEND_RESERVED, amount, UuidUtil.toBytes(walletId), amount) == 1;
    }

    /** @return false if there was not enough available balance (nothing changed) */
    public boolean debitAvailable(UUID walletId, BigDecimal amount) {
        return jdbc.update(DEBIT_AVAILABLE, amount, UuidUtil.toBytes(walletId), amount) == 1;
    }

    public void credit(UUID walletId, BigDecimal amount) {
        jdbc.update(CREDIT, amount, UuidUtil.toBytes(walletId));
    }
}