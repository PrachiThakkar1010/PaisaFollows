package com.paisafollows.auth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.common.UuidUtil;

@Service
public class SignupService {

    // ---------- All SQL used by sign-up ----------

    private static final String COUNT_EXISTING_USER =
            "SELECT COUNT(*) FROM `USER` WHERE email = ? OR pan = ?";

    private static final String INSERT_USER =
            "INSERT INTO `USER` (user_id, name, email, phone, password, date_of_birth, pan, created_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())";

    private static final String INSERT_ACCOUNT =
            "INSERT INTO ACCOUNT (account_id, user_id, status) VALUES (?, ?, 'ACTIVE')";

    private static final String INSERT_SEGMENT =
            "INSERT INTO ACCOUNT_SEGMENT (account_id, segment) VALUES (?, ?)";

    private static final String INSERT_WALLET =
            "INSERT INTO WALLET (wallet_id, account_id, currency, available_balance, reserved_balance) "
          + "VALUES (?, ?, 'INR', ?, 0)";

    private static final String INSERT_DEPOSIT =
            "INSERT INTO WALLET_TRANSACTION (transaction_id, wallet_id, transaction_type, amount, status, description, processed_at) "
          + "VALUES (?, ?, 'DEPOSIT', ?, 'COMPLETED', 'Starting virtual balance', NOW())";

    private static final String INSERT_PORTFOLIO =
            "INSERT INTO PORTFOLIO (portfolio_id, account_id, name) VALUES (?, ?, 'Main')";

    private static final String INSERT_WATCHLIST =
            "INSERT INTO WATCHLIST (watchlist_id, account_id, name) VALUES (?, ?, 'My watchlist')";

    // ----------------------------------------------

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final BigDecimal startingBalance;

    public SignupService(JdbcTemplate jdbc,
                         PasswordEncoder passwordEncoder,
                         @Value("${app.starting-balance}") BigDecimal startingBalance) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.startingBalance = startingBalance;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        String pan = req.pan().trim().toUpperCase();

        // Minor or major is decided from the date of birth, not stored separately.
        // It decides which segments (what the account may trade) are created below.
        boolean minor = Period.between(req.dateOfBirth(), LocalDate.now()).getYears() < 18;
        List<String> segments = minor
                ? List.of("EQUITY")
                : List.of("EQUITY", "COMMODITY", "F_AND_O");

        // Friendly check before inserting
        Integer existing = jdbc.queryForObject(COUNT_EXISTING_USER, Integer.class, email, pan);
        if (existing != null && existing > 0) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS, "An account with this email or PAN already exists");
        }

        UUID userId = UuidUtil.newId();
        UUID accountId = UuidUtil.newId();
        UUID walletId = UuidUtil.newId();
        UUID portfolioId = UuidUtil.newId();
        UUID watchlistId = UuidUtil.newId();

        try {
            jdbc.update(INSERT_USER, bytes(userId), req.name().trim(), email, req.phone(),
                    passwordEncoder.encode(req.password()), req.dateOfBirth(), pan);
        } catch (DuplicateKeyException e) {
            // Two people signed up with the same email at the same moment: the UNIQUE key caught it
            throw new ApiException(ErrorCode.ALREADY_EXISTS, "An account with this email or PAN already exists");
        }

        jdbc.update(INSERT_ACCOUNT, bytes(accountId), bytes(userId));
        for (String segment : segments) {
            jdbc.update(INSERT_SEGMENT, bytes(accountId), segment);
        }
        jdbc.update(INSERT_WALLET, bytes(walletId), bytes(accountId), startingBalance);
        jdbc.update(INSERT_DEPOSIT, bytes(UuidUtil.newId()), bytes(walletId), startingBalance);
        jdbc.update(INSERT_PORTFOLIO, bytes(portfolioId), bytes(accountId));
        jdbc.update(INSERT_WATCHLIST, bytes(watchlistId), bytes(accountId));

        return new RegisterResponse(userId, accountId, walletId, portfolioId, watchlistId,startingBalance, minor ? "MINOR" : "MAJOR", segments);
    }

    private static byte[] bytes(UUID id) {
        return UuidUtil.toBytes(id);
    }
}