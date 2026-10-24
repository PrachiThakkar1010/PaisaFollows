package com.paisafollows.auth;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.common.UuidUtil;

@Service
public class LoginService {

    // ---------- All SQL used by login and /api/me ----------

    /** Finds the user by email together with the ids of their active account, INR wallet and Main portfolio. */
    private static final String FIND_LOGIN_BY_EMAIL =
            "SELECT u.user_id, u.name, u.email, u.password, "
          + "       a.account_id, w.wallet_id, p.portfolio_id "
          + "FROM `USER` u "
          + "JOIN ACCOUNT a   ON a.user_id    = u.user_id    AND a.status   = 'ACTIVE' "
          + "JOIN WALLET w    ON w.account_id = a.account_id AND w.currency = 'INR' "
          + "JOIN PORTFOLIO p ON p.account_id = a.account_id AND p.name     = 'Main' "
          + "WHERE u.email = ? "
          + "LIMIT 1";

    private static final String FIND_WALLET_BALANCE =
            "SELECT available_balance, reserved_balance FROM WALLET WHERE wallet_id = ?";

    private static final String FIND_SEGMENTS =
            "SELECT segment FROM ACCOUNT_SEGMENT WHERE account_id = ? ORDER BY segment";

    // --------------------------------------------------------

    private static final String BAD_LOGIN = "Invalid email or password";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public LoginService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /** Checks email + password. Returns the logged-in user, or throws 401. */
    public AppUser authenticate(String email, String rawPassword) {
        List<Map<String, Object>> rows = jdbc.queryForList(FIND_LOGIN_BY_EMAIL, email.trim().toLowerCase());
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, BAD_LOGIN);
        }
        Map<String, Object> row = rows.get(0);

        String storedHash = (String) row.get("password");
        if (!passwordEncoder.matches(rawPassword, storedHash)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, BAD_LOGIN);
        }

        return new AppUser(
                UuidUtil.fromBytes((byte[]) row.get("user_id")),
                UuidUtil.fromBytes((byte[]) row.get("account_id")),
                UuidUtil.fromBytes((byte[]) row.get("wallet_id")),
                UuidUtil.fromBytes((byte[]) row.get("portfolio_id")),
                (String) row.get("name"),
                (String) row.get("email"));
    }

    /** Builds the /api/me reply with the balance read fresh from the database. */
    public MeResponse me(AppUser user) {
        Map<String, Object> wallet = jdbc.queryForMap(FIND_WALLET_BALANCE, UuidUtil.toBytes(user.walletId()));
        List<String> segments = jdbc.queryForList(FIND_SEGMENTS, String.class, UuidUtil.toBytes(user.accountId()));

        return new MeResponse(
                user.userId(), user.accountId(), user.walletId(), user.portfolioId(),
                user.name(), user.email(),
                (BigDecimal) wallet.get("available_balance"),
                (BigDecimal) wallet.get("reserved_balance"),
                segments);
    }
}